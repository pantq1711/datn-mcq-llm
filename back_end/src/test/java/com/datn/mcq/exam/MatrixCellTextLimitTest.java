package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D2 (soát backend trước deploy): độ dài các trường của ô ma trận đi vào prompt. content ≤ 1000 ký tự; mỗi yêu cầu cần đạt
 * (learningOutcome) ≤ 500 ký tự và tối đa 10 mục. Kiểm ở mọi đường tạo ô: tạo/sửa ma trận và sinh theo bài. Thông báo 400
 * tiếng Việt nêu số ô và giới hạn.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class MatrixCellTextLimitTest extends ExamFlowTestBase {

    private String cell(String content, String outcomeEasy) {
        return "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1,"
                + " \"topic\": \"Đa thức\", \"content\": \"" + content + "\","
                + " \"learningOutcomes\": {\"easy\": \"" + outcomeEasy + "\"}}";
    }

    private String body(String... cells) {
        return "{\"name\": \"Ma trận\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                + String.join(",", cells) + "]}";
    }

    @Test
    void contentToiDa1000KyTu() throws Exception {
        postJson("/api/exam-matrices", body(cell("a".repeat(1000), "Nhận biết."))).andExpect(status().isOk());
        postJson("/api/exam-matrices", body(cell("a".repeat(1001), "Nhận biết.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("Ô 1")))
                .andExpect(jsonPath("$.error").value(Matchers.containsString("content dài tối đa 1000 ký tự")))
                .andExpect(jsonPath("$.error").value(Matchers.containsString("1001")));
        assertEquals(1, count("SELECT count(*) FROM exam_matrix"), "lần bị từ chối không tạo gì");
    }

    @Test
    void moiYeuCauCanDatToiDa500KyTu() throws Exception {
        postJson("/api/exam-matrices", body(cell("Nội dung", "b".repeat(500)))).andExpect(status().isOk());
        postJson("/api/exam-matrices", body(cell("Nội dung", "b".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("Ô 1")))
                .andExpect(jsonPath("$.error").value(Matchers.containsString("tối đa 500 ký tự")));
    }

    @Test
    void doDaiTinhTheoKyTuNguoiDungThayKhongPhaiDonViUtf16() throws Exception {
        // 400 ký tự emoji = 800 đơn vị UTF-16: vẫn trong giới hạn 500 ký tự
        postJson("/api/exam-matrices", body(cell("Nội dung", "😀".repeat(400)))).andExpect(status().isOk());
        postJson("/api/exam-matrices", body(cell("Nội dung", "😀".repeat(501)))).andExpect(status().isBadRequest());
    }

    @Test
    void toiDa10MucYeuCauCanDat() throws Exception {
        String outcomes = IntStream.rangeClosed(1, 11).mapToObj(i -> "\"muc" + i + "\": \"x\"")
                .collect(Collectors.joining(","));
        postJson("/api/exam-matrices", body("{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"easy\","
                + " \"questionCount\": 1, \"topic\": \"Đa thức\", \"learningOutcomes\": {" + outcomes + "}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("tối đa 10 mục")));
    }

    @Test
    void suaMaTranCungKiemDoDai() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        performAs(teacher, put("/api/exam-matrices/" + matrix).contentType(MediaType.APPLICATION_JSON)
                .content(body(cell("a".repeat(1001), "Nhận biết."))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("content dài tối đa 1000 ký tự")));
        assertEquals("Đơn thức", jdbc.queryForObject("SELECT content FROM exam_matrix_cell WHERE matrix_id = ?",
                String.class, matrix));
    }

    private ResultActions lesson(String content, String outcomesJson) throws Exception {
        return postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"lesson\": \"Bài 1\","
                + (content == null ? "" : " \"content\": \"" + content + "\",")
                + (outcomesJson == null ? "" : " \"learningOutcomes\": " + outcomesJson + ",")
                + " \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1}]}");
    }

    @Test
    void sinhTheoBaiCungKiemContentVaYeuCauCanDat() throws Exception {
        lesson("a".repeat(1001), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("content dài tối đa 1000 ký tự")));
        lesson(null, "{\"easy\": \"" + "b".repeat(501) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("tối đa 500 ký tự")));
        assertEquals(0, count("SELECT count(*) FROM exam_matrix"));
        lesson("a".repeat(1000), "{\"easy\": \"" + "b".repeat(500) + "\"}").andExpect(status().isOk());
    }
}
