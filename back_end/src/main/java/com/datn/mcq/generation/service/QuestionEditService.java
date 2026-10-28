package com.datn.mcq.generation.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.common.util.TextDistanceUtils;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.generation.dto.QuestionEditInput;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.parser.LlmOutputRepair;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.generation.repository.GenerationJobRepository;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.qc.checker.FormatChecker;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * W1-2: tạo bản giáo viên sửa của một câu và chạy QC không cần LLM ngay trong transaction của nơi gọi.
 *
 * <ol>
 *   <li>Kiểm hình dạng theo dạng câu (400 INVALID_REQUEST), trim, tự đóng "$" quên ở cuối trường (LlmOutputRepair,
 *       ghi cảnh báo {@code <trường>: đã tự đóng $}). Không có thay đổi thì không ghi gì.</li>
 *   <li>Kiểm định dạng bằng FormatChecker trước khi ghi: lỗi chặn trả 400 FORMAT_INVALID, không ghi gì.</li>
 *   <li>Tạo một dòng generated_question MỚI (edited_from_question_id = bản trước), không bao giờ sửa dòng cũ, nên kết
 *       quả QC gắn theo id của từng bản và kết quả về muộn của bản cũ không đụng được bản mới.</li>
 *   <li>Pipeline QC (format, dedup) với Solver tắt; dedup loại trừ cả chuỗi bản trước (gồm bản gốc LLM).</li>
 *   <li>Đề, phương án/ý hoặc đáp án đổi: xếp job answer_check (Solver chạy ở worker, ngoài transaction). Chỉ đổi lời
 *       giải: chép kết quả Solver của bản trước (Solver không thấy lời giải, quyết định D5), không gọi LLM.</li>
 *   <li>Hủy job answer_check còn chờ của bản trước.</li>
 * </ol>
 * Trạng thái câu luôn do QcStatusDecider quyết (M-10, Q-2); Solver không bao giờ đổi đáp án giáo viên.
 */
@Service
public class QuestionEditService {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String FORMAT_INVALID = "FORMAT_INVALID";

    /** Trường thay đổi không cần chạy lại Solver (Solver không thấy lời giải). */
    static final String EXPLANATION = "explanation";

    /**
     * @param changed       false khi nội dung gửi lên trùng câu hiện tại (không ghi gì)
     * @param questionId    bản mới (hoặc câu hiện tại khi không đổi)
     * @param changedFields tên các trường đã đổi, theo thứ tự cố định
     * @param editDistance  tổng Levenshtein trên văn bản học sinh thấy (không tính lời giải, đáp án, nhãn)
     */
    public record EditOutcome(boolean changed, Integer questionId, List<String> changedFields, int editDistance) {}

    private final GeneratedQuestionRepository questionRepository;
    private final GeneratedQuestionStatementRepository statementRepository;
    private final GenerationJobRepository jobRepository;
    private final CatalogService catalogService;
    private final FormatChecker formatChecker;
    private final QcOrchestrator qcOrchestrator;
    private final DedupScopeResolver dedupScopeResolver;

    public QuestionEditService(GeneratedQuestionRepository questionRepository,
                               GeneratedQuestionStatementRepository statementRepository,
                               GenerationJobRepository jobRepository, CatalogService catalogService,
                               FormatChecker formatChecker, QcOrchestrator qcOrchestrator,
                               DedupScopeResolver dedupScopeResolver) {
        this.questionRepository = questionRepository;
        this.statementRepository = statementRepository;
        this.jobRepository = jobRepository;
        this.catalogService = catalogService;
        this.formatChecker = formatChecker;
        this.qcOrchestrator = qcOrchestrator;
        this.dedupScopeResolver = dedupScopeResolver;
    }

    @Transactional
    public EditOutcome createVersion(Integer baseQuestionId, QuestionEditInput input) {
        return createVersion(baseQuestionId, input, List.of());
    }

