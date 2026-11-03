package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.entity.ExamSlot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Markdown (pandoc) của đề ở chế độ giáo viên, để {@link PandocRunner} chuyển sang Word, theo khuôn đề kiểm tra THCS
 * (4/11):
 * <ul>
 *   <li>đầu đề: hai bảng không viền ({@link ExamHeader}), dòng "Họ và tên … Lớp …" và ô "Mã đề 101" có viền;</li>
 *   <li>mỗi phần một dòng hướng dẫn (tên phần đậm, phần sau thường, cùng đoạn); phần không có câu thì bỏ;</li>
 *   <li>"Câu k." đậm, thụt lề treo (kiểu {@code ExamQuestion}); mcq xếp cột bằng tab ({@link OptionLayout}); ý đúng/sai
 *       "a)" "b)"... thụt như phương án; cuối đề dòng "------ HẾT ------";</li>
 *   <li>sang trang mới: "ĐÁP ÁN VÀ HƯỚNG DẪN CHẤM – MÃ ĐỀ 101" với bảng đáp án từng phần và điểm (lấy từ
 *       {@link ExamScoringProperties}), rồi "LỜI GIẢI CHI TIẾT".</li>
 * </ul>
 * Paragraph có kiểu riêng viết bằng khối {@code ::: {custom-style="..."}}; đầu đề, bảng và các dòng không có công thức viết
 * bằng khối openxml thô (Markdown của pandoc không bỏ được viền bảng, không có tab, không có ngắt trang trước đoạn). Chữ
 * trong khối thô được thoát XML. Tab là mã giữ chỗ ({@link ExamTabs}) thay sau pandoc.
 *
 * <p>Chuẩn hóa chỉ trong file xuất (không sửa DB): mỗi phương án và mỗi ý đúng/sai kết thúc bằng dấu chấm nếu chưa có dấu
 * kết câu (kể cả khi kết thúc bằng công thức); câu dẫn kết thúc bằng "là:" hoặc "là :" thành "là".
 *
 * <p>Công thức giữ nguyên trong $...$ (bỏ khoảng trắng sát dấu $) để pandoc chuyển thành Equation gốc của Word; ký tự
 * đặc biệt của Markdown ở ngoài công thức được thoát để hiện đúng chữ.
 */
public final class ExamMarkdownBuilder {

    static final Map<String, String> PART_NAMES = Map.of(
            "I", "PHẦN I. Câu trắc nghiệm nhiều phương án lựa chọn",
            "II", "PHẦN II. Câu trắc nghiệm đúng sai",
            "III", "PHẦN III. Câu trắc nghiệm trả lời ngắn");
    static final String MISSING_FAILED = "[Chưa có câu – cần sinh lại]";
    static final String MISSING_GENERATING = "[Chưa có câu – đang sinh]";
    static final String NONE = "—";
    static final String END_OF_EXAM = "------ HẾT ------";
    static final String ANSWER_TITLE = "ĐÁP ÁN VÀ HƯỚNG DẪN CHẤM – MÃ ĐỀ " + ExamHeader.ORIGINAL_CODE;
    static final String SOLUTION_TITLE = "LỜI GIẢI CHI TIẾT";

    /** Bảng điểm mặc định (cùng giá trị mặc định của cấu hình {@code app.exam.scoring}). */
    static final ExamScoringProperties DEFAULT_SCORING = new ExamScoringProperties(new BigDecimal("0.25"),
            new BigDecimal("0.5"), List.of(BigDecimal.ZERO, new BigDecimal("0.1"), new BigDecimal("0.25"),
            new BigDecimal("0.5"), new BigDecimal("1.0")));

    /** Số câu tối đa mỗi bảng đáp án phần I; phần III chia theo {@link #SA_PER_TABLE}. */
    static final int MCQ_PER_TABLE = 12;
    static final int SA_PER_TABLE = 6;

