package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.generation.worker.StaleJobRecovery;
import com.datn.mcq.qc.checker.GroundingChecker;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
