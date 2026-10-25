package com.datn.mcq.exam.dto;

import java.util.List;

/** W1-2: body của PUT /api/exams/{examId}/slots/{slotId}/question (giáo viên sửa câu trong chỗ chưa Giữ). */
public final class ExamEditDtos {

    private ExamEditDtos() {}

    /**
     * Gửi đủ nội dung theo dạng câu của chỗ (thay thế toàn bộ; không sửa mức độ). Trường không áp dụng cho dạng câu để
     * trống (null), gửi lên thì 400.
     *
     * @param baseQuestionId câu (questionId) đang hiển thị khi giáo viên bắt đầu sửa; khác câu hiện tại của chỗ thì 409
     *                       VERSION_CONFLICT (có người đã sửa, Đổi, hoặc trang đã cũ)
     * @param correctOption  mcq: A-D
     * @param saAnswer       sa: đáp số
     * @param statements     tf: đủ 4 ý a-d
     */
    public record EditQuestionRequest(
            Integer baseQuestionId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctOption,
            String saAnswer,
            List<StatementEdit> statements,
            String explanation
    ) {}

    /** Một ý câu Đúng/Sai: key a-d, nội dung, nhãn T hoặc F. */
    public record StatementEdit(String key, String text, String label) {}
}
