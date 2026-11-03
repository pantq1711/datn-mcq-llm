package com.datn.mcq.exam.export;

/**
 * Khổ trang và hình học bố cục của file Word xuất ra (4/11, theo khuôn đề kiểm tra THCS): A4, lề trên/dưới 1,5 cm, trái
 * 2 cm, phải 1,5 cm; phông Times New Roman 13. Mọi con số bố cục (thụt lề câu, thụt lề phương án, vị trí dừng tab) lấy từ
 * đây để file mẫu ({@link ExamReferenceDoc}), bộ dựng Markdown và phép đo cột phương án ({@link OptionLayout}) không lệch nhau.
 *
 * <p>Đơn vị twips (1/20 point) như OOXML; lề tính từ cm theo 1 cm = 1440 / 2,54 twips.
 */
public final class ExamPage {

    /** A4: 595,3 × 841,9 pt. */
    public static final int PAGE_WIDTH_TWIPS = 11906;
    public static final int PAGE_HEIGHT_TWIPS = 16838;

    public static final double MARGIN_TOP_CM = 1.5;
    public static final double MARGIN_BOTTOM_CM = 1.5;
    public static final double MARGIN_LEFT_CM = 2.0;
    public static final double MARGIN_RIGHT_CM = 1.5;

    public static final int MARGIN_TOP_TWIPS = cmToTwips(MARGIN_TOP_CM);
    public static final int MARGIN_BOTTOM_TWIPS = cmToTwips(MARGIN_BOTTOM_CM);
    public static final int MARGIN_LEFT_TWIPS = cmToTwips(MARGIN_LEFT_CM);
    public static final int MARGIN_RIGHT_TWIPS = cmToTwips(MARGIN_RIGHT_CM);
    /** Khoảng cách chân trang tới mép giấy (nửa lề dưới). */
    public static final int FOOTER_DISTANCE_TWIPS = MARGIN_BOTTOM_TWIPS / 2;

    /** Bề rộng chữ giữa hai lề (twips). */
    public static final int TEXT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS;

    /** Cỡ chữ (pt) và phông của toàn bộ file, kể cả tiêu đề. */
    public static final int FONT_SIZE_PT = 13;
    public static final String FONT = "Times New Roman";

    /** Giãn dòng 1,15 (240 = đơn) và khoảng cách đoạn 0–3 pt. */
    public static final int LINE_SPACING = 276;
    public static final int PARAGRAPH_AFTER_TWIPS = 60;

    /**
     * Câu hỏi thụt lề treo: nhãn "Câu k." đứng ở lề, câu dẫn bắt đầu tại điểm này, các dòng sau thẳng với chữ đầu câu dẫn.
     * Nhãn "Câu 10." đậm cỡ 13 rộng khoảng 852 twips nên 1000 vẫn đủ chỗ.
     */
    public static final int QUESTION_INDENT_TWIPS = 1000;

    /** Phương án và ý đúng/sai thẳng hàng với câu dẫn; dòng thứ hai của phương án dài thẳng với chữ sau nhãn "A. ". */
    public static final int OPTION_LABEL_START_TWIPS = QUESTION_INDENT_TWIPS;
    public static final int OPTION_HANGING_TWIPS = 320;

    /** Id quan hệ của chân trang section đề / section đáp án (không trùng "rIdMT<n>" của bộ chuyển MathType). */
    public static final String EXAM_FOOTER_REL_ID = "rIdExamFooter";
    public static final String ANSWER_FOOTER_REL_ID = "rIdExamFooterAnswer";

    /**
     * w:sectPr của một section A4 theo các hằng số trên: cùng khổ và lề cho cả hai section (bộ ngắt dòng MathType đọc lề từ
     * sectPr cuối thân tài liệu). Section đáp án đánh số trang lại từ 1.
     */
    static String sectionProperties(String footerRelId, boolean restartPageNumbers) {
        return "<w:sectPr><w:footerReference w:type=\"default\" r:id=\"" + footerRelId + "\"/>"
                + "<w:footnotePr><w:numRestart w:val=\"eachSect\"/></w:footnotePr>"
                + "<w:pgSz w:w=\"" + PAGE_WIDTH_TWIPS + "\" w:h=\"" + PAGE_HEIGHT_TWIPS + "\"/>"
                + "<w:pgMar w:top=\"" + MARGIN_TOP_TWIPS + "\" w:right=\"" + MARGIN_RIGHT_TWIPS
                + "\" w:bottom=\"" + MARGIN_BOTTOM_TWIPS + "\" w:left=\"" + MARGIN_LEFT_TWIPS
                + "\" w:header=\"" + FOOTER_DISTANCE_TWIPS + "\" w:footer=\"" + FOOTER_DISTANCE_TWIPS
                + "\" w:gutter=\"0\"/>" + (restartPageNumbers ? "<w:pgNumType w:start=\"1\"/>" : "") + "</w:sectPr>";
    }

    /** W: bề rộng dòng sau thụt lề phương án (pt). */
    public static double optionLineWidthPt() {
        return (TEXT_WIDTH_TWIPS - OPTION_LABEL_START_TWIPS) / 20.0;
    }

    private ExamPage() {
    }

    static int cmToTwips(double cm) {
        return (int) Math.round(cm * 1440 / 2.54);
    }
}
