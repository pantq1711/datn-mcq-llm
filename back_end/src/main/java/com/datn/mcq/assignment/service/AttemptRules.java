package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.repository.AssignmentRepository.AssignmentRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AttemptRow;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
     * Id các lượt "trùng" (quyết định 16/11): có lượt KHÁC cùng lớp + số thứ tự trong cùng bài giao, xét MỌI lượt kể cả
     * chưa nộp; tính lúc đọc, không lưu. Một quy tắc cho cả bảng điểm (cờ {@code duplicate}) và thống kê câu (lượt trùng
     * không được tính), để hai nơi luôn khớp nhau.
     */
    static Set<Integer> duplicateIds(List<AttemptRow> attempts) {
        Map<String, Integer> seatCounts = new HashMap<>();
        for (AttemptRow t : attempts) {
            seatCounts.merge(seatKey(t), 1, Integer::sum);
        }
        Set<Integer> out = new HashSet<>();
        for (AttemptRow t : attempts) {
            if (seatCounts.get(seatKey(t)) > 1) {
                out.add(t.id());
            }
        }
        return out;
    }

    private static String seatKey(AttemptRow t) {
        return t.classLabel() + "#" + t.seatNo();
    }
}
