package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.dto.AssignmentDtos.AssignmentDto;
import com.datn.mcq.assignment.dto.AssignmentDtos.AttemptDto;
import com.datn.mcq.assignment.dto.AssignmentDtos.ClassDto;
import com.datn.mcq.assignment.dto.AssignmentDtos.ClassInput;
import com.datn.mcq.assignment.dto.AssignmentDtos.CreateAssignmentRequest;
import com.datn.mcq.assignment.dto.AssignmentDtos.DeleteAttemptsResult;
import com.datn.mcq.assignment.repository.AssignmentRepository;
import com.datn.mcq.assignment.repository.AssignmentRepository.AnswerRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AssignmentRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AttemptRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.ClassRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.ItemRow;
import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.service.ExamAssignmentSource;
import com.datn.mcq.exam.service.ExamAssignmentSource.AssignableSlot;
import com.datn.mcq.exam.service.ExamAssignmentSource.ExamForAssignment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API giáo viên của bài giao (docs/design/lam-bai-truc-tuyen.md mục 2, 3.1; quyết định 16/11): giao bài cho các lớp kèm sĩ
 * số, xem danh sách và bảng điểm, đóng, xóa một hoặc nhiều lượt, xóa bài giao. Chỉ chủ đề; đề của người khác hoặc đã xóa
 * trả 404 như không tồn tại.
 */
@Service
@EnableConfigurationProperties(ExamScoringProperties.class)
public class AssignmentService {

    public static final String ASSIGN_NOT_ALL_KEPT = "ASSIGN_NOT_ALL_KEPT";
    public static final String ASSIGN_SA_KEY_UNSUPPORTED = "ASSIGN_SA_KEY_UNSUPPORTED";
    public static final String ASSIGNMENT_CLOSED = "ASSIGNMENT_CLOSED";
    static final String INVALID_REQUEST = "INVALID_REQUEST";

    static final int MIN_DURATION = 5;
    static final int MAX_DURATION = 180;
    static final Duration MAX_OPEN = Duration.ofDays(30);
    static final int MAX_CLASSES = 10;
    static final int MAX_ROSTER = 99;
    /** Trần lượt làm = hệ số × tổng sĩ số (quyết định 16/11: lượt trùng được giữ nên cần dư). */
    static final int ATTEMPT_CAP_FACTOR = 2;
    static final int MAX_BULK_DELETE = 500;

    private final AssignmentRepository repository;
    private final AssignmentLoader loader;
    private final ExamAssignmentSource examSource;
    private final BankService bankService;
    private final ExamScoringProperties scoringProperties;

    public AssignmentService(AssignmentRepository repository, AssignmentLoader loader, ExamAssignmentSource examSource,
                             BankService bankService, ExamScoringProperties scoringProperties) {
        this.repository = repository;
        this.loader = loader;
        this.examSource = examSource;
        this.bankService = bankService;
        this.scoringProperties = scoringProperties;
    }

    static ApiErrorException invalid(String message) {
        return ApiErrorException.badRequest(INVALID_REQUEST, message);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    // ---------- T1: giao bài ----------

    /**
     * Giao đề {@code examId}. Kiểm yêu cầu (400) trước mọi truy cập DB; rồi khóa chia sẻ hàng đề (404 khi không phải đề của
     * người gọi), kiểm mọi chỗ đã chốt (409 {@value #ASSIGN_NOT_ALL_KEPT}) và khóa Trả lời ngắn gõ được trên điện thoại
     * (409 {@value #ASSIGN_SA_KEY_UNSUPPORTED}); không ghi gì khi có lỗi.
     */
    @Transactional
    public AssignmentDto create(Integer examId, Integer actorId, CreateAssignmentRequest request) {
        LocalDateTime now = now();
        if (request == null) {
            throw invalid("Thiếu nội dung yêu cầu.");
        }
        if (!Boolean.TRUE.equals(request.consentConfirmed())) {
            throw invalid("Cần xác nhận việc cho học sinh làm bài đã được nhà trường cho phép.");
        }
        Integer duration = request.durationMinutes();
        if (duration == null || duration < MIN_DURATION || duration > MAX_DURATION) {
            throw invalid("Thời gian làm bài phải từ " + MIN_DURATION + " đến " + MAX_DURATION + " phút.");
        }
        Instant closesAtInstant = request.closesAt();
        if (closesAtInstant == null) {
            throw invalid("Thiếu hạn đóng bài.");
        }
        LocalDateTime closesAt = LocalDateTime.ofInstant(closesAtInstant, ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.MICROS);
        if (closesAt.isBefore(now.plusMinutes(duration))) {
            throw invalid("Hạn đóng phải sau lúc giao ít nhất bằng thời gian làm bài.");
        }
        if (closesAt.isAfter(now.plus(MAX_OPEN))) {
            throw invalid("Hạn đóng không được quá 30 ngày kể từ lúc giao.");
        }
        List<ClassRow> classes = validateClasses(request.classes());

        ExamForAssignment exam = examSource.lockForAssignment(examId, actorId);
        List<AssignableSlot> slots = exam.slots();
        if (slots.isEmpty()) {
            throw ApiErrorException.conflict(ASSIGN_NOT_ALL_KEPT, "Đề chưa có câu nào.");
        }
        List<String> notKept = slots.stream().filter(s -> !s.settled() || s.bankItemId() == null)
                .map(AssignmentService::label).toList();
        if (!notKept.isEmpty()) {
            throw new ApiErrorException(HttpStatus.CONFLICT, ASSIGN_NOT_ALL_KEPT,
                    "Còn " + notKept.size() + " câu chưa Giữ. Giữ hoặc Sửa mọi câu rồi mới giao bài.", notKept);
        }
        Map<Integer, BankItemDto> bank = bankService.getItems(slots.stream().map(AssignableSlot::bankItemId).toList());
        List<String> badSa = slots.stream().filter(s -> s.questionType() == QuestionType.SA)
                .filter(s -> ShortAnswerValue.normalizedStrictAnswer(bank.get(s.bankItemId()).saAnswer()) == null)
                .map(AssignmentService::label).toList();
        if (!badSa.isEmpty()) {
            throw new ApiErrorException(HttpStatus.CONFLICT, ASSIGN_SA_KEY_UNSUPPORTED,
                    "Có câu Trả lời ngắn mà đáp số không phải số nguyên hoặc số thập phân; học sinh không gõ được"
                            + " trên điện thoại. Sửa đáp số rồi mới giao bài.", badSa);
        }

        String code = newUniqueCode();
        Scoring scoring = Scoring.of(scoringProperties);
        int id = repository.insertAssignment(examId, actorId, code, exam.title(), duration, closesAt,
                Boolean.TRUE.equals(request.showScore()), Boolean.TRUE.equals(request.showSolutions()),
                scoring.toJson(), now);
        repository.insertClasses(id, classes);
        List<ItemRow> items = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            AssignableSlot s = slots.get(i);
            items.add(new ItemRow(s.slotId(), s.bankItemId(), s.questionType().getDbValue(), s.part(), s.numberInPart(),
                    i + 1));
        }
        repository.insertItems(id, items);
        AssignmentRow row = repository.findById(id).orElseThrow();
        return toDto(row, loader.paper(row), false, now);
    }

