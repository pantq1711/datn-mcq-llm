package com.datn.mcq.generation.worker;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.service.TrueFalseQuestionStore;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.parser.McqJsonParser;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GenerationJobRepository;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Chứa các phương thức @Transactional ngắn cho hàng đợi generation_job.
 * Tách riêng khỏi GenerationJobPoller để lệnh gọi mạng tới LLM diễn ra hoàn toàn ngoài transaction DB
 * (Quy tắc 5 trong docs/architecture/backend-structure.md).
 */
@Service
public class GenerationJobProcessor {

    public record ClaimedJobContext(
            Integer jobId,
            Integer runId,
            Integer templateId,
            int attempt,
            Difficulty targetDifficulty,
            Strategy strategy,
            String modelName,
            Double temperature,
            String prompt,
            boolean validateLatex,
            QuestionType questionType,
            List<Difficulty> targetStatementDifficulties
    ) {}

    private final GenerationJobRepository generationJobRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final CatalogService catalogService;
    private final ExperimentService experimentService;
    private final PromptBuilder promptBuilder;
    private final TrueFalseQuestionStore trueFalseQuestionStore;

    public GenerationJobProcessor(
            GenerationJobRepository generationJobRepository,
            GeneratedQuestionRepository generatedQuestionRepository,
            CatalogService catalogService,
            ExperimentService experimentService,
            PromptBuilder promptBuilder,
            TrueFalseQuestionStore trueFalseQuestionStore) {
        this.generationJobRepository = generationJobRepository;
        this.generatedQuestionRepository = generatedQuestionRepository;
        this.catalogService = catalogService;
        this.experimentService = experimentService;
        this.promptBuilder = promptBuilder;
        this.trueFalseQuestionStore = trueFalseQuestionStore;
    }

    @Transactional
    public Optional<ClaimedJobContext> claimNextPendingJob() {
        LocalDateTime now = LocalDateTime.now();
        Optional<GenerationJob> optJob = generationJobRepository.findFirstDuePendingJob(now);
        if (optJob.isEmpty()) {
            return Optional.empty();
        }
        GenerationJob job = optJob.get();
        int nextAttempt = (job.getAttempt() != null ? job.getAttempt() : 0) + 1;
        job.setStatus(JobStatus.RUNNING);
        job.setAttempt(nextAttempt);
        job.setUpdatedAt(now);
        generationJobRepository.save(job);

        ExperimentRun run = experimentService.getById(job.getRunId());
        QuestionTemplate tpl = catalogService.getTemplateById(job.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());

        QuestionType questionType = job.getQuestionType() != null ? job.getQuestionType() : QuestionType.MCQ;
        List<Difficulty> statementLevels = GenerationService.splitLevels(job.getTargetStatementDifficulties());
        String prompt = questionType == QuestionType.TF
                ? TfPromptDraftV0.buildGenerationPrompt(
                        subject.getName(),
                        tpl.getGradeLevel(),
                        tpl.getTopic(),
                        tpl.getLearningOutcome(),
                        tpl.getSourceQuestion(),
                        statementLevels)
                : questionType == QuestionType.SA
                ? SaPromptDraftV0.buildGenerationPrompt(
                        subject.getName(),
                        tpl.getGradeLevel(),
                        tpl.getTopic(),
                        tpl.getLearningOutcome(),
                        tpl.getSourceQuestion(),
                        job.getTargetDifficulty())
                : promptBuilder.buildGenerationPrompt(
                        subject.getName(),
                        tpl.getGradeLevel(),
                        tpl.getTopic(),
                        tpl.getLearningOutcome(),
                        tpl.getSourceQuestion(),
                        run.getStrategy(),
                        job.getTargetDifficulty());
        boolean isMath = isMathSubject(subject.getName());

        return Optional.of(new ClaimedJobContext(
                job.getId(),
                job.getRunId(),
                job.getTemplateId(),
                nextAttempt,
                job.getTargetDifficulty(),
                run.getStrategy(),
                run.getModelName(),
                run.getTemperature(),
                prompt,
                isMath,
                questionType,
                statementLevels
        ));
    }

    /** Lưu câu Đúng/Sai sinh từ job (câu và 4 ý) và đánh dấu job done, trong cùng transaction ngắn (GĐ3). */
    @Transactional
    public GeneratedQuestion saveTrueFalseFromJob(
            ClaimedJobContext ctx,
            LlmCallResult llmResult,
            ParsedTfDto parsed) {
        GeneratedQuestion saved = trueFalseQuestionStore.save(ctx.templateId(), ctx.runId(),
                ctx.targetStatementDifficulties(), llmResult, parsed, ctx.attempt());
        markJobDone(ctx, llmResult, saved.getId(), parsed.parseError());
        return saved;
    }

