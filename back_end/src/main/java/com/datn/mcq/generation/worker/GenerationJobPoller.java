package com.datn.mcq.generation.worker;

import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.parser.McqJsonParser;
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
 *    chạy pipeline QC và cập nhật QuestionStatus.
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
    private final int maxAttempts;

    public GenerationJobPoller(
            GenerationJobProcessor processor,
            LlmClient llmClient,
            QcOrchestrator qcOrchestrator,
            @Value("${app.worker.max-attempts:3}") int maxAttempts) {
        this.processor = processor;
        this.llmClient = llmClient;
        this.qcOrchestrator = qcOrchestrator;
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
        LlmCallResult llmResult;
        try {
            llmResult = llmClient.generateMcq(
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
                runAnswerCheck
        ));
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

