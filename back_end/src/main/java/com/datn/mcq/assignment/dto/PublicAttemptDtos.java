package com.datn.mcq.assignment.dto;

import com.datn.mcq.assignment.dto.AssignmentDtos.PartScoreDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO của API công khai cho học sinh {@code /api/public/**} (docs/design/lam-bai-truc-tuyen.md mục 3.2). Dựng từ danh sách
 * trường CHO PHÉP, không tái dùng DTO xem đề. Khi bài còn mở không có trường nào mang đáp án, nhãn, đáp số, lời giải, độ
 * khó hay id câu ngân hàng; kết quả và phần xem lại ({@link ReviewPartDto}) chỉ có sau khi bài đóng (quyết định 16/11).
 */
public final class PublicAttemptDtos {

    private PublicAttemptDtos() {}

    public record ClassOptionDto(String classLabel, int size) {}

    /**
     * P1: trang chào; không có câu hỏi. {@code classes}: các lớp để học sinh chọn. {@code identityMode} (25/11): {@code name}
     * thì học sinh nhập họ tên, {@code seat} thì nhập số thứ tự. Không có họ tên của ai.
     */
    public record InfoDto(String title, int questionCount, int durationMinutes, LocalDateTime closesAt, String status,
                          boolean showScore, boolean showSolutions, String identityMode,
                          List<ClassOptionDto> classes) {}

    /**
     * P2. {@code classLabel} phải là một lớp của bài giao. Chế độ {@code seat}: {@code seatNo} trong 1..sĩ số của lớp đó
     * ({@code studentName} bị bỏ qua, không lưu). Chế độ {@code name} (25/11): {@code studentName} 2–60 ký tự sau khi chuẩn
     * hóa ({@code seatNo} bị bỏ qua). {@code confirmDuplicate} (25/11, mặc định false): đã có lượt cùng lớp + số / tên thì
     * chỉ tạo lượt khi true; false trả 409 {@code ATTEMPT_IDENTITY_TAKEN} và không tạo gì.
     */
    public record StartRequest(String classLabel, Integer seatNo, String studentName, Boolean confirmDuplicate) {}

    public record PublicStatementDto(String key, String text) {}

    /**
     * Một hình của câu (S1, docs/design/cau-co-hinh.md mục 4.1): token {@code ⟦img:<id>⟧} trong chữ câu trỏ tới {@code id}.
     * {@code url} ký theo lượt làm ({@code /api/public/question-images/{id}?exp&a&sig}), giao diện dùng nguyên. Cố ý không có
     * {@code role}, {@code sha256}, kích thước byte.
     */
    public record PublicImageDto(Long id, String mime, Integer widthPx, Integer heightPx, Short displayWidthMm,
                                 String url) {}

    /**
     * Một câu cho học sinh làm: mcq có {@code options}, tf có {@code statements} (chỉ key và text), sa chỉ có đề.
     * {@code images}: hình của câu dẫn, phương án, ý (không bao giờ có hình lời giải); không có khi câu không có hình.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicQuestionDto(Integer slotId, int number, String questionType, String questionText,
                                    Map<String, String> options, List<PublicStatementDto> statements,
                                    List<PublicImageDto> images) {}

    public record PublicPartDto(String part, List<PublicQuestionDto> items) {}

    /** Điểm (khi bài đã đóng và giáo viên bật {@code showScore}). Không có đáp án từng câu. */
    public record ResultDto(BigDecimal score, BigDecimal maxScore, BigDecimal score10, List<PartScoreDto> parts) {}

    /** Một ý của câu Đúng/Sai khi xem lại: nhãn đúng ({@code T}/{@code F}), lựa chọn của em (null: bỏ trống). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewStatementDto(String key, String text, String label, String yourAnswer, boolean correct) {}

    /**
     * Một câu khi xem lại (bài đã đóng, giáo viên bật {@code showSolutions}): đề, lựa chọn của em cạnh đáp án đúng, điểm câu
     * và lời giải. mcq: {@code options}, {@code yourChoice}, {@code correctOption}; tf: {@code statements}; sa:
     * {@code yourText}, {@code saAnswer}. {@code explanation} null khi câu không có lời giải. {@code images}: mọi hình của câu,
     * kể cả hình lời giải (S1); không có khi câu không có hình.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewItemDto(Integer slotId, int number, String questionType, String questionText,
                                Map<String, String> options, String yourChoice, String correctOption,
                                List<ReviewStatementDto> statements, String yourText, String saAnswer, int correctCount,
                                BigDecimal score, String explanation, List<PublicImageDto> images) {}

    public record ReviewPartDto(String part, List<ReviewItemDto> items) {}

    /**
     * P2 (có {@code attemptToken}, chỉ trả một lần) và P3. {@code parts}: câu hỏi, chỉ khi còn nộp được.
     * {@code duplicateSeat}: đã có lượt khác cùng lớp + số thứ tự, hoặc (chế độ {@code name}, 25/11) cùng lớp + họ tên
     * (giáo viên thấy cả hai lượt bị đánh dấu "trùng"). {@code identityMode}: chế độ của bài giao; đúng một trong
     * {@code seatNo} / {@code studentName} có giá trị. {@code studentName} là tên của CHÍNH lượt này (token), không bao
     * giờ là tên của lượt khác.
     * {@code resultsReleased}: bài đã đóng và hết ân hạn nộp; trước đó không có {@code result} hay {@code review}.
     * {@code resultsAt}: lúc dự kiến có kết quả (hạn đóng + ân hạn; giáo viên đóng sớm thì sớm hơn).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttemptViewDto(String attemptToken, String identityMode, String classLabel, Integer seatNo,
                                 String studentName, boolean duplicateSeat,
                                 LocalDateTime startedAt, LocalDateTime deadlineAt, LocalDateTime serverNow,
                                 long remainingSeconds, boolean submitted, LocalDateTime submittedAt,
                                 boolean resultsReleased, LocalDateTime resultsAt, List<PublicPartDto> parts,
                                 ResultDto result, List<ReviewPartDto> review) {}

    /** Một câu trả lời: mcq {@code choice} A-D; tf {@code tf} {"a": "T", ...}; sa {@code text} tối đa 20 ký tự. */
    public record AnswerInput(Integer slotId, String choice, Map<String, String> tf, String text) {}

    public record SubmitRequest(List<AnswerInput> answers) {}

    /** P4: chỉ báo "đã nộp" (quyết định 16/11: không trả điểm khi bài còn mở). */
    public record SubmitResultDto(LocalDateTime submittedAt, LocalDateTime resultsAt) {}
}
