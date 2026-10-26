package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.generation.worker.StaleJobRecovery;
import com.datn.mcq.qc.checker.GroundingChecker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-3 bước 6: QC "bám tài liệu" chạy như một job {@code grounding_check} của hàng đợi generation_job: xếp cùng
 * transaction với lúc job sinh câu xong (chỉ khi có tài liệu và câu chưa hỏng), claim SKIP LOCKED, LLM chạy ngoài
 * transaction, kết quả lưu ở question_grounding, hiện ở cờ "cần xem kỹ" và vết QC. NOT_SUPPORTED chỉ là cảnh báo: không
 * đổi trạng thái câu (Q-2), không chặn Giữ.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GroundingPipelineTest extends ExamFlowTestBase {

    private static final String LESSON = "Bài 3. Hằng đẳng thức đáng nhớ";
    private static final String TEXT = "Hằng đẳng thức đáng nhớ thứ nhất: bình phương của một tổng $(a+b)^2 = a^2 + 2ab + b^2$."
            + "\n\nVí dụ: tính $(x+1)^2$.";

    @Autowired
    GenerationJobProcessor processor;
    @Autowired
    StaleJobRecovery staleJobRecovery;

    private int sha;
    private int materialId;

    private int material() {
        materialId = insert("INSERT INTO lesson_material (owner_id, file_name, mime_type, size_bytes, sha256,"
                + " extract_method, extracted_text, char_count) VALUES (?, 'bai3.txt', 'text/plain', ?, ?, 'plain', ?, ?)",
                teacher, TEXT.getBytes(StandardCharsets.UTF_8).length, String.format("%064x", ++sha), TEXT,
                TEXT.codePointCount(0, TEXT.length()));
        return materialId;
    }

    /** Sinh theo bài một câu mcq; có tài liệu thì gắn tài liệu. */
    private int lesson(boolean withMaterial) throws Exception {
        String materials = withMaterial ? ", \"materialIds\": [" + material() + "]" : "";
        return json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"gradeLevel\": 8, \"lesson\": \""
                + LESSON + "\"" + materials + ", \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"easy\","
                + " \"questionCount\": 1}]}").andExpect(status().isOk())).get("examId").asInt();
    }

    private void makeDue() {
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
    }

    /** Chỉ xử lý MỘT job đến hạn (job sinh câu); job grounding xếp sau đó còn ở pending. */
    private void processOnlyGenerate(int examId) throws Exception {
        makeDue();
        assertTrue(poller().processNextJob(true).processed());
        progress(examId);
    }

    private int questionOf(int examId) {
        return count("SELECT generated_question_id FROM exam_slot WHERE exam_id = ? ORDER BY slot_no LIMIT 1", examId);
    }

    private List<Map<String, Object>> groundingJobs() {
        return jdbc.queryForList("SELECT * FROM generation_job WHERE job_type = 'grounding_check' ORDER BY id");
    }

    private Map<String, Object> groundingRow(int questionId) {
        return jdbc.queryForMap("SELECT * FROM question_grounding WHERE question_id = ?", questionId);
    }

    private JsonNode item(int examId) throws Exception {
        return json(perform(get("/api/exams/" + examId)).andExpect(status().isOk())).get("parts").get(0).get("items").get(0);
    }

    private JsonNode trace(int examId) throws Exception {
        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        int slot = slotId(examId, cell, 1);
        return json(perform(get("/api/exams/" + examId + "/slots/" + slot + "/qc-trace")).andExpect(status().isOk()))
                .get("lineages").get(0).get("versions").get(0);
    }

    private static List<String> reasons(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.get("reviewReasons").forEach(n -> out.add(n.asText()));
        return out;
    }

    // ---------- xếp job ----------

    @Test
    void jobGroundingXepKhiJobSinhCauXongMangAnhChupTaiLieuVaChuaGoiLlm() throws Exception {
        int examId = lesson(true);
        Map<String, Object> origin = jdbc.queryForMap("SELECT * FROM generation_job WHERE job_type = 'generate'");
        processOnlyGenerate(examId);

        List<Map<String, Object>> jobs = groundingJobs();
        assertEquals(1, jobs.size());
        Map<String, Object> g = jobs.get(0);
        int question = questionOf(examId);
        assertEquals(question, g.get("question_id"));
        assertEquals("pending", g.get("status"));
        assertEquals(null, g.get("exam_slot_id"), "job grounding không gắn chỗ, để đối soát không nhầm là job sinh câu");
        assertEquals(origin.get("run_id"), g.get("run_id"));
        assertEquals(origin.get("template_id"), g.get("template_id"));
        assertEquals(origin.get("material_context"), g.get("material_context"), "chấm đúng đoạn mà model sinh câu đã thấy");
        assertTrue(count("SELECT count(*) FROM generation_job WHERE id = ? AND material_refs = (SELECT material_refs FROM"
                + " generation_job WHERE id = ?)", g.get("id"), origin.get("id")) == 1, "refs giống job sinh câu");
        assertEquals(0, GROUNDING.calls, "chưa gọi LLM: việc đó thuộc worker, ngoài transaction");
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                question));
    }

    @Test
    void khongCoTaiLieuThiKhongCoJobGrounding() throws Exception {
        int examId = lesson(false);
        settle(examId);
        assertEquals(0, groundingJobs().size());
        assertEquals(0, GROUNDING.calls);
        assertEquals(null, trace(examId).get("qc").get("grounding").textValue(), "không dùng tài liệu: trace không có bước này");
        assertFalse(reasons(item(examId)).stream().anyMatch(r -> r.startsWith("grounding_")));
    }

    @Test
    void cauFormatFailedKhongDuocXepJobGroundingChiCauQuaQcMoiCo() throws Exception {
        generator.brokenMcq = 1;
        int examId = lesson(true);
        settle(examId);
        assertEquals(1, groundingJobs().size(), "câu format_failed bị loại và sinh lại; chỉ câu dùng được mới cần grounding");
        assertEquals(0, count("SELECT count(*) FROM generation_job g JOIN generated_question q ON q.id = g.question_id"
                + " WHERE g.job_type = 'grounding_check' AND q.status = 'format_failed'"));
        assertEquals(1, GROUNDING.calls);
    }

    // ---------- kết quả ----------

    @Test
    void supportedLuuKetQuaKhongDoiTrangThaiCauVaKhongCoCoXemKyChayLaiKhongGoiLai() throws Exception {
        int examId = lesson(true);
        settle(examId);
        int question = questionOf(examId);
        Map<String, Object> row = groundingRow(question);
        assertEquals("supported", row.get("verdict"));
        assertEquals(Boolean.TRUE, row.get("evidence_found"));
        assertTrue(String.valueOf(row.get("evidence_quote")).startsWith("Hằng đẳng thức đáng nhớ thứ nhất"));
        assertEquals("fake-grounding-model", row.get("judge_model"));
        assertEquals(Boolean.TRUE, row.get("generated_with_material"));
        assertEquals(groundingJobs().get(0).get("id"), row.get("source_job_id"));
        assertEquals(1, count("SELECT count(*) FROM question_grounding g JOIN generation_job j ON j.id = g.source_job_id"
                + " WHERE g.question_id = ? AND g.material_refs = j.material_refs", question));
        assertEquals("done", groundingJobs().get(0).get("status"));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                question), "grounding không đổi trạng thái câu (Q-2)");
        JsonNode it = item(examId);
        assertEquals("[]", it.get("reviewReasons").toString());
        assertFalse(it.get("needsReview").asBoolean());
        assertEquals("passed", it.get("qcState").asText());

        processAll();
        processAll();
        assertEquals(1, GROUNDING.calls, "mỗi câu chỉ một lần gọi judge (không gọi lại khi job đã xong)");
        assertTrue(GROUNDING.prompts.get(0).contains("Hằng đẳng thức đáng nhớ thứ nhất"), "prompt có đoạn tài liệu");
        assertTrue(GROUNDING.prompts.get(0).contains("Đáp án của người soạn: B"), "và đáp án của câu");
    }

    @Test
    void notSupportedLaCanhBaoBatCoXemKyKhongDoiTrangThaiVaKhongChanGiu() throws Exception {
        GROUNDING.verdict = "NOT_SUPPORTED";
        GROUNDING.quote = GroundingFake.Quote.NONE;
        int examId = lesson(true);
        settle(examId);
        int question = questionOf(examId);
        Map<String, Object> row = groundingRow(question);
        assertEquals("not_supported", row.get("verdict"));
        assertEquals(null, row.get("evidence_quote"));
        JsonNode it = item(examId);
        assertTrue(it.get("needsReview").asBoolean());
        assertTrue(reasons(it).contains("grounding_not_supported"), reasons(it).toString());
        assertEquals("needs_review", it.get("qcState").asText());
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                question), "không âm thầm loại câu, không đổi Q-2");
        assertEquals(1, json(perform(get("/api/exams")).andExpect(status().isOk())).get("items").get(0).get("needsReviewSlots")
                .asInt(), "danh sách đề đếm cả cờ này");

        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        perform(post("/api/exams/" + examId + "/slots/" + slotId(examId, cell, 1) + "/keep")).andExpect(status().isOk());
        assertEquals(1, count("SELECT count(*) FROM bank_item WHERE source_question_id = ?", question),
                "giáo viên vẫn Giữ được câu bị cảnh báo");
    }

    @Test
    void unknownCungBatCoXemKy() throws Exception {
        GROUNDING.verdict = "UNKNOWN";
        GROUNDING.quote = GroundingFake.Quote.NONE;
        int examId = lesson(true);
        settle(examId);
        assertEquals("unknown", groundingRow(questionOf(examId)).get("verdict"));
        assertTrue(reasons(item(examId)).contains("grounding_unknown"));
    }

    @Test
    void trichDanBiaThiSupportedBiHaXuongUnknown() throws Exception {
        GROUNDING.quote = GroundingFake.Quote.FAKE;
        int examId = lesson(true);
        settle(examId);
        Map<String, Object> row = groundingRow(questionOf(examId));
        assertEquals("unknown", row.get("verdict"));
        assertEquals(Boolean.FALSE, row.get("evidence_found"));
        assertTrue(String.valueOf(row.get("reason")).startsWith(GroundingChecker.EVIDENCE_NOT_FOUND_PREFIX));
        assertTrue(reasons(item(examId)).contains("grounding_unknown"));
    }

    // ---------- trạng thái chờ và lỗi ----------

    @Test
    void jobGroundingDangChoThiCoGroundingPendingVaDanhSachDeVanDemCo() throws Exception {
        int examId = lesson(true);
        processOnlyGenerate(examId);
        JsonNode it = item(examId);
        assertTrue(reasons(it).contains("grounding_pending"), reasons(it).toString());
        assertTrue(it.get("needsReview").asBoolean());
        assertEquals(1, json(perform(get("/api/exams")).andExpect(status().isOk())).get("items").get(0).get("needsReviewSlots")
                .asInt());
        JsonNode grounding = trace(examId).get("qc").get("grounding");
        assertEquals("pending", grounding.get("state").asText());
        assertEquals("pending", grounding.get("job").get("status").asText());
        assertTrue(grounding.get("evidenceQuote").isNull());
    }

    @Test
    void goiJudgeLoiHetLuotThuLaiThiJobFailedVaCoGroundingFailedKhongCoKetQua() throws Exception {
        GROUNDING.fail = true;
        int examId = lesson(true);
        settle(examId);
        Map<String, Object> job = groundingJobs().get(0);
        assertEquals("failed", job.get("status"));
        assertEquals(3, job.get("attempt"), "thử lại có giới hạn (max-attempts) rồi dừng");
        assertTrue(String.valueOf(job.get("last_error")).contains("Gemini lỗi giả lập"));
        assertEquals(3, GROUNDING.calls);
        assertEquals(0, count("SELECT count(*) FROM question_grounding"));
        assertTrue(reasons(item(examId)).contains("grounding_failed"));
        JsonNode grounding = trace(examId).get("qc").get("grounding");
        assertEquals("error", grounding.get("state").asText());
        assertTrue(grounding.get("error").asText().contains("Gemini lỗi giả lập"));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                questionOf(examId)));
    }

    @Test
    void thieuKhoaChoJudgeThiJobFailedNgayKhongGoiLlm() throws Exception {
        GROUNDING.configured = false;
        int examId = lesson(true);
        settle(examId);
        Map<String, Object> job = groundingJobs().get(0);
        assertEquals("failed", job.get("status"));
        assertEquals(1, job.get("attempt"), "lỗi cấu hình không thử lại");
        assertEquals(GroundingChecker.NOT_CONFIGURED, job.get("last_error"));
        assertEquals(0, GROUNDING.calls);
        assertTrue(reasons(item(examId)).contains("grounding_failed"));
    }

    // ---------- vết QC ----------

    @Test
    void vetQcCoBuocGroundingDayDuVaMaTaiLieuDaDung() throws Exception {
        int examId = lesson(true);
        settle(examId);
        JsonNode v = trace(examId);
        JsonNode g = v.get("qc").get("grounding");
        assertEquals("supported", g.get("state").asText());
        assertEquals("Lý do giả lập.", g.get("reason").asText());
        assertTrue(g.get("evidenceQuote").asText().startsWith("Hằng đẳng thức đáng nhớ thứ nhất"));
        assertTrue(g.get("evidenceFound").asBoolean());
        assertEquals("fake-grounding-model", g.get("judgeModel").asText());
        assertNotNull(g.get("checkedAt").asText());
        JsonNode ref = g.get("materialRefs").get(0);
        assertEquals(materialId, ref.get("materialId").asInt());
        assertEquals(0, ref.get("from").asInt());
        assertEquals(TEXT.length(), ref.get("to").asInt());
        assertEquals("whole", ref.get("selection").asText());
        assertEquals("done", g.get("job").get("status").asText());
        assertEquals("[]", v.get("reviewReasons").toString());
    }

    @Test
    void vetQcNotSupportedCoLyDoCoXemKyCuaPhienBan() throws Exception {
        GROUNDING.verdict = "NOT_SUPPORTED";
        int examId = lesson(true);
        settle(examId);
        JsonNode v = trace(examId);
        assertEquals("not_supported", v.get("qc").get("grounding").get("state").asText());
        assertTrue(reasons(v).contains("grounding_not_supported"));
    }

    // ---------- bản giáo viên sửa (bước 8) ----------

    private static void changeStem(ObjectNode body) {
        body.put("questionText", body.get("questionText").asText() + " (đã sửa)");
    }

    private ObjectNode editBody(int base, Consumer<ObjectNode> change) {
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
        change.accept(n);
        return n;
    }

    private ResultActions putEdit(int examId, Consumer<ObjectNode> change) throws Exception {
        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        int slot = slotId(examId, cell, 1);
        return perform(put("/api/exams/" + examId + "/slots/" + slot + "/question").contentType(MediaType.APPLICATION_JSON)
                .content(editBody(questionOf(examId), change).toString()));
    }

    /** Giáo viên sửa câu hiện tại của chỗ; trả id bản mới. */
    private int edit(int examId, Consumer<ObjectNode> change) throws Exception {
        return json(putEdit(examId, change).andExpect(status().isOk())).get("questionId").asInt();
    }

    private JsonNode version(int examId, int index) throws Exception {
        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        int slot = slotId(examId, cell, 1);
        return json(perform(get("/api/exams/" + examId + "/slots/" + slot + "/qc-trace")).andExpect(status().isOk()))
                .get("lineages").get(0).get("versions").get(index);
    }

    @Test
    void suaCauCoTaiLieuXepJobGroundingMoiChoBanSuaVoiCungAnhChupTaiLieu() throws Exception {
        int examId = lesson(true);
        settle(examId);
        Map<String, Object> first = groundingJobs().get(0);
        assertEquals(1, GROUNDING.calls);

        int edited = edit(examId, GroundingPipelineTest::changeStem);

        List<Map<String, Object>> jobs = groundingJobs();
        assertEquals(2, jobs.size(), "bản sửa nội dung cần chấm lại: judge thấy chính nội dung đã sửa");
        Map<String, Object> g = jobs.get(1);
        assertEquals(edited, g.get("question_id"));
        assertEquals("pending", g.get("status"));
        assertEquals(null, g.get("exam_slot_id"));
        assertEquals(first.get("run_id"), g.get("run_id"));
        assertEquals(first.get("template_id"), g.get("template_id"));
        assertEquals(first.get("material_context"), g.get("material_context"),
                "chấm theo đúng đoạn tài liệu model sinh câu đã thấy, không chọn lại");
        assertEquals(1, count("SELECT count(*) FROM generation_job a, generation_job b WHERE a.id = ? AND b.id = ?"
                + " AND a.material_refs = b.material_refs", g.get("id"), first.get("id")));
        assertEquals(1, GROUNDING.calls, "request sửa câu không gọi LLM; việc đó thuộc worker");
        assertEquals(edited, questionOf(examId));
        assertTrue(reasons(item(examId)).contains("grounding_pending"), reasons(item(examId)).toString());
    }

    @Test
    void banSuaDuocChamKhiWorkerChayKetQuaBanGocGiuNguyenVaVetQcCoCaHaiPhienBan() throws Exception {
        int examId = lesson(true);
        settle(examId);
        int original = questionOf(examId);
        int edited = edit(examId, GroundingPipelineTest::changeStem);
        processAll();

        assertEquals(2, GROUNDING.calls);
        Map<String, Object> row = groundingRow(edited);
        assertEquals("supported", row.get("verdict"));
        assertEquals(Boolean.TRUE, row.get("generated_with_material"));
        assertEquals(groundingJobs().get(1).get("id"), row.get("source_job_id"));
        assertEquals(1, count("SELECT count(*) FROM question_grounding WHERE question_id = ?", original),
                "kết quả của bản gốc không bị ghi đè");
        assertTrue(GROUNDING.prompts.get(1).contains("(đã sửa)"), "judge thấy nội dung bản sửa");

        JsonNode first = version(examId, 0).get("qc").get("grounding");
        JsonNode second = version(examId, 1).get("qc").get("grounding");
        assertEquals("supported", first.get("state").asText());
        assertEquals("supported", second.get("state").asText());
        assertEquals(first.get("materialRefs"), second.get("materialRefs"));
        assertFalse(reasons(item(examId)).stream().anyMatch(r -> r.startsWith("grounding_")), reasons(item(examId)).toString());
    }

    @Test
    void chiSuaLoiGiaiCungChamLaiViJudgeThayLoiGiai() throws Exception {
        int examId = lesson(true);
        settle(examId);
        edit(examId, n -> n.put("explanation", n.get("explanation").asText() + " Giải thích thêm."));
        assertEquals(2, groundingJobs().size());
        processAll();
        assertEquals(2, GROUNDING.calls);
        assertTrue(GROUNDING.prompts.get(1).contains("Giải thích thêm."));
    }

    @Test
    void suaCauKhongCoTaiLieuKhongXepJobGrounding() throws Exception {
        int examId = lesson(false);
        settle(examId);
        edit(examId, GroundingPipelineTest::changeStem);
        processAll();
        assertEquals(0, groundingJobs().size());
        assertEquals(0, GROUNDING.calls);
    }

    @Test
    void suaKhongDoiGiHoacSaiHinhDangKhongXepJobGrounding() throws Exception {
        int examId = lesson(true);
        settle(examId);
        int question = questionOf(examId);
        assertEquals(question, edit(examId, n -> { }), "không đổi gì: không tạo bản mới");
        putEdit(examId, n -> n.put("correctOption", "E")).andExpect(status().isBadRequest());
        assertEquals(1, groundingJobs().size());
        assertEquals(question, questionOf(examId));
    }

    @Test
    void suaHaiLanMoiBanMangAnhChupGocBanTruocBiThayThiJobCuaNoBiBoQua() throws Exception {
        int examId = lesson(true);
        settle(examId);
        Map<String, Object> first = groundingJobs().get(0);
        int second = edit(examId, GroundingPipelineTest::changeStem);
        int third = edit(examId, n -> n.put("questionText", n.get("questionText").asText() + " (lần 2)"));

        List<Map<String, Object>> jobs = groundingJobs();
        assertEquals(3, jobs.size());
        assertEquals(third, jobs.get(2).get("question_id"));
        assertEquals(first.get("material_context"), jobs.get(2).get("material_context"),
                "bản sửa lần 2 lấy ảnh chụp từ bản trước, cũng là ảnh chụp gốc");
        processAll();

        assertEquals("skipped: superseded", jdbc.queryForObject("SELECT last_error FROM generation_job WHERE id = ?",
                String.class, jobs.get(1).get("id")), "bản trung gian bị thay trước khi chấm: không gọi LLM");
        assertEquals(2, GROUNDING.calls, "chỉ bản gốc và bản cuối được chấm");
        assertEquals(0, count("SELECT count(*) FROM question_grounding WHERE question_id = ?", second));
        assertEquals(1, count("SELECT count(*) FROM question_grounding WHERE question_id = ?", third));
    }

    @Test
    void khongCoJobGroundingTruocDoThiLayAnhChupTuJobSinhCauCuaChuoiBanSua() throws Exception {
        int examId = lesson(true);
        settle(examId);
        jdbc.update("DELETE FROM question_grounding");
        jdbc.update("DELETE FROM generation_job WHERE job_type = 'grounding_check'");
        Map<String, Object> origin = jdbc.queryForMap("SELECT * FROM generation_job WHERE job_type = 'generate'");

        edit(examId, GroundingPipelineTest::changeStem);

        List<Map<String, Object>> jobs = groundingJobs();
        assertEquals(1, jobs.size());
        assertEquals(origin.get("material_context"), jobs.get(0).get("material_context"));
    }

    // ---------- đo chi phí: token và độ trễ ghi ở dòng kết quả (V21) ----------

    private Map<String, Object> answerCheckRow(int questionId) {
        return jdbc.queryForMap("SELECT * FROM quality_check_result WHERE question_id = ? AND check_type = 'answer_check'",
                questionId);
    }

    @Test
    void soLieuLuotGoiLlmGhiODongKetQuaCuaSolverTrongJobSinhCauVaCuaGrounding() throws Exception {
        int examId = lesson(true);
        settle(examId);
        int question = questionOf(examId);
        Map<String, Object> solver = answerCheckRow(question);
        assertEquals(10, solver.get("token_count"), "Solver chạy trong job sinh câu: số liệu của chính lượt Solver");
        assertEquals(1.0, solver.get("latency_ms"));
        Map<String, Object> grounding = groundingRow(question);
        assertEquals(5, grounding.get("token_count"));
        assertEquals(1.0, grounding.get("latency_ms"));
        assertEquals(0, count("SELECT count(*) FROM quality_check_result WHERE check_type <> 'answer_check'"
                + " AND (token_count IS NOT NULL OR latency_ms IS NOT NULL)"), "chỉ dòng answer_check mang số liệu");
    }

    @Test
    void banSuaDoiNoiDungCoSoLieuSolverODongMoiConBanChiDoiLoiGiaiChepKetQuaNenKhongTinhHaiLan() throws Exception {
        int examId = lesson(true);
        settle(examId);
        int stemEdit = edit(examId, GroundingPipelineTest::changeStem);
        processAll();
        Map<String, Object> rerun = answerCheckRow(stemEdit);
        assertEquals(10, rerun.get("token_count"), "Solver chạy lại ở job answer_check riêng");
        assertEquals(1.0, rerun.get("latency_ms"));
        assertEquals(5, groundingRow(stemEdit).get("token_count"));

        int explanationEdit = edit(examId, n -> n.put("explanation", n.get("explanation").asText() + " Giải thích thêm."));
        processAll();
        Map<String, Object> copied = answerCheckRow(explanationEdit);
        assertEquals(null, copied.get("token_count"), "kết quả Solver được chép, không có lượt gọi LLM nào mới");
        assertEquals(null, copied.get("latency_ms"));
        assertEquals(5, groundingRow(explanationEdit).get("token_count"), "judge thấy lời giải mới nên vẫn tốn một lượt");
    }

    // ---------- chi phí trung bình mỗi câu (docs/thong-ke/06-chi-phi.sql) ----------

    private List<Map<String, Object>> cost() throws IOException {
        return jdbc.queryForList(Files.readString(Path.of("..", "docs", "thong-ke", "06-chi-phi.sql"),
                StandardCharsets.UTF_8));
    }

    private static Map<String, Object> costRow(List<Map<String, Object>> rows, boolean withMaterial) {
        return rows.stream().filter(r -> "mcq".equals(r.get("question_type"))
                        && Boolean.valueOf(withMaterial).equals(r.get("co_tai_lieu"))).findFirst()
                .orElseThrow(() -> new AssertionError("Không có dòng co_tai_lieu = " + withMaterial + " trong " + rows));
    }

    private static double dbl(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).doubleValue();
    }

    @Test
    void thongKe06TinhChiPhiMoiCauLaSinhCongSolverCongGroundingTheoCaChuoiBanSuaVaKhongTinhHaiLanKetQuaChep()
            throws Exception {
        int withMaterial = lesson(true);
        settle(withMaterial);
        settle(lesson(false));

        Map<String, Object> a = costRow(cost(), true);
        assertEquals(1, ((Number) a.get("so_cau_llm")).intValue());
        assertEquals(10.0, dbl(a, "tb_token_sinh"), 0.01);
        assertEquals(10.0, dbl(a, "tb_token_solver"), 0.01);
        assertEquals(5.0, dbl(a, "tb_token_grounding"), 0.01);
        assertEquals(25.0, dbl(a, "tb_token_moi_cau"), 0.01, "10 sinh + 10 Solver + 5 grounding");
        assertEquals(3.0, dbl(a, "tb_latency_ms_moi_cau"), 0.01);
        assertEquals(0, ((Number) a.get("so_cau_thieu_so_do")).intValue());
        Map<String, Object> noMaterial = costRow(cost(), false);
        assertEquals(20.0, dbl(noMaterial, "tb_token_moi_cau"), 0.01, "không tài liệu: không có phần grounding");
        assertEquals(0, ((Number) noMaterial.get("so_cau_thieu_so_do")).intValue(), "không tài liệu thì không cần số grounding");

        edit(withMaterial, GroundingPipelineTest::changeStem);
        processAll();
        assertEquals(40.0, dbl(costRow(cost(), true), "tb_token_moi_cau"), 0.01,
                "tính cả Solver và grounding của bản sửa: 25 + 10 + 5");
        assertEquals(5.0, dbl(costRow(cost(), true), "tb_latency_ms_moi_cau"), 0.01);

        edit(withMaterial, n -> n.put("explanation", n.get("explanation").asText() + " Giải thích thêm."));
        processAll();
        assertEquals(45.0, dbl(costRow(cost(), true), "tb_token_moi_cau"), 0.01,
                "bản chỉ đổi lời giải chép kết quả Solver nên chỉ thêm 5 token grounding");
    }

    @Test
    void thongKe06DemCauThieuSoDoVaKhongLamSaiTrungBinh() throws Exception {
        int examId = lesson(true);
        settle(examId);
        jdbc.update("UPDATE question_grounding SET token_count = NULL, latency_ms = NULL");
        Map<String, Object> row = costRow(cost(), true);
        assertEquals(1, ((Number) row.get("so_cau_thieu_so_do")).intValue(), "câu có tài liệu mà thiếu số grounding");
        assertEquals(null, row.get("tb_token_grounding"), "không có số nào để lấy trung bình");
        assertEquals(20.0, dbl(row, "tb_token_moi_cau"), 0.01, "tổng chỉ cộng phần có số");
    }

    // ---------- thống kê (docs/thong-ke/05-grounding.sql) ----------

    private List<Map<String, Object>> stats() throws IOException {
        return jdbc.queryForList(Files.readString(Path.of("..", "docs", "thong-ke", "05-grounding.sql"),
                StandardCharsets.UTF_8));
    }

    private static Map<String, Object> statRow(List<Map<String, Object>> rows, boolean withMaterial, String state) {
        return rows.stream().filter(r -> "mcq".equals(r.get("question_type"))
                        && Boolean.valueOf(withMaterial).equals(r.get("co_tai_lieu")) && state.equals(r.get("trang_thai")))
                .findFirst().orElseThrow(() -> new AssertionError("Không có dòng " + withMaterial + "/" + state + " trong "
                        + rows));
    }

    private static int num(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    @Test
    void thongKe05DemTheoKetQuaChamChiTinhCauLlmGocVaTachCoKhongTaiLieu() throws Exception {
        // A: bám tài liệu (kèm một bản sửa: không tính vì không phải đầu ra LLM).
        int a = lesson(true);
        settle(a);
        edit(a, GroundingPipelineTest::changeStem);
        processAll();
        // B: judge không trích dẫn. C: judge trích dẫn bịa nên SUPPORTED bị hạ.
        GROUNDING.verdict = "UNKNOWN";
        GROUNDING.quote = GroundingFake.Quote.NONE;
        settle(lesson(true));
        GROUNDING.verdict = "SUPPORTED";
        GROUNDING.quote = GroundingFake.Quote.FAKE;
        settle(lesson(true));
        // D: thiếu khóa judge nên job grounding lỗi ngay.
        GROUNDING.quote = GroundingFake.Quote.REAL;
        GROUNDING.configured = false;
        settle(lesson(true));
        // E: không tài liệu. F: có tài liệu nhưng job grounding còn chờ.
        GROUNDING.configured = true;
        settle(lesson(false));
        processOnlyGenerate(lesson(true));

        List<Map<String, Object>> rows = stats();
        Map<String, Object> supported = statRow(rows, true, "supported");
        assertEquals(1, num(supported, "so_cau_llm"), rows.toString());
        assertEquals(1, num(supported, "trich_dan_kiem_chung_duoc"));
        assertEquals(20.0, ((Number) supported.get("ti_le_phan_tram")).doubleValue(), 0.05, "1 trên 5 câu có tài liệu");
        Map<String, Object> unknown = statRow(rows, true, "unknown");
        assertEquals(2, num(unknown, "so_cau_llm"));
        assertEquals(1, num(unknown, "ha_vi_thieu_trich_dan"), "chỉ câu C bị hạ vì trích dẫn không có thật");
        assertEquals(1, num(statRow(rows, true, "loi"), "so_cau_llm"));
        assertEquals(1, num(statRow(rows, true, "dang_cho"), "so_cau_llm"));
        assertEquals(1, num(statRow(rows, false, "khong_cham"), "so_cau_llm"));
        assertEquals(6, rows.stream().mapToInt(r -> num(r, "so_cau_llm")).sum(), "6 câu LLM gốc, bản sửa không tính");
    }

    // ---------- hàng đợi ----------

    @Test
    void chayKhongSolverKhongNhanJobGroundingVaJobKetORunningDuocTraVePending() throws Exception {
        int examId = lesson(true);
        processOnlyGenerate(examId);
        makeDue();
        assertTrue(processor.claimNextPendingJob(false).isEmpty(), "process-next không Solver chỉ nhận job sinh câu");
        assertEquals("pending", groundingJobs().get(0).get("status"));

        Optional<GenerationJobProcessor.ClaimedJobContext> claimed = processor.claimNextPendingJob(true);
        assertTrue(claimed.isPresent());
        assertEquals("grounding_check", claimed.get().jobType());
        assertEquals("running", groundingJobs().get(0).get("status"));
        jdbc.update("UPDATE generation_job SET started_at = now() - interval '1 hour' WHERE job_type = 'grounding_check'");
        assertEquals(1, staleJobRecovery.resetStaleJobs(), "job grounding kẹt ở running được trả về pending như mọi job");
        assertEquals("pending", groundingJobs().get(0).get("status"));
        assertEquals(0, GROUNDING.calls);
    }
}
