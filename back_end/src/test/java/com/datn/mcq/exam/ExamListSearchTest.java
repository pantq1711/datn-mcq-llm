package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đợt 3 (BE-2): GET /api/exams nhận {@code q} tùy chọn, lọc theo tên đề, không phân biệt hoa thường (ILIKE, cùng cách
 * tìm của ngân hàng; còn phân biệt dấu). Giữ phân trang, {@code kind} và thứ tự; chỉ đề chưa xóa của chính giáo viên.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamListSearchTest extends ExamFlowTestBase {

    private int matrix;

    @BeforeEach
    void setUp() throws Exception {
        matrix = createMatrix(mcqCell("Đa thức", null, 1));
    }

    private int buildTitled(int matrixId, String title) throws Exception {
        return json(postJson("/api/exams/build", "{\"matrixId\": " + matrixId + ", \"title\": \"" + title + "\"}")
                .andExpect(status().isOk())).get("examId").asInt();
    }

    /** GET /api/exams với các cặp tham số (tên, giá trị); giá trị truyền nguyên văn, MockMvc tự mã hóa. */
    private static MockHttpServletRequestBuilder listRequest(String... params) {
        MockHttpServletRequestBuilder request = get("/api/exams");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return request;
    }

    private JsonNode list(String... params) throws Exception {
        return json(perform(listRequest(params)).andExpect(status().isOk()));
    }

    private static List<String> titles(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("items").forEach(i -> out.add(i.get("title").asText()));
        return out;
    }

    @Test
    void timTheoTenKhongPhanBietHoaThuongGiuThuTuVaPhanTrang() throws Exception {
        buildTitled(matrix, "Giữa kì I – Toán 8 – 28/11");
        buildTitled(matrix, "Cuối kì I");
        buildTitled(matrix, "GIỮA KÌ II – Toán 8");

        JsonNode all = list("q", "giữa kì");
        assertEquals(List.of("GIỮA KÌ II – Toán 8", "Giữa kì I – Toán 8 – 28/11"), titles(all), "mới nhất trước");
        assertEquals(2, all.get("totalItems").asInt());

        JsonNode first = list("q", "Giữa", "page", "0", "size", "1");
        assertEquals(List.of("GIỮA KÌ II – Toán 8"), titles(first));
        assertEquals(2, first.get("totalItems").asInt());
        assertEquals(2, first.get("totalPages").asInt());
        assertEquals(List.of("Giữa kì I – Toán 8 – 28/11"), titles(list("q", "Giữa", "page", "1", "size", "1")));

        assertEquals(List.of("Giữa kì I – Toán 8 – 28/11"), titles(list("q", "  28/11 ")), "q được trim");
        assertEquals(0, list("q", "giua").get("totalItems").asInt(), "còn phân biệt dấu (không có unaccent)");
        assertEquals(3, list("q", "   ").get("totalItems").asInt(), "q chỉ có khoảng trắng: không lọc");
        assertEquals(3, list("q", "").get("totalItems").asInt(), "q rỗng: không lọc");
    }

    @Test
    void kyTuDaiDienCuaLikeDuocHieuLaChuThuong() throws Exception {
        buildTitled(matrix, "Đề 100% xong");
        buildTitled(matrix, "Đề 1000 câu");
        buildTitled(matrix, "Đề a_b");
        buildTitled(matrix, "Đề axb");
        buildTitled(matrix, "Đề có ! chấm than");

        assertEquals(List.of("Đề 100% xong"), titles(list("q", "100%")));
        assertEquals(List.of("Đề a_b"), titles(list("q", "a_b")));
        assertEquals(List.of("Đề có ! chấm than"), titles(list("q", "!")));
    }

    @Test
    void ketHopKindChiDeCuaMinhVaBoDeDaXoa() throws Exception {
        int mine = buildTitled(matrix, "Giữa kì I");
        int deleted = buildTitled(matrix, "Giữa kì I đã xóa");
        jdbc.update("UPDATE exam SET deleted_at = now() WHERE id = ?", deleted);
        String lesson = "{\"subjectId\": " + subject + ", \"gradeLevel\": 8, \"lesson\": \"Giữa kì theo bài\","
                + " \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1}]}";
        postJson("/api/exams/build-by-lesson", lesson).andExpect(status().isOk());

        int otherMatrix = json(performAs(otherTeacher, post("/api/exam-matrices").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Giữa kì I\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                        + mcqCell("Đa thức", null, 1) + "]}")).andExpect(status().isOk())).get("id").asInt();
        performAs(otherTeacher, post("/api/exams/build").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matrixId\": " + otherMatrix + "}")).andExpect(status().isOk());

        assertEquals(List.of("Giữa kì theo bài", "Giữa kì I"), titles(list("q", "giữa")));
        JsonNode byMatrix = list("q", "giữa", "kind", "matrix");
        assertEquals(List.of("Giữa kì I"), titles(byMatrix));
        assertEquals(mine, byMatrix.get("items").get(0).get("examId").asInt());
        assertEquals(List.of("Giữa kì theo bài"), titles(list("q", "giữa", "kind", "lesson")));
        assertEquals(0, list("q", "cuối", "kind", "lesson").get("totalItems").asInt());
    }

    @Test
    void qQuaDaiHoacKindSaiTra400() throws Exception {
        perform(listRequest("q", "a".repeat(100))).andExpect(status().isOk());
        perform(listRequest("q", "a".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("100")));
        perform(listRequest("q", "a", "kind", "abc")).andExpect(status().isBadRequest());
    }
}