    private static String label(AssignableSlot s) {
        return "Phần " + s.part() + ", Câu " + s.numberInPart();
    }

    private static List<ClassRow> validateClasses(List<ClassInput> input) {
        if (input == null || input.isEmpty()) {
            throw invalid("Cần khai ít nhất một lớp kèm sĩ số.");
        }
        if (input.size() > MAX_CLASSES) {
            throw invalid("Một bài giao tối đa " + MAX_CLASSES + " lớp.");
        }
        List<ClassRow> out = new ArrayList<>(input.size());
        Set<String> seen = new HashSet<>();
        for (ClassInput c : input) {
            String normalized = c == null ? null : AttemptTokens.normalizeClassLabel(c.classLabel());
            if (normalized == null) {
                throw invalid("Tên lớp chỉ gồm chữ không dấu, số, / . - (tối đa 10 ký tự), ví dụ 8A1.");
            }
            if (c.size() == null || c.size() < 1 || c.size() > MAX_ROSTER) {
                throw invalid("Sĩ số lớp " + normalized + " phải từ 1 đến " + MAX_ROSTER + ".");
            }
            if (!seen.add(normalized)) {
                throw invalid("Lớp " + normalized + " bị khai hai lần.");
            }
            out.add(new ClassRow(normalized, c.size()));
        }
        return out;
    }

