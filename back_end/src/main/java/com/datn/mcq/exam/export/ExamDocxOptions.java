package com.datn.mcq.exam.export;

/**
 * Phần của file Word nằm ngoài Markdown (chân trang và section cuối, do {@link ExamReferenceDoc} và {@link ExamSections}
 * dựng), theo tùy chọn xuất (16/11).
 *
 * @param examCode mã đề in ở chân trang ("Mã đề 205", "Đáp án mã đề 205"); null hoặc trống là {@link ExamHeader#ORIGINAL_CODE}
 * @param content  {@link ExportContent#EXAM} thì file chỉ có section đề (chân trang "Mã đề …", không đánh số lại trang);
 *                 {@link ExportContent#SPEC} (18/11) thì một section A4 ngang, chân trang "Bản đặc tả đề kiểm tra", mã đề
 *                 không dùng; null là {@link ExportContent#ALL}
 */
public record ExamDocxOptions(String examCode, ExportContent content) {

    /** Như trước 16/11: mã đề 101, đề kèm đáp án. */
    public static final ExamDocxOptions DEFAULT = new ExamDocxOptions(ExamHeader.ORIGINAL_CODE, ExportContent.ALL);

    public ExamDocxOptions {
        examCode = examCode == null || examCode.isBlank() ? ExamHeader.ORIGINAL_CODE : examCode.strip();
        content = content == null ? ExportContent.ALL : content;
    }

    boolean examOnly() {
        return content == ExportContent.EXAM;
    }

    boolean spec() {
        return content == ExportContent.SPEC;
    }
}