    @Transactional
    public GeneratedQuestion saveGeneratedQuestionFromJob(
            ClaimedJobContext ctx,
            LlmCallResult llmResult,
            ParsedMcqDto parsed) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(ctx.templateId());
        gq.setRunId(ctx.runId());
        gq.setQuestionText(parsed.questionText());
        gq.setOptionA(parsed.optionA());
        gq.setOptionB(parsed.optionB());
        gq.setOptionC(parsed.optionC());
        gq.setOptionD(parsed.optionD());
        gq.setCorrectOption(parsed.correctOption());
        gq.setExplanation(parsed.explanation());
        gq.setRawLlmOutput(llmResult.rawText() != null ? llmResult.rawText() : "");
        gq.setLlmDifficulty(ctx.targetDifficulty() != null ? ctx.targetDifficulty() : Difficulty.EASY);
        gq.setParseSuccess(parsed.parseSuccess());
        gq.setTokenCount(llmResult.tokenCount());
        gq.setLatencyMs(llmResult.latencyMs());
        gq.setCostUsd(llmResult.costUsd());
        gq.setAttemptCount(Math.max(1, ctx.attempt()));
        gq.setStatus(QuestionStatus.GENERATED);
        gq.setCreatedAt(LocalDateTime.now());
        GeneratedQuestion savedQuestion = generatedQuestionRepository.save(gq);
        markJobDone(ctx, llmResult, savedQuestion.getId(), parsed.parseError());
        return savedQuestion;
    }

    /** Lưu câu Trả lời ngắn sinh từ job và đánh dấu job done, trong cùng transaction ngắn (V4). */
    @Transactional
    public GeneratedQuestion saveShortAnswerFromJob(
            ClaimedJobContext ctx,
            LlmCallResult llmResult,
            ParsedSaDto parsed) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setQuestionType(QuestionType.SA);
        gq.setTemplateId(ctx.templateId());
        gq.setRunId(ctx.runId());
        gq.setQuestionText(parsed.questionText());
        gq.setSaAnswer(parsed.answer());
        gq.setExplanation(parsed.explanation());
        gq.setRawLlmOutput(llmResult.rawText() != null ? llmResult.rawText() : "");
        gq.setLlmDifficulty(ctx.targetDifficulty() != null ? ctx.targetDifficulty() : Difficulty.EASY);
        gq.setParseSuccess(parsed.parseSuccess());
        gq.setTokenCount(llmResult.tokenCount());
        gq.setLatencyMs(llmResult.latencyMs());
        gq.setCostUsd(llmResult.costUsd());
        gq.setAttemptCount(Math.max(1, ctx.attempt()));
        gq.setStatus(QuestionStatus.GENERATED);
        gq.setCreatedAt(LocalDateTime.now());
        GeneratedQuestion savedQuestion = generatedQuestionRepository.save(gq);
        markJobDone(ctx, llmResult, savedQuestion.getId(), parsed.parseError());
        return savedQuestion;
    }

    private void markJobDone(ClaimedJobContext ctx, LlmCallResult llmResult, Integer questionId, String parseError) {
        GenerationJob job = generationJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + ctx.jobId()));
        job.setQuestionId(questionId);
        job.setTokenCount(llmResult.tokenCount());
        job.setLatencyMs(llmResult.latencyMs());
        job.setCostUsd(llmResult.costUsd());
        job.setLastError(parseError);
        job.setStatus(JobStatus.DONE);
        job.setUpdatedAt(LocalDateTime.now());
        generationJobRepository.save(job);
    }

    @Transactional
    public void updateQuestionStatus(Integer questionId, QuestionStatus newStatus) {
        GeneratedQuestion gq = generatedQuestionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + questionId));
        gq.setStatus(newStatus);
        generatedQuestionRepository.save(gq);
    }

    @Transactional
    public GenerationJob recordJobFailure(Integer jobId, String errorMsg, int maxAttempts) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + jobId));
        int currentAttempt = job.getAttempt() != null ? job.getAttempt() : 1;
        job.setLastError(errorMsg);
        job.setUpdatedAt(LocalDateTime.now());
        if (currentAttempt < maxAttempts) {
            job.setStatus(JobStatus.PENDING);
            // Backoff lũy tiến 15s, 30s, 45s... khi gặp lỗi mạng hoặc rate-limit 429/503
            job.setNextRunAt(LocalDateTime.now().plusSeconds(15L * currentAttempt));
        } else {
            job.setStatus(JobStatus.FAILED);
        }
        return generationJobRepository.save(job);
    }

    public static boolean isMathSubject(String subjectName) {
        if (subjectName == null) {
            return true;
        }
        String lower = subjectName.toLowerCase();
        return lower.contains("toán") || lower.contains("toan") || lower.contains("math") || lower.contains("đại số");
    }
}

