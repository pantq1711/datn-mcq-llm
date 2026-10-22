package com.datn.mcq.generation.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.parser.McqJsonParser;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GenerationJobRepository;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class GenerationService {

    public record SyncGenerationRequest(
            Integer templateId,
            Strategy strategy,
            Difficulty targetDifficulty,
            String modelName,
            Double temperature,
            Boolean runAnswerCheck,
            String rawLlmOutputOverride
    ) {}

    public record GeneratedQuestionSummaryDto(
            Integer id,
            Integer templateId,
            Integer runId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Boolean parseSuccess,
            QuestionStatus status,
            Integer tokenCount,
            Double latencyMs,
            LocalDateTime createdAt
    ) {
        public static GeneratedQuestionSummaryDto fromEntity(GeneratedQuestion gq) {
            return new GeneratedQuestionSummaryDto(
                    gq.getId(),
                    gq.getTemplateId(),
                    gq.getRunId(),
                    gq.getQuestionText(),
                    gq.getOptionA(),
                    gq.getOptionB(),
                    gq.getOptionC(),
                    gq.getOptionD(),
                    gq.getParseSuccess(),
                    gq.getStatus(),
                    gq.getTokenCount(),
                    gq.getLatencyMs(),
                    gq.getCreatedAt()
            );
        }
    }

    public record SyncGenerationResponse(
            GeneratedQuestionSummaryDto question,
            QcPipelineReport qcReport
    ) {}

    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final CatalogService catalogService;
    private final ExperimentService experimentService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final QcOrchestrator qcOrchestrator;
    private final DedupScopeResolver dedupScopeResolver;

    public GenerationService(
            GeneratedQuestionRepository generatedQuestionRepository,
            GenerationJobRepository generationJobRepository,
            CatalogService catalogService,
            ExperimentService experimentService,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            QcOrchestrator qcOrchestrator,
            DedupScopeResolver dedupScopeResolver) {
        this.generatedQuestionRepository = generatedQuestionRepository;
        this.generationJobRepository = generationJobRepository;
        this.catalogService = catalogService;
        this.experimentService = experimentService;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.qcOrchestrator = qcOrchestrator;
        this.dedupScopeResolver = dedupScopeResolver;
    }

    /**
     * Sinh đồng bộ 1 câu hỏi (hoặc nhận rawLlmOutputOverride để test pipeline offline) + chạy QC pipeline.
     * Gọi LLM ngoài transaction DB trước khi ghi vào bảng generated_question.
     */
    public SyncGenerationResponse generateSync(SyncGenerationRequest req) {
        if (req.templateId() == null) {
            throw new IllegalArgumentException("Phải truyền templateId");
        }
        QuestionTemplate tpl = catalogService.getTemplateById(req.templateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());

        Strategy strategy = req.strategy() != null ? req.strategy() : Strategy.FEW_SHOT_STRUCTURED;
        Difficulty targetDiff = req.targetDifficulty() != null ? req.targetDifficulty() : Difficulty.EASY;
        Double temperature = req.temperature() != null ? req.temperature() : 0.7;
        String model = (req.modelName() != null && !req.modelName().isBlank())
                ? req.modelName().trim()
                : llmClient.getDefaultModel();

        String prompt = promptBuilder.buildGenerationPrompt(
                subject.getName(),
                tpl.getGradeLevel(),
                tpl.getTopic(),
                tpl.getLearningOutcome(),
                tpl.getSourceQuestion(),
                strategy,
                targetDiff
        );

        LlmCallResult llmResult;
        if (req.rawLlmOutputOverride() != null && !req.rawLlmOutputOverride().isBlank()) {
            llmResult = new LlmCallResult(req.rawLlmOutputOverride(), 0, 0.0, 0.0, model);
        } else {
            llmResult = llmClient.generateMcq(prompt, model, strategy, temperature);
        }

        ParsedMcqDto parsed = McqJsonParser.parse(llmResult.rawText());
        ExperimentRun run = experimentService.createRun(
                subject.getId(),
                Purpose.PRODUCTION,
                strategy,
                llmResult.modelName(),
                temperature,
                "sync-v1",
                prompt,
                1
        );

        GeneratedQuestion saved = saveParsedQuestion(tpl.getId(), run.getId(), targetDiff, llmResult, parsed);

        boolean validateLatex = GenerationJobProcessor.isMathSubject(subject.getName());
        boolean runAnswerCheck = req.runAnswerCheck() != null ? req.runAnswerCheck() : true;

        QcPipelineReport qcReport = qcOrchestrator.evaluateAndSave(new QuestionQcInput(
                saved.getId(),
                parsed.parseSuccess(),
                parsed.questionText(),
                parsed.optionA(),
                parsed.optionB(),
                parsed.optionC(),
                parsed.optionD(),
                parsed.correctOption(),
                parsed.explanation(),
                validateLatex,
                runAnswerCheck,
                parsed.parseError()
        ), dedupScopeResolver.resolve(run.getId()));
        GeneratedQuestion updated = updateStatusAndDifficulty(saved.getId(), qcReport.resultingStatus(), null);
        return new SyncGenerationResponse(GeneratedQuestionSummaryDto.fromEntity(updated), qcReport);
    }

    @Transactional
    public GeneratedQuestion saveParsedQuestion(
            Integer templateId,
            Integer runId,
            Difficulty llmDifficulty,
            LlmCallResult llmResult,
            ParsedMcqDto parsed) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(templateId);
        gq.setRunId(runId);
        gq.setQuestionText(parsed.questionText());
        gq.setOptionA(parsed.optionA());
        gq.setOptionB(parsed.optionB());
        gq.setOptionC(parsed.optionC());
        gq.setOptionD(parsed.optionD());
        gq.setCorrectOption(parsed.correctOption());
        gq.setExplanation(parsed.explanation());
        gq.setRawLlmOutput(llmResult.rawText() != null ? llmResult.rawText() : "");
        gq.setLlmDifficulty(llmDifficulty != null ? llmDifficulty : Difficulty.EASY);
        gq.setParseSuccess(parsed.parseSuccess());
        gq.setTokenCount(llmResult.tokenCount());
        gq.setLatencyMs(llmResult.latencyMs());
        gq.setCostUsd(llmResult.costUsd());
        gq.setAttemptCount(1);
        gq.setStatus(QuestionStatus.GENERATED);
        gq.setCreatedAt(LocalDateTime.now());
        return generatedQuestionRepository.save(gq);
    }

    @Transactional
    public List<GenerationJob> enqueueJobs(
            Integer templateId,
            Strategy strategy,
            Difficulty targetDifficulty,
            String modelName,
            int count) {
        if (templateId == null) {
            throw new IllegalArgumentException("Phải truyền templateId");
        }
        int numJobs = Math.max(1, Math.min(count, 50));
        QuestionTemplate tpl = catalogService.getTemplateById(templateId);
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        Strategy chosenStrategy = strategy != null ? strategy : Strategy.FEW_SHOT_STRUCTURED;
        Difficulty chosenDiff = targetDifficulty != null ? targetDifficulty : Difficulty.EASY;
        String prompt = promptBuilder.buildGenerationPrompt(
                subject.getName(),
                tpl.getGradeLevel(),
                tpl.getTopic(),
                tpl.getLearningOutcome(),
                tpl.getSourceQuestion(),
                chosenStrategy,
                chosenDiff
        );
        ExperimentRun run = experimentService.createRun(
                subject.getId(),
                Purpose.PRODUCTION,
                chosenStrategy,
                modelName != null && !modelName.isBlank() ? modelName : llmClient.getDefaultModel(),
                0.7,
                "async-queue-v1",
                prompt,
                numJobs
        );

        List<GenerationJob> created = new ArrayList<>(numJobs);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < numJobs; i++) {
            GenerationJob job = new GenerationJob();
            job.setRunId(run.getId());
            job.setTemplateId(tpl.getId());
            job.setTargetDifficulty(chosenDiff);
            job.setStatus(JobStatus.PENDING);
            job.setAttempt(0);
            // Giãn cách mỗi job 13 giây để tránh đụng trần 5 RPM trên Free Tier
            job.setNextRunAt(now.plusSeconds(13L * i));
            job.setCreatedAt(now);
            job.setUpdatedAt(now);
            created.add(generationJobRepository.save(job));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<GenerationJob> listJobs() {
        return generationJobRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public GeneratedQuestion getQuestionById(Integer id) {
        return generatedQuestionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi id=" + id));
    }

    @Transactional(readOnly = true)
    public List<GeneratedQuestion> listQuestions(QuestionStatus status) {
        if (status != null) {
            return generatedQuestionRepository.findByStatusOrderByIdDesc(status);
        }
        return generatedQuestionRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<GeneratedQuestion> listQuestionsByRunId(Integer runId) {
        return generatedQuestionRepository.findByRunIdOrderByIdAsc(runId);
    }

    @Transactional
    public GeneratedQuestion updateStatusAndDifficulty(
            Integer questionId,
            QuestionStatus newStatus,
            Difficulty finalDifficulty) {
        GeneratedQuestion gq = getQuestionById(questionId);
        if (newStatus != null) {
            gq.setStatus(newStatus);
        }
        if (finalDifficulty != null) {
            gq.setFinalDifficulty(finalDifficulty);
        }
        return generatedQuestionRepository.save(gq);
    }
}
