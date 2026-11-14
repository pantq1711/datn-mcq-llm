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
import com.datn.mcq.llm.prompt.MatrixCellSpec;
import com.datn.mcq.llm.prompt.McqProductionSchema;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
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

    static final String MCQ_STRATEGY_MESSAGE =
            "Câu trắc nghiệm luôn sinh với strategy zero-shot (thí nghiệm few-shot đã bỏ ngày 25/10).";
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
        if (type == QuestionType.MCQ) {
            throw new IllegalArgumentException(MCQ_STRATEGY_MESSAGE);
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

        Strategy strategy = Strategy.ZERO_SHOT;
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
                targetDiff
        );

        LlmCallResult llmResult;
        if (req.rawLlmOutputOverride() != null && !req.rawLlmOutputOverride().isBlank()) {
            llmResult = new LlmCallResult(req.rawLlmOutputOverride(), 0, 0.0, 0.0, model);
        } else {
            // Ngày 23/10: run production gọi JSON mode kèm schema MCQ (như sa/tf); strategy vẫn quyết định prompt.
            llmResult = llmClient.callJsonModeWithSchema(
                    prompt, model, temperature, McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON);
        }

        ParsedMcqDto parsed = McqJsonParser.parse(llmResult.rawText(), true);
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

        QcPipelineReport qcReport = qcOrchestrator.evaluateAndSave(
                mcqQcInput(saved.getId(), parsed, validateLatex, runAnswerCheck),
                dedupScopeResolver.resolve(run.getId()));
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

        ParsedSaDto parsed = SaJsonParser.parse(llmResult.rawText(), true);
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
     * Sinh đồng bộ 1 câu Đúng/Sai (GĐ3). Run: production, zero-shot, prompt_version tf-v2-draft. Gọi LLM ở JSON mode
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

        ParsedTfDto parsed = TfJsonParser.parse(llmResult.rawText(), true);
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
        ).withLlmOutput(true);
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

    /** Đầu vào QC của câu mcq vừa giải mã (dùng chung cho generate-sync và worker). */
    public static QuestionQcInput mcqQcInput(
            Integer questionId, ParsedMcqDto parsed, boolean validateLatex, boolean runAnswerCheck) {
        return new QuestionQcInput(
                questionId,
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
        ).withParseWarnings(parsed.parseWarnings()).withLlmOutput(true);
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
        ).withParseWarnings(parsed.parseWarnings()).withLlmOutput(true);
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
        // 13/11 (PA1, V29): lỗi sai kèm phương án nếu đầu ra có (prompt sinh lẻ không yêu cầu, thường NULL).
        gq.setDistractorErrors(com.datn.mcq.common.util.LetterJson.write(parsed.distractorErrors()));
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

    /**
     * V7 (phần C mục 2): run production, zero-shot cho một ô ma trận. prompt_text là prompt gốc của ô, prompt_version
     * "matrix-&lt;dạng&gt;-v0-draft", hyperparams là thông số ô để worker dựng lại prompt.
     */
    @Transactional
    public ExperimentRun createMatrixRun(Integer subjectId, MatrixCellSpec spec, int numQuestions) {
        return experimentService.createRun(
                subjectId,
                Purpose.PRODUCTION,
                Strategy.ZERO_SHOT,
                llmClient.getDefaultModel(),
                0.7,
                MatrixPromptDraftV0.promptVersion(spec.questionType()),
                MatrixPromptDraftV0.buildGenerationPrompt(spec),
                numQuestions,
                spec.questionType(),
                spec.toJson());
    }

    /** model_name của run chứa câu nhập từ Word (không phải model LLM nào). */
    public static final String IMPORT_MODEL_NAME = "import-docx";
    /** prompt_version của run chứa câu nhập từ Word. */
    public static final String IMPORT_PROMPT_VERSION = "import-docx-v1";

    /**
     * V26 (nhập từ Word): run production, zero-shot cho câu nhập của một dạng ({@code chk_er_tf_sa_production}), không gắn
     * model LLM nào; không có job, không gọi LLM. Run chỉ để giữ khóa ngoại (run_id, question_type) của generated_question.
     */
    @Transactional
    public ExperimentRun createImportRun(Integer subjectId, QuestionType type, int numQuestions) {
        ExperimentRun run = experimentService.createRun(subjectId, Purpose.PRODUCTION, Strategy.ZERO_SHOT,
                IMPORT_MODEL_NAME, 0.0, IMPORT_PROMPT_VERSION, "", numQuestions, type);
        run.setStatus("imported");
        run.setEndedAt(run.getStartedAt());
        return run;
    }

    /**
     * Ảnh chụp đoạn tài liệu bài học của một job sinh câu (W2-3): {@code context} là khối văn bản đưa vào prompt, {@code
     * refsJson} là mảng JSON các đoạn đã chọn (để truy vết).
     */
    public record MaterialSnapshot(String context, String refsJson) {}

    /** V7: một job sinh câu cho một chỗ trong đề, thuộc run của ô (không có tài liệu). */
    @Transactional
    public GenerationJob enqueueMatrixJob(Integer runId, Integer templateId, MatrixCellSpec spec, Integer examSlotId,
                                          LocalDateTime nextRunAt, String promptNote) {
        return enqueueMatrixJob(runId, templateId, spec, examSlotId, nextRunAt, promptNote, null);
    }

    /**
     * V7: một job sinh câu cho một chỗ trong đề. W2-3: {@code material} (null khi không có tài liệu) được ghi vào
     * generation_job.material_context/material_refs; worker dựng prompt từ đúng ảnh chụp này.
     */
    @Transactional
    public GenerationJob enqueueMatrixJob(Integer runId, Integer templateId, MatrixCellSpec spec, Integer examSlotId,
                                          LocalDateTime nextRunAt, String promptNote, MaterialSnapshot material) {
        return enqueueMatrixJob(runId, templateId, spec, examSlotId, nextRunAt, promptNote, material, JobRequest.NONE);
    }

    /**
     * Yêu cầu hiệu lực của một job sinh câu (12/11, PR B, V28), tính lúc xếp hàng.
     *
     * @param codes mã chip đã lọc theo dạng câu (rỗng khi không có)
     * @param text  văn bản tự do đã chuẩn hóa; null khi không có
     * @param scope {@code replace} (lượt Đổi câu tự đặt, kể cả khi bỏ hết), {@code cell}, {@code exam}; null khi job không
     *              có yêu cầu
     */
    public record JobRequest(List<String> codes, String text, String scope) {
        public static final JobRequest NONE = new JobRequest(List.of(), null, null);

        public JobRequest {
            codes = codes == null ? List.of() : List.copyOf(codes);
            text = text == null || text.isBlank() ? null : text;
        }

        /** Yêu cầu đã lưu của một job (lần sinh lại tự động giữ yêu cầu của lượt đó). */
        public static JobRequest of(GenerationJob job) {
            return new JobRequest(job.getRequestCodes(), job.getRequestText(), job.getRequestScope());
        }
    }

    /** Như trên, kèm yêu cầu hiệu lực của job (PR B); {@code request} null là không có yêu cầu. */
    @Transactional
    public GenerationJob enqueueMatrixJob(Integer runId, Integer templateId, MatrixCellSpec spec, Integer examSlotId,
                                          LocalDateTime nextRunAt, String promptNote, MaterialSnapshot material,
                                          JobRequest request) {
        LocalDateTime now = LocalDateTime.now();
        GenerationJob job = new GenerationJob();
        job.setQuestionType(spec.questionType());
        job.setRunId(runId);
        job.setTemplateId(templateId);
        job.setTargetDifficulty(spec.questionType() == QuestionType.TF ? null : spec.difficulty());
        job.setTargetStatementDifficulties(spec.questionType() == QuestionType.TF
                ? joinLevels(spec.statementDifficulties()) : null);
        job.setExamSlotId(examSlotId);
        job.setPromptNote(promptNote == null || promptNote.isBlank() ? null : promptNote);
        job.setMaterialContext(material == null ? null : material.context());
        job.setMaterialRefs(material == null ? null : material.refsJson());
        if (request != null) {
            job.setRequestCodes(request.codes());
            job.setRequestText(request.text());
            job.setRequestScope(request.scope());
        }
        job.setStatus(JobStatus.PENDING);
        job.setAttempt(0);
        job.setNextRunAt(nextRunAt != null ? nextRunAt : now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return generationJobRepository.save(job);
    }

    /** V7: job mới nhất của một chỗ trong đề. */
    @Transactional(readOnly = true)
    public java.util.Optional<GenerationJob> latestJobForSlot(Integer examSlotId) {
        return generationJobRepository.findFirstByExamSlotIdOrderByIdDesc(examSlotId);
    }

    /**
     * C-01: mốc chạy tiếp sớm nhất của các job đang hoãn vì hạn mức ngày thuộc các chỗ {@code examSlotIds}
     * (xem {@link QuotaDeferral}); rỗng khi không có. Một truy vấn, không phụ thuộc số chỗ.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<java.time.LocalDateTime> earliestQuotaResume(java.util.Collection<Integer> examSlotIds) {
        if (examSlotIds.isEmpty()) {
            return java.util.Optional.empty();
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return java.util.Optional.ofNullable(generationJobRepository.earliestQuotaResume(examSlotIds, now,
                now.plus(QuotaDeferral.FAR), QuotaDeferral.PREFIX + "%"));
    }

    /** W1-2: job answer_check (chạy lại Solver) mới nhất của một bản giáo viên sửa; rỗng với câu LLM. */
    @Transactional(readOnly = true)
    public java.util.Optional<GenerationJob> latestAnswerCheckJob(Integer questionId) {
        return generationJobRepository.findFirstByQuestionIdAndJobTypeOrderByIdDesc(questionId,
                GenerationJob.TYPE_ANSWER_CHECK);
    }

    /** W2-3: job grounding (chấm "bám tài liệu") của một câu; rỗng khi câu không có tài liệu hoặc chưa được xếp job. */
    @Transactional(readOnly = true)
    public java.util.Optional<GenerationJob> latestGroundingJob(Integer questionId) {
        return generationJobRepository.findFirstByQuestionIdAndJobTypeOrderByIdDesc(questionId,
                GenerationJob.TYPE_GROUNDING_CHECK);
    }

    /** F-07: job kiểm mới nhất của một tập câu: answer_check (Solver) và grounding_check (bám tài liệu), khóa theo id câu. */
    public record CheckJobs(java.util.Map<Integer, GenerationJob> answerCheck, java.util.Map<Integer, GenerationJob> grounding) {}

    /** F-07: như {@link #latestAnswerCheckJob} và {@link #latestGroundingJob} cho cả tập câu, bằng một truy vấn. */
    @Transactional(readOnly = true)
    public CheckJobs latestCheckJobs(java.util.Collection<Integer> questionIds) {
        java.util.Map<Integer, GenerationJob> answer = new java.util.HashMap<>();
        java.util.Map<Integer, GenerationJob> grounding = new java.util.HashMap<>();
        if (!questionIds.isEmpty()) {
            for (GenerationJob job : generationJobRepository.findLatestCheckJobs(questionIds)) {
                if (GenerationJob.TYPE_ANSWER_CHECK.equals(job.getJobType())) {
                    answer.put(job.getQuestionId(), job);
                } else if (GenerationJob.TYPE_GROUNDING_CHECK.equals(job.getJobType())) {
                    grounding.put(job.getQuestionId(), job);
                }
            }
        }
        return new CheckJobs(answer, grounding);
    }

    /**
     * PA1 (13/11, H-4): lỗi sai của phương án nhiễu đang hiển thị của các câu ({@link DistractorView}). Chỉ câu mcq có lỗi để
     * hiển thị có khóa trong kết quả. Kết quả kiểm đã lưu của chính các câu lấy từ {@code storedKnown} nếu nơi gọi đã nạp
     * ({@code knownIds}: các id đã biết có hay không có dòng answer_check); bản trước của bản sửa CHƯA có dòng answer_check
     * được nạp thêm theo lô (chỉ khi có bản sửa đang chờ kiểm lại).
     *
     * @param storedKnown kết quả đã lưu của các câu CÓ dòng answer_check (map rỗng khi dòng không kiểm lỗi)
     */
    @Transactional(readOnly = true)
    public java.util.Map<Integer, java.util.Map<String, DistractorView.Item>> distractorViews(
            java.util.Collection<GeneratedQuestion> questions,
            java.util.Map<Integer, java.util.Map<String, com.datn.mcq.enums.DistractorCheck>> storedKnown,
            java.util.Set<Integer> knownIds) {
        java.util.Map<Integer, java.util.Map<String, DistractorView.Item>> out = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, GeneratedQuestion> cache = new java.util.HashMap<>();
        java.util.List<GeneratedQuestion> shown = new java.util.ArrayList<>();
        for (GeneratedQuestion q : questions) {
            cache.put(q.getId(), q);
            if (q.getQuestionType() == QuestionType.MCQ && !DistractorView.shownTexts(q).isEmpty()) {
                shown.add(q);
            }
        }
        if (shown.isEmpty()) {
            return out;
        }
        java.util.Map<Integer, java.util.Map<String, com.datn.mcq.enums.DistractorCheck>> stored =
                new java.util.HashMap<>(storedKnown);
        java.util.Set<Integer> known = new java.util.HashSet<>(knownIds);
        java.util.Set<Integer> needChecks = new java.util.LinkedHashSet<>();
        shown.forEach(q -> {
            if (!known.contains(q.getId())) {
                needChecks.add(q.getId());
            }
        });
        if (!needChecks.isEmpty()) {
            stored.putAll(qcOrchestrator.storedDistractorChecks(needChecks));
            known.addAll(needChecks);
        }
        // Lần ngược chuỗi bản sửa chưa có dòng answer_check, mỗi vòng một lô (số vòng = độ sâu chuỗi đang chờ, thường 1).
        java.util.List<GeneratedQuestion> frontier = new java.util.ArrayList<>(shown);
        for (int depth = 0; depth < DistractorView.MAX_DEPTH && !frontier.isEmpty(); depth++) {
            java.util.Set<Integer> prevIds = new java.util.LinkedHashSet<>();
            for (GeneratedQuestion q : frontier) {
                if (!stored.containsKey(q.getId()) && q.getEditedFromQuestionId() != null) {
                    prevIds.add(q.getEditedFromQuestionId());
                }
            }
            java.util.Set<Integer> missing = new java.util.LinkedHashSet<>(prevIds);
            missing.removeAll(cache.keySet());
            if (!missing.isEmpty()) {
                for (GeneratedQuestion p : generatedQuestionRepository.findAllById(missing)) {
                    cache.put(p.getId(), p);
                }
            }
            java.util.Set<Integer> unknown = new java.util.LinkedHashSet<>(prevIds);
            unknown.removeAll(known);
            if (!unknown.isEmpty()) {
                stored.putAll(qcOrchestrator.storedDistractorChecks(unknown));
                known.addAll(unknown);
            }
            frontier = prevIds.stream().map(cache::get).filter(java.util.Objects::nonNull).toList();
        }
        for (GeneratedQuestion q : shown) {
            out.put(q.getId(), DistractorView.resolve(q, cache::get, stored::get));
        }
        return out;
    }

    /** F-07: nhiều câu theo id bằng một truy vấn; id không có thì NoSuchElementException như {@link #getQuestionById}. */
    @Transactional(readOnly = true)
    public java.util.Map<Integer, GeneratedQuestion> questionsByIds(java.util.Collection<Integer> ids) {
        java.util.Map<Integer, GeneratedQuestion> out = new java.util.LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (GeneratedQuestion q : generatedQuestionRepository.findAllById(ids)) {
            out.put(q.getId(), q);
        }
        for (Integer id : ids) {
            if (!out.containsKey(id)) {
                throw new NoSuchElementException("Không tìm thấy câu hỏi id=" + id);
            }
        }
        return out;
    }

    /** F-07: 4 ý (a-d) của các câu tf trong tập bằng một truy vấn; câu không có ý thì không có khóa. */
    @Transactional(readOnly = true)
    public java.util.Map<Integer, List<GeneratedQuestionStatement>> statementsByQuestionIds(
            java.util.Collection<Integer> questionIds) {
        java.util.Map<Integer, List<GeneratedQuestionStatement>> out = new java.util.HashMap<>();
        if (questionIds.isEmpty()) {
            return out;
        }
        for (GeneratedQuestionStatement s : statementRepository.findByQuestionIdInOrderByQuestionIdAscPositionAsc(questionIds)) {
            out.computeIfAbsent(s.getQuestionId(), k -> new ArrayList<>()).add(s);
        }
        return out;
    }

    /** F-07: như {@link #originalQuestionId} cho cả tập câu bằng một truy vấn đệ quy; mọi id được hỏi đều có trong kết quả. */
    @Transactional(readOnly = true)
    public java.util.Map<Integer, Integer> originalQuestionIds(java.util.Collection<Integer> questionIds) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>();
        if (questionIds.isEmpty()) {
            return out;
        }
        for (Object[] row : generatedQuestionRepository.findOriginalIds(questionIds)) {
            out.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }
        for (Integer id : questionIds) {
            out.putIfAbsent(id, id);
        }
        return out;
    }

    /**
     * PR B (12/11): mức lấy yêu cầu ({@code replace}, {@code cell}, {@code exam}) của job đã sinh ra từng câu, một truy vấn;
     * câu không có job hoặc job không có yêu cầu thì vắng mặt.
     */
    @Transactional(readOnly = true)
    public java.util.Map<Integer, String> requestScopes(java.util.Collection<Integer> questionIds) {
        java.util.Map<Integer, String> out = new java.util.HashMap<>();
        if (questionIds.isEmpty()) {
            return out;
        }
        for (Object[] row : generationJobRepository.findRequestScopes(questionIds)) {
            out.put(((Number) row[0]).intValue(), String.valueOf(row[1]));
        }
        return out;
    }

    /** W1-3: mọi job sinh câu (không kể answer_check) của một chỗ trong đề, theo thứ tự tạo. */
    @Transactional(readOnly = true)
    public List<GenerationJob> generateJobsForSlot(Integer examSlotId) {
        return generationJobRepository.findByExamSlotIdAndJobTypeOrderByIdAsc(examSlotId, GenerationJob.TYPE_GENERATE);
    }

    /** W1-3: bản giáo viên sửa tiếp theo của một câu; rỗng nếu chưa có. */
    @Transactional(readOnly = true)
    public java.util.Optional<GeneratedQuestion> editedChild(Integer questionId) {
        return generatedQuestionRepository.findByEditedFromQuestionId(questionId);
    }

    /**
     * W1-2: bản gốc LLM của một câu theo chuỗi edited_from_question_id (chính câu đó nếu là đầu ra LLM).
     */
    @Transactional(readOnly = true)
    public Integer originalQuestionId(Integer questionId) {
        GeneratedQuestion cur = getQuestionById(questionId);
        for (int i = 0; i < 1000 && cur.getEditedFromQuestionId() != null; i++) {
            cur = getQuestionById(cur.getEditedFromQuestionId());
        }
        return cur.getId();
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
     * tf (GĐ3): run production, zero-shot, prompt_version tf-v2-draft; bắt buộc 4 mức của 4 ý, ghi vào
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
        Strategy chosenStrategy = Strategy.ZERO_SHOT;
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
                    tpl.getLearningOutcome(), tpl.getSourceQuestion(), chosenDiff);
            // async-queue-v1 là run few-shot cũ (C1/C2/C3), worker từ chối; zero-shot dùng bản v2.
            promptVersion = "async-queue-v2";
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
            // W2-4b (C): bộ điều tiết Gemini dùng chung lo giãn cách giữa các lượt gọi; job xếp hàng chạy ngay.
            job.setNextRunAt(now);
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
