package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-3 phần 2(c): câu sinh mới và câu giáo viên sửa bị dedup với các câu đang có trong CÙNG đề, kể cả câu ở ô khác
 * chưa qua QC (trạng thái generated) và văn bản câu ngân hàng đã sửa lúc duyệt (khác câu nguồn).
 *
 * <p>Dedup hiện có chỉ so với câu qc_passed/in_bank của run production cùng môn và câu cùng run; hai khoảng trống đó là
 * nội dung của test này. Bài kiểm đối chứng ({@link #doiChungCauQcPassedODaKhacDaBiBat}) chứng minh văn bản dựng tay
 * khớp đúng đầu ra của LLM giả, để các test còn lại không đỏ vì sai dữ liệu.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamInExamDedupTest extends ExamFlowTestBase {

    /** Đầu ra của FakeGenerator khi sameMcq = true (k = 1). */
    private static final String SAME_TEXT = "Bậc của đơn thức $2x^1$ là:";
    private static final String[] SAME_OPTIONS = {"$2$", "$1$", "$0$", "$3$"};

    private int matrixId;
    private int examId;
    private int cell1;
    private int cell2;

    @BeforeEach
    void setUp() throws Exception {
        generator.sameMcq = true;
        matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1), mcqCell("Phân thức", "Rút gọn", 1));
        cell1 = cellId(matrixId, 1);
        cell2 = cellId(matrixId, 2);
    }

    /** Đặt vào chỗ {@code slot} một câu sinh sẵn có trạng thái {@code status} (hủy job sinh của chỗ). */
    private int placeGenerated(int slot, int cell, String text, String[] options, String status) {
        int run = count("SELECT run_id FROM exam_slot WHERE id = ?", slot);
        int tpl = count("SELECT template_id FROM exam_matrix_cell WHERE id = ?", cell);
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, explanation, raw_llm_output, parse_success, status,"
                + " llm_difficulty) VALUES (?, ?, ?, ?, ?, ?, ?, 'B', 'Lời giải.', '{}', TRUE, ?, 'easy')", tpl, run,
                text, options[0], options[1], options[2], options[3], status);
        jdbc.update("DELETE FROM generation_job WHERE exam_slot_id = ?", slot);
        jdbc.update("UPDATE exam_slot SET state = 'ready', generated_question_id = ?, attempts = 1 WHERE id = ?", q,
                slot);
        return q;
    }

    private Map<String, Object> firstJobQuestion(int slot) {
        return jdbc.queryForMap("SELECT g.id AS id, g.status AS status FROM generation_job j JOIN generated_question g"
                + " ON g.id = j.question_id WHERE j.exam_slot_id = ? ORDER BY j.id LIMIT 1", slot);
    }

    private Map<String, Object> dedupRow(int questionId) {
        return jdbc.queryForMap("SELECT passed, similarity_score, compared_to_question_id FROM quality_check_result"
                + " WHERE question_id = ? AND check_type = 'dedup'", questionId);
    }

    // ---------- câu sinh mới ----------

    @Test
    void doiChungCauQcPassedODaKhacDaBiBat() throws Exception {
        examId = build(matrixId).get("examId").asInt();
        int slot1 = slotId(examId, cell1, 1);
        int slot2 = slotId(examId, cell2, 1);
        int sibling = placeGenerated(slot1, cell1, SAME_TEXT, SAME_OPTIONS, "qc_passed");

        processAll();

        Map<String, Object> q = firstJobQuestion(slot2);
        assertEquals("duplicate", q.get("status"));
        assertEquals(sibling, dedupRow((Integer) q.get("id")).get("compared_to_question_id"));
    }

    @Test
    void sinhTrungCauODaKhacChuaQuaSolverThiBiDedup() throws Exception {
        examId = build(matrixId).get("examId").asInt();
        int slot1 = slotId(examId, cell1, 1);
        int slot2 = slotId(examId, cell2, 1);
        int sibling = placeGenerated(slot1, cell1, SAME_TEXT, SAME_OPTIONS, "generated");

        processAll();

        Map<String, Object> q = firstJobQuestion(slot2);
        assertEquals("duplicate", q.get("status"), "câu lệch Solver ở ô khác vẫn nằm trong đề");
        Map<String, Object> dedup = dedupRow((Integer) q.get("id"));
        assertEquals(false, dedup.get("passed"));
        assertEquals(sibling, dedup.get("compared_to_question_id"));
        assertEquals(1.0, ((Number) dedup.get("similarity_score")).doubleValue(), 1e-9);

        // Mọi lần sinh lại đều trùng: hết 3 lượt thì chỗ hỏng, không có câu trùng lọt vào đề.
        settle(examId);
        assertEquals("failed", slotState(slot2));
        assertEquals("ready", slotState(slot1), "chỗ đã có câu không bị ảnh hưởng");
    }

    @Test
    void sinhTrungVanBanNganHangDaSuaThiBiDedup() throws Exception {
        // Câu nguồn của câu ngân hàng khác hẳn: văn bản trong ngân hàng là bản giáo viên đã sửa lúc duyệt.
        int item = bankMcq(null, "Đa thức", "easy", "Một câu nguồn khác hoàn toàn", SAME_TEXT, SAME_OPTIONS);
        examId = build(matrixId).get("examId").asInt();
        assertEquals(item, count("SELECT bank_item_id FROM exam_slot WHERE exam_id = ? AND cell_id = ?", examId, cell1));
        int slot2 = slotId(examId, cell2, 1);

        processAll();

        Map<String, Object> q = firstJobQuestion(slot2);
        assertEquals("duplicate", q.get("status"), "dedup phải so với văn bản đang nằm trong đề, không chỉ câu nguồn");
        int source = count("SELECT source_question_id FROM bank_item WHERE id = ?", item);
        assertEquals(source, dedupRow((Integer) q.get("id")).get("compared_to_question_id"),
                "trỏ về câu nguồn của câu ngân hàng (khóa ngoại tới generated_question)");
    }

    @Test
    void khacDangCauHoacChoDaBoCauKhongGayTrung() throws Exception {
        examId = build(matrixId).get("examId").asInt();
        int slot1 = slotId(examId, cell1, 1);
        int slot2 = slotId(examId, cell2, 1);
        placeGenerated(slot1, cell1, SAME_TEXT, SAME_OPTIONS, "generated");
        // Chỗ 1 bị Đổi: câu cũ rời khỏi đề nên không còn là ứng viên so trùng.
        jdbc.update("UPDATE exam_slot SET state = 'generating', generated_question_id = NULL WHERE id = ?", slot1);

        processAll();

        assertEquals("qc_passed", firstJobQuestion(slot2).get("status"));
    }

    // ---------- câu giáo viên sửa ----------

    private ObjectNode editBody(int base, String text, String[] options) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("baseQuestionId", base);
        n.put("questionText", text);
        n.put("optionA", options[0]);
        n.put("optionB", options[1]);
        n.put("optionC", options[2]);
        n.put("optionD", options[3]);
        n.put("correctOption", "B");
        n.put("explanation", "Lời giải do giáo viên.");
        return n;
    }

    private JsonNode edit(int slot, ObjectNode body) throws Exception {
        return json(perform(put("/api/exams/" + examId + "/slots/" + slot + "/question")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk()));
    }

    @Test
    void suaCauThanhTrungCauNganHangDaSuaThiBaoTrung() throws Exception {
        generator.sameMcq = false;
        String[] bankOptions = {"$7$", "$8$", "$9$", "$10$"};
        bankMcq(null, "Đa thức", "easy", "Câu nguồn không liên quan", "Đơn thức $7x^9$ có bậc là:", bankOptions);
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        int slot2 = slotId(examId, cell2, 1);
        assertEquals("ready", slotState(slot2));
        int base = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot2);
        int source = count("SELECT source_question_id FROM bank_item");

        JsonNode item = edit(slot2, editBody(base, "Đơn thức $7x^9$ có bậc là:", bankOptions));

        assertEquals("needs_review", item.get("qcState").asText());
        assertTrue(item.get("reviewReasons").toString().contains("duplicate: " + source), item.toString());
        assertEquals("duplicate", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, item.get("questionId").asInt()));
    }

    @Test
    void suaCauThanhTrungCauODaKhacChuaQuaSolverThiBaoTrung() throws Exception {
        generator.sameMcq = false;
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        int slot1 = slotId(examId, cell1, 1);
        int slot2 = slotId(examId, cell2, 1);
        int base = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot2);
        int sibling = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot1);
        jdbc.update("UPDATE generated_question SET status = 'generated' WHERE id = ?", sibling);
        Map<String, Object> s = jdbc.queryForMap("SELECT question_text, option_a, option_b, option_c, option_d FROM"
                + " generated_question WHERE id = ?", sibling);

        JsonNode item = edit(slot2, editBody(base, (String) s.get("question_text"), new String[] {
                (String) s.get("option_a"), (String) s.get("option_b"), (String) s.get("option_c"),
                (String) s.get("option_d")}));

        assertEquals("needs_review", item.get("qcState").asText());
        assertTrue(item.get("reviewReasons").toString().contains("duplicate: " + sibling), item.toString());
    }

    @Test
    void suaCauKhacHanCacCauTrongDeKhongBiBaoTrung() throws Exception {
        generator.sameMcq = false;
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        int slot2 = slotId(examId, cell2, 1);
        int base = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot2);

        JsonNode item = edit(slot2, editBody(base, "Cho đa thức $P = x^2 - 4$. Nghiệm của $P$ là:",
                new String[] {"$\\pm 2$", "$2$", "$-2$", "$4$"}));

        assertEquals("checking", item.get("qcState").asText(), "không trùng: Solver xếp hàng kiểm lại");
        assertEquals(List.of(), jdbc.queryForList("SELECT id FROM generated_question WHERE status = 'duplicate'"));
        perform(get("/api/exams/" + examId)).andExpect(status().isOk());
    }
}
