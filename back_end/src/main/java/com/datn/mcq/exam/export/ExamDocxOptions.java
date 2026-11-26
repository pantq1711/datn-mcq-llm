package com.datn.mcq.exam.export;

/**
 * Phần của file Word nằm ngoài Markdown (chân trang và section cuối, do {@link ExamReferenceDoc} và {@link ExamSections}
 * dựng), theo tùy chọn xuất (16/11).
 *
 * @param examCode   mã đề in ở chân trang ("Mã đề 205", "Đáp án mã đề 205"); null hoặc trống là {@link ExamHeader#ORIGINAL_CODE}
 * @param content    {@link ExportContent#EXAM} thì file chỉ có section đề (chân trang "Mã đề …", không đánh số lại trang);
 *                   {@link ExportContent#SPEC} (18/11) thì một section A4 ngang, chân trang "Bản đặc tả đề kiểm tra", mã đề
 *                   không dùng; {@link ExportContent#YOUNGMIX} (26/11) thì một section không chân trang; null là
 *                   {@link ExportContent#ALL}
 * @param phach      (26/11) có trang tờ phách đầu file (section riêng, không chân trang; section đề đánh số trang lại từ 1)
 *                   và đầu đề "Mã đề | Điểm bài thi | Mã phách"; chỉ áp cho {@link ExportContent#ALL} và
 *                   {@link ExportContent#EXAM}, nội dung khác luôn false
 * @param fontSizePt (26/11) cỡ chữ của đề, {@value #DEFAULT_FONT_SIZE_PT} hoặc {@value #LARGE_FONT_SIZE_PT}; bản đặc tả luôn
 *                   {@value #DEFAULT_FONT_SIZE_PT}
 */
public record ExamDocxOptions(String examCode, ExportContent content, boolean phach, int fontSizePt) {

    /** Cỡ chữ mặc định của file xuất (như trước 26/11) và cỡ lớn chọn thêm được. */
    public static final int DEFAULT_FONT_SIZE_PT = ExamPage.FONT_SIZE_PT;
    public static final int LARGE_FONT_SIZE_PT = 14;

    /** Như trước 16/11: mã đề 101, đề kèm đáp án. */
    public static final ExamDocxOptions DEFAULT = new ExamDocxOptions(ExamHeader.ORIGINAL_CODE, ExportContent.ALL);

    public ExamDocxOptions {
        examCode = examCode == null || examCode.isBlank() ? ExamHeader.ORIGINAL_CODE : examCode.strip();
        content = content == null ? ExportContent.ALL : content;
        if (fontSizePt != DEFAULT_FONT_SIZE_PT && fontSizePt != LARGE_FONT_SIZE_PT) {
            throw new IllegalArgumentException(FONT_SIZE_MESSAGE);
        }
        if (content == ExportContent.SPEC) {
            fontSizePt = DEFAULT_FONT_SIZE_PT;
        }
        if (content != ExportContent.ALL && content != ExportContent.EXAM) {
            phach = false;
        }
    }

    /** Không có tờ phách, cỡ chữ mặc định. */
    public ExamDocxOptions(String examCode, ExportContent content) {
        this(examCode, content, false, DEFAULT_FONT_SIZE_PT);
    }

    private static final String FONT_SIZE_MESSAGE = "Tham số fontSize chỉ nhận " + DEFAULT_FONT_SIZE_PT + " hoặc "
            + LARGE_FONT_SIZE_PT;
    private static final String PHACH_MESSAGE = "Tham số phach chỉ nhận true hoặc false";

    /**
     * Tham số {@code fontSize} của API xuất (26/11): "12" hoặc "14" (bỏ khoảng trắng hai đầu). Không có (null hoặc trống) là
     * {@value #DEFAULT_FONT_SIZE_PT}.
     *
     * @throws IllegalArgumentException giá trị khác (API trả 400)
     */
    public static int parseFontSize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_FONT_SIZE_PT;
        }
        String v = value.strip();
        if (v.equals(Integer.toString(DEFAULT_FONT_SIZE_PT))) {
            return DEFAULT_FONT_SIZE_PT;
        }
        if (v.equals(Integer.toString(LARGE_FONT_SIZE_PT))) {
            return LARGE_FONT_SIZE_PT;
        }
        throw new IllegalArgumentException(FONT_SIZE_MESSAGE);
    }

    /**
     * Tham số {@code phach} của API xuất (26/11): "true" hoặc "false", không phân biệt hoa thường. Không có (null hoặc trống)
     * là false.
     *
     * @throws IllegalArgumentException giá trị khác (API trả 400)
     */
    public static boolean parsePhach(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.strip();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException(PHACH_MESSAGE);
    }

    boolean examOnly() {
        return content == ExportContent.EXAM;
    }

    boolean spec() {
        return content == ExportContent.SPEC;
    }

    boolean youngMix() {
        return content == ExportContent.YOUNGMIX;
    }

    /** Số đo dọc theo cỡ chữ (dòng cao của ô mã đề...): {@code twipsAt12} ở cỡ 12, nhân theo tỉ lệ cỡ đã chọn. */
    int scaled(int twipsAt12) {
        return Math.round(twipsAt12 * fontSizePt / (float) DEFAULT_FONT_SIZE_PT);
    }
}
