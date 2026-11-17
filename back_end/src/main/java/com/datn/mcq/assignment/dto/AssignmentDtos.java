package com.datn.mcq.assignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** DTO của API giáo viên {@code /api/exams/{examId}/assignments} (docs/design/lam-bai-truc-tuyen.md mục 3.1). */
public final class AssignmentDtos {

    private AssignmentDtos() {}

    /**
     * Một lớp được làm bài (quyết định 16/11): nhãn lớp (chuẩn hóa: bỏ khoảng trắng, viết hoa; chữ Latin, số, / . -; 1–10 ký
     * tự) và sĩ số 1..99. Học sinh chọn lớp trong danh sách này, số thứ tự phải trong 1..sĩ số.
     */
    public record ClassInput(String classLabel, Integer size) {}

    /**
     * T1. {@code showSolutions} (16/11, mặc định false): sau khi bài đóng, học sinh mở lại link xem đáp án và lời giải từng
     * câu cạnh lựa chọn của mình. {@code classes}: 1..10 lớp, nhãn không trùng.
     */
    public record CreateAssignmentRequest(Integer durationMinutes, Instant closesAt, Boolean showScore,
                                          Boolean showSolutions, Boolean consentConfirmed, List<ClassInput> classes) {}

    public record ClassDto(String classLabel, int size) {}

    /** Điểm một Phần của một lượt. Phần II đếm theo ý ({@code total} = 4 × số câu). */
    public record PartScoreDto(String part, int correct, int total, BigDecimal score) {}

    /**
     * Một dòng bảng điểm. {@code state}: {@code in_progress}, {@code submitted}, {@code expired}. {@code duplicate}
     * (16/11): có lượt khác cùng lớp + số thứ tự trong bài giao này; tính lúc đọc, cả hai lượt đều bị đánh dấu và không
     * được tính vào thống kê hậu kiểm (PR B, cùng quy tắc). Điểm chỉ có khi đã nộp.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttemptDto(Integer attemptId, String classLabel, int seatNo, String state, boolean duplicate,
                             LocalDateTime startedAt, LocalDateTime submittedAt, BigDecimal score, BigDecimal score10,
                             List<PartScoreDto> parts) {}

    /**
     * Bài giao. {@code attempts} chỉ có ở T3 (xem một bài giao); T2 (danh sách) không có. {@code maxAttempts} = 2 × tổng sĩ
     * số (quyết định 16/11). {@code statsComputedAt} (PR B): lúc tính thống kê câu gần nhất; không có khi chưa tính hoặc
     * thống kê đã bị vô hiệu do xóa lượt.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssignmentDto(Integer assignmentId, String publicCode, String title, String status,
                                int durationMinutes, LocalDateTime closesAt, LocalDateTime closedAt, boolean showScore,
                                boolean showSolutions, LocalDateTime createdAt, List<ClassDto> classes,
                                int questionCount, BigDecimal maxScore, int maxAttempts, int startedCount,
                                int submittedCount, int duplicateCount, LocalDateTime statsComputedAt,
                                List<AttemptDto> attempts) {}

    /** Xóa nhiều lượt một lần (quyết định 16/11): 1..500 id, id không thuộc bài giao bị bỏ qua. */
    public record DeleteAttemptsRequest(List<Integer> attemptIds) {}

    public record DeleteAttemptsResult(int deleted) {}
}
