package com.datn.mcq.generation.worker;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.common.util.TextClip;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.service.PreviousQuestionLookup;
import com.datn.mcq.generation.service.TrueFalseQuestionStore;
import com.datn.mcq.llm.prompt.MatrixCellSpec;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfLabelPattern;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.parser.McqJsonParser;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.generation.repository.GenerationJobRepository;
import com.datn.mcq.generation.service.StoredQcInput;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.dto.SolverVerdict;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
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

    /** V14: giới hạn độ dài generated_question.solver_error (khớp CHECK chk_gq_solver_error). */
    public static final int MAX_SOLVER_ERROR = 1000;

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
            List<Difficulty> targetStatementDifficulties,
            Purpose purpose,
            /* mẫu nhãn bắt buộc của job tf theo ma trận (TfLabelPattern, đọc từ prompt_note); null với job khác */
            String tfLabelPattern,
            /* V13 (W1-2): loại job; với answer_check chỉ có jobId, runId, templateId, attempt, questionType, questionId */
            String jobType,
            /* answer_check: bản giáo viên sửa cần chạy lại Solver; null với job sinh câu */
            Integer questionId,
            /* W1-3: chỗ trong đề mà job sinh câu phục vụ (generation_job.exam_slot_id); null với job thường */
            Integer examSlotId
    ) {
        public boolean answerCheck() {
            return GenerationJob.TYPE_ANSWER_CHECK.equals(jobType);
        }
    }

    private final GenerationJobRepository generationJobRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final CatalogService catalogService;
    private final ExperimentService experimentService;
    private final PromptBuilder promptBuilder;
    private final PreviousQuestionLookup previousQuestionLookup;
    private final TrueFalseQuestionStore trueFalseQuestionStore;
    private final GeneratedQuestionStatementRepository statementRepository;
    private final QcOrchestrator qcOrchestrator;

    public GenerationJobProcessor(
            GenerationJobRepository generationJobRepository,
            GeneratedQuestionRepository generatedQuestionRepository,
            CatalogService catalogService,
            ExperimentService experimentService,
            PromptBuilder promptBuilder,
            TrueFalseQuestionStore trueFalseQuestionStore,
            PreviousQuestionLookup previousQuestionLookup,
            GeneratedQuestionStatementRepository statementRepository,
            QcOrchestrator qcOrchestrator) {
        this.generationJobRepository = generationJobRepository;
        this.generatedQuestionRepository = generatedQuestionRepository;
        this.catalogService = catalogService;
        this.experimentService = experimentService;
        this.promptBuilder = promptBuilder;
        this.trueFalseQuestionStore = trueFalseQuestionStore;
        this.previousQuestionLookup = previousQuestionLookup;
        this.statementRepository = statementRepository;
        this.qcOrchestrator = qcOrchestrator;
    }

    @Transactional
    public Optional<ClaimedJobContext> claimNextPendingJob() {
        return claimNextPendingJob(true);
    }

    /**
     * @param includeAnswerCheck false: chỉ nhận job sinh câu (process-next với runAnswerCheck = false, W1-2)
     */
    @Transactional
    public Optional<ClaimedJobContext> claimNextPendingJob(boolean includeAnswerCheck) {
        LocalDateTime now = LocalDateTime.now();
        // Đợt A: FOR UPDATE SKIP LOCKED, đổi sang running trong cùng transaction nên không nhận trùng job.
        Optional<GenerationJob> optJob = includeAnswerCheck
                ? generationJobRepository.lockNextDuePendingJob(now)
                : generationJobRepository.lockNextDuePendingGenerateJob(now);
        if (optJob.isEmpty()) {
            return Optional.empty();
        }
        GenerationJob job = optJob.get();
        int nextAttempt = (job.getAttempt() != null ? job.getAttempt() : 0) + 1;
        job.setStatus(JobStatus.RUNNING);
        job.setAttempt(nextAttempt);
        job.setStartedAt(now);
        job.setUpdatedAt(now);
        generationJobRepository.save(job);

        QuestionType jobQuestionType = job.getQuestionType() != null ? job.getQuestionType() : QuestionType.MCQ;
        if (GenerationJob.TYPE_ANSWER_CHECK.equals(job.getJobType())) {
            // W1-2: job chạy lại Solver cho bản giáo viên sửa, không dựng prompt sinh câu.
            return Optional.of(new ClaimedJobContext(job.getId(), job.getRunId(), job.getTemplateId(), nextAttempt,
                    null, null, null, null, null, false, jobQuestionType, List.of(), null, null,
                    job.getJobType(), job.getQuestionId(), null));
        }

        ExperimentRun run = experimentService.getById(job.getRunId());
        QuestionTemplate tpl = catalogService.getTemplateById(job.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());

        QuestionType questionType = job.getQuestionType() != null ? job.getQuestionType() : QuestionType.MCQ;
        List<Difficulty> statementLevels = GenerationService.splitLevels(job.getTargetStatementDifficulties());
        // V7: run sinh câu theo ô ma trận mang thông số ô ở hyperparams; prompt dựng lại kèm câu đã sinh của ô.
        boolean matrix = MatrixPromptDraftV0.isMatrixVersion(run.getPromptVersion()) && run.getHyperparams() != null;
        String prompt = matrix
                ? MatrixPromptDraftV0.buildGenerationPrompt(MatrixCellSpec.fromJson(run.getHyperparams()),
                        previousQuestionLookup.recentForMatrix(tpl.getId(), questionType), job.getPromptNote())
                : questionType == QuestionType.TF
                ? TfPromptDraftV0.buildGenerationPrompt(
                        subject.getName(),
                        tpl.getGradeLevel(),
                        tpl.getTopic(),
                        tpl.getLearningOutcome(),
                        tpl.getSourceQuestion(),
                        statementLevels,
                        previousQuestionLookup.recent(tpl.getId(), QuestionType.TF))
                : questionType == QuestionType.SA
                ? SaPromptDraftV0.buildGenerationPrompt(
                        subject.getName(),
                        tpl.getGradeLevel(),
                        tpl.getTopic(),
                        tpl.getLearningOutcome(),
                        tpl.getSourceQuestion(),
                        job.getTargetDifficulty(),
                        previousQuestionLookup.recent(tpl.getId(), QuestionType.SA))
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
                statementLevels,
                run.getPurpose(),
                matrix && questionType == QuestionType.TF ? TfLabelPattern.fromNote(job.getPromptNote()) : null,
                job.getJobType(),
                null,
                job.getExamSlotId()
        ));
    }

    /**
     * W1-2, bước 1 của job answer_check (transaction ngắn, không gọi mạng): dựng đầu vào Solver từ bản đã lưu. Bản đã
     * bị thay bằng bản sửa mới, hoặc không còn ở generated (ví dụ đã Giữ), thì đánh dấu job xong và trả rỗng, không
     * gọi LLM.
     */
    @Transactional
    public Optional<QuestionQcInput> prepareAnswerCheck(ClaimedJobContext ctx) {
        GeneratedQuestion gq = generatedQuestionRepository.findById(ctx.questionId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + ctx.questionId()));
        String skip = skipReason(gq);
        if (skip != null) {
            finishAnswerCheckJob(ctx.jobId(), skip);
            return Optional.empty();
        }
        Subject subject = catalogService.getSubjectById(catalogService.getTemplateById(gq.getTemplateId()).getSubjectId());
        return Optional.of(StoredQcInput.of(gq,
                gq.getQuestionType() == QuestionType.TF ? statementRepository.findByQuestionIdOrderByPositionAsc(gq.getId())
                        : List.of(),
                isMathSubject(subject.getName()), true));
    }

    /**
     * W1-2, bước 3 của job answer_check (transaction ngắn): ghi kết quả Solver, tính trạng thái câu (QcStatusDecider,
     * theo dòng format và dedup đã lưu) và đánh dấu job xong trong CÙNG một transaction. Nếu trong lúc Solver chạy câu đã
     * bị thay bằng bản sửa mới (hoặc không còn ở generated) thì bỏ kết quả: kết quả của bản cũ không được ghi lên bất kỳ
     * bản nào. Không bao giờ đổi đáp án của câu.
     *
     * @return trạng thái câu sau khi ghi
     */
    @Transactional
    public QuestionStatus completeAnswerCheck(ClaimedJobContext ctx, SolverVerdict verdict) {
        GeneratedQuestion gq = generatedQuestionRepository.findById(ctx.questionId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + ctx.questionId()));
        String skip = skipReason(gq);
        if (skip != null) {
            finishAnswerCheckJob(ctx.jobId(), skip);
            return gq.getStatus();
        }
        qcOrchestrator.recordSolver(gq.getId(), verdict);
        QuestionStatus status = qcOrchestrator.decideWithStoredChecks(gq.getId(), verdict.status());
        gq.setStatus(status);
        generatedQuestionRepository.save(gq);
        finishAnswerCheckJob(ctx.jobId(), null);
        return status;
    }

    /** W1-2: job answer_check thất bại ngay, không thử lại (lỗi cấu hình như thiếu API key cho Solver). */
    @Transactional
    public GenerationJob failJobNow(Integer jobId, String error) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + jobId));
        job.setStatus(JobStatus.FAILED);
        job.setLastError(error);
        job.setUpdatedAt(LocalDateTime.now());
        return generationJobRepository.save(job);
    }

    private String skipReason(GeneratedQuestion gq) {
        if (generatedQuestionRepository.existsByEditedFromQuestionId(gq.getId())) {
            return "skipped: superseded";
        }
        if (gq.getStatus() != QuestionStatus.GENERATED) {
            return "skipped: status " + gq.getStatus().getDbValue();
        }
        return null;
    }

    private void finishAnswerCheckJob(Integer jobId, String note) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + jobId));
        job.setStatus(JobStatus.DONE);
        job.setLastError(note);
        job.setUpdatedAt(LocalDateTime.now());
        generationJobRepository.save(job);
    }

    /**
     * Lưu câu Đúng/Sai sinh từ job (câu và 4 ý) và ghi câu vào job, trong cùng transaction ngắn (GĐ3). Job chưa done
     * (L-39, xem {@link #finishGeneratedJob}).
     */
    @Transactional
    public GeneratedQuestion saveTrueFalseFromJob(
            ClaimedJobContext ctx,
            LlmCallResult llmResult,
            ParsedTfDto parsed) {
        GeneratedQuestion saved = trueFalseQuestionStore.save(ctx.templateId(), ctx.runId(),
                ctx.targetStatementDifficulties(), llmResult, parsed, ctx.attempt());
        recordJobQuestion(ctx, llmResult, saved.getId(), parsed.parseError());
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
        recordJobQuestion(ctx, llmResult, savedQuestion.getId(), parsed.parseError());
        return savedQuestion;
    }

    /** Lưu câu Trả lời ngắn sinh từ job và ghi câu vào job, trong cùng transaction ngắn (V4); job chưa done (L-39). */
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
        recordJobQuestion(ctx, llmResult, savedQuestion.getId(), parsed.parseError());
        return savedQuestion;
    }

    /**
     * Ghi câu vừa lưu và số liệu lời gọi LLM vào job. L-39: job VẪN ở running, chưa done: QC (dedup, Solver) chưa
     * chạy, câu còn ở trạng thái trung gian generated. Đối soát chỗ trong đề thấy job running thì chờ, không coi câu là
     * lệch Solver. Job chỉ done ở {@link #finishGeneratedJob}, cùng transaction với trạng thái QC cuối.
     */
    private void recordJobQuestion(ClaimedJobContext ctx, LlmCallResult llmResult, Integer questionId, String parseError) {
        GenerationJob job = generationJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + ctx.jobId()));
        job.setQuestionId(questionId);
        job.setTokenCount(llmResult.tokenCount());
        job.setLatencyMs(llmResult.latencyMs());
        job.setCostUsd(llmResult.costUsd());
        job.setLastError(parseError);
        job.setUpdatedAt(LocalDateTime.now());
        generationJobRepository.save(job);
    }

    /**
     * L-39: trạng thái QC cuối của câu vừa sinh và job done trong CÙNG một transaction ngắn (sau khi QC chạy xong, ngoài
     * transaction). Nhờ đó không ai thấy job done mà câu còn ở trạng thái trung gian.
     */
    @Transactional
    public void finishGeneratedJob(Integer jobId, Integer questionId, QuestionStatus newStatus) {
        finishGeneratedJob(jobId, questionId, newStatus, null);
    }

    /**
     * @param solverError (V14, W1-3) lý do Solver lỗi với câu này (đã có từ báo cáo QC khi answer_check ở trạng thái
     *                    ERROR); null khi Solver không lỗi. Cắt theo giới hạn cột.
     */
    @Transactional
    public void finishGeneratedJob(Integer jobId, Integer questionId, QuestionStatus newStatus, String solverError) {
        updateQuestionStatus(questionId, newStatus);
        if (solverError != null) {
            GeneratedQuestion gq = generatedQuestionRepository.findById(questionId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + questionId));
            gq.setSolverError(TextClip.clip(solverError, MAX_SOLVER_ERROR));
            generatedQuestionRepository.save(gq);
        }
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + jobId));
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

