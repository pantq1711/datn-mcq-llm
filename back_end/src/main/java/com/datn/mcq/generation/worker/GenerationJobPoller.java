package com.datn.mcq.generation.worker;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.qc.dto.SolverVerdict;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.generation.parser.SaJsonParser;
import com.datn.mcq.generation.parser.TfJsonParser;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.llm.prompt.McqProductionSchema;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfLabelPattern;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.parser.McqJsonParser;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.llm.client.GeminiRateLimiter;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.client.QuotaExhaustedException;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.checker.GroundingChecker;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.GroundingResult;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Poller chạy ngoài transaction DB:
 * 1. Gọi GenerationJobProcessor.claimNextPendingJob() (transaction ngắn #1).
 * 2. Gọi LlmClient (JSON mode kèm schema theo dạng câu) ngoài transaction.
 * 3. Lưu GeneratedQuestion + ghi câu vào GenerationJob, job vẫn running (transaction ngắn #2),
 *    dựng phạm vi dedup theo purpose của run, chạy pipeline QC (format -> dedup -> answer_check).
 * 4. Cập nhật QuestionStatus và đánh dấu job done trong cùng một transaction ngắn (#3, L-39: đối soát không bao giờ thấy
 *    job done khi câu còn ở trạng thái trung gian).
 */
@Component
public class GenerationJobPoller {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationJobPoller.class);

    public record ProcessedJobSummary(
            boolean processed,
            Integer jobId,
            Integer questionId,
            String jobStatus,
            String questionStatus,
            String error
    ) {}

    private final GenerationJobProcessor processor;
    private final LlmClient llmClient;
    private final QcOrchestrator qcOrchestrator;
    private final DedupScopeResolver dedupScopeResolver;
    private final int maxAttempts;
    private final GeminiRateLimiter rateLimiter;

    public GenerationJobPoller(
            GenerationJobProcessor processor,
            LlmClient llmClient,
            QcOrchestrator qcOrchestrator,
            DedupScopeResolver dedupScopeResolver,
            int maxAttempts) {
        this(processor, llmClient, qcOrchestrator, dedupScopeResolver, maxAttempts, null);
    }

    @Autowired
    public GenerationJobPoller(
            GenerationJobProcessor processor,
            LlmClient llmClient,
            QcOrchestrator qcOrchestrator,
            DedupScopeResolver dedupScopeResolver,
            @Value("${app.worker.max-attempts:3}") int maxAttempts,
            @Autowired(required = false) GeminiRateLimiter rateLimiter) {
        this.processor = processor;
        this.llmClient = llmClient;
        this.qcOrchestrator = qcOrchestrator;
        this.dedupScopeResolver = dedupScopeResolver;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.rateLimiter = rateLimiter;
    }

    /**
     * @param runAnswerCheck false: không gọi Solver; job answer_check (W1-2) khi đó không được nhận
     */
    public ProcessedJobSummary processNextJob(boolean runAnswerCheck) {
        if (rateLimiter != null && !rateLimiter.hasDailyQuota()) {
            return new ProcessedJobSummary(false, null, null, "quota_exhausted", null, null);
        }
        Optional<GenerationJobProcessor.ClaimedJobContext> claimedOpt = processor.claimNextPendingJob(runAnswerCheck);
        if (claimedOpt.isEmpty()) {
            return new ProcessedJobSummary(false, null, null, "idle", null, null);
        }
        GenerationJobProcessor.ClaimedJobContext ctx = claimedOpt.get();
        if (ctx.answerCheck()) {
            return processAnswerCheck(ctx);
        }
        if (ctx.grounding()) {
            return processGrounding(ctx);
        }
        boolean shortAnswer = ctx.questionType() == QuestionType.SA;
        boolean trueFalse = ctx.questionType() == QuestionType.TF;
        // Chế độ sửa đầu ra (escape LaTeX, tự đóng $) chỉ cho run production; run experiment giữ giải mã chặt.
        boolean production = ctx.purpose() != Purpose.EXPERIMENT;
        if (!processor.renewClaim(ctx)) {
            return staleSummary(ctx.jobId());
        }
        LlmCallResult llmResult;
        try {
            llmResult = shortAnswer
                    // V4: câu sa gọi JSON mode kèm schema SA (propertyOrdering: lời giải trước đáp số).
                    ? llmClient.callJsonModeWithSchema(
                            ctx.prompt(), ctx.modelName(), ctx.temperature(),
                            SaPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON)
                    : trueFalse
                    // GĐ3: câu tf gọi JSON mode kèm schema TF (đề dẫn, 4 ý, lời giải, 4 nhãn).
                    ? llmClient.callJsonModeWithSchema(
                            ctx.prompt(), ctx.modelName(), ctx.temperature(),
                            TfPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON)
                    // Câu mcq gọi kèm schema (25/10: không còn cấu hình riêng theo strategy cho run experiment).
                    : llmClient.callJsonModeWithSchema(
                            ctx.prompt(), ctx.modelName(), ctx.temperature(),
                            McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON);
        } catch (QuotaExhaustedException ex) {
            LOG.warn("Job {}: chạm hạn mức ngày của Gemini khi sinh câu, trả về pending tới {}: {}",
                    ctx.jobId(), ex.getResetAt(), ex.getMessage());
            var deferredJob = processor.deferJobOnQuotaExhausted(ctx, ex.getResetAt(), ex.getMessage());
            return new ProcessedJobSummary(
                    true,
                    ctx.jobId(),
                    null,
                    deferredJob.getStatus().getDbValue(),
                    null,
                    ex.getMessage()
            );
        } catch (RuntimeException ex) {
            var failedJob = processor.recordJobFailure(ctx, ex.getMessage(), maxAttempts);
            return new ProcessedJobSummary(
                    true,
                    ctx.jobId(),
                    null,
                    failedJob.getStatus().getDbValue(),
                    null,
                    ex.getMessage()
            );
        }

        // Đợt A (ngày 23/10) + W2-4b (A): lỗi khi giải mã/lưu câu (chưa tạo câu) vẫn đi đường thử lại/failed; lỗi sau khi câu
        // đã lưu trong saveAndCheck kết thúc job done và giữ câu generated để không sinh câu mồ côi.
        try {
            return saveAndCheck(ctx, llmResult, runAnswerCheck, production);
        } catch (StaleJobClaimException ex) {
            return staleSummary(ctx.jobId());
        } catch (RuntimeException ex) {
            String error = "Lỗi sau lời gọi LLM: " + ex.getMessage();
            LOG.warn("Job {}: {}", ctx.jobId(), error, ex);
            var failedJob = processor.recordJobFailure(ctx, error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), null, failedJob.getStatus().getDbValue(), null, error);
        }
    }

    private static ProcessedJobSummary staleSummary(Integer jobId) {
        return new ProcessedJobSummary(true, jobId, null, "stale", null, "stale finish");
    }

    /**
     * W1-2: job answer_check của một bản giáo viên sửa. (1) transaction ngắn dựng đầu vào (bản đã bị thay thì bỏ, không
     * gọi LLM); (2) gọi Solver NGOÀI transaction; (3) transaction ngắn ghi kết quả, trạng thái câu và job done cùng lúc.
     * Lỗi Solver (mạng, hết thời gian chờ, phản hồi hỏng) đi đường thử lại có giới hạn ({@code max-attempts}, backoff)
     * rồi failed; thiếu API key thì failed ngay. Không bao giờ coi lỗi là đạt.
     */
    private ProcessedJobSummary processAnswerCheck(GenerationJobProcessor.ClaimedJobContext ctx) {
        Optional<QuestionQcInput> input;
        try {
            input = processor.prepareAnswerCheck(ctx);
        } catch (StaleJobClaimException ex) {
            return staleSummary(ctx.jobId());
        } catch (RuntimeException ex) {
            var failedJob = processor.recordJobFailure(ctx, "Lỗi trước lời gọi Solver: " + ex.getMessage(),
                    maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(),
                    null, ex.getMessage());
        }
        if (input.isEmpty()) {
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", null, "skipped");
        }
        if (!qcOrchestrator.solverAvailable()) {
            processor.failJobNow(ctx, QcOrchestrator.SOLVER_NOT_CONFIGURED);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "failed", null,
                    QcOrchestrator.SOLVER_NOT_CONFIGURED);
        }
        SolverVerdict verdict;
        try {
            verdict = qcOrchestrator.callSolver(input.get());
        } catch (QuotaExhaustedException ex) {
            LOG.warn("Job {}: chạm hạn mức ngày của Gemini khi chạy answer_check, trả về pending tới {}: {}",
                    ctx.jobId(), ex.getResetAt(), ex.getMessage());
            var deferredJob = processor.deferJobOnQuotaExhausted(ctx, ex.getResetAt(), ex.getMessage());
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(),
                    deferredJob.getStatus().getDbValue(), null, ex.getMessage());
        } catch (RuntimeException ex) {
            String error = "Không hoàn tất answer_check: " + ex.getMessage();
            var failedJob = processor.recordJobFailure(ctx, error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(),
                    null, error);
        }
        try {
            QuestionStatus status = processor.completeAnswerCheck(ctx, verdict);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", status.getDbValue(), null);
        } catch (StaleJobClaimException ex) {
            return staleSummary(ctx.jobId());
        } catch (RuntimeException ex) {
            String error = "Lỗi sau lời gọi LLM: " + ex.getMessage();
            LOG.warn("Job {}: {}", ctx.jobId(), error, ex);
            var failedJob = processor.recordJobFailure(ctx, error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(),
                    null, error);
        }
    }

    /**
     * W2-3: job grounding_check, QC "bám tài liệu" của một câu đã sinh. (1) transaction ngắn dựng đầu vào (câu đã bị thay thì
     * bỏ, không gọi LLM); (2) gọi judge NGOÀI transaction; (3) transaction ngắn ghi kết quả và job done cùng lúc. Lỗi judge
     * (mạng, hết thời gian chờ, phản hồi hỏng) đi đường thử lại có giới hạn ({@code max-attempts}, backoff) rồi failed; thiếu
     * API key thì failed ngay. Không bao giờ đổi trạng thái câu: kết quả chỉ là cảnh báo.
     */
    private ProcessedJobSummary processGrounding(GenerationJobProcessor.ClaimedJobContext ctx) {
        Optional<GenerationJobProcessor.GroundingJobInput> input;
        try {
            input = processor.prepareGrounding(ctx);
        } catch (StaleJobClaimException ex) {
            return staleSummary(ctx.jobId());
        } catch (RuntimeException ex) {
            var failedJob = processor.recordJobFailure(ctx, "Lỗi trước lời gọi judge: " + ex.getMessage(),
                    maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(), null,
                    ex.getMessage());
        }
        if (input.isEmpty()) {
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", null, "skipped");
        }
        if (!qcOrchestrator.groundingAvailable()) {
            processor.failJobNow(ctx, GroundingChecker.NOT_CONFIGURED);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "failed", null,
                    GroundingChecker.NOT_CONFIGURED);
        }
        GroundingResult result;
        try {
            result = qcOrchestrator.callGrounding(input.get().question(), input.get().context());
        } catch (QuotaExhaustedException ex) {
            LOG.warn("Job {}: chạm hạn mức ngày của Gemini khi chạy grounding_check, trả về pending tới {}: {}",
                    ctx.jobId(), ex.getResetAt(), ex.getMessage());
            var deferredJob = processor.deferJobOnQuotaExhausted(ctx, ex.getResetAt(), ex.getMessage());
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(),
                    deferredJob.getStatus().getDbValue(), null, ex.getMessage());
        } catch (RuntimeException ex) {
            String error = "Không hoàn tất grounding_check: " + ex.getMessage();
            var failedJob = processor.recordJobFailure(ctx, error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(), null,
                    error);
        }
        try {
            processor.completeGrounding(ctx, result, input.get().refsJson());
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", null, null);
        } catch (StaleJobClaimException ex) {
            return staleSummary(ctx.jobId());
        } catch (RuntimeException ex) {
            String error = "Lỗi sau lời gọi judge: " + ex.getMessage();
            LOG.warn("Job {}: {}", ctx.jobId(), error, ex);
            var failedJob = processor.recordJobFailure(ctx, error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(), null,
                    error);
        }
    }

    /**
     * V14 (W1-3): lý do Solver lỗi để lưu cạnh câu; chỉ khi answer_check ở trạng thái ERROR (không có kết quả hợp lệ).
     * "Chưa chạy" (NOT_RUN) và "lệch" (có dòng answer_check) không ghi lỗi.
     */
    private static String solverError(QcPipelineReport report) {
        return report.answerCheckStatus() == com.datn.mcq.qc.dto.AnswerCheckStatus.ERROR
                ? report.solverReasoning() : null;
    }

    private ProcessedJobSummary saveAndCheck(GenerationJobProcessor.ClaimedJobContext ctx, LlmCallResult llmResult,
                                             boolean runAnswerCheck, boolean production) {
        boolean shortAnswer = ctx.questionType() == QuestionType.SA;
        boolean trueFalse = ctx.questionType() == QuestionType.TF;
        GeneratedQuestion savedQuestion;
        QuestionQcInput qcInput;
        String parseError;
        if (trueFalse) {
            ParsedTfDto parsedTf = TfJsonParser.parse(llmResult.rawText(), production);
            // Ngày 23/10: job tf theo ma trận có mẫu nhãn bắt buộc; nhãn khác mẫu chỉ cảnh báo (bật "cần xem kỹ").
            String mismatch = parsedTf.parseSuccess()
                    ? TfLabelPattern.mismatchWarning(ctx.tfLabelPattern(), parsedTf.labels()) : null;
            if (mismatch != null) {
                parsedTf = parsedTf.withParseWarning(mismatch);
            }
            savedQuestion = processor.saveTrueFalseFromJob(ctx, llmResult, parsedTf);
            qcInput = GenerationService.trueFalseQcInput(
                    savedQuestion.getId(), parsedTf, ctx.validateLatex(), runAnswerCheck);
            parseError = parsedTf.parseError();
        } else if (shortAnswer) {
            ParsedSaDto parsedSa = SaJsonParser.parse(llmResult.rawText(), production);
            savedQuestion = processor.saveShortAnswerFromJob(ctx, llmResult, parsedSa);
            qcInput = GenerationService.shortAnswerQcInput(
                    savedQuestion.getId(), parsedSa, ctx.validateLatex(), runAnswerCheck);
            parseError = parsedSa.parseError();
        } else {
            ParsedMcqDto parsed = McqJsonParser.parse(llmResult.rawText(), production);
            savedQuestion = processor.saveGeneratedQuestionFromJob(ctx, llmResult, parsed);
            qcInput = GenerationService.mcqQcInput(
                    savedQuestion.getId(), parsed, ctx.validateLatex(), runAnswerCheck);
            parseError = parsed.parseError();
        }

        QcPipelineReport qcReport;
        try {
            DedupScope scope = dedupScopeResolver.resolveForSlot(ctx.runId(), ctx.examSlotId());
            qcReport = runAnswerCheck
                    ? qcOrchestrator.evaluateAndSave(
                            qcInput,
                            scope,
                            () -> {
                                if (!processor.renewClaim(ctx)) {
                                    processor.discardStaleQuestion(ctx, savedQuestion.getId());
                                    throw new StaleJobClaimException(ctx.jobId(), ctx.claimSeq());
                                }
                            })
                    : qcOrchestrator.evaluateAndSave(qcInput, scope);
        } catch (StaleJobClaimException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String error = "Lỗi sau lời gọi LLM: " + ex.getMessage();
            LOG.warn("Job {}: {}", ctx.jobId(), error, ex);
            if (!processor.finishGeneratedJob(ctx, savedQuestion.getId(), QuestionStatus.GENERATED, error, error)) {
                return staleSummary(ctx.jobId());
            }
            return new ProcessedJobSummary(
                    true, ctx.jobId(), savedQuestion.getId(), "done", QuestionStatus.GENERATED.getDbValue(), error);
        }

        if (!processor.finishGeneratedJob(
                ctx, savedQuestion.getId(), qcReport.resultingStatus(), solverError(qcReport), null)) {
            return staleSummary(ctx.jobId());
        }

        return new ProcessedJobSummary(
                true,
                ctx.jobId(),
                savedQuestion.getId(),
                "done",
                qcReport.resultingStatus().getDbValue(),
                parseError
        );
    }
}
