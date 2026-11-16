package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rà soát logic nghiệp vụ 15/11 (quyết định 16/11):
 * <ul>
 *   <li>NV-10: lấy câu ngân hàng cho ô (tạo đề và Đổi lấy từ ngân hàng) chỉ nhận câu CÙNG khối với ma trận/bài; câu có
 *       khối trống bị loại;</li>
 *   <li>NV-11: tùy chọn {@code useBank} khi tạo đề (theo ma trận và theo bài), mặc định true; false thì mọi chỗ soạn mới.</li>
 * </ul>
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamBankGradeAndUseBankTest extends ExamFlowTestBase {

    private static final String TOPIC = "Đa thức";

    private List<Integer> bankIdsOf(int examId) {
        return jdbc.queryForList("SELECT bank_item_id FROM exam_slot WHERE exam_id = ? AND bank_item_id IS NOT NULL"
                + " ORDER BY id", Integer.class, examId);
    }

    /** Câu mcq dễ trong ngân hàng, template có khối {@code grade} (null: khối trống). */
    private int bankWithGrade(Integer grade, String text) {
        int item = bankMcq(TOPIC, "easy", text);
        jdbc.update("UPDATE question_template SET grade_level = ? WHERE id = (SELECT g.template_id FROM bank_item b"
                + " JOIN generated_question g ON g.id = b.source_question_id WHERE b.id = ?)", grade, item);
        return item;
    }

    // ---------- NV-10: tạo đề ----------

    @Test
    void taoDeKhongLayCauKhacKhoiHoacKhoiTrong() throws Exception {
        bankWithGrade(7, "Câu lớp 7 cùng chủ đề");
        bankWithGrade(null, "Câu không khai khối");
        int matrix = createMatrix(mcqCell(TOPIC, null, 1)); // ma trận khối 8
        JsonNode built = build(matrix);
        assertEquals(0, built.get("fromBank").asInt(), "câu khác khối và câu khối trống không được chọn");
        assertEquals(1, built.get("jobsQueued").asInt());
        assertEquals(List.of(), bankIdsOf(built.get("examId").asInt()));
    }

    @Test
    void taoDeLayCauCungKhoi() throws Exception {
        bankWithGrade(7, "Câu lớp 7");
        int same = bankWithGrade(8, "Câu lớp 8");
        bankWithGrade(9, "Câu lớp 9");
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        JsonNode built = build(matrix);
        assertEquals(1, built.get("fromBank").asInt());
        assertEquals(List.of(same), bankIdsOf(built.get("examId").asInt()));
    }

    @Test
    void taoDeTheoBaiChiLayCauCungKhoiCuaBai() throws Exception {
        bankWithGrade(9, "Câu lớp 9");
        int seven = bankWithGrade(7, "Câu lớp 7");
        int examId = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"gradeLevel\": 7,"
                + " \"lesson\": \"" + TOPIC + "\", \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"easy\","
                + " \"questionCount\": 2}]}").andExpect(status().isOk())).get("examId").asInt();
        assertEquals(List.of(seven), bankIdsOf(examId), "đề lớp 7 chỉ nhận câu lớp 7");
    }

    // ---------- NV-10: Đổi lấy từ ngân hàng ----------

    @Test
    void doiLayTuNganHangKhongLayCauKhacKhoiHoacKhoiTrong() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        bankWithGrade(9, "Câu lớp 9");
        bankWithGrade(null, "Câu khối trống");
        String url = "/api/exams/" + examId + "/slots/" + slot + "/replace";
        postJson(url, "{\"source\": \"bank\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK_EXHAUSTED"));
        int same = bankWithGrade(8, "Câu lớp 8");
        assertEquals(same, json(postJson(url, "{\"source\": \"bank\"}").andExpect(status().isOk()))
                .get("bankItemId").asInt());
    }

    // ---------- NV-11: useBank ----------

    @Test
    void useBankFalseThiMoiChoSoanMoi() throws Exception {
        bankMcq(TOPIC, "easy", "Câu ngân hàng một");
        bankMcq(TOPIC, "easy", "Câu ngân hàng hai");
        int matrix = createMatrix(mcqCell(TOPIC, null, 2));
        JsonNode built = json(postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"useBank\": false}")
                .andExpect(status().isOk()));
        assertEquals(0, built.get("fromBank").asInt());
        assertEquals(2, built.get("jobsQueued").asInt());
        assertEquals(List.of(), bankIdsOf(built.get("examId").asInt()));
        assertEquals(2, count("SELECT requested FROM generation_quota"), "câu soạn mới tính vào hạn mức");
    }

    @Test
    void useBankMacDinhVaTrueVanLayNganHang() throws Exception {
        bankMcq(TOPIC, "easy", "Câu ngân hàng một");
        bankMcq(TOPIC, "easy", "Câu ngân hàng hai");
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        assertEquals(1, build(matrix).get("fromBank").asInt(), "không gửi useBank: mặc định lấy ngân hàng");
        JsonNode explicit = json(postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"useBank\": true}")
                .andExpect(status().isOk()));
        assertEquals(1, explicit.get("fromBank").asInt());
    }

    // ---------- NV-41: tổng điểm trắc nghiệm trong API xem đề ----------

    @Test
    void xemDeTraTongDiemTracNghiemTheoThangDiemCauHinh() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 2));
        int examId = build(matrix).get("examId").asInt();
        JsonNode view = json(perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals(0, new java.math.BigDecimal("0.5").compareTo(view.get("totalScore").decimalValue()),
                "2 câu mcq × 0,25 (kể cả chỗ đang soạn): " + view.get("totalScore"));
    }

    @Test
    void useBankFalseKhiTaoDeTheoBai() throws Exception {
        bankMcq(TOPIC, "easy", "Câu ngân hàng");
        JsonNode built = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"lesson\": \"" + TOPIC + "\", \"useBank\": false, \"cells\": [{\"questionType\":"
                + " \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1}]}").andExpect(status().isOk()));
        assertEquals(0, built.get("fromBank").asInt());
        assertEquals(1, built.get("jobsQueued").asInt());
        JsonNode byDefault = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"lesson\": \"" + TOPIC + "\", \"cells\": [{\"questionType\": \"mcq\","
                + " \"difficulty\": \"easy\", \"questionCount\": 1}]}").andExpect(status().isOk()));
        assertEquals(1, byDefault.get("fromBank").asInt());
    }
}
