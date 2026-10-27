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
import com.datn.mcq.llm.client.GeminiDailyUsageLookup;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.dto.GroundingResult;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.dto.SolverVerdict;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.datn.mcq.qc.service.GroundingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * <p>W2-4b (A), hàng rào claim: mỗi lần nhận job tăng {@code generation_job.claim_seq} (V22) và worker giữ giá trị đó trong
 * {@link ClaimedJobContext#claimSeq()}. Mọi bước ghi kết quả (lưu câu, finish, fail, kết quả answer_check/grounding) mở đầu
 * bằng {@link GenerationJobRepository#renewClaim}: chỉ khi job còn running với đúng claim_seq thì mới ghi tiếp; không thì
 * bỏ kết quả (log WARN "stale finish"). Lệnh đó giữ khóa dòng job tới hết transaction của bước ghi, nên job không thể bị trả
 * về hay nhận lại giữa chừng.
 */
@Service
public class GenerationJobProcessor implements GeminiDailyUsageLookup {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationJobProcessor.class);

    /**
     * W2-4b (C): đếm tổng số lượt gọi Gemini đã lưu kết quả trong DB kể từ mốc {@code since} (00:00 giờ Thái Bình Dương
     * đã đổi sang giờ địa phương của JVM): câu LLM sinh ({@code edited_from_question_id IS NULL}) + kết quả Solver có
     * {@code token_count} + kết quả judge "bám tài liệu" có {@code token_count}.
     */
    @Override
    @Transactional(readOnly = true)
    public int countCallsSince(LocalDateTime since) {
        int questions = generatedQuestionRepository.countLlmGeneratedSince(since);
        int solver = qcOrchestrator.countSolverCallsSince(since);
        int grounding = groundingService != null ? groundingService.countCallsSince(since) : 0;
        return questions + solver + grounding;
    }

    /** V14: giới hạn độ dài generated_question.solver_error (khớp CHECK chk_gq_solver_error). */
    public static final int MAX_SOLVER_ERROR = 1000;

    /** prompt_version của run mcq tạo bằng enqueue trước 25/10 (chiến lược few-shot của thí nghiệm C1/C2/C3). */
    static final String LEGACY_MCQ_PROMPT_VERSION = "async-queue-v1";
    static final String LEGACY_MCQ_JOB_ERROR = "Job mcq theo chiến lược C1/C2/C3 (prompt_version "
            + LEGACY_MCQ_PROMPT_VERSION + ") không còn được hỗ trợ từ 25/10/2026; hãy tạo lại job.";

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
            Integer examSlotId,
            /* V22 (W2-4b): claim_seq lúc nhận job, hàng rào cho mọi ghi kết quả (khác attempt, bị hoàn lại khi reset job kẹt) */
            int claimSeq
    ) {
        public boolean answerCheck() {
            return GenerationJob.TYPE_ANSWER_CHECK.equals(jobType);
        }

        /** W2-3: job chấm "bám tài liệu" của một câu đã sinh; như answer_check chỉ có các trường định danh và questionId. */
        public boolean grounding() {
            return GenerationJob.TYPE_GROUNDING_CHECK.equals(jobType);
        }
    }

    /**
     * W2-3: đầu vào của một lần chấm "bám tài liệu", dựng từ câu và job đã lưu.
     *
     * @param context  đúng đoạn tài liệu model sinh câu đã thấy (generation_job.material_context)
     * @param refsJson mảng JSON các đoạn đó (generation_job.material_refs), để ghi cùng kết quả
     */
    public record GroundingJobInput(QuestionQcInput question, String context, String refsJson) {}

    private final GenerationJobRepository generationJobRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final CatalogService catalogService;
    private final ExperimentService experimentService;
    private final PromptBuilder promptBuilder;
    private final PreviousQuestionLookup previousQuestionLookup;
    private final TrueFalseQuestionStore trueFalseQuestionStore;
    private final GeneratedQuestionStatementRepository statementRepository;
    private final QcOrchestrator qcOrchestrator;

    /** W2-3: lưu kết quả grounding; nạp bằng field để không đổi constructor. */
    @Autowired
    private GroundingService groundingService;

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
        // V22 (W2-4b): claim_seq chỉ tăng; attempt thì bị hoàn lại khi reset job kẹt nên không làm hàng rào được.
        int claimSeq = (job.getClaimSeq() != null ? job.getClaimSeq() : 0) + 1;
        job.setStatus(JobStatus.RUNNING);
        job.setAttempt(nextAttempt);
        job.setClaimSeq(claimSeq);
        job.setStartedAt(now);
        job.setUpdatedAt(now);
        generationJobRepository.save(job);

        QuestionType jobQuestionType = job.getQuestionType() != null ? job.getQuestionType() : QuestionType.MCQ;
        if (GenerationJob.TYPE_ANSWER_CHECK.equals(job.getJobType())) {
            // W1-2: job chạy lại Solver cho bản giáo viên sửa, không dựng prompt sinh câu.
            return Optional.of(new ClaimedJobContext(job.getId(), job.getRunId(), job.getTemplateId(), nextAttempt,
                    null, null, null, null, null, false, jobQuestionType, List.of(), null, null,
                    job.getJobType(), job.getQuestionId(), null, claimSeq));
        }

        if (GenerationJob.TYPE_GROUNDING_CHECK.equals(job.getJobType())) {
            // W2-3: job chấm "bám tài liệu", không dựng prompt sinh câu; đoạn tài liệu đọc ở prepareGrounding.
            return Optional.of(new ClaimedJobContext(job.getId(), job.getRunId(), job.getTemplateId(), nextAttempt,
                    null, null, null, null, null, false, jobQuestionType, List.of(), null, null,
                    job.getJobType(), job.getQuestionId(), null, claimSeq));
        }

        ExperimentRun run = experimentService.getById(job.getRunId());
        if (LEGACY_MCQ_PROMPT_VERSION.equals(run.getPromptVersion())) {
            // Thí nghiệm C1/C2/C3 đã bỏ (25/10): không dựng lại prompt few-shot, cũng không xử lý riêng; job cũ thất bại
            // ngay (không gọi LLM) và worker nhận job kế tiếp.
            job.setStatus(JobStatus.FAILED);
            job.setLastError(LEGACY_MCQ_JOB_ERROR);
            job.setUpdatedAt(now);
            generationJobRepository.save(job);
            return claimNextPendingJob(includeAnswerCheck);
        }
        QuestionTemplate tpl = catalogService.getTemplateById(job.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());

        QuestionType questionType = job.getQuestionType() != null ? job.getQuestionType() : QuestionType.MCQ;
        List<Difficulty> statementLevels = GenerationService.splitLevels(job.getTargetStatementDifficulties());
        // V7: run sinh câu theo ô ma trận mang thông số ô ở hyperparams; prompt dựng lại kèm câu đã sinh của ô.
        boolean matrix = MatrixPromptDraftV0.isMatrixVersion(run.getPromptVersion()) && run.getHyperparams() != null;
        String prompt = matrix
                ? MatrixPromptDraftV0.buildGenerationPrompt(MatrixCellSpec.fromJson(run.getHyperparams()),
                        previousQuestionLookup.recentForMatrix(tpl.getId(), questionType), job.getPromptNote(),
                        job.getMaterialContext())
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
                job.getExamSlotId(),
                claimSeq
        ));
    }

    // ---------- hàng rào claim (W2-4b, A) ----------

    /**
     * Kiểm tra worker còn là chủ job và gia hạn {@code started_at} (để job đang sống, kể cả đang chờ bộ điều tiết hay
     * Gemini chậm, không bị trả về pending). Gọi trước MỖI lượt gọi Gemini của job. {@code false}: job đã bị trả về/nhận
     * lại/đổi trạng thái, worker phải dừng ngay (không gọi Gemini, không ghi gì).
     */
    @Transactional
    public boolean renewClaim(ClaimedJobContext ctx) {
        return holdsClaim(ctx);
    }

    private boolean holdsClaim(ClaimedJobContext ctx) {
        return generationJobRepository.renewClaim(ctx.jobId(), ctx.claimSeq(), LocalDateTime.now()) > 0;
    }

    /** Mở đầu mỗi bước ghi kết quả: không còn là chủ job thì ném lỗi để transaction của bước đó rollback. */
    private void requireClaim(ClaimedJobContext ctx) {
        if (!holdsClaim(ctx)) {
            LOG.warn("stale finish: job {} (claim_seq {}) không còn running với claim_seq này, bỏ kết quả của worker cũ",
                    ctx.jobId(), ctx.claimSeq());
            throw new StaleJobClaimException(ctx.jobId(), ctx.claimSeq());
        }
    }

    /**
     * Câu đã lưu của một claim đã bị loại (job bị trả về và nhận lại trong lúc worker cũ chạy QC) bị xóa cùng các dòng con
     * (dòng QC, các ý tf) trong CÙNG transaction. Câu đã được nơi khác dùng (chỗ trong đề, ngân hàng, đánh giá, ...) thì
     * KHÔNG xóa, chỉ log WARN. Chỉ gọi khi {@link #renewClaim} trả false.
     * (Hạn chế đã biết: nếu giữa lúc kiểm tra tham chiếu và lệnh xóa có transaction khác chèn tham chiếu FK tới câu,
     * lệnh xóa sẽ lỗi FK, transaction rollback và câu được giữ lại, dữ liệu không hỏng).
     */
    @Transactional
    public void discardStaleQuestion(ClaimedJobContext ctx, Integer questionId) {
        discardOrphanQuestion(ctx.jobId(), ctx.claimSeq(), questionId);
    }

    private void discardOrphanQuestion(Integer jobId, int claimSeq, Integer questionId) {
        if (questionId == null) {
            return;
        }
        if (generatedQuestionRepository.isReferencedBesidesJob(questionId, jobId)) {
            LOG.warn("stale finish: câu {} của job {} (claim_seq {}) đã được nơi khác tham chiếu (chỗ trong đề, ngân hàng,"
                    + " đánh giá, ...), giữ lại không xóa", questionId, jobId, claimSeq);
            return;
        }
        generationJobRepository.clearQuestionLink(jobId, questionId);
        qcOrchestrator.deleteAllResults(questionId);
        generatedQuestionRepository.deleteStatementsOf(questionId);
        generatedQuestionRepository.deleteOrphan(questionId);
        LOG.warn("stale finish: đã xóa câu mồ côi {} của job {} (claim_seq {})", questionId, jobId, claimSeq);
    }

    /**
     * W1-2, bước 1 của job answer_check (transaction ngắn, không gọi mạng): dựng đầu vào Solver từ bản đã lưu. Bản đã
     * bị thay bằng bản sửa mới, hoặc không còn ở generated (ví dụ đã Giữ), thì đánh dấu job xong và trả rỗng, không
     * gọi LLM.
     */
    @Transactional
    public Optional<QuestionQcInput> prepareAnswerCheck(ClaimedJobContext ctx) {
        requireClaim(ctx);
        GeneratedQuestion gq = generatedQuestionRepository.findById(ctx.questionId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + ctx.questionId()));
        String skip = skipReason(gq);
        if (skip != null) {
            finishQuestionJob(ctx.jobId(), skip);
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
     * bản nào. Không bao giờ đổi đáp án của câu. W2-4b: job không còn thuộc claim này thì ném
     * {@link StaleJobClaimException} (rollback, không ghi gì).
     *
     * @return trạng thái câu sau khi ghi
     */
    @Transactional
    public QuestionStatus completeAnswerCheck(ClaimedJobContext ctx, SolverVerdict verdict) {
        requireClaim(ctx);
        GeneratedQuestion gq = generatedQuestionRepository.findById(ctx.questionId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + ctx.questionId()));
        String skip = skipReason(gq);
        if (skip != null) {
            finishQuestionJob(ctx.jobId(), skip);
            return gq.getStatus();
        }
        qcOrchestrator.recordSolver(gq.getId(), verdict);
        QuestionStatus status = qcOrchestrator.decideWithStoredChecks(gq.getId(), verdict.status());
        gq.setStatus(status);
        gq.setSolverError(null);
        generatedQuestionRepository.save(gq);
        finishQuestionJob(ctx.jobId(), null);
        return status;
    }

    /**
     * W2-3, bước 1 của job grounding_check (transaction ngắn, không gọi mạng): dựng đầu vào cho judge từ câu đã lưu và đoạn
     * tài liệu chụp ở job. Câu đã bị thay bằng bản giáo viên sửa mới, hoặc đã có kết quả, thì đánh dấu job xong và trả rỗng,
     * không gọi LLM.
     */
    @Transactional
    public Optional<GroundingJobInput> prepareGrounding(ClaimedJobContext ctx) {
        requireClaim(ctx);
        GenerationJob job = generationJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + ctx.jobId()));
        GeneratedQuestion gq = generatedQuestionRepository.findById(ctx.questionId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + ctx.questionId()));
        String skip = null;
        if (generatedQuestionRepository.existsByEditedFromQuestionId(gq.getId())) {
            skip = "skipped: superseded";
        } else if (groundingService.find(gq.getId()).isPresent()) {
            skip = "skipped: already checked";
        }
        if (skip != null) {
            finishQuestionJob(ctx.jobId(), skip);
            return Optional.empty();
        }
        QuestionQcInput input = StoredQcInput.of(gq,
                gq.getQuestionType() == QuestionType.TF ? statementRepository.findByQuestionIdOrderByPositionAsc(gq.getId())
                        : List.of(), false, false);
        return Optional.of(new GroundingJobInput(input, job.getMaterialContext(), job.getMaterialRefs()));
    }

    /**
     * W2-3, bước 3 của job grounding_check (transaction ngắn): ghi kết quả và đánh dấu job xong trong CÙNG một transaction.
     * KHÔNG đổi generated_question.status (Q-2, M-10): kết quả chỉ là cảnh báo. W2-4b: job không còn thuộc claim này thì
     * ném {@link StaleJobClaimException} (rollback, không ghi gì).
     */
    @Transactional
    public void completeGrounding(ClaimedJobContext ctx, GroundingResult result, String refsJson) {
        requireClaim(ctx);
        groundingService.record(ctx.questionId(), result, refsJson, true, ctx.jobId());
        finishQuestionJob(ctx.jobId(), null);
    }

    /**
     * W1-2: job answer_check thất bại ngay, không thử lại (lỗi cấu hình như thiếu API key cho Solver). W2-4b: job không
     * còn thuộc claim này thì không đổi gì, trả job như đang có.
     */
    @Transactional
    public GenerationJob failJobNow(ClaimedJobContext ctx, String error) {
        boolean owned = holdsClaim(ctx);
        GenerationJob job = generationJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + ctx.jobId()));
        if (!owned) {
            LOG.warn("stale finish: job {} (claim_seq {}) không còn running với claim_seq này, bỏ lỗi của worker cũ",
                    ctx.jobId(), ctx.claimSeq());
            return job;
        }
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

    /** Đánh dấu job gắn với một câu (answer_check, grounding_check) đã xong; {@code note} (nếu có) ghi lý do bỏ qua. */
    private void finishQuestionJob(Integer jobId, String note) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + jobId));
        job.setStatus(JobStatus.DONE);
        job.setLastError(note);
        job.setUpdatedAt(LocalDateTime.now());
        generationJobRepository.save(job);
    }

    /**
     * Lưu câu Đúng/Sai sinh từ job (câu và 4 ý) và ghi câu vào job, trong cùng transaction ngắn (GĐ3). Job chưa done
     * (L-39, xem {@link #finishGeneratedJob}). W2-4b: job không còn thuộc claim này thì ném
     * {@link StaleJobClaimException} trước khi ghi bất cứ thứ gì (không tạo câu).
     */
    @Transactional
    public GeneratedQuestion saveTrueFalseFromJob(
            ClaimedJobContext ctx,
            LlmCallResult llmResult,
            ParsedTfDto parsed) {
        requireClaim(ctx);
        GeneratedQuestion saved = trueFalseQuestionStore.save(ctx.templateId(), ctx.runId(),
                ctx.targetStatementDifficulties(), llmResult, parsed, ctx.attempt());
        recordJobQuestion(ctx, llmResult, saved.getId(), parsed.parseError());
        return saved;
    }

    /** Lưu câu mcq sinh từ job; hàng rào claim như {@link #saveTrueFalseFromJob}. */
    @Transactional
    public GeneratedQuestion saveGeneratedQuestionFromJob(
            ClaimedJobContext ctx,
            LlmCallResult llmResult,
            ParsedMcqDto parsed) {
        requireClaim(ctx);
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

    /**
     * Lưu câu Trả lời ngắn sinh từ job và ghi câu vào job, trong cùng transaction ngắn (V4); job chưa done (L-39). Hàng rào
     * claim như {@link #saveTrueFalseFromJob}.
     */
    @Transactional
    public GeneratedQuestion saveShortAnswerFromJob(
            ClaimedJobContext ctx,
            LlmCallResult llmResult,
            ParsedSaDto parsed) {
        requireClaim(ctx);
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
     *
     * <p>W2-4b (A): chỉ có hiệu lực khi worker còn là chủ job (running, đúng claim_seq). Không thì BỎ kết quả: không đổi
     * trạng thái câu hay job, câu mồ côi do claim này tạo bị xóa cùng dòng con trong cùng transaction (trừ khi đã được nơi
     * khác dùng), log WARN "stale finish", trả {@code false}.
     *
     * @param solverError (V14, W1-3) lý do Solver lỗi với câu này (đã có từ báo cáo QC khi answer_check ở trạng thái
     *                    ERROR); null khi Solver không lỗi. Cắt theo giới hạn cột.
     * @param jobNote     nếu khác null: ghi vào generation_job.last_error (lỗi xảy ra SAU khi câu đã lưu: job vẫn done, câu
     *                    giữ generated, không sinh câu mới)
     * @return true: đã ghi; false: worker không còn là chủ job, kết quả bị bỏ
     */
    @Transactional
    public boolean finishGeneratedJob(ClaimedJobContext ctx, Integer questionId, QuestionStatus newStatus,
                                      String solverError, String jobNote) {
        if (!holdsClaim(ctx)) {
            LOG.warn("stale finish: job {} (claim_seq {}) không còn running với claim_seq này, bỏ kết quả của worker cũ",
                    ctx.jobId(), ctx.claimSeq());
            discardOrphanQuestion(ctx.jobId(), ctx.claimSeq(), questionId);
            return false;
        }
        updateQuestionStatus(questionId, newStatus);
        if (solverError != null) {
            GeneratedQuestion gq = generatedQuestionRepository.findById(questionId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + questionId));
            gq.setSolverError(TextClip.clip(solverError, MAX_SOLVER_ERROR));
            generatedQuestionRepository.save(gq);
        }
        GenerationJob job = generationJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + ctx.jobId()));
        job.setStatus(JobStatus.DONE);
        if (jobNote != null) {
            job.setLastError(jobNote);
        }
        job.setUpdatedAt(LocalDateTime.now());
        generationJobRepository.save(job);
        enqueueAnswerCheckOnSolverError(job, questionId, newStatus, solverError, jobNote);
        enqueueGrounding(job, questionId, newStatus);
        return true;
    }

    /**
     * W2-4b (B): khi câu vừa sinh qua format và dedup (newStatus == GENERATED) nhưng Solver nội tuyến gặp lỗi kỹ thuật
     * (solverError != null, không phải lỗi sau khi lưu câu jobNote != null), xếp một job answer_check riêng cho chính
     * câu đó để thử lại riêng bước Solver (tối đa max-attempts lần) thay vì sinh lại câu mới.
     */
    private void enqueueAnswerCheckOnSolverError(GenerationJob origin, Integer questionId, QuestionStatus status,
                                                 String solverError, String jobNote) {
        if (solverError == null
                || jobNote != null
                || !GenerationJob.TYPE_GENERATE.equals(origin.getJobType())
                || status != QuestionStatus.GENERATED
                || generationJobRepository.findFirstByQuestionIdAndJobTypeOrderByIdDesc(questionId,
                        GenerationJob.TYPE_ANSWER_CHECK).isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        GenerationJob check = new GenerationJob();
        check.setJobType(GenerationJob.TYPE_ANSWER_CHECK);
        check.setRunId(origin.getRunId());
        check.setTemplateId(origin.getTemplateId());
        check.setQuestionType(origin.getQuestionType());
        check.setTargetStatementDifficulties(origin.getTargetStatementDifficulties());
        check.setQuestionId(questionId);
        check.setStatus(JobStatus.PENDING);
        check.setAttempt(0);
        check.setNextRunAt(now);
        check.setCreatedAt(now);
        check.setUpdatedAt(now);
        generationJobRepository.save(check);
    }

    /**
     * W2-3: câu sinh từ đề có tài liệu (job mang material_context) và còn dùng được (qua QC, hoặc lệch Solver nên có thể
     * được dùng kèm cờ) được xếp MỘT job grounding_check, trong CÙNG transaction với việc job sinh câu xong. Nhờ đó khi đối
     * soát thấy job sinh câu done thì job grounding đã có (hiện "đang kiểm"). Câu format_failed hoặc trùng sẽ bị sinh lại nên
     * không chấm. Mỗi câu tối đa một job (cả chỉ mục duy nhất ở DB lẫn kiểm tra ở đây).
     */
    private void enqueueGrounding(GenerationJob origin, Integer questionId, QuestionStatus status) {
        if (origin.getMaterialContext() == null
                || !GenerationJob.TYPE_GENERATE.equals(origin.getJobType())
                || (status != QuestionStatus.QC_PASSED && status != QuestionStatus.GENERATED)
                || generationJobRepository.findFirstByQuestionIdAndJobTypeOrderByIdDesc(questionId,
                        GenerationJob.TYPE_GROUNDING_CHECK).isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        GenerationJob g = new GenerationJob();
        g.setJobType(GenerationJob.TYPE_GROUNDING_CHECK);
        g.setRunId(origin.getRunId());
        g.setTemplateId(origin.getTemplateId());
        g.setQuestionType(origin.getQuestionType());
        g.setQuestionId(questionId);
        g.setMaterialContext(origin.getMaterialContext());
        g.setMaterialRefs(origin.getMaterialRefs());
        g.setStatus(JobStatus.PENDING);
        g.setAttempt(0);
        g.setNextRunAt(now);
        g.setCreatedAt(now);
        g.setUpdatedAt(now);
        generationJobRepository.save(g);
    }

    @Transactional
    public void updateQuestionStatus(Integer questionId, QuestionStatus newStatus) {
        GeneratedQuestion gq = generatedQuestionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generated_question id=" + questionId));
        gq.setStatus(newStatus);
        generatedQuestionRepository.save(gq);
    }

    /**
     * Ghi lỗi của một lần chạy: còn lượt thì job về pending (thử lại sau backoff), hết lượt thì failed. W2-4b (A): chỉ có
     * hiệu lực khi worker còn là chủ job; không thì không đổi gì (một lỗi muộn của worker cũ không được đè kết quả của
     * worker mới) và trả job như đang có.
     */
    @Transactional
    public GenerationJob recordJobFailure(ClaimedJobContext ctx, String errorMsg, int maxAttempts) {
        boolean owned = holdsClaim(ctx);
        GenerationJob job = generationJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + ctx.jobId()));
        if (!owned) {
            LOG.warn("stale finish: job {} (claim_seq {}) không còn running với claim_seq này, bỏ lỗi của worker cũ: {}",
                    ctx.jobId(), ctx.claimSeq(), errorMsg);
            return job;
        }
        int currentAttempt = job.getAttempt() != null ? job.getAttempt() : 1;
        job.setLastError(errorMsg);
        job.setUpdatedAt(LocalDateTime.now());
        if (ctx.answerCheck() && ctx.questionId() != null && errorMsg != null) {
            generatedQuestionRepository.findById(ctx.questionId()).ifPresent(gq -> {
                if (gq.getEditedFromQuestionId() == null) {
                    gq.setSolverError(TextClip.clip(errorMsg, MAX_SOLVER_ERROR));
                    generatedQuestionRepository.save(gq);
                }
            });
        }
        if (currentAttempt < maxAttempts) {
            job.setStatus(JobStatus.PENDING);
            // Backoff lũy tiến 15s, 30s, 45s... khi gặp lỗi mạng hoặc rate-limit 429/503
            job.setNextRunAt(LocalDateTime.now().plusSeconds(15L * currentAttempt));
        } else {
            job.setStatus(JobStatus.FAILED);
        }
        return generationJobRepository.save(job);
    }

    /**
     * W2-4b (C): job sinh câu chạm hạn mức ngày của Gemini ngay lúc gọi LLM: trả về {@code pending}, hẹn chạy lại vào mốc
     * đặt lại hạn mức ({@code resetAt}, 00:00 giờ Thái Bình Dương), xóa {@code started_at} và hoàn lại lượt
     * {@code attempt} đã tăng lúc nhận job (không tính vào số lần thử). Rào bằng {@link #holdsClaim}.
     */
    @Transactional
    public GenerationJob deferJobOnQuotaExhausted(ClaimedJobContext ctx, LocalDateTime resetAt, String reason) {
        boolean owned = holdsClaim(ctx);
        GenerationJob job = generationJobRepository.findById(ctx.jobId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy generation_job id=" + ctx.jobId()));
        if (!owned) {
            LOG.warn("stale finish: job {} (claim_seq {}) không còn running với claim_seq này, bỏ hoãn quota của worker cũ",
                    ctx.jobId(), ctx.claimSeq());
            return job;
        }
        int currentAttempt = job.getAttempt() != null ? job.getAttempt() : 1;
        job.setStatus(JobStatus.PENDING);
        job.setAttempt(Math.max(0, currentAttempt - 1));
        job.setNextRunAt(resetAt);
        job.setStartedAt(null);
        job.setLastError(TextClip.clip("quota: " + reason, MAX_SOLVER_ERROR));
        job.setUpdatedAt(LocalDateTime.now());
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
