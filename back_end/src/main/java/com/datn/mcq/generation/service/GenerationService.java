package com.datn.mcq.generation.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.generation.dto.StatementTextDto;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.parser.SaJsonParser;
import com.datn.mcq.generation.parser.TfJsonParser;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    /**
     * @param questionType dạng câu (V4); bỏ trống là mcq. sa luôn sinh với strategy zero-shot, purpose production,
     *                     prompt_version sa-v1-draft (quyết định 2 ngày 22/10); tf chưa hỗ trợ (GĐ3).
     */
    public record SyncGenerationRequest(
            Integer templateId,
            Strategy strategy,
            Difficulty targetDifficulty,
            String modelName,
            Double temperature,
            Boolean runAnswerCheck,
            String rawLlmOutputOverride,
            QuestionType questionType,
            List<Difficulty> targetStatementDifficulties
    ) {
        /** Chữ ký trước V4 (mcq). */
        public SyncGenerationRequest(Integer templateId, Strategy strategy, Difficulty targetDifficulty,
                                     String modelName, Double temperature, Boolean runAnswerCheck,
                                     String rawLlmOutputOverride) {
            this(templateId, strategy, targetDifficulty, modelName, temperature, runAnswerCheck, rawLlmOutputOverride,
                    null, null);
        }
    }

    static final String SA_STRATEGY_MESSAGE = "Câu trả lời ngắn luôn sinh với strategy zero-shot.";
    static final String TF_STRATEGY_MESSAGE = "Câu đúng/sai luôn sinh với strategy zero-shot.";
    static final String TF_LEVELS_MESSAGE =
            "Câu đúng/sai phải có targetStatementDifficulties gồm đúng 4 mức (ý a, b, c, d).";

    /** Dạng câu được yêu cầu; bỏ trống là mcq. */
    static QuestionType requireSupportedType(QuestionType requested) {
        return requested != null ? requested : QuestionType.MCQ;
    }

    static void requireZeroShotForShortAnswer(QuestionType type, Strategy strategy) {
        if (strategy == null || strategy == Strategy.ZERO_SHOT) {
            return;
        }
        if (type == QuestionType.SA) {
            throw new IllegalArgumentException(SA_STRATEGY_MESSAGE);
        }
        if (type == QuestionType.TF) {
            throw new IllegalArgumentException(TF_STRATEGY_MESSAGE);
        }
    }

    /**
     * Mức mục tiêu (GĐ3): câu tf bắt buộc đúng 4 mức cho 4 ý và không nhận targetDifficulty cấp câu; dạng khác không
     * nhận targetStatementDifficulties (chỉ chặn khi khác null).
     */
    static void validateTargetDifficulties(QuestionType type, Difficulty targetDifficulty,
                                           List<Difficulty> statementDifficulties) {
        if (type == QuestionType.TF) {
            if (statementDifficulties == null || statementDifficulties.size() != 4
                    || statementDifficulties.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(TF_LEVELS_MESSAGE);
            }
            if (targetDifficulty != null) {
                throw new IllegalArgumentException(
                        "targetDifficulty không áp dụng cho câu đúng/sai; dùng targetStatementDifficulties.");
            }
        } else if (statementDifficulties != null) {
            throw new IllegalArgumentException("targetStatementDifficulties chỉ áp dụng cho câu đúng/sai.");
        }
    }

    /** Mức 4 ý lưu ở generation_job.target_statement_difficulties, ví dụ "easy,easy,medium,hard". */
    public static String joinLevels(List<Difficulty> levels) {
        return String.join(",", levels.stream().map(Difficulty::getDbValue).toList());
    }

    public static List<Difficulty> splitLevels(String joined) {
        if (joined == null || joined.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(joined.split(","))
                .map(v -> DbEnum.fromDbValue(Difficulty.class, v))
                .toList();
    }

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
            LocalDateTime createdAt,
            QuestionType questionType,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<StatementTextDto> statements
    ) {
        /** Không kèm đáp án (correct_option, sa_answer), giống trước V4. */
        public static GeneratedQuestionSummaryDto fromEntity(GeneratedQuestion gq) {
            return fromEntity(gq, List.of());
        }

        /**
         * Câu tf kèm 4 ý (vị trí và nội dung, không kèm nhãn); mcq/sa hoặc tf giải mã lỗi (không có ý) thì không có
         * key statements.
         */
        public static GeneratedQuestionSummaryDto fromEntity(GeneratedQuestion gq,
                                                             List<GeneratedQuestionStatement> statements) {
            List<StatementTextDto> texts = gq.getQuestionType() == QuestionType.TF && statements != null
                    && !statements.isEmpty()
                    ? statements.stream().map(s -> new StatementTextDto(s.getStatementKey(), s.getStatementText()))
                            .toList()
                    : null;
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
                    gq.getCreatedAt(),
                    gq.getQuestionType(),
                    texts
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
    private final TrueFalseQuestionStore trueFalseQuestionStore;
    private final GeneratedQuestionStatementRepository statementRepository;
    private final PreviousQuestionLookup previousQuestionLookup;

    public GenerationService(
            GeneratedQuestionRepository generatedQuestionRepository,
            GenerationJobRepository generationJobRepository,
            CatalogService catalogService,
            ExperimentService experimentService,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            QcOrchestrator qcOrchestrator,
            DedupScopeResolver dedupScopeResolver,
            TrueFalseQuestionStore trueFalseQuestionStore,
            GeneratedQuestionStatementRepository statementRepository,
            PreviousQuestionLookup previousQuestionLookup) {
        this.generatedQuestionRepository = generatedQuestionRepository;
        this.generationJobRepository = generationJobRepository;
        this.catalogService = catalogService;
        this.experimentService = experimentService;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.qcOrchestrator = qcOrchestrator;
        this.dedupScopeResolver = dedupScopeResolver;
        this.trueFalseQuestionStore = trueFalseQuestionStore;
        this.statementRepository = statementRepository;
        this.previousQuestionLookup = previousQuestionLookup;
    }

    /**
     * Sinh đồng bộ 1 câu hỏi (hoặc nhận rawLlmOutputOverride để test pipeline offline) + chạy QC pipeline.
     * Gọi LLM ngoài transaction DB trước khi ghi vào bảng generated_question.
     */
    public SyncGenerationResponse generateSync(SyncGenerationRequest req) {
        if (req.templateId() == null) {
            throw new IllegalArgumentException("Phải truyền templateId");
        }
        QuestionType questionType = requireSupportedType(req.questionType());
        requireZeroShotForShortAnswer(questionType, req.strategy());
        validateTargetDifficulties(questionType, req.targetDifficulty(), req.targetStatementDifficulties());
        QuestionTemplate tpl = catalogService.getTemplateById(req.templateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        if (questionType == QuestionType.SA) {
            return generateShortAnswerSync(req, tpl, subject);
        }
        if (questionType == QuestionType.TF) {
            return generateTrueFalseSync(req, tpl, subject);
        }

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

    /**
     * Sinh đồng bộ 1 câu Trả lời ngắn (V4, GĐ2). Run: production, zero-shot, prompt_version sa-v1-draft.
     * Gọi LLM ở JSON mode kèm schema SA (lời giải trước đáp số) ngoài transaction DB, rồi chạy QC như mcq.
     */
    private SyncGenerationResponse generateShortAnswerSync(
            SyncGenerationRequest req, QuestionTemplate tpl, Subject subject) {
        Difficulty targetDiff = req.targetDifficulty() != null ? req.targetDifficulty() : Difficulty.EASY;
        Double temperature = req.temperature() != null ? req.temperature() : 0.7;
        String model = (req.modelName() != null && !req.modelName().isBlank())
                ? req.modelName().trim()
                : llmClient.getDefaultModel();
        String prompt = SaPromptDraftV0.buildGenerationPrompt(
                subject.getName(),
                tpl.getGradeLevel(),
                tpl.getTopic(),
                tpl.getLearningOutcome(),
                tpl.getSourceQuestion(),
                targetDiff
        );
        // Độ đa dạng (Q1-Q3 ngày 22/10): prompt gửi model có thêm các câu đã sinh; run lưu prompt gốc.
        String sentPrompt = SaPromptDraftV0.buildGenerationPrompt(subject.getName(), tpl.getGradeLevel(),
                tpl.getTopic(), tpl.getLearningOutcome(), tpl.getSourceQuestion(), targetDiff,
                previousQuestionLookup.recent(tpl.getId(), QuestionType.SA));

        LlmCallResult llmResult;
        if (req.rawLlmOutputOverride() != null && !req.rawLlmOutputOverride().isBlank()) {
            llmResult = new LlmCallResult(req.rawLlmOutputOverride(), 0, 0.0, 0.0, model);
        } else {
            llmResult = llmClient.callJsonModeWithSchema(
                    sentPrompt, model, temperature, SaPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON);
        }

        ParsedSaDto parsed = SaJsonParser.parse(llmResult.rawText());
        ExperimentRun run = experimentService.createRun(
                subject.getId(),
                Purpose.PRODUCTION,
                Strategy.ZERO_SHOT,
                llmResult.modelName(),
                temperature,
                SaPromptDraftV0.PROMPT_VERSION,
                prompt,
                1,
                QuestionType.SA
        );

        GeneratedQuestion saved = saveParsedShortAnswer(tpl.getId(), run.getId(), targetDiff, llmResult, parsed, 1);
        boolean runAnswerCheck = req.runAnswerCheck() != null ? req.runAnswerCheck() : true;
        QcPipelineReport qcReport = qcOrchestrator.evaluateAndSave(
                shortAnswerQcInput(saved.getId(), parsed, GenerationJobProcessor.isMathSubject(subject.getName()),
                        runAnswerCheck),
                dedupScopeResolver.resolve(run.getId()));
        GeneratedQuestion updated = updateStatusAndDifficulty(saved.getId(), qcReport.resultingStatus(), null);
        return new SyncGenerationResponse(GeneratedQuestionSummaryDto.fromEntity(updated), qcReport);
    }

    /**
     * Sinh đồng bộ 1 câu Đúng/Sai (GĐ3). Run: production, zero-shot, prompt_version tf-v1-draft. Gọi LLM ở JSON mode
     * kèm schema TF ngoài transaction DB; lưu câu và 4 ý trong một transaction; rồi chạy QC.
     */
    private SyncGenerationResponse generateTrueFalseSync(
            SyncGenerationRequest req, QuestionTemplate tpl, Subject subject) {
        List<Difficulty> levels = req.targetStatementDifficulties();
        Double temperature = req.temperature() != null ? req.temperature() : 0.7;
        String model = (req.modelName() != null && !req.modelName().isBlank())
                ? req.modelName().trim()
                : llmClient.getDefaultModel();
        String prompt = TfPromptDraftV0.buildGenerationPrompt(
                subject.getName(),
                tpl.getGradeLevel(),
                tpl.getTopic(),
                tpl.getLearningOutcome(),
                tpl.getSourceQuestion(),
                levels
        );
        // Độ đa dạng (Q1-Q3 ngày 22/10): prompt gửi model có thêm các câu đã sinh; run lưu prompt gốc.
        String sentPrompt = TfPromptDraftV0.buildGenerationPrompt(subject.getName(), tpl.getGradeLevel(),
                tpl.getTopic(), tpl.getLearningOutcome(), tpl.getSourceQuestion(), levels,
                previousQuestionLookup.recent(tpl.getId(), QuestionType.TF));

        LlmCallResult llmResult;
        if (req.rawLlmOutputOverride() != null && !req.rawLlmOutputOverride().isBlank()) {
            llmResult = new LlmCallResult(req.rawLlmOutputOverride(), 0, 0.0, 0.0, model);
        } else {
            llmResult = llmClient.callJsonModeWithSchema(
                    sentPrompt, model, temperature, TfPromptDraftV0.GENERATION_RESPONSE_SCHEMA_JSON);
        }

        ParsedTfDto parsed = TfJsonParser.parse(llmResult.rawText());
        ExperimentRun run = experimentService.createRun(
                subject.getId(),
                Purpose.PRODUCTION,
                Strategy.ZERO_SHOT,
                llmResult.modelName(),
                temperature,
                TfPromptDraftV0.PROMPT_VERSION,
                prompt,
                1,
                QuestionType.TF
        );

        GeneratedQuestion saved = trueFalseQuestionStore.save(tpl.getId(), run.getId(), levels, llmResult, parsed, 1);
        boolean runAnswerCheck = req.runAnswerCheck() != null ? req.runAnswerCheck() : true;
        QcPipelineReport qcReport = qcOrchestrator.evaluateAndSave(
                trueFalseQcInput(saved.getId(), parsed, GenerationJobProcessor.isMathSubject(subject.getName()),
                        runAnswerCheck),
                dedupScopeResolver.resolve(run.getId()));
        GeneratedQuestion updated = updateStatusAndDifficulty(saved.getId(), qcReport.resultingStatus(), null);
        return new SyncGenerationResponse(
                GeneratedQuestionSummaryDto.fromEntity(updated, getStatements(saved.getId())), qcReport);
    }

    /** Đầu vào QC của câu tf vừa giải mã (dùng chung cho generate-sync và worker). */
    public static QuestionQcInput trueFalseQcInput(
            Integer questionId, ParsedTfDto parsed, boolean validateLatex, boolean runAnswerCheck) {
        return QuestionQcInput.trueFalse(
                questionId,
                parsed.parseSuccess(),
                parsed.questionText(),
                parsed.statements(),
                parsed.labels(),
                parsed.explanation(),
                validateLatex,
                runAnswerCheck,
                parsed.parseError(),
                parsed.parseWarnings()
        );
    }

    /** 4 ý của một câu tf theo thứ tự a-d (rỗng với mcq/sa hoặc khi giải mã lỗi). */
    @Transactional(readOnly = true)
    public List<GeneratedQuestionStatement> getStatements(Integer questionId) {
        return statementRepository.findByQuestionIdOrderByPositionAsc(questionId);
    }

    /**
     * Mức độ cuối của 4 ý khi câu tf vào ngân hàng (GĐ3): chép từ mức giáo viên chấm. Chạy trong transaction của nơi
     * gọi (duyệt production), nên rollback cùng nhau.
     */
    @Transactional
    public void updateStatementFinalDifficulties(Integer questionId, List<Difficulty> finalDifficulties) {
        List<GeneratedQuestionStatement> statements = statementRepository.findByQuestionIdOrderByPositionAsc(questionId);
        if (statements.size() != 4 || finalDifficulties == null || finalDifficulties.size() != 4) {
            throw new IllegalStateException("Câu đúng/sai id=" + questionId + " không có đủ 4 ý");
        }
        for (int i = 0; i < 4; i++) {
            statements.get(i).setFinalDifficulty(finalDifficulties.get(i));
        }
        statementRepository.saveAll(statements);
    }

    /** Đầu vào QC của câu sa vừa giải mã (dùng chung cho generate-sync và worker). */
    public static QuestionQcInput shortAnswerQcInput(
            Integer questionId, ParsedSaDto parsed, boolean validateLatex, boolean runAnswerCheck) {
        return QuestionQcInput.shortAnswer(
                questionId,
                parsed.parseSuccess(),
                parsed.questionText(),
                parsed.explanation(),
                parsed.answer(),
                parsed.answerIsString(),
                validateLatex,
                runAnswerCheck,
                parsed.parseError()
        );
    }

    /**
     * Lưu câu sa: không có phương án và correct_option; sa_answer là chuỗi LLM viết (đã trim), raw_llm_output giữ
     * bản gốc. llm_difficulty là mức mục tiêu khi sinh, như mcq (F-8, chờ Q-5).
     */
    @Transactional
    public GeneratedQuestion saveParsedShortAnswer(
            Integer templateId,
            Integer runId,
            Difficulty targetDifficulty,
            LlmCallResult llmResult,
            ParsedSaDto parsed,
            int attemptCount) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setQuestionType(QuestionType.SA);
        gq.setTemplateId(templateId);
        gq.setRunId(runId);
        gq.setQuestionText(parsed.questionText());
        gq.setSaAnswer(parsed.answer());
        gq.setExplanation(parsed.explanation());
        gq.setRawLlmOutput(llmResult.rawText() != null ? llmResult.rawText() : "");
        gq.setLlmDifficulty(targetDifficulty != null ? targetDifficulty : Difficulty.EASY);
        gq.setParseSuccess(parsed.parseSuccess());
        gq.setTokenCount(llmResult.tokenCount());
        gq.setLatencyMs(llmResult.latencyMs());
        gq.setCostUsd(llmResult.costUsd());
        gq.setAttemptCount(Math.max(1, attemptCount));
        gq.setStatus(QuestionStatus.GENERATED);
        gq.setCreatedAt(LocalDateTime.now());
        return generatedQuestionRepository.save(gq);
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
        return enqueueJobs(templateId, strategy, targetDifficulty, modelName, count, null, null);
    }

    /**
     * Tạo job sinh câu (V4: theo dạng câu). sa: run production, zero-shot, prompt_version sa-v1-draft.
     * tf (GĐ3): run production, zero-shot, prompt_version tf-v1-draft; bắt buộc 4 mức của 4 ý, ghi vào
     * target_statement_difficulties; target_difficulty cấp câu để NULL.
     */
    @Transactional
    public List<GenerationJob> enqueueJobs(
            Integer templateId,
            Strategy strategy,
            Difficulty targetDifficulty,
            String modelName,
            int count,
            QuestionType requestedType,
            List<Difficulty> targetStatementDifficulties) {
        if (templateId == null) {
            throw new IllegalArgumentException("Phải truyền templateId");
        }
        QuestionType questionType = requireSupportedType(requestedType);
        requireZeroShotForShortAnswer(questionType, strategy);
        validateTargetDifficulties(questionType, targetDifficulty, targetStatementDifficulties);
        int numJobs = Math.max(1, Math.min(count, 50));
        QuestionTemplate tpl = catalogService.getTemplateById(templateId);
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        boolean trueFalse = questionType == QuestionType.TF;
        boolean shortAnswer = questionType == QuestionType.SA;
        Strategy chosenStrategy = shortAnswer || trueFalse ? Strategy.ZERO_SHOT
                : strategy != null ? strategy : Strategy.FEW_SHOT_STRUCTURED;
        Difficulty chosenDiff = trueFalse ? null : targetDifficulty != null ? targetDifficulty : Difficulty.EASY;
        String prompt;
        String promptVersion;
        if (trueFalse) {
            prompt = TfPromptDraftV0.buildGenerationPrompt(subject.getName(), tpl.getGradeLevel(), tpl.getTopic(),
                    tpl.getLearningOutcome(), tpl.getSourceQuestion(), targetStatementDifficulties);
            promptVersion = TfPromptDraftV0.PROMPT_VERSION;
        } else if (shortAnswer) {
            prompt = SaPromptDraftV0.buildGenerationPrompt(subject.getName(), tpl.getGradeLevel(), tpl.getTopic(),
                    tpl.getLearningOutcome(), tpl.getSourceQuestion(), chosenDiff);
            promptVersion = SaPromptDraftV0.PROMPT_VERSION;
        } else {
            prompt = promptBuilder.buildGenerationPrompt(subject.getName(), tpl.getGradeLevel(), tpl.getTopic(),
                    tpl.getLearningOutcome(), tpl.getSourceQuestion(), chosenStrategy, chosenDiff);
            promptVersion = "async-queue-v1";
        }
        ExperimentRun run = experimentService.createRun(
                subject.getId(),
                Purpose.PRODUCTION,
                chosenStrategy,
                modelName != null && !modelName.isBlank() ? modelName : llmClient.getDefaultModel(),
                0.7,
                promptVersion,
                prompt,
                numJobs,
                questionType
        );

        List<GenerationJob> created = new ArrayList<>(numJobs);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < numJobs; i++) {
            GenerationJob job = new GenerationJob();
            job.setQuestionType(questionType);
            job.setRunId(run.getId());
            job.setTemplateId(tpl.getId());
            job.setTargetDifficulty(chosenDiff);
            job.setTargetStatementDifficulties(trueFalse ? joinLevels(targetStatementDifficulties) : null);
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
