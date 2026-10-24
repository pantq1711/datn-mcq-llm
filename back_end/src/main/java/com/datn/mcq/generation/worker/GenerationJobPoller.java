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
 * 2. Gọi LlmClient.generateMcq(...) ngoài transaction.
 * 3. Lưu GeneratedQuestion + cập nhật GenerationJob (transaction ngắn #2),
 *    dựng phạm vi dedup theo purpose của run, chạy pipeline QC (format -> dedup -> answer_check)
 *    và cập nhật QuestionStatus.
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
        boolean shortAnswer = ctx.questionType() == QuestionType.SA;
        boolean trueFalse = ctx.questionType() == QuestionType.TF;
        // Chế độ sửa đầu ra (escape LaTeX, tự đóng $) và schema MCQ chỉ cho run production; experiment giữ nguyên.
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
                    // Ngày 23/10: run production gọi MCQ kèm schema; run experiment (C1/C2/C3) giữ cấu hình theo strategy.
                    : production
                    ? llmClient.callJsonModeWithSchema(
                            ctx.prompt(), ctx.modelName(), ctx.temperature(),
                            McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON)
                    : llmClient.generateMcq(
                            ctx.prompt(),
                            ctx.modelName(),
                            ctx.strategy(),
                            ctx.temperature()
                    );
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
                    dedupScopeResolver.resolve(ctx.runId()));
            processor.updateQuestionStatus(savedTf.getId(), tfReport.resultingStatus());
            return new ProcessedJobSummary(true, ctx.jobId(), savedTf.getId(), "done",
                    tfReport.resultingStatus().getDbValue(), parsedTf.parseError());
        }

        if (shortAnswer) {
            ParsedSaDto parsedSa = SaJsonParser.parse(llmResult.rawText(), production);
            GeneratedQuestion savedSa = processor.saveShortAnswerFromJob(ctx, llmResult, parsedSa);
            QcPipelineReport saReport = qcOrchestrator.evaluateAndSave(
                    GenerationService.shortAnswerQcInput(savedSa.getId(), parsedSa, ctx.validateLatex(), runAnswerCheck),
                    dedupScopeResolver.resolve(ctx.runId()));
            processor.updateQuestionStatus(savedSa.getId(), saReport.resultingStatus());
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
        ).withParseWarnings(parsed.parseWarnings()), dedupScopeResolver.resolve(ctx.runId()));
        processor.updateQuestionStatus(savedQuestion.getId(), qcReport.resultingStatus());

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

