package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-3 phần 3: {@code GET /api/exams/{examId}/slots/{slotId}/qc-trace} — chuỗi phiên bản của câu trong ô, từng bước QC
 * (format, tự sửa LaTeX, dedup, Solver: đạt / lệch / lỗi / chưa chạy), lịch sử thao tác của ô. Chỉ đọc, chủ sở hữu mới
 * xem được (404 như các API đề khác), không lộ raw_llm_output.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamQcTraceTest extends ExamFlowTestBase {

    private int matrixId;
    private int cell1;

    @BeforeEach
    void setUp() throws Exception {
        generator.sameMcq = false;
        matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        cell1 = cellId(matrixId, 1);
    }

    private JsonNode trace(int examId, int slotId) throws Exception {
        return json(traceRaw(examId, slotId).andExpect(status().isOk()));
    }

    private ResultActions traceRaw(int examId, int slotId) throws Exception {
        return perform(get("/api/exams/" + examId + "/slots/" + slotId + "/qc-trace"));
    }

    private ObjectNode editBody(int base) {
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", base);
        ObjectNode n = objectMapper.createObjectNode();
        n.put("baseQuestionId", base);
        n.put("questionText", (String) q.get("question_text"));
        n.put("optionA", (String) q.get("option_a"));
        n.put("optionB", (String) q.get("option_b"));
        n.put("optionC", (String) q.get("option_c"));
        n.put("optionD", (String) q.get("option_d"));
        n.put("correctOption", (String) q.get("correct_option"));
        n.put("explanation", (String) q.get("explanation"));
        return n;
    }

    private JsonNode edit(int examId, int slot, ObjectNode body) throws Exception {
        return json(perform(put("/api/exams/" + examId + "/slots/" + slot + "/question")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk()));
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static void collectKeys(JsonNode node, List<String> out) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(k -> {
                out.add(k);
                collectKeys(node.get(k), out);
            });
        } else if (node.isArray()) {
            node.forEach(n -> collectKeys(n, out));
        }
    }

    // ---------- chuỗi phiên bản và từng bước QC ----------

    @Test
    void chuoiLanSinhPhienBanVaBuocQcDungThuTu() throws Exception {
        generator.brokenMcq = 1;
        generator.unclosedMcq = 1;
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        processAll();
        progress(examId);
        processAll();
        progress(examId);
        assertEquals("ready", slotState(slot));
        int q2 = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot);

        // Giáo viên sửa phương án C và đáp án; Solver (luôn chọn B) chưa chạy.
        ObjectNode body = editBody(q2);
        body.put("optionC", "$9$");
        body.put("correctOption", "C");
        JsonNode edited = edit(examId, slot, body);
        int q3 = edited.get("questionId").asInt();
        JsonNode pending = trace(examId, slot);
        JsonNode pendingVersion = pending.get("lineages").get(1).get("versions").get(1);
        assertEquals("pending", pendingVersion.get("qc").get("answerCheck").get("state").asText());
        assertEquals("pending", pendingVersion.get("qc").get("answerCheck").get("job").get("status").asText());
        processAll();

        // Đổi câu khác kèm yêu cầu, rồi Giữ câu mới.
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/replace").contentType(MediaType.APPLICATION_JSON)
                .content("{\"instruction\": \"Dùng ngữ cảnh mua vở\"}")).andExpect(status().isOk());
        settle(examId);
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
        int q4 = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot);

        JsonNode t = trace(examId, slot);
        assertEquals(examId, t.get("examId").asInt());
        assertEquals(slot, t.get("slotId").asInt());
        assertEquals("mcq", t.get("questionType").asText());
        assertEquals("kept", t.get("state").asText());
        assertEquals("generated", t.get("source").asText());
        assertEquals(q4, t.get("currentQuestionId").asInt());
        assertNotNull(t.get("bankItemId"));
        assertFalse(t.get("bankItemId").isNull());

        JsonNode lineages = t.get("lineages");
        assertEquals(3, lineages.size(), "ba lần sinh: lỗi định dạng, câu được sửa, câu sau Đổi");
        List<Integer> jobIds = jdbc.queryForList("SELECT id FROM generation_job WHERE exam_slot_id = ? ORDER BY id",
                Integer.class, slot);
        assertEquals(jobIds, List.of(lineages.get(0).get("job").get("jobId").asInt(),
                lineages.get(1).get("job").get("jobId").asInt(), lineages.get(2).get("job").get("jobId").asInt()),
                "theo thứ tự các job sinh của chỗ");

        // Lần sinh 1: format_failed, các bước sau không chạy.
        JsonNode l1 = lineages.get(0);
        assertEquals("generated", l1.get("origin").asText());
        assertEquals("done", l1.get("job").get("status").asText());
        JsonNode v1 = l1.get("versions").get(0);
        assertEquals(1, l1.get("versions").size());
        assertEquals("llm", v1.get("kind").asText());
        assertEquals("format_failed", v1.get("status").asText());
        assertFalse(v1.get("current").asBoolean());
        assertFalse(v1.get("qc").get("format").get("passed").asBoolean());
        assertFalse(v1.get("qc").get("format").get("errors").isEmpty());
        assertTrue(v1.get("qc").get("dedup").isNull());
        assertEquals("not_run", v1.get("qc").get("answerCheck").get("state").asText());
        assertEquals("format_failed", v1.get("qc").get("answerCheck").get("reason").asText());

        // Lần sinh 2: câu LLM (tự đóng $, dedup, Solver khớp) rồi bản giáo viên sửa (Solver lệch).
        JsonNode l2 = lineages.get(1);
        assertEquals(2, l2.get("versions").size());
        JsonNode llm = l2.get("versions").get(0);
        assertEquals(q2, llm.get("questionId").asInt());
        assertEquals("llm", llm.get("kind").asText());
        assertEquals("qc_passed", llm.get("status").asText());
        assertTrue(llm.get("qc").get("format").get("passed").asBoolean());
        assertEquals(List.of("option_a: đã tự đóng $"), texts(llm.get("qc").get("format").get("autoRepairs")));
        assertEquals(List.of(), texts(llm.get("qc").get("format").get("warnings")));
        assertTrue(texts(llm.get("reviewReasons")).contains("warning: option_a: đã tự đóng $"));
        JsonNode dedup = llm.get("qc").get("dedup");
        assertTrue(dedup.get("passed").asBoolean());
        double stored = jdbc.queryForObject("SELECT threshold_used FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'dedup'", Double.class, q2);
        assertEquals(stored, dedup.get("threshold").asDouble(), 1e-9, "đúng ngưỡng đã lưu lúc kiểm");
        assertEquals(0.9, dedup.get("threshold").asDouble(), 1e-9, "ngưỡng dedup hiện hành");
        JsonNode solver = llm.get("qc").get("answerCheck");
        assertEquals("consistent", solver.get("state").asText());
        assertEquals("B", solver.get("judgeAnswer").asText());
        assertEquals("r", solver.get("solverReasoning").asText());
        assertNotNull(solver.get("judgeModel"));
        assertEquals("B", llm.get("answer").get("correctOption").asText());

        JsonNode teacherVersion = l2.get("versions").get(1);
        assertEquals(q3, teacherVersion.get("questionId").asInt());
        assertEquals("teacher_edit", teacherVersion.get("kind").asText());
        assertEquals(teacher, teacherVersion.get("editedBy").get("id").asInt());
        assertEquals("Cô An", teacherVersion.get("editedBy").get("name").asText());
        assertTrue(texts(teacherVersion.get("changedFields")).containsAll(List.of("option_c", "correct_option")));
        assertTrue(teacherVersion.get("editDistance").asInt() > 0);
        assertEquals("C", teacherVersion.get("answer").get("correctOption").asText());
        assertFalse(teacherVersion.get("current").asBoolean(), "đã Đổi sang câu khác");
        JsonNode inconsistent = teacherVersion.get("qc").get("answerCheck");
        assertEquals("inconsistent", inconsistent.get("state").asText(), "Solver chọn B, giáo viên chốt C");
        assertEquals("B", inconsistent.get("judgeAnswer").asText());
        assertEquals("r", inconsistent.get("solverReasoning").asText());
        assertTrue(teacherVersion.get("qc").get("dedup").get("passed").asBoolean());

        // Lần sinh 3: câu sau Đổi, hiện tại và đã Giữ.
        JsonNode v4 = lineages.get(2).get("versions").get(0);
        assertEquals(q4, v4.get("questionId").asInt());
        assertTrue(v4.get("current").asBoolean());
        assertEquals("in_bank", v4.get("status").asText());

        // Lịch sử thao tác theo thứ tự thời gian: Sửa, Đổi (kèm yêu cầu), Giữ.
        JsonNode actions = t.get("actions");
        assertEquals(List.of("edit", "replace", "keep"), List.of(actions.get(0).get("action").asText(),
                actions.get(1).get("action").asText(), actions.get(2).get("action").asText()));
        assertEquals(q3, actions.get(0).get("questionId").asInt());
        assertEquals("Cô An", actions.get(0).get("actor").get("name").asText());
        assertTrue(texts(actions.get(0).get("changedFields")).contains("option_c"));
        assertTrue(actions.get(0).get("editDistance").asInt() > 0);
        assertEquals("Dùng ngữ cảnh mua vở", actions.get(1).get("instruction").asText());
        assertEquals(q3, actions.get(1).get("questionId").asInt(), "câu bị Đổi ra khỏi chỗ");
        assertEquals(q4, actions.get(2).get("questionId").asInt());
        assertFalse(actions.get(2).get("bankItemId").isNull());
    }

    // ---------- Solver: lệch / lỗi / chưa chạy ----------

    @Test
    void solverLoiVoiCauLlmGocDuocGhiLaiVaPhanBietVoiChuaChay() throws Exception {
        SOLVER.fail = true;
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        processAll();

        int q = count("SELECT question_id FROM generation_job WHERE exam_slot_id = ?", slot);
        assertEquals(0, count("SELECT count(*) FROM quality_check_result WHERE question_id = ? AND"
                + " check_type = 'answer_check'", q), "lỗi Solver không tạo dòng answer_check");
        String stored = jdbc.queryForObject("SELECT solver_error FROM generated_question WHERE id = ?", String.class,
                q);
        assertNotNull(stored);
        assertTrue(stored.contains("Solver giả: hết thời gian chờ"), stored);

        JsonNode version = trace(examId, slot).get("lineages").get(0).get("versions").get(0);
        JsonNode solver = version.get("qc").get("answerCheck");
        assertEquals("error", solver.get("state").asText(), "lỗi, không phải chưa chạy");
        assertTrue(solver.get("error").asText().contains("Solver giả: hết thời gian chờ"));
        assertTrue(texts(version.get("reviewReasons")).contains("solver_failed"));
        assertEquals("generated", version.get("status").asText());
        assertTrue(version.get("qc").get("dedup").get("passed").asBoolean(), "dedup đã chạy xong trước Solver");
    }

    @Test
    void banSuaSolverThatBaiGhiLoiTrongJob() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        settle(examId);
        SOLVER.fail = true;
        ObjectNode body = editBody(count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot));
        body.put("questionText", "Đơn thức $5x^7$ có bậc là:");
        edit(examId, slot, body);
        for (int i = 0; i < 4; i++) {
            processAll();
        }
        JsonNode versions = trace(examId, slot).get("lineages").get(0).get("versions");
        JsonNode solver = versions.get(versions.size() - 1).get("qc").get("answerCheck");
        assertEquals("error", solver.get("state").asText());
        assertEquals("failed", solver.get("job").get("status").asText());
        assertTrue(solver.get("error").asText().contains("Solver giả: hết thời gian chờ"), solver.toString());
    }

    @Test
    void loiGiaiThichDaiBiCatVaBanChiSuaLoiGiaiChepLoiGiaiThichCuaSolver() throws Exception {
        SOLVER.reasoning = "x".repeat(5000);
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        settle(examId);
        int q = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot);
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, q), "lời giải thích dài không làm hỏng QC");
        assertEquals(4000, count("SELECT char_length(solver_reasoning) FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", q));

        ObjectNode body = editBody(q);
        body.put("explanation", "Lời giải mới của giáo viên.");
        int copy = edit(examId, slot, body).get("questionId").asInt();
        assertEquals(1, SOLVER.calls, "chỉ một lần gọi Solver (câu gốc); bản chỉ sửa lời giải không gọi lại");
        JsonNode versions = trace(examId, slot).get("lineages").get(0).get("versions");
        JsonNode solver = versions.get(1).get("qc").get("answerCheck");
        assertEquals(copy, versions.get(1).get("questionId").asInt());
        assertEquals("consistent", solver.get("state").asText());
        assertEquals(4000, solver.get("solverReasoning").asText().length(), "bản chép giữ lời giải thích của Solver");
    }

    // ---------- câu lấy từ ngân hàng ----------

    @Test
    void chiNganHangCoMotLanSinhOriginBank() throws Exception {
        int item = bankMcq("Đa thức", "easy", "Câu ngân hàng");
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        JsonNode t = trace(examId, slot);
        assertEquals("bank", t.get("source").asText());
        assertEquals(item, t.get("bankItemId").asInt());
        assertEquals(1, t.get("lineages").size());
        JsonNode l = t.get("lineages").get(0);
        assertEquals("bank", l.get("origin").asText());
        assertTrue(l.get("job").isNull());
        JsonNode v = l.get("versions").get(0);
        assertEquals("llm", v.get("kind").asText());
        assertEquals("in_bank", v.get("status").asText());
        assertTrue(v.get("current").asBoolean());
        assertEquals("not_run", v.get("qc").get("answerCheck").get("state").asText());
        assertTrue(v.get("qc").get("format").isNull(), "câu dựng tay không có dòng QC");
        assertEquals(0, t.get("actions").size());
    }

    // ---------- chỉ đọc, quyền, không lộ dữ liệu ----------

    @Test
    void chiDocKhongDoiSoatKhongGhiGi() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        processAll();
        assertEquals("generating", slotState(slot), "job đã xong nhưng chưa ai đối soát");
        List<Integer> before = footprint(slot);

        JsonNode t = trace(examId, slot);

        assertEquals("generating", t.get("state").asText());
        assertEquals("qc_passed", t.get("lineages").get(0).get("versions").get(0).get("status").asText());
        assertEquals(before, footprint(slot), "xem vết QC không được đối soát hay ghi bất cứ gì");
        assertEquals("generating", slotState(slot));
    }

    private List<Integer> footprint(int slot) {
        return List.of(count("SELECT count(*) FROM generated_question"), count("SELECT count(*) FROM generation_job"),
                count("SELECT count(*) FROM exam_slot_action"), count("SELECT count(*) FROM quality_check_result"),
                count("SELECT attempts FROM exam_slot WHERE id = ?", slot),
                count("SELECT solver_retries FROM exam_slot WHERE id = ?", slot));
    }

    @Test
    void giaoVienKhacAdminVaChuaDangNhapKhongXemDuoc() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        processAll();
        List<Integer> before = footprint(slot);

        JsonNode notFound = json(performAs(otherTeacher, get("/api/exams/" + examId + "/slots/" + slot + "/qc-trace"))
                .andExpect(status().isNotFound()));
        assertTrue(notFound.has("error"));
        assertEquals(before, footprint(slot));
        assertEquals("generating", slotState(slot), "giáo viên khác không kích hoạt đối soát");
        mockMvc.perform(get("/api/exams/" + examId + "/slots/" + slot + "/qc-trace").with(AuthTestSupport.admin(
                insert("INSERT INTO users (name, email, role) VALUES ('QT', 'qt.flow@test', 'admin')"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/exams/" + examId + "/slots/" + slot + "/qc-trace"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chonSaiDeHoacChoTra404() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        int otherExam = build(matrixId).get("examId").asInt();
        int otherSlot = slotId(otherExam, cell1, 1);
        traceRaw(examId, otherSlot).andExpect(status().isNotFound());
        traceRaw(examId, 999999).andExpect(status().isNotFound());
        traceRaw(999999, slot).andExpect(status().isNotFound());
        traceRaw(otherExam, otherSlot).andExpect(status().isOk());
    }

    @Test
    void khongLoRawLlmOutputPromptHayChiPhi() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        settle(examId);
        int q = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot);
        String raw = jdbc.queryForObject("SELECT raw_llm_output FROM generated_question WHERE id = ?", String.class, q);
        assertTrue(raw.length() > 20);

        JsonNode t = trace(examId, slot);
        List<String> keys = new ArrayList<>();
        collectKeys(t, keys);
        for (String key : keys) {
            String k = key.toLowerCase();
            assertFalse(k.contains("raw") || k.contains("prompt") || k.contains("token") || k.contains("cost")
                    || k.contains("latency"), "key không được lộ: " + key);
        }
        assertFalse(t.toString().contains(raw), "không lộ nội dung raw_llm_output");
        assertFalse(t.toString().contains("Đây là câu thứ"), "không lộ ghi chú prompt của job");
    }
}
