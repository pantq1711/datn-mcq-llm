package com.datn.mcq.generation.worker;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.generation.parser.SaJsonParser;
import com.datn.mcq.generation.parser.TfJsonParser;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.parser.McqJsonParser;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
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

    public ProcessedJobSummary processNextJob(boolean runAnswerCheck) {
        Optional<GenerationJobProcessor.ClaimedJobContext> claimedOpt = processor.claimNextPendingJob();
        if (claimedOpt.isEmpty()) {
            return new ProcessedJobSummary(false, null, null, "idle", null, null);
        }
        GenerationJobProcessor.ClaimedJobContext ctx = claimedOpt.get();
        boolean shortAnswer = ctx.questionType() == QuestionType.SA;
        boolean trueFalse = ctx.questionType() == QuestionType.TF;
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

        if (trueFalse) {
            ParsedTfDto parsedTf = TfJsonParser.parse(llmResult.rawText());
            GeneratedQuestion savedTf = processor.saveTrueFalseFromJob(ctx, llmResult, parsedTf);
            QcPipelineReport tfReport = qcOrchestrator.evaluateAndSave(
                    GenerationService.trueFalseQcInput(savedTf.getId(), parsedTf, ctx.validateLatex(), runAnswerCheck),
                    dedupScopeResolver.resolve(ctx.runId()));
            processor.updateQuestionStatus(savedTf.getId(), tfReport.resultingStatus());
            return new ProcessedJobSummary(true, ctx.jobId(), savedTf.getId(), "done",
                    tfReport.resultingStatus().getDbValue(), parsedTf.parseError());
        }

        if (shortAnswer) {
            ParsedSaDto parsedSa = SaJsonParser.parse(llmResult.rawText());
            GeneratedQuestion savedSa = processor.saveShortAnswerFromJob(ctx, llmResult, parsedSa);
            QcPipelineReport saReport = qcOrchestrator.evaluateAndSave(
                    GenerationService.shortAnswerQcInput(savedSa.getId(), parsedSa, ctx.validateLatex(), runAnswerCheck),
                    dedupScopeResolver.resolve(ctx.runId()));
            processor.updateQuestionStatus(savedSa.getId(), saReport.resultingStatus());
            return new ProcessedJobSummary(true, ctx.jobId(), savedSa.getId(), "done",
                    saReport.resultingStatus().getDbValue(), parsedSa.parseError());
        }

        ParsedMcqDto parsed = McqJsonParser.parse(llmResult.rawText());
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
        ), dedupScopeResolver.resolve(ctx.runId()));
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