    private String newUniqueCode() {
        for (int i = 0; i < 8; i++) {
            String code = AttemptTokens.newPublicCode();
            if (!repository.codeExists(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không tạo được mã bài giao, thử lại.");
    }

    // ---------- T2, T3: xem ----------

    @Transactional
    public List<AssignmentDto> list(Integer examId, Integer actorId) {
        examSource.requireOwned(examId, actorId);
        LocalDateTime now = now();
        List<AssignmentDto> out = new ArrayList<>();
        for (AssignmentRow row : repository.listByExam(examId)) {
            AssignmentRow fresh = loader.refresh(row, now);
            out.add(toDto(fresh, loader.paper(fresh), false, now));
        }
        return out;
    }

    @Transactional
    public AssignmentDto get(Integer examId, Integer assignmentId, Integer actorId) {
        AssignmentRow row = loader.refresh(owned(examId, assignmentId, actorId), now());
        return toDto(row, loader.paper(row), true, now());
    }

    private AssignmentRow owned(Integer examId, Integer assignmentId, Integer actorId) {
        examSource.requireOwned(examId, actorId);
        return repository.findInExam(assignmentId, examId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài giao id=" + assignmentId));
    }

    private AssignmentRow ownedLocked(Integer examId, Integer assignmentId, Integer actorId) {
        AssignmentRow row = owned(examId, assignmentId, actorId);
        return repository.lockById(row.id())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài giao id=" + assignmentId));
    }

    private AssignmentDto toDto(AssignmentRow a, AssignmentPaper paper, boolean withAttempts, LocalDateTime now) {
        List<ClassRow> classes = repository.classes(a.id());
        int roster = classes.stream().mapToInt(ClassRow::rosterSize).sum();
        List<AttemptRow> attempts = repository.attempts(a.id());
        Map<String, Integer> seatCounts = new HashMap<>();
        for (AttemptRow t : attempts) {
            seatCounts.merge(t.classLabel() + "#" + t.seatNo(), 1, Integer::sum);
        }
        int submitted = 0;
        int duplicates = 0;
        for (AttemptRow t : attempts) {
            if (t.submittedAt() != null) {
                submitted++;
            }
            if (seatCounts.get(t.classLabel() + "#" + t.seatNo()) > 1) {
                duplicates++;
            }
        }
        List<AttemptDto> rows = null;
        if (withAttempts) {
            Map<Integer, Map<Integer, AnswerRow>> answers = new HashMap<>();
            for (AnswerRow ans : repository.answersOfAssignment(a.id())) {
                answers.computeIfAbsent(ans.attemptId(), k -> new HashMap<>()).put(ans.slotId(), ans);
            }
            rows = new ArrayList<>(attempts.size());
            for (AttemptRow t : attempts) {
                boolean duplicate = seatCounts.get(t.classLabel() + "#" + t.seatNo()) > 1;
                String state = AttemptRules.state(a, t, now);
                if (t.submittedAt() == null) {
                    rows.add(new AttemptDto(t.id(), t.classLabel(), t.seatNo(), state, duplicate, t.startedAt(), null,
                            null, null, null));
                } else {
                    rows.add(new AttemptDto(t.id(), t.classLabel(), t.seatNo(), state, duplicate, t.startedAt(),
                            t.submittedAt(), t.score(), AssignmentPaper.score10(t.score(), t.maxScore()),
                            paper.partScores(answers.getOrDefault(t.id(), Map.of()))));
                }
            }
        }
        return new AssignmentDto(a.id(), a.publicCode(), a.title(), a.status(), a.durationMinutes(), a.closesAt(),
                a.closedAt(), a.showScore(), a.showSolutions(), a.createdAt(),
                classes.stream().map(c -> new ClassDto(c.classLabel(), c.rosterSize())).toList(),
                paper.questionCount(), paper.maxScore(), ATTEMPT_CAP_FACTOR * roster, attempts.size(), submitted,
                duplicates, rows);
    }

    // ---------- T4: đóng ----------

    /** Đóng tay; đã đóng (kể cả đóng lười) thì trả nguyên trạng. Lượt chưa nộp không nộp được nữa. */
    @Transactional
    public AssignmentDto close(Integer examId, Integer assignmentId, Integer actorId) {
        LocalDateTime now = now();
        AssignmentRow row = loader.refresh(ownedLocked(examId, assignmentId, actorId), now);
        if (row.open()) {
            repository.closeNow(row.id(), now);
            row = repository.findById(row.id()).orElseThrow();
        }
        return toDto(row, loader.paper(row), false, now);
    }

    // ---------- T5, xóa nhiều lượt ----------

    /**
     * T5: xóa một lượt (học sinh mất máy thì vào lại được, hoặc giáo viên xóa dữ liệu của em đó). Ở MỌI trạng thái của bài
     * giao, kể cả sau khi đóng (quyết định 16/11, ý 4: dữ liệu học sinh giữ tới khi giáo viên xóa).
     */
    @Transactional
    public void deleteAttempt(Integer examId, Integer assignmentId, Integer attemptId, Integer actorId) {
        AssignmentRow row = ownedLocked(examId, assignmentId, actorId);
        if (repository.deleteAttempts(row.id(), List.of(attemptId)) == 0) {
            throw new NoSuchElementException("Không tìm thấy lượt làm id=" + attemptId);
        }
    }

    /**
     * Xóa nhiều lượt một lần (quyết định 16/11), ví dụ các lượt trùng số thứ tự hoặc lượt phá. 1..{@value #MAX_BULK_DELETE}
     * id; id không thuộc bài giao bị bỏ qua (không lộ lượt của bài khác). Ở mọi trạng thái, như T5.
     */
    @Transactional
    public DeleteAttemptsResult deleteAttempts(Integer examId, Integer assignmentId, List<Integer> attemptIds,
                                               Integer actorId) {
        if (attemptIds == null || attemptIds.isEmpty() || attemptIds.size() > MAX_BULK_DELETE
                || attemptIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("Cần 1 đến " + MAX_BULK_DELETE + " mã lượt làm.");
        }
        AssignmentRow row = ownedLocked(examId, assignmentId, actorId);
        Set<Integer> ids = attemptIds.stream().collect(Collectors.toCollection(LinkedHashSet::new));
        return new DeleteAttemptsResult(repository.deleteAttempts(row.id(), ids));
    }

    // ---------- T6: xóa bài giao ----------

    /** Xóa bài giao cùng mọi lượt làm và câu trả lời (xóa dây chuyền), ở trạng thái nào cũng được. */
    @Transactional
    public void delete(Integer examId, Integer assignmentId, Integer actorId) {
        AssignmentRow row = ownedLocked(examId, assignmentId, actorId);
        repository.delete(row.id());
    }
}
