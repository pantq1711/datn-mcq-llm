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
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.checker.GroundingChecker;
import com.datn.mcq.qc.dto.GroundingResult;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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

    public GenerationJobPoller(
            GenerationJobProcessor processor,
            LlmClient llmClient,
            QcOrchestrator qcOrchestrator,
            DedupScopeResolver dedupScopeResolver,
            @Value("${app.worker.max-attempts:3}") int maxAttempts) {
        this.processor = processor;
        this.llmClient = llmClient;
        this.qcOrchestrator = qcOrchestrator;
        this.dedupScopeResolver = dedupScopeResolver;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-delay-ms:15000}")
    public void pollOnSchedule() {
        processNextJob(true);
    }

    /**
     * @param runAnswerCheck false: không gọi Solver; job answer_check (W1-2) khi đó không được nhận
     */
    public ProcessedJobSummary processNextJob(boolean runAnswerCheck) {
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
        } catch (RuntimeException ex) {
            var failedJob = processor.recordJobFailure(ctx.jobId(), ex.getMessage(), maxAttempts);
            return new ProcessedJobSummary(
                    true,
                    ctx.jobId(),
                    null,
                    failedJob.getStatus().getDbValue(),
                    null,
                    ex.getMessage()
            );
        }

        // Đợt A (ngày 23/10): lỗi ở phần sau lời gọi LLM (giải mã, lưu câu, QC) đi cùng đường thử lại/failed như lỗi
        // gọi LLM, không để job kẹt ở running.
        try {
            return saveAndCheck(ctx, llmResult, runAnswerCheck, production);
        } catch (RuntimeException ex) {
            String error = "Lỗi sau lời gọi LLM: " + ex.getMessage();
            LOG.warn("Job {}: {}", ctx.jobId(), error, ex);
            var failedJob = processor.recordJobFailure(ctx.jobId(), error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), null, failedJob.getStatus().getDbValue(), null, error);
        }
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
        } catch (RuntimeException ex) {
            var failedJob = processor.recordJobFailure(ctx.jobId(), "Lỗi trước lời gọi Solver: " + ex.getMessage(),
                    maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(),
                    null, ex.getMessage());
        }
        if (input.isEmpty()) {
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", null, "skipped");
        }
        if (!qcOrchestrator.solverAvailable()) {
            processor.failJobNow(ctx.jobId(), QcOrchestrator.SOLVER_NOT_CONFIGURED);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "failed", null,
                    QcOrchestrator.SOLVER_NOT_CONFIGURED);
        }
        SolverVerdict verdict;
        try {
            verdict = qcOrchestrator.callSolver(input.get());
        } catch (RuntimeException ex) {
            String error = "Không hoàn tất answer_check: " + ex.getMessage();
            var failedJob = processor.recordJobFailure(ctx.jobId(), error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(),
                    null, error);
        }
        try {
            QuestionStatus status = processor.completeAnswerCheck(ctx, verdict);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", status.getDbValue(), null);
        } catch (RuntimeException ex) {
            String error = "Lỗi sau lời gọi LLM: " + ex.getMessage();
            LOG.warn("Job {}: {}", ctx.jobId(), error, ex);
            var failedJob = processor.recordJobFailure(ctx.jobId(), error, maxAttempts);
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
        } catch (RuntimeException ex) {
            var failedJob = processor.recordJobFailure(ctx.jobId(), "Lỗi trước lời gọi judge: " + ex.getMessage(),
                    maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(), null,
                    ex.getMessage());
        }
        if (input.isEmpty()) {
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", null, "skipped");
        }
        if (!qcOrchestrator.groundingAvailable()) {
            processor.failJobNow(ctx.jobId(), GroundingChecker.NOT_CONFIGURED);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "failed", null,
                    GroundingChecker.NOT_CONFIGURED);
        }
        GroundingResult result;
        try {
            result = qcOrchestrator.callGrounding(input.get().question(), input.get().context());
        } catch (RuntimeException ex) {
            String error = "Không hoàn tất grounding_check: " + ex.getMessage();
            var failedJob = processor.recordJobFailure(ctx.jobId(), error, maxAttempts);
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), failedJob.getStatus().getDbValue(), null,
                    error);
        }
        try {
            processor.completeGrounding(ctx, result, input.get().refsJson());
            return new ProcessedJobSummary(true, ctx.jobId(), ctx.questionId(), "done", null, null);
        } catch (RuntimeException ex) {
            String error = "Lỗi sau lời gọi judge: " + ex.getMessage();
            LOG.warn("Job {}: {}", ctx.jobId(), error, ex);
            var failedJob = processor.recordJobFailure(ctx.jobId(), error, maxAttempts);
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
        if (trueFalse) {
            ParsedTfDto parsedTf = TfJsonParser.parse(llmResult.rawText(), production);
            // Ngày 23/10: job tf theo ma trận có mẫu nhãn bắt buộc; nhãn khác mẫu chỉ cảnh báo (bật "cần xem kỹ").
            String mismatch = parsedTf.parseSuccess()
                    ? TfLabelPattern.mismatchWarning(ctx.tfLabelPattern(), parsedTf.labels()) : null;
            if (mismatch != null) {
                parsedTf = parsedTf.withParseWarning(mismatch);
            }
            GeneratedQuestion savedTf = processor.saveTrueFalseFromJob(ctx, llmResult, parsedTf);
            QcPipelineReport tfReport = qcOrchestrator.evaluateAndSave(
                    GenerationService.trueFalseQcInput(savedTf.getId(), parsedTf, ctx.validateLatex(), runAnswerCheck),
                    dedupScopeResolver.resolveForSlot(ctx.runId(), ctx.examSlotId()));
            processor.finishGeneratedJob(ctx.jobId(), savedTf.getId(), tfReport.resultingStatus(), solverError(tfReport));
            return new ProcessedJobSummary(true, ctx.jobId(), savedTf.getId(), "done",
                    tfReport.resultingStatus().getDbValue(), parsedTf.parseError());
        }

        if (shortAnswer) {
            ParsedSaDto parsedSa = SaJsonParser.parse(llmResult.rawText(), production);
            GeneratedQuestion savedSa = processor.saveShortAnswerFromJob(ctx, llmResult, parsedSa);
            QcPipelineReport saReport = qcOrchestrator.evaluateAndSave(
                    GenerationService.shortAnswerQcInput(savedSa.getId(), parsedSa, ctx.validateLatex(), runAnswerCheck),
                    dedupScopeResolver.resolveForSlot(ctx.runId(), ctx.examSlotId()));
            processor.finishGeneratedJob(ctx.jobId(), savedSa.getId(), saReport.resultingStatus(), solverError(saReport));
            return new ProcessedJobSummary(true, ctx.jobId(), savedSa.getId(), "done",
                    saReport.resultingStatus().getDbValue(), parsedSa.parseError());
        }

        ParsedMcqDto parsed = McqJsonParser.parse(llmResult.rawText(), production);
        GeneratedQuestion savedQuestion = processor.saveGeneratedQuestionFromJob(ctx, llmResult, parsed);

        QcPipelineReport qcReport = qcOrchestrator.evaluateAndSave(new QuestionQcInput(
                savedQuestion.getId(),
                parsed.parseSuccess(),
                parsed.questionText(),
                parsed.optionA(),
                parsed.optionB(),
                parsed.optionC(),
                parsed.optionD(),
                parsed.correctOption(),
                parsed.explanation(),
                ctx.validateLatex(),
                runAnswerCheck,
                parsed.parseError()
        ).withParseWarnings(parsed.parseWarnings()), dedupScopeResolver.resolveForSlot(ctx.runId(), ctx.examSlotId()));
        processor.finishGeneratedJob(ctx.jobId(), savedQuestion.getId(), qcReport.resultingStatus(), solverError(qcReport));

        return new ProcessedJobSummary(
                true,
                ctx.jobId(),
                savedQuestion.getId(),
                "done",
                qcReport.resultingStatus().getDbValue(),
                parsed.parseError()
        );
    }
}

