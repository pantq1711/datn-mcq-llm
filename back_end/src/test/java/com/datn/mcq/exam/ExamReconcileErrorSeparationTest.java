package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-4b (B): tách lỗi kỹ thuật (ERROR) khỏi bất đồng đáp án (INCONSISTENT) trong {@code ExamBuildService.reconcileSlot}
 * và QC, kèm {@code exam_slot.failure_reason} (V23).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamReconcileErrorSeparationTest extends ExamFlowTestBase {

    private int matrixId;
    private int cell1;

    @BeforeEach
    void setUp() throws Exception {
        matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        cell1 = cellId(matrixId, 1);
    }

    private GenerationJobPoller pollerWith(LlmClient llm) {
        return new GenerationJobPoller(jobProcessor, llm, qcOrchestrator, dedupScopeResolver, 3);
    }

    private void processAllWith(LlmClient llm) {
        GenerationJobPoller p = pollerWith(llm);
        for (int i = 0; i < 50; i++) {
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
            if (!p.processNextJob(true).processed()) {
                return;
            }
        }
    }

    private LlmClient failingGenerator(int failCount, AtomicInteger calls) {
        return new LlmClient("FAKE_KEY_FOR_TESTS", "fake-generator-model") {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature,
                                                        String schema) {
                int c = calls.incrementAndGet();
                if (c <= failCount) {
                    throw new IllegalStateException("Gemini 503 timeout lần " + c);
                }
                return generator.callJsonModeWithSchema(prompt, modelName, temperature, schema);
            }
        };
    }

    private JsonNode firstItem(int examId) throws Exception {
        return json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()))
                .get("parts").get(0).get("items").get(0);
    }

    private JsonNode firstCellSlot(int examId, int cellId) throws Exception {
        return json(perform(get("/api/exams/" + examId + "/cells/" + cellId)).andExpect(status().isOk())).get(0);
    }

    private JsonNode trace(int examId, int slotId) throws Exception {
        return json(perform(get("/api/exams/" + examId + "/slots/" + slotId + "/qc-trace")).andExpect(status().isOk()));
    }

    private static List<String> reasons(JsonNode item) {
        List<String> out = new ArrayList<>();
        item.get("reviewReasons").forEach(n -> out.add(n.asText()));
        return out;
    }

    // ---------- 1. ERROR ở job generate ----------

    @Test
    void jobSinhCauLoiKyThuat3LanThiChoFailedLlmErrorKhongXepJobThuHai() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        AtomicInteger calls = new AtomicInteger();

        processAllWith(failingGenerator(10, calls));
        JsonNode p = progress(examId);

        assertEquals(3, calls.get(), "chỉ thử lại ở mức job (3 lần), không nhân 3 ở mức slot thành 9 lần");
        assertEquals(1, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot),
                "không xếp job sinh câu thứ hai cho slot khi job đầu đã hết lượt thử kỹ thuật");
        Map<String, Object> s = jdbc.queryForMap("SELECT state, attempts, failure_reason FROM exam_slot WHERE id = ?",
                slot);
        assertEquals("failed", s.get("state"));
        assertEquals(1, s.get("attempts"));
        assertEquals("llm_error", s.get("failure_reason"));
        assertEquals("partial", p.get("status").asText());
        assertEquals(1, p.get("failed").asInt());

        JsonNode item = firstItem(examId);
        assertEquals("failed", item.get("state").asText());
        assertEquals("llm_error", item.get("failureReason").asText());

        JsonNode slotDto = firstCellSlot(examId, cell1);
        assertEquals("failed", slotDto.get("state").asText());
        assertEquals("llm_error", slotDto.get("failureReason").asText());

        JsonNode qcTrace = trace(examId, slot);
        assertEquals("failed", qcTrace.get("state").asText());
        assertEquals("llm_error", qcTrace.get("failureReason").asText());
    }

    @Test
    void jobSinhCauLoiKyThuat1LanRoiThanhCongThiChoReady() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);
        AtomicInteger calls = new AtomicInteger();

        processAllWith(failingGenerator(1, calls));
        JsonNode p = progress(examId);

        assertEquals(2, calls.get());
        assertEquals(1, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot));
        assertEquals(2, count("SELECT attempt FROM generation_job WHERE exam_slot_id = ?", slot));
        assertEquals("ready", p.get("status").asText());
        Map<String, Object> s = jdbc.queryForMap("SELECT state, attempts, failure_reason FROM exam_slot WHERE id = ?",
                slot);
        assertEquals("ready", s.get("state"));
        assertEquals(1, s.get("attempts"));
        assertNull(s.get("failure_reason"));
        assertFalse(firstItem(examId).has("failureReason"));
        assertFalse(firstCellSlot(examId, cell1).has("failureReason"));
    }

    // ---------- 2. QC exhausted (format_failed / duplicate) và ràng buộc failure_reason ----------

    @Test
    void formatFailed3LanThiChoFailedQcExhaustedVaDoiCauXoaFailureReason() throws Exception {
        generator.brokenMcq = 3;
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        settle(examId);

        Map<String, Object> s = jdbc.queryForMap("SELECT state, attempts, failure_reason FROM exam_slot WHERE id = ?",
                slot);
        assertEquals("failed", s.get("state"));
        assertEquals(3, s.get("attempts"));
        assertEquals("qc_exhausted", s.get("failure_reason"));
        assertEquals("qc_exhausted", firstItem(examId).get("failureReason").asText());
        assertEquals("qc_exhausted", firstCellSlot(examId, cell1).get("failureReason").asText());
        assertEquals("qc_exhausted", trace(examId, slot).get("failureReason").asText());

        // Ràng buộc DB chk_es_failure_reason: chỉ nhận giá trị thuộc ('llm_error', 'qc_exhausted') khi state = 'failed',
        // và bắt buộc NULL khi state <> 'failed'.
        for (String bad : List.of(
                "UPDATE exam_slot SET failure_reason = 'other' WHERE id = ?",
                "UPDATE exam_slot SET state = 'generating', failure_reason = 'qc_exhausted' WHERE id = ?")) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(bad, slot), bad);
        }

        // Giáo viên bấm Đổi câu trên chỗ hỏng: xóa failure_reason và sinh lại bình thường.
        JsonNode replaced = json(postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{}")
                .andExpect(status().isOk()));
        assertEquals("generating", replaced.get("state").asText());
        assertFalse(replaced.has("failureReason"));
        assertNull(jdbc.queryForObject("SELECT failure_reason FROM exam_slot WHERE id = ?", String.class, slot));

        settle(examId);
        assertEquals("ready", slotState(slot));
        assertNull(jdbc.queryForObject("SELECT failure_reason FROM exam_slot WHERE id = ?", String.class, slot));
    }

    // ---------- 3. ERROR ở Solver (tách khỏi INCONSISTENT) ----------

    @Test
    void solverLoiKyThuatChiThuLaiSolverKhongSinhLaiCauMoiVaGiuCauKemCoSolverFailed() throws Exception {
        SOLVER.fail = true;
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        // Bước 1: chạy job generate. Solver nội tuyến lỗi -> câu vẫn lưu (generated + solver_error) và xếp job answer_check.
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
        assertTrue(poller().processNextJob(true).processed());

        int q = count("SELECT question_id FROM generation_job WHERE exam_slot_id = ?", slot);
        assertEquals("generated", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, q));
        assertEquals(1, count("SELECT count(*) FROM generation_job WHERE question_id = ? AND job_type = 'answer_check'"
                + " AND status = 'pending'", q), "xếp job answer_check để thử lại riêng bước Solver");

        // Khi job answer_check còn pending, đối soát giữ slot ở generating (chưa vội sinh lại hay chốt câu).
        JsonNode whilePending = progress(examId);
        assertEquals("generating", whilePending.get("status").asText());
        assertEquals("generating", slotState(slot));
        assertEquals(1, count("SELECT attempts FROM exam_slot WHERE id = ?", slot));
        assertEquals(0, count("SELECT solver_retries FROM exam_slot WHERE id = ?", slot));

        // Bước 2: chạy hết 3 lần thử của job answer_check (đều lỗi kỹ thuật).
        processAll();
        assertEquals("failed", jdbc.queryForObject("SELECT status FROM generation_job WHERE question_id = ?"
                + " AND job_type = 'answer_check'", String.class, q));
        assertEquals(3, count("SELECT attempt FROM generation_job WHERE question_id = ? AND job_type = 'answer_check'",
                q));
        assertEquals(4, SOLVER.calls, "1 lần nội tuyến + 3 lần thử lại ở job answer_check");

        // Bước 3: đối soát sau khi Solver hết lượt thử kỹ thuật -> KHÔNG sinh lại câu mới, dùng câu hiện có kèm cờ solver_failed.
        JsonNode done = progress(examId);
        assertEquals("ready", done.get("status").asText());
        assertEquals(1, generator.mcqPrompts.size(), "không gọi LLM sinh lại câu mới khi Solver lỗi kỹ thuật");
        assertEquals(1, count("SELECT count(*) FROM generated_question"), "chỉ đúng 1 câu được sinh");
        Map<String, Object> s = jdbc.queryForMap("SELECT state, attempts, solver_retries, generated_question_id,"
                + " failure_reason FROM exam_slot WHERE id = ?", slot);
        assertEquals("ready", s.get("state"));
        assertEquals(1, s.get("attempts"));
        assertEquals(0, s.get("solver_retries"));
        assertEquals(q, s.get("generated_question_id"));
        assertNull(s.get("failure_reason"));

        JsonNode item = firstItem(examId);
        assertEquals("ready", item.get("state").asText());
        assertTrue(item.get("needsReview").asBoolean());
        assertEquals("needs_review", item.get("qcState").asText());
        assertEquals(List.of("solver_failed"), reasons(item));
    }

    @Test
    void solverLoiTamThoiLucSinhCauRoiThanhCongOJobAnswerCheckThiQcPassed() throws Exception {
        // Lần gọi 1 (nội tuyến trong job generate) lỗi mạng; lần gọi 2 (job answer_check) thành công và đồng ý đáp án B.
        SOLVER.fail = true;
        SOLVER.onCall = () -> {
            // Lần 1 vẫn ném lỗi vì SOLVER.fail = true; ngay trước lần 2 sẽ tắt fail ở dưới.
        };
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'");
        assertTrue(poller().processNextJob(true).processed());
        int q = count("SELECT question_id FROM generation_job WHERE exam_slot_id = ?", slot);
        assertNotNull(jdbc.queryForObject("SELECT solver_error FROM generated_question WHERE id = ?", String.class, q));

        // Bây giờ Solver hồi phục cho job answer_check.
        SOLVER.fail = false;
        settle(examId);

        assertEquals(1, generator.mcqPrompts.size(), "không sinh lại câu");
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, q));
        assertNull(jdbc.queryForObject("SELECT solver_error FROM generated_question WHERE id = ?", String.class, q),
                "Solver thử lại thành công thì xóa solver_error cũ");
        assertEquals("ready", slotState(slot));
        JsonNode item = firstItem(examId);
        assertFalse(item.get("needsReview").asBoolean());
        assertEquals("passed", item.get("qcState").asText());
        assertEquals(List.of(), reasons(item));
    }

    // ---------- 4. INCONSISTENT giữ nguyên Q-2 ----------

    @Test
    void solverLechDapAnGiuNguyenQuyTacQ2SinhLai1LanRoiGiuKemCoSolverInconsistent() throws Exception {
        SOLVER.mcqJudge = "C";
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        settle(examId);

        assertEquals(0, count("SELECT count(*) FROM generation_job WHERE job_type = 'answer_check'"),
                "Solver trả kết quả hợp lệ nhưng lệch đáp án thì không phải lỗi kỹ thuật, không xếp job answer_check");
        assertEquals(2, generator.mcqPrompts.size(), "Q-2: sinh lại đúng 1 lần");
        Map<String, Object> s = jdbc.queryForMap("SELECT state, attempts, solver_retries FROM exam_slot WHERE id = ?",
                slot);
        assertEquals("ready", s.get("state"));
        assertEquals(2, s.get("attempts"));
        assertEquals(1, s.get("solver_retries"));
        assertEquals(List.of("solver_inconsistent"), reasons(firstItem(examId)));
    }

    // ---------- 4b. Solver v3 xét từng phương án (thẩm định 31/10): MULTIPLE_CORRECT / NO_CORRECT theo đường Q-2 ----------

    private String judgeAnswerOfSlot(int slot) {
        return jdbc.queryForObject("SELECT qcr.judge_answer FROM quality_check_result qcr JOIN exam_slot s"
                + " ON s.generated_question_id = qcr.question_id WHERE s.id = ? AND qcr.check_type = 'answer_check'",
                String.class, slot);
    }

    private String solverReasoningOfSlot(int slot) {
        return jdbc.queryForObject("SELECT qcr.solver_reasoning FROM quality_check_result qcr JOIN exam_slot s"
                + " ON s.generated_question_id = qcr.question_id WHERE s.id = ? AND qcr.check_type = 'answer_check'",
                String.class, slot);
    }

    @Test
    void solverThayHaiPhuongAnDungThiSinhLai1LanRoiGiuKemCoSolverMultipleCorrect() throws Exception {
        SOLVER.mcqOptions = "A,B";
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        settle(examId);

        assertEquals(0, count("SELECT count(*) FROM generation_job WHERE job_type = 'answer_check'"),
                "nhiều phương án đúng là kết quả hợp lệ, không phải lỗi kỹ thuật");
        assertEquals(2, generator.mcqPrompts.size(), "Q-2: sinh lại đúng 1 lần như INCONSISTENT");
        Map<String, Object> s = jdbc.queryForMap("SELECT state, attempts, solver_retries FROM exam_slot WHERE id = ?",
                slot);
        assertEquals("ready", s.get("state"));
        assertEquals(2, s.get("attempts"));
        assertEquals(1, s.get("solver_retries"));
        assertEquals("multiple", judgeAnswerOfSlot(slot));
        String notes = solverReasoningOfSlot(slot);
        assertTrue(notes.startsWith("[Xét từng phương án] 2/4 phương án đúng"), notes);
        assertTrue(notes.contains("A: đúng – kiểm A") && notes.contains("C: sai – kiểm C"), notes);
        JsonNode item = firstItem(examId);
        assertTrue(item.get("needsReview").asBoolean());
        assertEquals(List.of("solver_multiple_correct"), reasons(item));
    }

    @Test
    void solverKhongThayPhuongAnNaoDungThiSinhLai1LanRoiGiuKemCoSolverNoCorrect() throws Exception {
        SOLVER.mcqOptions = "";
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        settle(examId);

        assertEquals(2, generator.mcqPrompts.size(), "Q-2: sinh lại đúng 1 lần");
        assertEquals(1, count("SELECT solver_retries FROM exam_slot WHERE id = ?", slot));
        assertEquals("none", judgeAnswerOfSlot(slot));
        assertEquals(List.of("solver_no_correct"), reasons(firstItem(examId)));
    }

    @Test
    void solverThayDungMotPhuongAnKhopDapAnThiQcPassedKhongSinhLai() throws Exception {
        SOLVER.mcqOptions = "B";
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        settle(examId);

        assertEquals(1, generator.mcqPrompts.size());
        assertEquals("B", judgeAnswerOfSlot(slot));
        JsonNode item = firstItem(examId);
        assertEquals("passed", item.get("qcState").asText());
        assertEquals(List.of(), reasons(item));
    }

    @Test
    void solverThayDungMotPhuongAnKhacDapAnThiVanLaSolverInconsistent() throws Exception {
        SOLVER.mcqOptions = "C";
        int examId = build(matrixId).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        settle(examId);

        assertEquals(2, generator.mcqPrompts.size(), "Q-2: sinh lại đúng 1 lần");
        assertEquals("C", judgeAnswerOfSlot(slot));
        assertEquals(List.of("solver_inconsistent"), reasons(firstItem(examId)));
    }

    // ---------- 5. ERROR ở grounding ----------

    @Test
    void groundingLoiKyThuat3LanThiGiuCauKemCoGroundingFailedKhongSinhLai() throws Exception {
        String text = "Đơn thức là biểu thức đại số chỉ gồm một số, hoặc một biến, hoặc một tích giữa các số và các biến.";
        int m = insert("INSERT INTO lesson_material (owner_id, file_name, mime_type, size_bytes, sha256,"
                + " extract_method, extracted_text, char_count) VALUES (?, 'don-thuc.txt', 'text/plain', ?, ?,"
                + " 'plain', ?, ?)", teacher, text.getBytes(StandardCharsets.UTF_8).length, "a".repeat(64), text,
                text.length());
        GROUNDING.fail = true;

        int examId = json(postJson("/api/exams/build", "{\"matrixId\": " + matrixId + ", \"materialIds\": [" + m + "]}")
                .andExpect(status().isOk())).get("examId").asInt();
        int slot = slotId(examId, cell1, 1);

        settle(examId);

        assertEquals(1, generator.mcqPrompts.size(), "grounding lỗi kỹ thuật không làm sinh lại câu");
        assertEquals(3, GROUNDING.calls, "job grounding_check thử tối đa 3 lần");
        assertEquals("ready", slotState(slot));
        JsonNode item = firstItem(examId);
        assertTrue(item.get("needsReview").asBoolean());
        assertEquals(List.of("grounding_failed"), reasons(item));
    }
}

