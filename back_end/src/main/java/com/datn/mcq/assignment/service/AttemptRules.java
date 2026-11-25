package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.repository.AssignmentRepository.AssignmentRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AttemptRow;

import java.text.Collator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Quy tắc thời gian của bài giao và lượt làm (docs/design/lam-bai-truc-tuyen.md mục 2.2, quyết định 16/11). Hàm thuần.
 *
 * <ul>
 *   <li>Nộp được khi chưa nộp, {@code now ≤ deadline_at + ân hạn} và bài không bị giáo viên đóng TRƯỚC hạn của lượt
 *       ({@code closed_at < deadline_at}: đóng tay giữa giờ). Đóng lười có {@code closed_at = closes_at ≥ deadline_at}
 *       nên vẫn còn ân hạn.</li>
 *   <li>Kết quả (điểm, đáp án, lời giải) chỉ mở khi bài đã đóng VÀ hết ân hạn tính từ lúc đóng: lúc đó không lượt nào còn
 *       nộp được, nên học sinh nộp xong không thể chuyển đáp án cho bạn còn đang trong ân hạn.</li>
 * </ul>
 */
final class AttemptRules {

    /** Ân hạn nộp sau hạn của lượt (quyết định 16/11: 2 phút, thay cho 30 giây của thiết kế 14/11). */
    static final Duration GRACE = Duration.ofMinutes(2);

    static final String IN_PROGRESS = "in_progress";
    static final String SUBMITTED = "submitted";
    static final String EXPIRED = "expired";

    /** Quy tắc so chữ khi sắp bảng điểm theo họ tên (25/11). */
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private AttemptRules() {}

    /** Giáo viên đóng bài trước hạn của lượt này. */
    static boolean closedBeforeDeadline(AssignmentRow a, AttemptRow attempt) {
        return !a.open() && a.closedAt().isBefore(attempt.deadlineAt());
    }

    static boolean pastGrace(AttemptRow attempt, LocalDateTime now) {
        return now.isAfter(attempt.deadlineAt().plus(GRACE));
    }

    static boolean canSubmit(AssignmentRow a, AttemptRow attempt, LocalDateTime now) {
        return attempt.submittedAt() == null && !closedBeforeDeadline(a, attempt) && !pastGrace(attempt, now);
    }

    static String state(AssignmentRow a, AttemptRow attempt, LocalDateTime now) {
        if (attempt.submittedAt() != null) {
            return SUBMITTED;
        }
        return canSubmit(a, attempt, now) ? IN_PROGRESS : EXPIRED;
    }

    /** Lúc kết quả mở (bài mở: dự kiến theo hạn đóng). */
    static LocalDateTime resultsAt(AssignmentRow a) {
        return (a.open() ? a.closesAt() : a.closedAt()).plus(GRACE);
    }

    static boolean resultsReleased(AssignmentRow a, LocalDateTime now) {
        return !a.open() && !now.isBefore(a.closedAt().plus(GRACE));
    }

    static long remainingSeconds(AttemptRow attempt, LocalDateTime now) {
        long s = Duration.between(now, attempt.deadlineAt()).toSeconds();
        return Math.max(0, s);
    }

    /**
     * Id các lượt "trùng" (quyết định 16/11): có lượt KHÁC cùng lớp + số thứ tự (hoặc, chế độ họ tên từ 25/11, cùng lớp +
     * họ tên theo {@link StudentNames#key}) trong cùng bài giao, xét MỌI lượt kể cả chưa nộp; tính lúc đọc, không lưu. Một
     * quy tắc cho cả bảng điểm (cờ {@code duplicate}), thống kê câu (lượt trùng không được tính) và báo trùng trước khi
     * bắt đầu ({@link #identityKey}), để các nơi luôn khớp nhau.
     */
    static Set<Integer> duplicateIds(List<AttemptRow> attempts) {
        Map<String, Integer> counts = new HashMap<>();
        for (AttemptRow t : attempts) {
            counts.merge(t.classLabel() + identityKey(t.seatNo(), t.studentName()), 1, Integer::sum);
        }
        Set<Integer> out = new HashSet<>();
        for (AttemptRow t : attempts) {
            if (counts.get(t.classLabel() + identityKey(t.seatNo(), t.studentName())) > 1) {
                out.add(t.id());
            }
        }
        return out;
    }

    /**
     * Khóa danh tính của một lượt TRONG một lớp: {@code #<số>} hoặc {@code @<họ tên chữ thường>}. Hai tiền tố khác nhau
     * nên số và tên không bao giờ trùng nhau (mỗi bài giao chỉ một chế độ).
     */
    static String identityKey(Integer seatNo, String studentName) {
        return studentName != null ? "@" + StudentNames.key(studentName) : "#" + seatNo;
    }

    /**
     * Thứ tự bảng điểm ở chế độ họ tên (25/11): giữ thứ tự lớp đã có (thứ tự khai lúc giao), trong lớp theo tên (chữ cuối)
     * rồi theo cả họ và tên, như sổ điểm; so theo quy tắc chữ tiếng Việt. Cùng tên thì theo lúc bắt đầu, rồi id.
     */
    static List<AttemptRow> byClassThenName(List<AttemptRow> attempts) {
        Map<String, Integer> classOrder = new HashMap<>();
        for (AttemptRow t : attempts) {
            classOrder.putIfAbsent(t.classLabel(), classOrder.size());
        }
        Collator collator = Collator.getInstance(VIETNAMESE);
        Comparator<String> text = Comparator.nullsFirst((String x, String y) -> collator.compare(x, y));
        Comparator<AttemptRow> order = Comparator.<AttemptRow>comparingInt(t -> classOrder.get(t.classLabel()))
                .thenComparing(t -> t.studentName() == null ? null : StudentNames.givenName(t.studentName()), text)
                .thenComparing(AttemptRow::studentName, text)
                .thenComparing(AttemptRow::startedAt)
                .thenComparing(AttemptRow::id);
        List<AttemptRow> out = new ArrayList<>(attempts);
        out.sort(order);
        return out;
    }
}