    /** Ký tự thoát bằng "\" khi ở ngoài công thức. */
    private static final String ESCAPED = "\\*_[]<>#`~^|@";
    /** Đầu đoạn dạng "1." "a)" "A." sẽ thành danh sách đánh số: thoát dấu chấm/ngoặc. */
    private static final Pattern LIST_START = Pattern.compile("^(\\d+|[A-Za-z])([.)])");
    /** Đầu đoạn "- " "+ " sẽ thành danh sách gạch đầu dòng. */
    private static final Pattern BULLET_START = Pattern.compile("^[-+]\\s");
    /** Cuối câu dẫn: "là:" hoặc "là :". */
    private static final Pattern STEM_COLON = Pattern.compile("(?<![\\p{L}\\p{N}_])là\\s*:\\s*$");
    /** Dòng lời giải mở đầu một ý: "a)", "b.", "Ý c)". */
    private static final Pattern STATEMENT_MARK = Pattern.compile("^(?:Ý\\s*)?\\(?([a-dA-D])[).]\\s*(.*)$");

    private ExamMarkdownBuilder() {}

    /** Đoạn nhỏ của chuỗi nguồn: chữ thường hoặc công thức (nội dung giữa hai dấu $). */
    record Piece(boolean math, String text) {}

    /** Đầu đề chỉ có tên đề (không có thông tin ma trận): môn để trống. */
    public static String build(ExamViewDto exam) {
        return build(exam, ExamHeader.blank(exam.title()), DEFAULT_SCORING);
    }

    public static String build(ExamViewDto exam, ExamHeader header) {
        return build(exam, header, DEFAULT_SCORING);
    }

    public static String build(ExamViewDto exam, ExamHeader header, ExamScoringProperties scoring) {
        List<String> blocks = new ArrayList<>();
        blocks.add(headerBlock(header));
        List<PartDto> parts = exam.parts().stream().filter(p -> !p.items().isEmpty()).toList();
        for (PartDto part : parts) {
            blocks.add(div("ExamInstruction", instruction(part)));
            int k = 1;
            for (ExamItemDto item : part.items()) {
                question(blocks, k++, item);
            }
        }
        // Dòng HẾT kết thúc section đề (sectPr trong pPr của đoạn này); đáp án là section mới, sang trang mới, số trang từ 1.
        blocks.add(raw("<w:p><w:pPr><w:pStyle w:val=\"ExamCenter\"/>"
                + ExamPage.sectionProperties(ExamPage.EXAM_FOOTER_REL_ID, false) + "</w:pPr>" + plainRun(END_OF_EXAM)
                + "</w:p>"));

        blocks.add(raw(paragraph("Heading1", ANSWER_TITLE, false)));
        for (PartDto part : parts) {
            answers(blocks, part, scoring);
        }

        blocks.add(raw(paragraph("Heading1", SOLUTION_TITLE, false)));
        for (PartDto part : parts) {
            blocks.add(raw(paragraph("ExamSubHeading", "PHẦN " + part.part(), false)));
            int k = 1;
            for (ExamItemDto item : part.items()) {
                solution(blocks, k++, item);
            }
        }
        return String.join("\n\n", blocks) + "\n";
    }

    // ---------------------------------------------------------------- dòng hướng dẫn, câu hỏi

    private static String instruction(PartDto part) {
        int n = part.items().size();
        String name = PART_NAMES.getOrDefault(part.part(), "PHẦN " + part.part());
        String rest = "Thí sinh trả lời từ câu 1 đến câu " + n + ".";
        rest += switch (part.part()) {
            case "I" -> " Mỗi câu hỏi thí sinh chỉ chọn một phương án.";
            case "II" -> " Trong mỗi ý a), b), c), d) ở mỗi câu, thí sinh chọn đúng hoặc sai.";
            default -> "";
        };
        return "**" + name + ".** " + rest;
    }

    private static String label(int k) {
        return "**Câu " + k + ".**";
    }

    private static boolean hasQuestion(ExamItemDto item) {
        return item.questionText() != null;
    }