    /**
     * @param sameExamCandidates (W1-3) các câu đang nằm trong cùng đề (chỗ khác), để bản sửa không trùng chúng; rỗng
     *                           khi không có đề
     */
    @Transactional
    public EditOutcome createVersion(Integer baseQuestionId, QuestionEditInput input,
                                     List<DedupCandidate> sameExamCandidates) {
        return createVersion(baseQuestionId, input, sameExamCandidates, null);
    }

    @Transactional
    public EditOutcome createVersion(Integer baseQuestionId, QuestionEditInput input,
                                     List<DedupCandidate> sameExamCandidates, Integer creatorUserId) {
        GeneratedQuestion base = questionRepository.findById(baseQuestionId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi id=" + baseQuestionId));
        QuestionType type = base.getQuestionType() != null ? base.getQuestionType() : QuestionType.MCQ;
        List<GeneratedQuestionStatement> baseStatements = type == QuestionType.TF
                ? statementRepository.findByQuestionIdOrderByPositionAsc(base.getId()) : List.of();

        List<String> repairs = new ArrayList<>();
        Content edited = Content.fromInput(type, input, repairs);
        Content current = Content.fromQuestion(base, baseStatements);
        List<String> changed = edited.changedFrom(current);
        if (changed.isEmpty()) {
            return new EditOutcome(false, base.getId(), List.of(), 0);
        }

        QuestionTemplate tpl = catalogService.getTemplateById(base.getTemplateId());
        boolean validateLatex = GenerationJobProcessor.isMathSubject(
                catalogService.getSubjectById(tpl.getSubjectId()).getName());
        FormatChecker.FormatCheckOutcome format = formatChecker.check(edited.qcInput(null, validateLatex, repairs));
        if (!format.passed()) {
            throw new ApiErrorException(HttpStatus.BAD_REQUEST, FORMAT_INVALID,
                    "Bản sửa không đạt kiểm tra định dạng: " + String.join("; ", format.errors()), format.errors());
        }

        GeneratedQuestion version = saveVersion(base, type, edited, baseStatements);

        DedupScope scope = dedupScopeResolver.resolve(version.getRunId());
        if (scope != null) {
            scope = scope.excluding(chainIds(base)).withExtraCandidates(sameExamCandidates).withCreatorUserId(creatorUserId);
        }
        QcPipelineReport report = qcOrchestrator.evaluateAndSave(
                edited.qcInput(version.getId(), validateLatex, repairs), scope);
        QuestionStatus status = report.resultingStatus();
        if (report.dedupStatus() == DedupStatus.NO_DUPLICATE) {
            boolean solverContentChanged = changed.stream().anyMatch(f -> !EXPLANATION.equals(f));
            if (solverContentChanged) {
                enqueueAnswerCheck(version, type);
            } else {
                Optional<AnswerCheckStatus> copied = qcOrchestrator.copyAnswerCheck(base.getId(), version.getId());
                if (copied.isPresent()) {
                    status = qcOrchestrator.decideWithStoredChecks(version.getId(), copied.get());
                } else if (hasActiveAnswerCheck(base.getId())) {
                    // Bản trước còn đang chờ Solver: chuyển việc kiểm sang bản mới (cùng nội dung Solver xét).
                    enqueueAnswerCheck(version, type);
                }
            }
        }
        jobRepository.cancelPendingAnswerChecks(base.getId(), LocalDateTime.now());
        version.setStatus(status);
        questionRepository.save(version);
        enqueueGrounding(base, version, type, status);
        return new EditOutcome(true, version.getId(), changed, edited.editDistance(current));
    }

    /**
     * W2-3 bước 8: câu của chuỗi sinh từ đề có tài liệu thì bản sửa cũng được chấm "bám tài liệu" lại, vì judge thấy cả đề,
     * phương án, đáp án và lời giải nên bất kỳ thay đổi nào cũng có thể đổi kết luận. Job dùng lại NGUYÊN ảnh chụp đoạn tài
     * liệu của chuỗi (job grounding của bản gần nhất có, nếu không thì job sinh câu), không chọn lại đoạn. Chỉ xếp job, không
     * gọi LLM trong request. Bản trước (nếu job còn chờ) bị bỏ qua ở worker vì đã có bản sửa thay thế. Cùng điều kiện với câu
     * sinh ra: chỉ câu còn dùng được (qua QC hoặc đang chờ Solver).
     */
    private void enqueueGrounding(GeneratedQuestion base, GeneratedQuestion version, QuestionType type,
                                  QuestionStatus status) {
        if (status != QuestionStatus.QC_PASSED && status != QuestionStatus.GENERATED) {
            return;
        }
        groundingSource(base).ifPresent(source -> {
            LocalDateTime now = LocalDateTime.now();
            GenerationJob job = new GenerationJob();
            job.setJobType(GenerationJob.TYPE_GROUNDING_CHECK);
            job.setQuestionType(type);
            job.setRunId(version.getRunId());
            job.setTemplateId(version.getTemplateId());
            job.setQuestionId(version.getId());
            job.setMaterialContext(source.getMaterialContext());
            job.setMaterialRefs(source.getMaterialRefs());
            job.setStatus(JobStatus.PENDING);
            job.setAttempt(0);
            job.setNextRunAt(now);
            job.setCreatedAt(now);
            job.setUpdatedAt(now);
            jobRepository.save(job);
        });
    }

    /** Job (grounding hoặc sinh câu) gần nhất của chuỗi bản sửa mang ảnh chụp tài liệu; rỗng khi câu không dùng tài liệu. */
    private Optional<GenerationJob> groundingSource(GeneratedQuestion base) {
        for (Integer id : chainIds(base)) {
            for (String jobType : List.of(GenerationJob.TYPE_GROUNDING_CHECK, GenerationJob.TYPE_GENERATE)) {
                Optional<GenerationJob> job = jobRepository.findFirstByQuestionIdAndJobTypeOrderByIdDesc(id, jobType);
                if (job.isPresent() && job.get().getMaterialContext() != null) {
                    return job;
                }
            }
        }
        return Optional.empty();
    }

    /** Job answer_check đang chờ hoặc đang chạy của câu. */
    @Transactional(readOnly = true)
    public boolean hasActiveAnswerCheck(Integer questionId) {
        return jobRepository.findFirstByQuestionIdAndJobTypeOrderByIdDesc(questionId, GenerationJob.TYPE_ANSWER_CHECK)
                .map(j -> j.getStatus() == JobStatus.PENDING || j.getStatus() == JobStatus.RUNNING)
                .orElse(false);
    }

    /** Câu và mọi bản trước nó (theo edited_from_question_id), tới bản gốc LLM. */
    private Set<Integer> chainIds(GeneratedQuestion question) {
        Set<Integer> ids = new LinkedHashSet<>();
        GeneratedQuestion cur = question;
        while (cur != null && ids.add(cur.getId()) && cur.getEditedFromQuestionId() != null) {
            cur = questionRepository.findById(cur.getEditedFromQuestionId()).orElse(null);
        }
        return ids;
    }

    private GeneratedQuestion saveVersion(GeneratedQuestion base, QuestionType type, Content c,
                                          List<GeneratedQuestionStatement> baseStatements) {
        GeneratedQuestion v = new GeneratedQuestion();
        v.setQuestionType(type);
        v.setTemplateId(base.getTemplateId());
        v.setRunId(base.getRunId());
        v.setEditedFromQuestionId(base.getId());
        v.setQuestionText(c.questionText);
        v.setExplanation(c.explanation);
        if (type == QuestionType.MCQ) {
            v.setOptionA(c.optionA);
            v.setOptionB(c.optionB);
            v.setOptionC(c.optionC);
            v.setOptionD(c.optionD);
            v.setCorrectOption(c.correctOption);
        } else if (type == QuestionType.SA) {
            v.setSaAnswer(c.saAnswer);
        }
        // Mức mục tiêu của câu gốc (không sửa mức độ; không định nghĩa lại llm_difficulty, Q-5).
        v.setLlmDifficulty(type == QuestionType.TF ? null : base.getLlmDifficulty());
        // Bản giáo viên sửa không có đầu ra LLM.
        v.setRawLlmOutput("");
        v.setParseSuccess(true);
        v.setAttemptCount(1);
        v.setStatus(QuestionStatus.GENERATED);
        v.setCreatedAt(LocalDateTime.now());
        GeneratedQuestion saved = questionRepository.saveAndFlush(v);
        if (type == QuestionType.TF) {
            StatementKey[] keys = StatementKey.values();
            for (int i = 0; i < keys.length; i++) {
                GeneratedQuestionStatement s = new GeneratedQuestionStatement(saved.getId(), keys[i]);
                s.setStatementText(c.statements.get(i));
                s.setLlmLabel(c.labels.get(i));
                s.setLlmDifficulty(i < baseStatements.size() ? baseStatements.get(i).getLlmDifficulty() : null);
                statementRepository.save(s);
            }
            statementRepository.flush();
        }
        return saved;
    }

    private void enqueueAnswerCheck(GeneratedQuestion version, QuestionType type) {
        LocalDateTime now = LocalDateTime.now();
        GenerationJob job = new GenerationJob();
        job.setJobType(GenerationJob.TYPE_ANSWER_CHECK);
        job.setQuestionType(type);
        job.setRunId(version.getRunId());
        job.setTemplateId(version.getTemplateId());
        job.setQuestionId(version.getId());
        if (type == QuestionType.TF) {
            // CHECK chk_gj_target_statements: job tf ghi mức 4 ý (của câu, không đổi khi sửa).
            List<Difficulty> levels = statementRepository.findByQuestionIdOrderByPositionAsc(version.getId()).stream()
                    .map(GeneratedQuestionStatement::getLlmDifficulty).toList();
            if (levels.size() != 4 || levels.contains(null)) {
                throw new IllegalStateException("Câu đúng/sai id=" + version.getId() + " thiếu mức độ của ý");
            }
            job.setTargetStatementDifficulties(GenerationService.joinLevels(levels));
        }
        job.setStatus(JobStatus.PENDING);
        job.setAttempt(0);
        job.setNextRunAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobRepository.save(job);
    }

    /** Nội dung một câu theo dạng, đã trim và (với đầu vào giáo viên) đã tự đóng "$". */
    static final class Content {
        final QuestionType type;
        String questionText;
        String optionA;
        String optionB;
        String optionC;
        String optionD;
        Option correctOption;
        String saAnswer;
        List<String> statements = List.of();
        List<TruthValue> labels = List.of();
        String explanation;

        private Content(QuestionType type) {
            this.type = type;
        }

        static Content fromQuestion(GeneratedQuestion q, List<GeneratedQuestionStatement> statements) {
            Content c = new Content(q.getQuestionType());
            c.questionText = q.getQuestionText();
            c.optionA = q.getOptionA();
            c.optionB = q.getOptionB();
            c.optionC = q.getOptionC();
            c.optionD = q.getOptionD();
            c.correctOption = q.getCorrectOption();
            c.saAnswer = q.getSaAnswer();
            c.statements = statements.stream().map(GeneratedQuestionStatement::getStatementText).toList();
            c.labels = statements.stream().map(GeneratedQuestionStatement::getLlmLabel).toList();
            c.explanation = q.getExplanation();
            return c;
        }

        static Content fromInput(QuestionType type, QuestionEditInput in, List<String> repairs) {
            if (in == null) {
                throw invalid("Thiếu nội dung câu sửa");
            }
            Content c = new Content(type);
            c.questionText = text("question_text", required(in.questionText(), "questionText"), repairs);
            c.explanation = text("explanation", required(in.explanation(), "explanation"), repairs);
            switch (type) {
                case MCQ -> {
                    forbid(in.saAnswer(), "saAnswer", type);
                    forbid(in.statements(), "statements", type);
                    c.optionA = text("option_a", required(in.optionA(), "optionA"), repairs);
                    c.optionB = text("option_b", required(in.optionB(), "optionB"), repairs);
                    c.optionC = text("option_c", required(in.optionC(), "optionC"), repairs);
                    c.optionD = text("option_d", required(in.optionD(), "optionD"), repairs);
                    String raw = required(in.correctOption(), "correctOption").strip().toUpperCase();
                    if (!List.of("A", "B", "C", "D").contains(raw)) {
                        throw invalid("correctOption phải là A, B, C hoặc D");
                    }
                    c.correctOption = DbEnum.fromDbValue(Option.class, raw);
                }
                case SA -> {
                    forbidOptions(in, type);
                    forbid(in.statements(), "statements", type);
                    // Như parser sa: đáp số chỉ trim, vẫn tự đóng "$" (ví dụ "$3\sqrt{2}").
                    c.saAnswer = text("answer", required(in.saAnswer(), "saAnswer"), repairs);
                    // 27/10: đáp án đạt STRICT được lưu ở dạng chuẩn hóa; không đạt thì giữ nguyên để FormatChecker báo 400.
                    String normalized = ShortAnswerValue.normalizedStrictAnswer(c.saAnswer);
                    c.saAnswer = normalized != null ? normalized : c.saAnswer;
                }
                case TF -> {
                    forbidOptions(in, type);
                    forbid(in.saAnswer(), "saAnswer", type);
                    List<QuestionEditInput.StatementEdit> items = in.statements();
                    if (items == null || items.size() != 4) {
                        throw invalid("Câu đúng/sai phải gửi đủ 4 ý a, b, c, d");
                    }
                    String[] texts = new String[4];
                    TruthValue[] labels = new TruthValue[4];
                    Set<String> seen = new HashSet<>();
                    for (QuestionEditInput.StatementEdit s : items) {
                        String key = s == null || s.key() == null ? null : s.key().strip().toLowerCase();
                        int i = key == null ? -1 : List.of("a", "b", "c", "d").indexOf(key);
                        if (i < 0 || !seen.add(key)) {
                            throw invalid("Câu đúng/sai phải gửi đủ 4 ý a, b, c, d (mỗi ý một lần)");
                        }
                        texts[i] = text("statement_" + key, required(s.text(), "statements[" + key + "].text"),
                                repairs);
                        labels[i] = TruthValue.fromLabel(required(s.label(), "statements[" + key + "].label"));
                        if (labels[i] == null) {
                            throw invalid("Nhãn ý " + key + " phải là T hoặc F");
                        }
                    }
                    c.statements = List.of(texts);
                    c.labels = List.of(labels);
                }
            }
            return c;
        }

        /** Tên các trường khác {@code other}, theo thứ tự cố định (so sau trim và chuẩn hóa xuống dòng). */
        List<String> changedFrom(Content other) {
            List<String> out = new ArrayList<>();
            diff(out, "question_text", questionText, other.questionText);
            if (type == QuestionType.MCQ) {
                diff(out, "option_a", optionA, other.optionA);
                diff(out, "option_b", optionB, other.optionB);
                diff(out, "option_c", optionC, other.optionC);
                diff(out, "option_d", optionD, other.optionD);
                if (correctOption != other.correctOption) {
                    out.add("correct_option");
                }
            } else if (type == QuestionType.SA) {
                diff(out, "sa_answer", saAnswer, other.saAnswer);
            } else {
                String[] keys = {"a", "b", "c", "d"};
                for (int i = 0; i < 4; i++) {
                    diff(out, "statement_" + keys[i], statements.get(i), at(other.statements, i));
                }
                for (int i = 0; i < 4; i++) {
                    if (!Objects.equals(labels.get(i), at(other.labels, i))) {
                        out.add("label_" + keys[i]);
                    }
                }
            }
            diff(out, EXPLANATION, explanation, other.explanation);
            return out;
        }

        /** Như edit_distance của chấm mù: mcq đề và 4 phương án; sa đề; tf đề dẫn và 4 ý. Không tính lời giải. */
        int editDistance(Content original) {
            if (type == QuestionType.MCQ) {
                return TextDistanceUtils.totalQuestionEditDistance(original.questionText, original.optionA,
                        original.optionB, original.optionC, original.optionD, questionText, optionA, optionB, optionC,
                        optionD);
            }
            int d = TextDistanceUtils.levenshteinDistance(original.questionText, questionText);
            if (type == QuestionType.TF) {
                for (int i = 0; i < 4; i++) {
                    d += TextDistanceUtils.levenshteinDistance(at(original.statements, i), statements.get(i));
                }
            }
            return d;
        }

        QuestionQcInput qcInput(Integer questionId, boolean validateLatex, List<String> repairs) {
            // Solver tắt: answer_check của bản sửa chạy ở job riêng (hoặc được chép khi chỉ đổi lời giải).
            return switch (type) {
                case TF -> QuestionQcInput.trueFalse(questionId, true, questionText, statements,
                        labels.stream().map(TruthValue::getDbValue).toList(), explanation, validateLatex, false, null,
                        repairs);
                case SA -> QuestionQcInput.shortAnswer(questionId, true, questionText, explanation, saAnswer, true,
                        validateLatex, false, null).withParseWarnings(repairs);
                case MCQ -> new QuestionQcInput(questionId, true, questionText, optionA, optionB, optionC, optionD,
                        correctOption, explanation, validateLatex, false).withParseWarnings(repairs);
            };
        }

        private static <T> T at(List<T> list, int i) {
            return i < list.size() ? list.get(i) : null;
        }

        private static void diff(List<String> out, String field, String a, String b) {
            if (!TextDistanceUtils.normalize(a).equals(TextDistanceUtils.normalize(b))) {
                out.add(field);
            }
        }

        private static String text(String field, String value, List<String> repairs) {
            String s = value.strip();
            // Như parser sinh câu: gỡ vỏ \text{...} ngoài $ rồi mới tự đóng $ (27/10, đơn vị đo viết ngoài công thức).
            LlmOutputRepair.TextRepair t = LlmOutputRepair.stripTextOutsideMath(s);
            if (t.removed() > 0) {
                repairs.add(LlmOutputRepair.textWarning(field, t.removed()));
                s = t.text();
            }
            LlmOutputRepair.DollarRepair d = LlmOutputRepair.closeTrailingDollar(s);
            if (d.closed()) {
                repairs.add(LlmOutputRepair.dollarWarning(field));
                s = d.text();
            }
            return s;
        }

        private static String required(String value, String name) {
            if (value == null) {
                throw invalid("Thiếu " + name);
            }
            return value;
        }

        private static void forbid(Object value, String name, QuestionType type) {
            if (value != null) {
                throw invalid(name + " không áp dụng cho câu " + type.getDbValue());
            }
        }

        private static void forbidOptions(QuestionEditInput in, QuestionType type) {
            forbid(in.optionA(), "optionA", type);
            forbid(in.optionB(), "optionB", type);
            forbid(in.optionC(), "optionC", type);
            forbid(in.optionD(), "optionD", type);
            forbid(in.correctOption(), "correctOption", type);
        }

        private static ApiErrorException invalid(String message) {
            return ApiErrorException.badRequest(INVALID_REQUEST, message);
        }
    }
}
