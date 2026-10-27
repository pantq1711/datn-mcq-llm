package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.datn.mcq.generation.worker.StaleJobRecovery;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2-4b (A): hàng rào claim cho kết quả của worker. Worker 1 chậm quá hạn, job bị trả về pending, worker 2 nhận lại: mọi
 * thứ worker 1 ghi sau đó không được có hiệu lực. Hai worker được mô phỏng tuần tự và có kiểm soát (không luồng thật):
 * worker 2 chạy lồng trong móc của lượt gọi LLM/Solver của worker 1. DB thật datn_mcq_test, LLM giả.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class GenerationJobFenceFlowTest extends ExamFlowTestBase {

    @Autowired
    private StaleJobRecovery staleJobRecovery;

    private int examId;
    private int slot;
    private int job;

    @BeforeEach
    void setUp() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        examId = build(matrixId).get("examId").asInt();
        slot = count("SELECT id FROM exam_slot WHERE exam_id = ?", examId);
        job = count("SELECT id FROM generation_job WHERE exam_slot_id = ?", slot);
    }

    /** Job của worker 1 quá 300 giây: được trả về pending như StaleJobScheduler làm. */
    private void staleReset() {
        jdbc.update("UPDATE generation_job SET started_at = now() - interval '400 seconds' WHERE id = ?", job);
        assertEquals(1, staleJobRecovery.resetStaleJobs());
    }

    /** LLM sinh câu của worker 1: chạy {@code hook} ngay lúc "đang gọi Gemini", rồi trả kết quả (hoặc lỗi 500). */
    private LlmClient worker1Llm(ExamQuestionEditTest.ThrowingRunnable hook, boolean thenFail) {
        return new LlmClient("FAKE_KEY_FOR_TESTS", "fake-model") {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature,
                                                        String schema) {
                try {
                    hook.run();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                if (thenFail) {
                    throw new IllegalStateException("Gemini 500 (worker cũ)");
                }
                return generator.callJsonModeWithSchema(prompt, modelName, temperature, schema);
            }
        };
    }

    private GenerationJobPoller worker1(LlmClient llm) {
        return new GenerationJobPoller(jobProcessor, llm, qcOrchestrator, dedupScopeResolver, 3);
    }

    @Test
    void workerCuGhiKetQuaSauKhiJobDaDuocNhanLaiKhongTaoCauThuHai() {
        GenerationJobPoller w1 = worker1(worker1Llm(() -> {
            staleReset();
            // worker 2 nhận lại job và làm xong trước khi worker 1 kịp ghi gì.
            assertTrue(poller().processNextJob(true).processed());
        }, false));

        GenerationJobPoller.ProcessedJobSummary summary = w1.processNextJob(true);

        assertEquals("stale", summary.jobStatus(), "kết quả của worker 1 bị bỏ");
        assertEquals(1, count("SELECT count(*) FROM generated_question"), "chỉ câu của worker 2");
        Map<String, Object> j = jdbc.queryForMap("SELECT status, attempt, claim_seq, question_id FROM generation_job"
                + " WHERE id = ?", job);
        assertEquals("done", j.get("status"));
        assertEquals(1, j.get("attempt"));
        assertEquals(2, j.get("claim_seq"), "mỗi lần nhận tăng claim_seq");
        assertEquals(count("SELECT id FROM generated_question"), j.get("question_id"));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question", String.class));
    }

    @Test
    void workerCuBaoLoiSauKhiWorkerMoiDaDoneThiJobGiuDoneVaKhongChayLanBa() {
        GenerationJobPoller w1 = worker1(worker1Llm(() -> {
            staleReset();
            assertTrue(poller().processNextJob(true).processed());
        }, true));

        w1.processNextJob(true);

        Map<String, Object> j = jdbc.queryForMap("SELECT status, attempt, last_error, next_run_at FROM generation_job"
                + " WHERE id = ?", job);
        assertEquals("done", j.get("status"), "lỗi muộn của worker cũ không đè kết quả của worker mới");
        assertEquals(1, j.get("attempt"));
        assertNull(j.get("last_error"));
        jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute'");
        assertFalse(poller().processNextJob(true).processed(), "job không chạy lần thứ ba");
        assertEquals(1, count("SELECT count(*) FROM generated_question"));
    }

    @Test
    void workerCuHetHanTrongLucQcThiCauMoCoiBiXoaCungCacDongCon() {
        int[] orphan = new int[1];
        SOLVER.onCall = () -> {
            orphan[0] = count("SELECT question_id FROM generation_job WHERE id = ?", job);
            staleReset();
            // worker 2 nhận job nhưng chưa kịp lưu câu.
            assertTrue(jobProcessor.claimNextPendingJob().isPresent());
        };

        GenerationJobPoller.ProcessedJobSummary summary = poller().processNextJob(true);

        assertEquals("stale", summary.jobStatus());
        assertEquals(0, count("SELECT count(*) FROM generated_question WHERE id = ?", orphan[0]),
                "câu của worker cũ bị xóa cùng transaction với việc loại job");
        assertEquals(0, count("SELECT count(*) FROM generated_question"));
        assertEquals(0, count("SELECT count(*) FROM quality_check_result"), "dòng QC con cũng bị xóa");
        Map<String, Object> j = jdbc.queryForMap("SELECT status, question_id FROM generation_job WHERE id = ?", job);
        assertEquals("running", j.get("status"), "job vẫn của worker 2");
        assertNull(j.get("question_id"));
    }

    @Test
    void cauMoCoiDaNamTrongChoCuaDeThiKhongXoa() {
        int[] orphan = new int[1];
        SOLVER.onCall = () -> {
            orphan[0] = count("SELECT question_id FROM generation_job WHERE id = ?", job);
            staleReset();
            assertTrue(jobProcessor.claimNextPendingJob().isPresent());
            // Giáo viên xem tiến độ đúng lúc đó và chỗ đã dùng câu của worker cũ.
            jdbc.update("UPDATE exam_slot SET state = 'ready', generated_question_id = ? WHERE id = ?", orphan[0],
                    slot);
        };

        GenerationJobPoller.ProcessedJobSummary summary = poller().processNextJob(true);

        assertEquals("stale", summary.jobStatus());
        assertEquals(1, count("SELECT count(*) FROM generated_question WHERE id = ?", orphan[0]),
                "câu đang được chỗ trong đề tham chiếu thì không xóa");
        assertEquals("generated", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, orphan[0]), "job bị loại nên trạng thái QC cuối không được ghi");
        assertEquals(orphan[0], count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot));
    }
}

