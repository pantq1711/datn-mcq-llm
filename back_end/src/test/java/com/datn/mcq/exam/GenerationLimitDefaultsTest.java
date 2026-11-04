package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1: ngưỡng mặc định khi không cấu hình gì: tối đa 60 câu mỗi ma trận / mỗi đề tạo theo bài (trần job chờ 60 và trần ngày
 * 120 cùng nằm ở {@code app.generation-limits}, kiểm số liệu ở {@link GenerationLimitTest}).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GenerationLimitDefaultsTest extends ExamFlowTestBase {

    private String matrixBody(int firstCell, int secondCell) {
        return "{\"name\": \"Ma trận lớn\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                + mcqCell("Đa thức", null, firstCell) + "," + mcqCell("Phân thức", null, secondCell) + "]}";
    }

    @Test
    void maTranMacDinhToiDa60CauMoiMaTran() throws Exception {
        postJson("/api/exam-matrices", matrixBody(30, 31)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("tối đa 60 câu")))
                .andExpect(jsonPath("$.error").value(Matchers.containsString("61")));
        postJson("/api/exam-matrices", matrixBody(30, 30)).andExpect(status().isOk());
    }

    @Test
    void sinhTheoBaiMacDinhToiDa60Cau() throws Exception {
        postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"lesson\": \"Bài 1\", \"cells\": ["
                + "{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 30},"
                + "{\"questionType\": \"mcq\", \"difficulty\": \"medium\", \"questionCount\": 31}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("tối đa 60 câu")));
    }
}