    private static void question(List<String> blocks, int k, ExamItemDto item) {
        if (!hasQuestion(item)) {
            blocks.add(div("ExamQuestion", label(k) + ExamTabs.labelToken()
                    + inline(ExamSlot.FAILED.equals(item.state()) ? MISSING_FAILED : MISSING_GENERATING)));
            return;
        }
        List<String> stem = stemParagraphs(item.questionText());
        blocks.add(div("ExamQuestion", label(k) + ExamTabs.labelToken() + (stem.isEmpty() ? "" : stem.get(0))));
        if (stem.size() > 1) {
            blocks.add(div("ExamQuestionCont", String.join("\n\n", stem.subList(1, stem.size()))));
        }
        switch (item.questionType()) {
            case MCQ -> blocks.add(div("ExamOption", mcqOptions(item)));
            case TF -> {
                List<String> lines = new ArrayList<>();
                for (ItemStatementDto s : statements(item)) {
                    lines.add(s.key().getDbValue() + "\\) " + inline(endWithPeriod(oneLine(s.text()))));
                }
                if (!lines.isEmpty()) {
                    blocks.add(div("ExamOption", String.join("\n\n", lines)));
                }
            }
            case SA -> {
                // chỉ có đề
            }
        }
    }

    /** Mỗi dòng không trống của câu dẫn thành một đoạn; câu dẫn kết thúc bằng "là:" thì bỏ dấu hai chấm. */
    private static List<String> stemParagraphs(String text) {
        List<String> lines = Arrays.stream(text.split("\\R")).map(String::strip).filter(l -> !l.isEmpty()).toList();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == lines.size() - 1) {
                line = STEM_COLON.matcher(line).replaceFirst("là");
            }
            out.add(inline(line));
        }
        return out;
    }

    /** Bốn phương án xếp 1 dòng 4 cột, 2 dòng 2 cột hoặc mỗi phương án một dòng, tùy bề rộng đo được. */
    private static String mcqOptions(ExamItemDto item) {
        List<String> raw = Arrays.asList(item.optionA(), item.optionB(), item.optionC(), item.optionD()).stream()
                .map(o -> endWithPeriod(oneLine(o))).toList();
        OptionLayout layout = OptionLayout.choose(raw);
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            cells.add("**" + (char) ('A' + i) + ".** " + inline(raw.get(i)));
        }
        int[] stops = layout.stopsTwips();
        List<String> paragraphs = new ArrayList<>();
        for (int from = 0; from < cells.size(); from += layout.columns) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < layout.columns && from + c < cells.size(); c++) {
                if (c > 0) {
                    line.append(ExamTabs.token(stops[c - 1]));
                }
                line.append(cells.get(from + c));
            }
            paragraphs.add(line.toString());
        }
        return String.join("\n\n", paragraphs);
    }

    private static List<ItemStatementDto> statements(ExamItemDto item) {
        return item.statements() == null ? List.of() : item.statements().stream()
                .sorted(Comparator.comparing(ItemStatementDto::key)).toList();
    }

    // ---------------------------------------------------------------- đáp án

    private static void answers(List<String> blocks, PartDto part, ExamScoringProperties scoring) {
        blocks.add(raw(paragraph("ExamSubHeading", "PHẦN " + part.part(), false)));
        List<ExamItemDto> items = part.items();
        switch (part.part()) {
            case "I" -> {
                for (int from = 0; from < items.size(); from += MCQ_PER_TABLE) {
                    int to = Math.min(items.size(), from + MCQ_PER_TABLE);
                    List<String> numbers = new ArrayList<>();
                    List<String> picks = new ArrayList<>();
                    for (int i = from; i < to; i++) {
                        numbers.add(Integer.toString(i + 1));
                        picks.add(hasQuestion(items.get(i)) && items.get(i).correctOption() != null
                                ? items.get(i).correctOption().name() : NONE);
                    }
                    addTableSeparator(blocks, from);
                    blocks.add(raw(answerTable("Câu", numbers, "Chọn", picks, MCQ_PER_TABLE)));
                }
                blocks.add(div("ExamNote", "Mỗi câu trả lời đúng thí sinh được " + points(scoring.mcqPerQuestion())
                        + " điểm."));
            }
            case "II" -> {
                blocks.add(raw(trueFalseTable(items)));
                blocks.add(div("ExamNote", "Điểm của mỗi câu: thí sinh chọn đúng 1 ý được " + points(scoring.tfScore(1))
                        + " điểm; đúng 2 ý được " + points(scoring.tfScore(2)) + " điểm; đúng 3 ý được "
                        + points(scoring.tfScore(3)) + " điểm; đúng cả 4 ý được " + points(scoring.tfScore(4)) + " điểm."));
            }
            case "III" -> {
                for (int from = 0; from < items.size(); from += SA_PER_TABLE) {
                    int to = Math.min(items.size(), from + SA_PER_TABLE);
                    List<String> numbers = new ArrayList<>();
                    List<String> values = new ArrayList<>();
                    for (int i = from; i < to; i++) {
                        numbers.add(Integer.toString(i + 1));
                        values.add(plainAnswer(items.get(i)));
                    }
                    addTableSeparator(blocks, from);
                    blocks.add(raw(answerTable("Câu", numbers, "Đáp án", values, SA_PER_TABLE)));
                }
                blocks.add(div("ExamNote", "Mỗi câu trả lời đúng thí sinh được " + points(scoring.saPerQuestion())
                        + " điểm."));
            }
            default -> {
                // phần lạ: không có bảng đáp án
            }
        }
    }

    /**
     * Giữa hai bảng đáp án liền nhau (câu thứ {@code from} &gt; 0 mở bảng thứ hai trở đi) phải có một đoạn trống: Word gộp hai
     * bảng kề nhau không có đoạn nào ở giữa thành một bảng.
     */
    private static void addTableSeparator(List<String> blocks, int from) {
        if (from > 0) {
            blocks.add(raw(spacer()));
        }
    }

    private static String plainAnswer(ExamItemDto item) {
        if (!hasQuestion(item) || item.saAnswer() == null || item.saAnswer().isBlank()) {
            return NONE;
        }
        return item.saAnswer().strip().replace("$", "");
    }

    /** Điểm theo dấu phẩy thập phân, bỏ số 0 thừa: 0,25; 0,5; 1. */
    static String points(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    // ---------------------------------------------------------------- lời giải

    private static void solution(List<String> blocks, int k, ExamItemDto item) {
        if (!hasQuestion(item)) {
            blocks.add(label(k) + " " + NONE);
            return;
        }
        String explanation = paragraphs(item.explanation());
        switch (item.questionType()) {
            case MCQ -> {
                blocks.add(item.correctOption() != null ? "**Câu " + k + ". Chọn " + item.correctOption().name() + ".**"
                        : label(k) + " " + NONE);
                if (!explanation.isEmpty()) {
                    blocks.add(explanation);
                }
            }
            case SA -> {
                String answer = item.saAnswer() == null ? "" : item.saAnswer().strip();
                blocks.add(answer.isEmpty() ? label(k) + " " + NONE
                        : "**Câu " + k + ". Đáp án: " + inline(answer) + ".**");
                if (!explanation.isEmpty()) {
                    blocks.add(explanation);
                }
            }
            case TF -> {
                blocks.add(label(k));
                List<ItemStatementDto> list = statements(item);
                Map<StatementKey, String> split = splitByStatement(item.explanation(), list);
                for (ItemStatementDto s : list) {
                    blocks.add("**" + s.key().getDbValue() + ") " + truth(s.label()) + ".**");
                    if (split != null && !split.get(s.key()).isBlank()) {
                        blocks.add(paragraphs(split.get(s.key())));
                    }
                }
                if (split == null && !explanation.isEmpty()) {
                    blocks.add(explanation);
                }
            }
        }
    }

    private static String truth(TruthValue label) {
        return label == TruthValue.TRUE ? "Đúng" : label == TruthValue.FALSE ? "Sai" : NONE;
    }

    /**
     * Tách lời giải câu đúng/sai theo từng ý. Tách được khi mọi dòng thuộc về một ý: các ý a), b), c), d) (đúng bấy nhiêu
     * ý của câu, mỗi ý một lần, theo thứ tự) và không có dòng nào đứng trước ý đầu. Ngược lại trả null (giữ nguyên cả đoạn).
     */
    static Map<StatementKey, String> splitByStatement(String explanation, List<ItemStatementDto> statements) {
        if (explanation == null || explanation.isBlank() || statements.isEmpty()) {
            return null;
        }
        Map<StatementKey, StringBuilder> parts = new LinkedHashMap<>();
        StatementKey current = null;
        for (String raw : explanation.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher m = STATEMENT_MARK.matcher(line);
            if (m.matches()) {
                current = StatementKey.valueOf(m.group(1).toUpperCase(java.util.Locale.ROOT));
                if (parts.containsKey(current)) {
                    return null;
                }
                parts.put(current, new StringBuilder(m.group(2)));
            } else if (current == null) {
                return null;
            } else {
                StringBuilder b = parts.get(current);
                b.append(b.isEmpty() ? "" : "\n").append(line);
            }
        }
        List<StatementKey> expected = statements.stream().map(ItemStatementDto::key).toList();
        if (!new ArrayList<>(parts.keySet()).equals(expected)) {
            return null;
        }
        Map<StatementKey, String> out = new LinkedHashMap<>();
        parts.forEach((k, v) -> out.put(k, v.toString()));
        return out;
    }

    // ---------------------------------------------------------------- đầu đề (khối openxml thô)

    static final String HEADER_LEFT_1 = "PHÒNG GD&ĐT ……………";
    static final String HEADER_LEFT_2 = "TRƯỜNG THCS ……………";

    /**
     * Cột trái 3700 twips (185pt) đủ cho "TRƯỜNG THCS ……………" đậm; cột phải 6222 (311pt) đủ cho dòng "Thời gian làm bài: …,
     * không kể thời gian phát đề" (nghiêng, khoảng 295pt) nằm trọn một dòng. Bảng đầu đề không có lề ô (đo trên Word 4/11).
     */
    private static final int HEADER_LEFT_WIDTH = 3700;

    /**
     * Đầu đề: bảng 2 cột không viền (trái PHÒNG GD&ĐT, TRƯỜNG THCS, "(Đề có … trang)"; phải tên kì, năm học, môn, thời gian
     * làm bài), rồi bảng dòng dưới: "Họ và tên … Lớp …" và ô "Mã đề 101" có viền. Số trang là trường NUMPAGES.
     */
    static String headerBlock(ExamHeader header) {
        String left = headerParagraph(HEADER_LEFT_1, false, false) + headerParagraph(HEADER_LEFT_2, true, false)
                + pageCountParagraph();
        String right = headerParagraph(header.titleLine(), true, false) + headerParagraph(header.yearLine(), false, false)
                + headerParagraph(header.subjectLine(), false, false) + headerParagraph(header.durationLine(), false, true);
        int rightWidth = ExamPage.TEXT_WIDTH_TWIPS - HEADER_LEFT_WIDTH;
        int codeWidth = 2300;
        String noBorders = "<w:tblBorders><w:top w:val=\"nil\"/><w:left w:val=\"nil\"/><w:bottom w:val=\"nil\"/>"
                + "<w:right w:val=\"nil\"/><w:insideH w:val=\"nil\"/><w:insideV w:val=\"nil\"/></w:tblBorders>";
        String box = "<w:tcBorders><w:top w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"auto\"/>"
                + "<w:left w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"auto\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"auto\"/>"
                + "<w:right w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"auto\"/></w:tcBorders>";
        return "```{=openxml}\n"
                + "<w:tbl><w:tblPr><w:tblW w:w=\"" + ExamPage.TEXT_WIDTH_TWIPS + "\" w:type=\"dxa\"/>" + noBorders
                + "<w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:left w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"0\" w:type=\"dxa\"/>"
                + "</w:tblCellMar><w:tblLook w:val=\"0000\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"" + HEADER_LEFT_WIDTH + "\"/><w:gridCol w:w=\"" + rightWidth + "\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"" + HEADER_LEFT_WIDTH + "\" w:type=\"dxa\"/></w:tcPr>" + left + "</w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"" + rightWidth + "\" w:type=\"dxa\"/></w:tcPr>" + right + "</w:tc></w:tr></w:tbl>\n"
                + spacer() + "\n"
                + "<w:tbl><w:tblPr><w:tblW w:w=\"" + ExamPage.TEXT_WIDTH_TWIPS + "\" w:type=\"dxa\"/>" + noBorders
                + "<w:tblLayout w:type=\"fixed\"/><w:tblLook w:val=\"0000\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"" + (ExamPage.TEXT_WIDTH_TWIPS - codeWidth) + "\"/><w:gridCol w:w=\""
                + codeWidth + "\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"" + (ExamPage.TEXT_WIDTH_TWIPS - codeWidth) + "\" w:type=\"dxa\"/>"
                + "<w:vAlign w:val=\"center\"/></w:tcPr>"
                + "<w:p><w:pPr><w:spacing w:before=\"60\" w:after=\"60\"/></w:pPr><w:r><w:t xml:space=\"preserve\">"
                + xml(header.nameLine()) + "</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"" + codeWidth + "\" w:type=\"dxa\"/>" + box + "<w:vAlign w:val=\"center\"/></w:tcPr>"
                + headerParagraph(header.codeLine(), true, false) + "</w:tc></w:tr></w:tbl>\n"
                + spacer() + "\n"
                + "```";
    }

    private static String headerParagraph(String text, boolean bold, boolean italic) {
        return "<w:p><w:pPr><w:pStyle w:val=\"ExamHeaderCell\"/></w:pPr>" + run(text, bold, italic) + "</w:p>";
    }

    /**
     * "(Đề có … trang)" nghiêng; số trang là trường SECTIONPAGES, tức số trang của riêng phần đề (không tính trang đáp án);
     * kết quả tạm "…" cho tới khi Word cập nhật.
     */
    private static String pageCountParagraph() {
        String rPr = "<w:rPr><w:i/><w:iCs/></w:rPr>";
        return "<w:p><w:pPr><w:pStyle w:val=\"ExamHeaderCell\"/></w:pPr>" + run("(Đề có ", false, true)
                + "<w:r>" + rPr + "<w:fldChar w:fldCharType=\"begin\"/></w:r>"
                + "<w:r>" + rPr + "<w:instrText xml:space=\"preserve\"> SECTIONPAGES </w:instrText></w:r>"
                + "<w:r>" + rPr + "<w:fldChar w:fldCharType=\"separate\"/></w:r>"
                + run("…", false, true)
                + "<w:r>" + rPr + "<w:fldChar w:fldCharType=\"end\"/></w:r>"
                + run(" trang)", false, true) + "</w:p>";
    }

    // ---------------------------------------------------------------- khối openxml: đoạn, bảng

    private static String raw(String xml) {
        return "```{=openxml}\n" + xml + "\n```";
    }

    private static String div(String style, String content) {
        return "::: {custom-style=\"" + style + "\"}\n" + content + "\n:::";
    }

    /** Đoạn một run, kiểu {@code style}; {@code pageBreakBefore} cho tiêu đề phải sang trang mới. */
    private static String paragraph(String style, String text, boolean pageBreakBefore) {
        return "<w:p><w:pPr><w:pStyle w:val=\"" + style + "\"/>" + (pageBreakBefore ? "<w:pageBreakBefore/>" : "")
                + "</w:pPr>" + plainRun(text) + "</w:p>";
    }

    private static String spacer() {
        return "<w:p><w:pPr><w:pStyle w:val=\"ExamSpacer\"/></w:pPr></w:p>";
    }

    private static String plainRun(String text) {
        String t = xml(text);
        boolean edge = !text.equals(text.strip());
        return "<w:r><w:t" + (edge ? " xml:space=\"preserve\"" : "") + ">" + t + "</w:t></w:r>";
    }

    private static String run(String text, boolean bold, boolean italic) {
        String rPr = (bold ? "<w:b/><w:bCs/>" : "") + (italic ? "<w:i/><w:iCs/>" : "");
        return "<w:r>" + (rPr.isEmpty() ? "" : "<w:rPr>" + rPr + "</w:rPr>") + "<w:t xml:space=\"preserve\">" + xml(text)
                + "</w:t></w:r>";
    }

    private static final String TABLE_BORDERS = "<w:tblBorders>"
            + "<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>"
            + "<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>"
            + "<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>"
            + "<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>"
            + "<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>"
            + "<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/></w:tblBorders>";

    /** Ô bảng: {@code vMerge} 0 = không gộp, 1 = bắt đầu gộp dọc, 2 = tiếp tục. */
    private record Cell(String text, boolean bold, int vMerge) {
        static Cell of(String text, boolean bold) {
            return new Cell(text, bold, 0);
        }
    }

    /** Bảng 2 hàng "Câu | 1 | 2 …" / "Chọn | C | B …"; cột đầu rộng 1000, các cột số rộng đều theo {@code slots} cột. */
    private static String answerTable(String headLabel, List<String> numbers, String rowLabel, List<String> values,
                                      int slots) {
        int first = 1000;
        int col = (ExamPage.TEXT_WIDTH_TWIPS - first) / slots;
        List<Integer> grid = new ArrayList<>(List.of(first));
        List<Cell> head = new ArrayList<>(List.of(Cell.of(headLabel, true)));
        List<Cell> row = new ArrayList<>(List.of(Cell.of(rowLabel, true)));
        for (int i = 0; i < numbers.size(); i++) {
            grid.add(col);
            head.add(Cell.of(numbers.get(i), true));
            row.add(Cell.of(values.get(i), false));
        }
        return table(grid, List.of(head, row));
    }

    /** Bảng "Câu | Ý | Đáp án": mỗi câu một hàng cho từng ý a–d, ô Câu gộp dọc. */
    private static String trueFalseTable(List<ExamItemDto> items) {
        List<List<Cell>> rows = new ArrayList<>();
        rows.add(List.of(Cell.of("Câu", true), Cell.of("Ý", true), Cell.of("Đáp án", true)));
        for (int i = 0; i < items.size(); i++) {
            ExamItemDto item = items.get(i);
            List<ItemStatementDto> list = hasQuestion(item) ? statements(item) : List.of();
            if (list.isEmpty()) {
                rows.add(List.of(Cell.of(Integer.toString(i + 1), false), Cell.of(NONE, false), Cell.of(NONE, false)));
                continue;
            }
            for (int j = 0; j < list.size(); j++) {
                ItemStatementDto s = list.get(j);
                rows.add(List.of(j == 0 ? new Cell(Integer.toString(i + 1), false, 1) : new Cell("", false, 2),
                        Cell.of(s.key().getDbValue(), false), Cell.of(truth(s.label()), false)));
            }
        }
        return table(List.of(1200, 1200, 2400), rows);
    }

    /** Bảng có viền, canh giữa trang, bố cục cố định; hàng đầu và cột đầu do bên gọi đánh dấu đậm. */
    private static String table(List<Integer> grid, List<List<Cell>> rows) {
        int total = grid.stream().mapToInt(Integer::intValue).sum();
        StringBuilder sb = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"" + total + "\" w:type=\"dxa\"/>"
                + "<w:jc w:val=\"center\"/>" + TABLE_BORDERS + "<w:tblLayout w:type=\"fixed\"/><w:tblCellMar>"
                + "<w:left w:w=\"60\" w:type=\"dxa\"/><w:right w:w=\"60\" w:type=\"dxa\"/></w:tblCellMar>"
                + "<w:tblLook w:val=\"0000\"/></w:tblPr><w:tblGrid>");
        grid.forEach(w -> sb.append("<w:gridCol w:w=\"").append(w).append("\"/>"));
        sb.append("</w:tblGrid>");
        for (List<Cell> row : rows) {
            sb.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>");
            for (int c = 0; c < row.size(); c++) {
                Cell cell = row.get(c);
                sb.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(grid.get(c)).append("\" w:type=\"dxa\"/>")
                        .append(cell.vMerge() == 1 ? "<w:vMerge w:val=\"restart\"/>" : cell.vMerge() == 2 ? "<w:vMerge/>" : "")
                        .append("<w:vAlign w:val=\"center\"/></w:tcPr><w:p><w:pPr><w:pStyle w:val=\"ExamTableCell\"/></w:pPr>")
                        .append(cell.text().isEmpty() ? "" : run(cell.text(), cell.bold(), false)).append("</w:p></w:tc>");
            }
            sb.append("</w:tr>");
        }
        return sb.append("</w:tbl>").toString();
    }

    private static String xml(String text) {
        // F-05: gộp xuống dòng thành khoảng trắng; tên đề nhiều dòng có dòng "```" sẽ đóng sớm khối openxml của đầu đề.
        return text.replaceAll("\\s*\\R\\s*", " ").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ---------------------------------------------------------------- chữ

    /** Mỗi dòng không trống thành một đoạn. */
    static String paragraphs(String text) {
        if (text == null) {
            return "";
        }
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(l -> !l.isEmpty())
                .map(ExamMarkdownBuilder::inline).collect(Collectors.joining("\n\n"));
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").strip();
    }

    /** Thêm dấu chấm cuối nếu chưa có dấu kết câu (. ? ! …), kể cả khi kết thúc bằng công thức. Chuỗi rỗng giữ nguyên. */
    static String endWithPeriod(String text) {
        String s = text == null ? "" : text.strip();
        if (s.isEmpty() || ".?!…".indexOf(s.charAt(s.length() - 1)) >= 0) {
            return s;
        }
        return s + ".";
    }

    /**
     * Cắt chuỗi nguồn thành chữ thường và công thức: {@code $...$} (nội dung khác rỗng) là công thức, xuống dòng trong công
     * thức thành khoảng trắng; {@code \$} và dấu $ lẻ là chữ "$". Hai ký tự vùng riêng của mã giữ chỗ tab bị loại.
     */
    static List<Piece> pieces(String source) {
        String text = ExamTabs.strip(source);
        List<Piece> out = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n && text.charAt(i + 1) == '$') {
                plain.append('$');
                i += 2;
                continue;
            }
            if (c == '$') {
                int close = closingDollar(text, i + 1);
                String inner = close < 0 ? "" : text.substring(i + 1, close).strip();
                if (inner.isEmpty()) {
                    plain.append('$');
                    i++;
                } else {
                    if (plain.length() > 0) {
                        out.add(new Piece(false, plain.toString()));
                        plain.setLength(0);
                    }
                    // F-05: xuống dòng trong công thức thành khoảng trắng; nếu không, một dòng của "công thức" có thể bắt đầu
                    // bằng ```{=openxml} hoặc "<" và thành khối raw thật khi pandoc đọc Markdown.
                    out.add(new Piece(true, inner.replaceAll("\\s*\\R\\s*", " ")));
                    i = close + 1;
                }
                continue;
            }
            plain.append(c);
            i++;
        }
        if (plain.length() > 0) {
            out.add(new Piece(false, plain.toString()));
        }
        return out;
    }

    /** Một đoạn văn: giữ công thức $...$, thoát ký tự Markdown ở ngoài công thức. */
    static String inline(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Piece piece : pieces(text)) {
            if (piece.math()) {
                out.append('$').append(piece.text()).append('$');
                continue;
            }
            for (char c : piece.text().toCharArray()) {
                if (c == '$' || ESCAPED.indexOf(c) >= 0) {
                    out.append('\\');
                }
                out.append(c);
            }
        }
        String s = out.toString();
        Matcher list = LIST_START.matcher(s);
        if (list.find()) {
            return list.group(1) + "\\" + list.group(2) + s.substring(list.end());
        }
        return BULLET_START.matcher(s).find() ? "\\" + s : s;
    }

    private static int closingDollar(String text, int from) {
        for (int j = from; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c == '\\' && j + 1 < text.length() && text.charAt(j + 1) == '$') {
                j++;
            } else if (c == '$') {
                return j;
            }
        }
        return -1;
    }
}
