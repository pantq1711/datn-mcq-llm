package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chống câu cùng khuôn trong MỘT đề (thẩm định 31/10), mức tích hợp, LLM và Solver giả:
 * <ul>
 *   <li>Lớp 1: prompt sinh câu cho một chỗ có khối "câu đã có trong ô này" gồm các chỗ khác cùng ô (kể cả câu mới nhất
 *       của chỗ đang sinh, chưa đối soát), không gồm ô khác và không gồm câu của chính chỗ đang được Đổi.</li>
 *   <li>Lớp 2: xem đề gắn lý do {@code similar_in_exam: <số thứ tự câu kia>} cho câu hệ thống sinh cùng khuôn, tính lúc
 *       xem, không ghi DB, không đổi trạng thái, không so giữa các đề, câu ngân hàng được so nhưng không bị gắn.</li>
 * </ul>
 * FakeGenerator sinh "Bậc của đơn thức $(k+1)x^k$ là:" với k tăng dần: sau khi số thay bằng # hai câu là một.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamSimilarInExamTest extends ExamFlowTestBase {

    private int examId;

    @BeforeEach
    void setUp() {
        generator.sameMcq = false;
    }

    private String sameCellBlock(String prompt) {
        int at = prompt.indexOf(MatrixPromptDraftV0.SAME_CELL_HEADER);
        if (at < 0) {
            return null;
        }
        return prompt.substring(at, prompt.indexOf(MatrixPromptDraftV0.SAME_CELL_RULE));
    }

    private String questionText(int slot) {
        return jdbc.queryForObject("SELECT g.question_text FROM generation_job j JOIN generated_question g ON"
                + " g.id = j.question_id WHERE j.exam_slot_id = ? ORDER BY j.id LIMIT 1", String.class, slot);
    }

    private JsonNode view() throws Exception {
        return json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
    }

    private static List<JsonNode> items(JsonNode view) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode part : view.get("parts")) {
            part.get("items").forEach(out::add);
        }
        return out;
    }

    private static List<String> reasons(JsonNode item) {
        List<String> out = new ArrayList<>();
        item.get("reviewReasons").forEach(r -> out.add(r.asText()));
        return out;
    }

    // ---------- Lớp 1: prompt sinh câu ----------

    @Test
    void choSauThayCauMoiNhatCuaChoTruocKhiChuaDoiSoat() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 2));
        examId = build(matrixId).get("examId").asInt();
        int slot1 = slotId(examId, cellId(matrixId, 1), 1);
        processAll();

        assertEquals(2, generator.mcqPrompts.size());
        String first = generator.mcqPrompts.get(0);
        String second = generator.mcqPrompts.get(1);
        assertEquals(null, sameCellBlock(first), "chỗ đầu chưa thấy câu nào cùng ô");
        String block = sameCellBlock(second);
        assertTrue(block != null && block.contains("1. " + questionText(slot1)), second);
        assertEquals("generating", slotState(slot1), "chưa đối soát: chỗ 1 vẫn đang sinh nhưng câu của nó đã được thấy");
        assertTrue(second.contains(MatrixPromptDraftV0.SAME_CELL_RULE));
    }

    @Test
    void moiChoMangDongKNVaKhiaCanhTheoThuTuChoCungGhiChuCuaJob() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 2));
        examId = build(matrixId).get("examId").asInt();
        processAll();

        String first = generator.mcqPrompts.get(0);
        String second = generator.mcqPrompts.get(1);
        // Ô chỉ có 1 yêu cầu cần đạt (m = 1) và 2 chỗ (n = 2): cả hai chỗ cùng khía cạnh 1/1 nên được dặn đổi hình thức.
        for (String prompt : List.of(first, second)) {
            assertTrue(prompt.contains("khía cạnh thứ 1/1"), prompt);
            assertTrue(prompt.contains(MatrixPromptDraftV0.SAME_FORM_HINT), prompt);
        }
        assertTrue(first.contains("câu thứ 1/2 của ô này"), first);
        assertTrue(second.contains("câu thứ 2/2 của ô này"), second);
        // Ghi chú lưu ở job cũng mang phân công (sinh lại cùng chỗ cho cùng kết quả).
        String note = jdbc.queryForObject("SELECT prompt_note FROM generation_job WHERE exam_slot_id = ?"
                + " ORDER BY id LIMIT 1", String.class, slotId(examId, cellId(matrixId, 1), 2));
        assertTrue(note.contains("khía cạnh thứ 1/1"), note);
    }

    @Test
    void khongLayCauCuaOKhacCungDe() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 2), mcqCell("Phân thức", "Rút gọn", 1));
        examId = build(matrixId).get("examId").asInt();
        processAll();

        assertEquals(3, generator.mcqPrompts.size());
        assertTrue(sameCellBlock(generator.mcqPrompts.get(1)) != null, "chỗ 2 của ô 1 thấy chỗ 1");
        assertEquals(null, sameCellBlock(generator.mcqPrompts.get(2)), "ô 2 chỉ có một câu: không có câu cùng ô");
    }

    @Test
    void doiCauBoCauDangBiThayKhoiDanhSach() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 2));
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        int cell = cellId(matrixId, 1);
        int slot1 = slotId(examId, cell, 1);
        int slot2 = slotId(examId, cell, 2);
        String keptText = jdbc.queryForObject("SELECT g.question_text FROM exam_slot s JOIN generated_question g ON"
                + " g.id = s.generated_question_id WHERE s.id = ?", String.class, slot1);
        String replacedText = jdbc.queryForObject("SELECT g.question_text FROM exam_slot s JOIN generated_question g ON"
                + " g.id = s.generated_question_id WHERE s.id = ?", String.class, slot2);
        assertFalse(keptText.equals(replacedText));

        int before = generator.mcqPrompts.size();
        postJson("/api/exams/" + examId + "/slots/" + slot2 + "/replace", "{}").andExpect(status().isOk());
        processAll();

        assertEquals(before + 1, generator.mcqPrompts.size());
        String block = sameCellBlock(generator.mcqPrompts.get(before));
        assertTrue(block != null, generator.mcqPrompts.get(before));
        assertTrue(block.contains(keptText), "thấy câu của chỗ còn lại: " + block);
        assertFalse(block.contains(replacedText), "câu đang bị thay không có trong danh sách: " + block);
    }

    // ---------- Lớp 2: cờ similar_in_exam khi xem đề ----------

    @Test
    void haiCauCungKhuonTrongDeBiGanCoHaiPhiaKhongDoiTrangThai() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1), mcqCell("Phân thức", "Rút gọn", 1));
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        assertEquals(List.of("qc_passed", "qc_passed"), jdbc.queryForList(
                "SELECT status FROM generated_question ORDER BY id", String.class));
        int qcRows = count("SELECT count(*) FROM quality_check_result");

        JsonNode view = view();
        List<JsonNode> items = items(view);
        assertEquals(List.of("similar_in_exam: 2"), reasons(items.get(0)));
        assertEquals(List.of("similar_in_exam: 1"), reasons(items.get(1)));
        for (JsonNode item : items) {
            assertTrue(item.get("needsReview").asBoolean());
            assertEquals("needs_review", item.get("qcState").asText());
        }
        assertEquals(2, view.get("needsReviewCount").asInt());

        view();
        assertEquals(List.of("qc_passed", "qc_passed"), jdbc.queryForList(
                "SELECT status FROM generated_question ORDER BY id", String.class), "chỉ gắn cờ khi xem, không đổi trạng thái");
        assertEquals(qcRows, count("SELECT count(*) FROM quality_check_result"), "không ghi DB");
    }

    @Test
    void cauNganHangDuocSoNhungKhongBiGanCoCauSinhTrongCapThiBiGan() throws Exception {
        bankMcq(null, "Đa thức", "easy", "Câu nguồn khác hẳn", "Bậc của đơn thức $9x^8$ là:",
                new String[] {"$10$", "$8$", "$0$", "$11$"});
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1), mcqCell("Phân thức", "Rút gọn", 1));
        examId = build(matrixId).get("examId").asInt();
        settle(examId);

        List<JsonNode> items = items(view());
        assertEquals("bank", items.get(0).get("source").asText());
        assertFalse(items.get(0).get("needsReview").asBoolean(), "câu ngân hàng không bị gắn cờ");
        assertEquals(List.of(), reasons(items.get(0)));
        assertEquals("generated", items.get(1).get("source").asText());
        assertEquals(List.of("similar_in_exam: 1"), reasons(items.get(1)));
    }

    @Test
    void cauODeKhacKhongDuocSoSanh() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        int otherExam = build(matrixId).get("examId").asInt();
        settle(otherExam);
        assertEquals(2, count("SELECT count(*) FROM generated_question WHERE status = 'qc_passed'"));

        assertEquals(List.of(), reasons(items(view()).get(0)));
        examId = otherExam;
        assertEquals(List.of(), reasons(items(view()).get(0)));
    }
}
