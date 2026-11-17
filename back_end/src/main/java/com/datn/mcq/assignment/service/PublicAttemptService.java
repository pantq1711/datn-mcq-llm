package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.dto.PublicAttemptDtos.AnswerInput;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.AttemptViewDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.ClassOptionDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.InfoDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.PublicPartDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.ResultDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.ReviewPartDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.StartRequest;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.SubmitRequest;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.SubmitResultDto;
import com.datn.mcq.assignment.repository.AssignmentRepository;
import com.datn.mcq.assignment.repository.AssignmentRepository.AnswerRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AssignmentRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AttemptRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.ClassRow;
import com.datn.mcq.common.exception.ApiErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * API công khai cho học sinh (docs/design/lam-bai-truc-tuyen.md mục 3.2, 4; quyết định 16/11). Không đăng nhập; lượt làm
 * nhận diện bằng token ở header. Điểm, đáp án và lời giải chỉ trả khi bài đã đóng và hết ân hạn nộp
 * ({@link AttemptRules#resultsReleased}); trong lúc bài mở, nộp bài chỉ trả "đã nộp".
 */
@Service
public class PublicAttemptService {

    public static final String ASSIGNMENT_FULL = "ASSIGNMENT_FULL";
    public static final String ATTEMPT_TOKEN_INVALID = "ATTEMPT_TOKEN_INVALID";
    public static final String ATTEMPT_ALREADY_SUBMITTED = "ATTEMPT_ALREADY_SUBMITTED";
    public static final String ATTEMPT_EXPIRED = "ATTEMPT_EXPIRED";
    static final int SA_TEXT_MAX = 20;
    private static final Set<String> MCQ_CHOICES = Set.of("A", "B", "C", "D");
    private static final Set<String> TF_VALUES = Set.of("T", "F");

    private final AssignmentRepository repository;
    private final AssignmentLoader loader;
    private final PublicRateLimiter limiter;

    public PublicAttemptService(AssignmentRepository repository, AssignmentLoader loader, PublicRateLimiter limiter) {
        this.repository = repository;
        this.loader = loader;
        this.limiter = limiter;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static NoSuchElementException notFound() {
        return new NoSuchElementException("Không tìm thấy bài, kiểm lại đường link.");
    }

    private static ApiErrorException tokenInvalid() {
        return new ApiErrorException(HttpStatus.UNAUTHORIZED, ATTEMPT_TOKEN_INVALID,
                "Không tìm thấy lượt làm bài trên máy này. Mở lại đường link để bắt đầu.");
    }

    // ---------- P1: trang chào ----------

    /** Trang chào: không câu hỏi, không giới hạn tần suất (quyết định 16/11). Mã sai: 404. */
    @Transactional
    public InfoDto info(String code) {
        AssignmentRow a = loader.refresh(byCode(code), now());
        return new InfoDto(a.title(), repository.items(a.id()).size(), a.durationMinutes(), a.closesAt(), a.status(),
                a.showScore(), a.showSolutions(), repository.classes(a.id()).stream()
                .map(c -> new ClassOptionDto(c.classLabel(), c.rosterSize())).toList());
    }

    private AssignmentRow byCode(String code) {
        if (!AttemptTokens.looksLikeCode(code)) {
            throw notFound();
        }
        return repository.findByCode(code).orElseThrow(PublicAttemptService::notFound);
    }

    // ---------- P2: bắt đầu ----------

    /**
     * Bắt đầu một lượt. Thứ tự: hạn mức theo IP (trước mọi truy cập DB); dạng yêu cầu (400); mã (404); khóa hàng bài giao;
     * bài đã đóng (409); lớp phải thuộc bài giao và số thứ tự trong 1..sĩ số (400); trần 2 × tổng sĩ số (409
     * {@value #ASSIGNMENT_FULL}). Trùng lớp + số thứ tự: vẫn tạo lượt mới, cả hai lượt bị đánh dấu "trùng" lúc đọc.
     */
    @Transactional
    public AttemptViewDto start(String code, StartRequest request, String clientIp) {
        limiter.checkStart(clientIp);
        if (request == null || request.seatNo() == null) {
            throw AssignmentService.invalid("Thiếu lớp hoặc số thứ tự.");
        }
        String classLabel = AttemptTokens.normalizeClassLabel(request.classLabel());
        if (classLabel == null) {
            throw AssignmentService.invalid("Chọn lớp của em trong danh sách.");
        }
        AssignmentRow found = byCode(code);
        LocalDateTime now = now();
        AssignmentRow a = loader.refresh(repository.lockById(found.id()).orElseThrow(PublicAttemptService::notFound),
                now);
        if (!a.open()) {
            throw ApiErrorException.conflict(AssignmentService.ASSIGNMENT_CLOSED, "Bài đã đóng.");
        }
        List<ClassRow> classes = repository.classes(a.id());
        ClassRow cls = classes.stream().filter(c -> c.classLabel().equals(classLabel)).findFirst()
                .orElseThrow(() -> AssignmentService.invalid("Lớp " + classLabel + " không có trong bài này."));
        int seat = request.seatNo();
        if (seat < 1 || seat > cls.rosterSize()) {
            throw AssignmentService.invalid("Số thứ tự của lớp " + classLabel + " phải từ 1 đến " + cls.rosterSize()
                    + ".");
        }
        int roster = classes.stream().mapToInt(ClassRow::rosterSize).sum();
        if (repository.countAttempts(a.id()) >= AssignmentService.ATTEMPT_CAP_FACTOR * roster) {
            throw ApiErrorException.conflict(ASSIGNMENT_FULL, "Bài đã đủ số lượt làm. Báo thầy cô.");
        }
        String token = AttemptTokens.newToken();
        LocalDateTime deadline = now.plusMinutes(a.durationMinutes());
        if (deadline.isAfter(a.closesAt())) {
            deadline = a.closesAt();
        }
        int id = repository.insertAttempt(a.id(), classLabel, seat, AttemptTokens.sha256Hex(token), now, deadline);
        AttemptRow attempt = repository.attemptById(id).orElseThrow();
        return view(a, attempt, loader.paper(a), now, token);
    }

    // ---------- P3: mở lại ----------

    @Transactional
    public AttemptViewDto current(String token) {
        AttemptRow attempt = attemptOf(token);
        limiter.checkResume(attempt.id());
        LocalDateTime now = now();
        AssignmentRow a = loader.refresh(repository.findById(attempt.assignmentId()).orElseThrow(), now);
        return view(a, attempt, loader.paper(a), now, null);
    }

    private AttemptRow attemptOf(String token) {
        if (!AttemptTokens.looksLikeToken(token)) {
            throw tokenInvalid();
        }
        return repository.attemptByTokenHash(AttemptTokens.sha256Hex(token)).orElseThrow(PublicAttemptService::tokenInvalid);
    }

    private AttemptViewDto view(AssignmentRow a, AttemptRow attempt, AssignmentPaper paper, LocalDateTime now,
                                String token) {
        boolean submitted = attempt.submittedAt() != null;
        boolean canSubmit = AttemptRules.canSubmit(a, attempt, now);
        boolean released = AttemptRules.resultsReleased(a, now);
        boolean duplicate = repository.countSameSeat(a.id(), attempt.classLabel(), attempt.seatNo()) > 1;
        List<PublicPartDto> parts = canSubmit ? paper.publicParts() : null;
        ResultDto result = null;
        List<ReviewPartDto> review = null;
        if (released && (a.showScore() || a.showSolutions())) {
            Map<Integer, AnswerRow> answers = new HashMap<>();
            for (AnswerRow ans : repository.answersOfAttempt(attempt.id())) {
                answers.put(ans.slotId(), ans);
            }
            if (a.showScore() && submitted) {
                result = new ResultDto(attempt.score(), attempt.maxScore(),
                        AssignmentPaper.score10(attempt.score(), attempt.maxScore()), paper.partScores(answers));
            }
            if (a.showSolutions()) {
                review = paper.review(answers);
            }
        }
        return new AttemptViewDto(token, attempt.classLabel(), attempt.seatNo(), duplicate, attempt.startedAt(),
                attempt.deadlineAt(), now, AttemptRules.remainingSeconds(attempt, now), submitted,
                attempt.submittedAt(), released, AttemptRules.resultsAt(a), parts, result, review);
    }

    // ---------- P4: nộp ----------

    /**
     * Nộp một lần. Đã nộp: 409 {@value #ATTEMPT_ALREADY_SUBMITTED}; giáo viên đóng bài trước hạn của lượt: 409
     * {@code ASSIGNMENT_CLOSED}; quá hạn + ân hạn 2 phút: 409 {@value #ATTEMPT_EXPIRED}; câu trả lời sai dạng: 400, không
     * ghi gì. Chấm và ghi mọi câu trong cùng transaction. Không trả điểm (quyết định 16/11).
     */
    @Transactional
    public SubmitResultDto submit(String token, SubmitRequest request) {
        AttemptRow attempt = attemptOf(token);
        limiter.checkSubmit(attempt.id());
        LocalDateTime now = now();
        AssignmentRow a = loader.refresh(repository.findById(attempt.assignmentId()).orElseThrow(), now);
        if (attempt.submittedAt() != null) {
            throw alreadySubmitted();
        }
        if (AttemptRules.closedBeforeDeadline(a, attempt)) {
            throw ApiErrorException.conflict(AssignmentService.ASSIGNMENT_CLOSED,
                    "Thầy cô đã đóng bài trước khi em nộp.");
        }
        if (AttemptRules.pastGrace(attempt, now)) {
            throw ApiErrorException.conflict(ATTEMPT_EXPIRED, "Đã hết giờ làm bài.");
        }
        AssignmentPaper paper = loader.paper(a);
        Map<Integer, AnswerInput> answers = validate(request, paper);
        List<AnswerRow> graded = paper.grade(attempt.id(), answers);
        BigDecimal score = AssignmentPaper.total(graded);
        if (repository.markSubmitted(attempt.id(), now, score, paper.maxScore()) == 0) {
            throw alreadySubmitted();
        }
        repository.insertAnswers(graded);
        return new SubmitResultDto(now, AttemptRules.resultsAt(a));
    }

    private static ApiErrorException alreadySubmitted() {
        return ApiErrorException.conflict(ATTEMPT_ALREADY_SUBMITTED, "Em đã nộp bài này rồi.");
    }

    /**
     * Kiểm dạng câu trả lời trước khi ghi: không quá số câu của đề (AN-07), {@code slotId} thuộc đề và không lặp, trường
     * đúng dạng câu, lựa chọn A-D, khóa ý a-d với giá trị T/F, đáp số tối đa {@value #SA_TEXT_MAX} ký tự. Câu thiếu là bỏ
     * trống.
     */
    static Map<Integer, AnswerInput> validate(SubmitRequest request, AssignmentPaper paper) {
        if (request == null || request.answers() == null) {
            throw AssignmentService.invalid("Thiếu danh sách câu trả lời.");
        }
        List<AnswerInput> list = request.answers();
        if (list.size() > paper.questionCount()) {
            throw AssignmentService.invalid("Số câu trả lời nhiều hơn số câu của đề.");
        }
        Map<Integer, AnswerInput> out = new LinkedHashMap<>();
        for (AnswerInput in : list) {
            if (in == null || in.slotId() == null) {
                throw AssignmentService.invalid("Câu trả lời thiếu mã câu.");
            }
            AssignmentPaper.Item item = paper.item(in.slotId());
            if (item == null) {
                throw AssignmentService.invalid("Câu trả lời không thuộc đề này.");
            }
            if (out.containsKey(in.slotId())) {
                throw AssignmentService.invalid("Một câu được trả lời hai lần.");
            }
            out.put(in.slotId(), switch (item.type()) {
                case MCQ -> {
                    if (in.tf() != null || in.text() != null
                            || (in.choice() != null && !MCQ_CHOICES.contains(in.choice()))) {
                        throw AssignmentService.invalid("Câu trắc nghiệm chỉ nhận lựa chọn A, B, C hoặc D.");
                    }
                    yield in;
                }
                case TF -> {
                    if (in.choice() != null || in.text() != null) {
                        throw AssignmentService.invalid("Câu Đúng/Sai chỉ nhận lựa chọn theo từng ý.");
                    }
                    Map<String, String> tf = new HashMap<>();
                    if (in.tf() != null) {
                        for (Map.Entry<String, String> e : in.tf().entrySet()) {
                            if (!AttemptGrader.STATEMENT_KEYS.contains(e.getKey())
                                    || (e.getValue() != null && !TF_VALUES.contains(e.getValue()))) {
                                throw AssignmentService.invalid("Ý chỉ gồm a, b, c, d và giá trị Đ (T) hoặc S (F).");
                            }
                            if (e.getValue() != null) {
                                tf.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                    yield new AnswerInput(in.slotId(), null, tf, null);
                }
                case SA -> {
                    if (in.choice() != null || in.tf() != null) {
                        throw AssignmentService.invalid("Câu Trả lời ngắn chỉ nhận đáp số.");
                    }
                    if (in.text() != null && in.text().strip().length() > SA_TEXT_MAX) {
                        throw AssignmentService.invalid("Đáp số dài tối đa " + SA_TEXT_MAX + " ký tự.");
                    }
                    yield in;
                }
            });
        }
        return out;
    }
}
