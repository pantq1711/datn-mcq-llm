package com.datn.mcq.exam.export;

/**
 * Khổ trang và hình học bố cục của file Word xuất ra (4/11, theo mẫu đề kiểm tra của một trường THCS): A4, lề trái 2 cm,
 * các lề còn lại 1 cm, khoảng cách đầu trang 0,5 cm và chân trang 1 cm; phông Times New Roman 12. Mọi con số bố cục (mốc
 * tab của phương án, vị trí dừng tab) lấy từ đây để file mẫu ({@link ExamReferenceDoc}), bộ dựng Markdown và phép đo cột
 * phương án ({@link OptionLayout}) không lệch nhau.
 *
 * <p>Đơn vị twips (1/20 point) như OOXML. Các số đo lấy nguyên từ pgMar, footer1.xml và tab dừng của mẫu.
 */
public final class ExamPage {

    /** A4: 595,3 × 841,9 pt. */
    public static final int PAGE_WIDTH_TWIPS = 11906;
    public static final int PAGE_HEIGHT_TWIPS = 16838;

    public static final int MARGIN_TOP_TWIPS = 567;
    public static final int MARGIN_BOTTOM_TWIPS = 567;
    public static final int MARGIN_LEFT_TWIPS = 1134;
    public static final int MARGIN_RIGHT_TWIPS = 567;
    /** Khoảng cách đầu trang / chân trang tới mép giấy. */
    public static final int HEADER_DISTANCE_TWIPS = 283;
    public static final int FOOTER_DISTANCE_TWIPS = 567;

    /** Bề rộng chữ giữa hai lề (twips): 10205 = 510,25 pt. */
    public static final int TEXT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS;

    /** Cỡ chữ (pt) và phông của toàn bộ file, kể cả tiêu đề. */
    public static final int FONT_SIZE_PT = 12;
    public static final String FONT = "Times New Roman";

    /** Giãn dòng 1,15 (240 = đơn) và khoảng cách đoạn 0–3 pt. */
    public static final int LINE_SPACING = 276;
    public static final int PARAGRAPH_AFTER_TWIPS = 60;

    /**
     * Mốc tab đầu tiên của phương án và ý đúng/sai (mẫu: tab trái tại 283 rồi nhãn "A." / "a)"). Câu hỏi "Câu k." không
     * thụt lề treo: các dòng sau về lề trái. Phương án dài quá một dòng cũng chạy về lề trái như mẫu.
     */
    public static final int OPTION_LABEL_START_TWIPS = 283;

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
                + "\" w:header=\"" + HEADER_DISTANCE_TWIPS + "\" w:footer=\"" + FOOTER_DISTANCE_TWIPS
                + "\" w:gutter=\"0\"/>" + (restartPageNumbers ? "<w:pgNumType w:start=\"1\"/>" : "") + "</w:sectPr>";
    }

    /** W: bề rộng dòng sau mốc tab đầu của phương án (pt): 496,1. */
    public static double optionLineWidthPt() {
        return (TEXT_WIDTH_TWIPS - OPTION_LABEL_START_TWIPS) / 20.0;
    }

    private ExamPage() {
    }
}
