package com.datn.mcq.exam.export;

import com.datn.mcq.common.util.AlignmentAmpersand;
import com.datn.mcq.common.util.DegreeSymbols;
import com.datn.mcq.common.util.LiteralNewlines;
import com.datn.mcq.common.util.MissingLatexBackslash;
import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.entity.ExamSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Markdown (pandoc) của đề ở chế độ giáo viên, để {@link PandocRunner} chuyển sang Word, theo mẫu đề kiểm tra của một
 * trường THCS (4/11):
 * <ul>
 *   <li>đầu đề: bảng 2 cột không viền rồi bảng có viền "Mã đề | Điểm bài thi | Mã phách" ({@link ExamHeader});</li>
 *   <li>mỗi phần có tiêu đề đậm in hoa kèm tổng điểm ("PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN (3,0 điểm)") và một dòng hướng
 *       dẫn nghiêng; phần không có câu thì bỏ;</li>
 *   <li>"Câu k." đậm, không thụt lề treo; mcq xếp cột bằng tab ({@link OptionLayout}); ý đúng/sai "a)" "b)"... đi tới
 *       mốc tab 283 như phương án; cuối đề dòng "------ HẾT ------" đậm nghiêng;</li>
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
            "I", "PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN",
            "II", "PHẦN II. TRẮC NGHIỆM ĐÚNG/SAI",
            "III", "PHẦN III. TRẮC NGHIỆM TRẢ LỜI NGẮN");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
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

    /** Ngày xuất là ngày hiện tại ở Việt Nam (năm học và năm trong đầu đề lấy từ đây). */
    public static String build(ExamViewDto exam, ExamHeader header, ExamScoringProperties scoring) {
        return build(exam, header, scoring, LocalDate.now(VIETNAM));
    }

    /** @param today ngày xuất; cho phép cố định ngày khi kiểm thử */
    public static String build(ExamViewDto examView, ExamHeader header, ExamScoringProperties scoring, LocalDate today) {
        // Câu đã lưu từ trước có thể còn "\n" chữ trong công thức, và độ viết ^{\circ} thành chỉ số trên cỡ nhỏ: sửa trong file
        // xuất (không sửa DB), cho cả Equation và MathType.
        ExamViewDto exam = withoutLiteralNewlines(examView);
        List<String> blocks = new ArrayList<>();
        blocks.add(headerBlock(header, today));
        List<PartDto> parts = exam.parts().stream().filter(p -> !p.items().isEmpty()).toList();
        for (PartDto part : parts) {
            blocks.add(raw(paragraph("ExamPartHeading", partHeading(part, scoring), false)));
            blocks.add(raw("<w:p><w:pPr><w:pStyle w:val=\"ExamInstruction\"/></w:pPr>" + run(instruction(part), false, true)
                    + "</w:p>"));
            int k = 1;
            for (ExamItemDto item : part.items()) {
                question(blocks, k++, item);
            }
        }
        // Dòng HẾT (đậm nghiêng) kết thúc section đề (sectPr trong pPr của đoạn này); đáp án là section mới, sang trang mới,
        // số trang từ 1.
        blocks.add(raw("<w:p><w:pPr><w:pStyle w:val=\"ExamCenter\"/>"
                + ExamPage.sectionProperties(ExamPage.EXAM_FOOTER_REL_ID, false) + "</w:pPr>" + run(END_OF_EXAM, true, true)
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

    /**
     * Bản sao của đề với "\n" chữ đã chuẩn hóa ({@link LiteralNewlines}), lệnh LaTeX mất "\" và dấu căn lề "&" thừa đã sửa,
     * rồi ký hiệu độ trong công thức về "°" ({@link DegreeSymbols}) ở câu dẫn, phương án, ý đúng/sai và lời giải (xem
     * {@link #cleanText}).
     */
    static ExamViewDto withoutLiteralNewlines(ExamViewDto exam) {
        List<PartDto> parts = exam.parts().stream()
                .map(p -> new PartDto(p.part(), p.required(), p.ready(),
                        p.items().stream().map(ExamMarkdownBuilder::withoutLiteralNewlines).toList()))
                .toList();
        return new ExamViewDto(exam.examId(), exam.examCode(), exam.title(), exam.mode(), exam.status(), exam.matrixId(),
                exam.createdAt(), exam.buildStatus(), exam.needsReviewCount(), parts, exam.kind());
    }

    private static ExamItemDto withoutLiteralNewlines(ExamItemDto i) {
        List<ItemStatementDto> statements = i.statements() == null ? null : i.statements().stream()
                .map(s -> new ItemStatementDto(s.key(), cleanText(s.text()), s.label(), s.difficulty()))
                .toList();
        return new ExamItemDto(i.slotId(), i.cellId(), i.slotNo(), i.part(), i.topic(), i.questionType(), i.state(),
                i.source(), i.bankItemId(), i.questionId(), cleanText(i.questionText()),
                cleanText(i.optionA()), cleanText(i.optionB()),
                cleanText(i.optionC()), cleanText(i.optionD()), i.correctOption(),
                i.saAnswer(), statements, cleanText(i.explanation()), i.difficulty(),
                i.needsReview(), i.reviewReasons(), i.qcState(), i.edited(), i.originalQuestionId(), i.failureReason());
    }

    /**
     * "\n" và "\\" chữ trước (có thể làm lộ công thức), rồi lệnh LaTeX mất "\" ({@link MissingLatexBackslash}: "70^circ" thành
     * "70^\circ"), dấu căn lề "&" ngoài môi trường \begin ({@link AlignmentAmpersand}: "$&= 5x$" thành "$= 5x$"), cuối cùng
     * ký hiệu độ ("70°"); không có gì để đổi thì trả đúng đối tượng ban đầu.
     */
    private static String cleanText(String text) {
        String s = MissingLatexBackslash.repair(LiteralNewlines.repair(text).text()).text();
        return DegreeSymbols.normalize(AlignmentAmpersand.repair(s).text());
    }

    // ---------------------------------------------------------------- dòng hướng dẫn, câu hỏi

    /** "PHẦN I. TRẮC NGHIỆM NHIỀU LỰA CHỌN (3,0 điểm)": tổng điểm = điểm một câu × số câu (phần lạ không ghi điểm). */
    private static String partHeading(PartDto part, ExamScoringProperties scoring) {
        int n = part.items().size();
        BigDecimal perQuestion = switch (part.part()) {
            case "I" -> scoring.mcqPerQuestion();
            case "II" -> scoring.tfScore(4);
            case "III" -> scoring.saPerQuestion();
            default -> null;
        };
        String name = PART_NAMES.getOrDefault(part.part(), "PHẦN " + part.part());
        return perQuestion == null ? name : name + " (" + totalPoints(perQuestion.multiply(BigDecimal.valueOf(n)))
                + " điểm)";
    }

    /** Dòng hướng dẫn nghiêng dưới tiêu đề phần. */
    private static String instruction(PartDto part) {
        int n = part.items().size();
        return switch (part.part()) {
            case "I" -> "Học sinh chọn một đáp án đúng duy nhất từ câu 1 đến câu " + n + ".";
            case "II" -> "Học sinh trả lời Đúng (Đ) hoặc Sai (S) cho từng ý a), b), c), d) trong mỗi câu.";
            case "III" -> "Học sinh viết câu trả lời ngắn gọn (kết quả số) từ câu 1 đến câu " + n + ".";
            default -> "Học sinh trả lời từ câu 1 đến câu " + n + ".";
        };
    }

    private static String label(int k) {
        return "**Câu " + k + ".**";
    }

    private static boolean hasQuestion(ExamItemDto item) {
        return item.questionText() != null;
    }

    private static void question(List<String> blocks, int k, ExamItemDto item) {
        if (!hasQuestion(item)) {
            blocks.add(div("ExamQuestion", label(k) + " "
                    + inline(ExamSlot.FAILED.equals(item.state()) ? MISSING_FAILED : MISSING_GENERATING)));
            return;
        }
        List<String> stem = stemParagraphs(item.questionText());
        blocks.add(div("ExamQuestion", label(k) + (stem.isEmpty() ? "" : " " + stem.get(0))));
        if (stem.size() > 1) {
            blocks.add(div("ExamQuestionCont", String.join("\n\n", stem.subList(1, stem.size()))));
        }
        switch (item.questionType()) {
            case MCQ -> blocks.add(div("ExamOption", mcqOptions(item)));
            case TF -> {
                List<String> lines = new ArrayList<>();
                for (ItemStatementDto s : statements(item)) {
                    lines.add(ExamTabs.token(ExamPage.OPTION_LABEL_START_TWIPS) + "**" + s.key().getDbValue() + ")** "
                            + inline(endWithPeriod(oneLine(s.text()))));
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
            // mẫu: mỗi dòng phương án mở đầu bằng tab tới mốc 283 rồi mới tới nhãn "A."
            StringBuilder line = new StringBuilder(ExamTabs.token(ExamPage.OPTION_LABEL_START_TWIPS));
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

    /** Tổng điểm của phần: dấu phẩy thập phân, ít nhất một chữ số thập phân: 3,0; 0,5; 0,75. */
    static String totalPoints(BigDecimal value) {
        BigDecimal v = value.stripTrailingZeros();
        if (v.scale() < 1) {
            v = v.setScale(1);
        }
        return v.toPlainString().replace('.', ',');
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

    static final String HEADER_LEFT_1 = "UBND PHƯỜNG ……………";
    static final String HEADER_LEFT_2 = "TRƯỜNG THCS ……………";

    /** Cột trái / phải của bảng đầu đề, tỉ lệ 4194 : 6011 như mẫu (cộng lại đúng bề rộng chữ). */
    static final int HEADER_LEFT_WIDTH = 4194;
    static final int HEADER_RIGHT_WIDTH = 6011;
    /** Hàng 2 của bảng "Mã đề | Điểm bài thi | Mã phách" cao 4 dòng × 360 twips như mẫu. */
    static final int CODE_ROW_HEIGHT = 1440;
    private static final List<String> BOX_TITLES = List.of("Mã đề", "Điểm bài thi", "Mã phách");

    /**
     * Đầu đề theo mẫu của trường: bảng 2 cột không viền (trái: UBND PHƯỜNG, TRƯỜNG THCS đậm căn giữa; phải căn giữa: dòng
     * "Thứ… ngày… tháng… năm <năm>", tên kì, năm học, môn và lớp, thời gian làm bài), rồi bảng có viền 3 cột bằng nhau,
     * toàn chiều rộng: hàng tiêu đề "Mã đề | Điểm bài thi | Mã phách", hàng 2 cao như mẫu ghi mã đề ở ô 1.
     */
    static String headerBlock(ExamHeader header, LocalDate today) {
        String left = headerParagraph(HEADER_LEFT_1, true, false) + headerParagraph(HEADER_LEFT_2, true, false);
        String right = headerParagraph(header.dateLine(today), false, true)
                + headerParagraph(header.titleLine(), true, false)
                + headerParagraph(header.yearLine(today), true, false)
                + headerParagraph(header.subjectLine(), true, false)
                + headerParagraph(header.durationLine(), false, true);
        String noBorders = "<w:tblBorders><w:top w:val=\"nil\"/><w:left w:val=\"nil\"/><w:bottom w:val=\"nil\"/>"
                + "<w:right w:val=\"nil\"/><w:insideH w:val=\"nil\"/><w:insideV w:val=\"nil\"/></w:tblBorders>";
        int third = (ExamPage.TEXT_WIDTH_TWIPS + 2) / 3;
        int[] boxGrid = {third, third, ExamPage.TEXT_WIDTH_TWIPS - 2 * third};
        StringBuilder titleRow = new StringBuilder("<w:tr>");
        StringBuilder codeRow = new StringBuilder("<w:tr><w:trPr><w:trHeight w:val=\"" + CODE_ROW_HEIGHT + "\"/></w:trPr>");
        for (int c = 0; c < 3; c++) {
            titleRow.append(boxCell(boxGrid[c], boxParagraph(BOX_TITLES.get(c), true)));
            codeRow.append(boxCell(boxGrid[c], boxParagraph(c == 0 ? header.code() : "", c == 0)));
        }
        return "```{=openxml}\n"
                + "<w:tbl><w:tblPr><w:tblW w:w=\"" + ExamPage.TEXT_WIDTH_TWIPS + "\" w:type=\"dxa\"/>" + noBorders
                + "<w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:left w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"0\" w:type=\"dxa\"/>"
                + "</w:tblCellMar><w:tblLook w:val=\"0000\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"" + HEADER_LEFT_WIDTH + "\"/><w:gridCol w:w=\"" + HEADER_RIGHT_WIDTH + "\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"" + HEADER_LEFT_WIDTH + "\" w:type=\"dxa\"/></w:tcPr>" + left + "</w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"" + HEADER_RIGHT_WIDTH + "\" w:type=\"dxa\"/></w:tcPr>" + right
                + "</w:tc></w:tr></w:tbl>\n"
                + gap() + "\n"
                + "<w:tbl><w:tblPr><w:tblW w:w=\"" + ExamPage.TEXT_WIDTH_TWIPS + "\" w:type=\"dxa\"/>" + TABLE_BORDERS
                + "<w:tblLayout w:type=\"fixed\"/><w:tblLook w:val=\"0000\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"" + boxGrid[0] + "\"/><w:gridCol w:w=\"" + boxGrid[1]
                + "\"/><w:gridCol w:w=\"" + boxGrid[2] + "\"/></w:tblGrid>"
                + titleRow + "</w:tr>" + codeRow + "</w:tr></w:tbl>\n"
                + spacer() + "\n"
                + "```";
    }

    private static String headerParagraph(String text, boolean bold, boolean italic) {
        return "<w:p><w:pPr><w:pStyle w:val=\"ExamHeaderCell\"/></w:pPr>" + run(text, bold, italic) + "</w:p>";
    }

    private static String boxCell(int width, String paragraph) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/></w:tcPr>" + paragraph
                + "</w:tc>";
    }

    /** Đoạn trong bảng Mã đề / Điểm / Mã phách: căn giữa, dòng cao 360 twips như mẫu. */
    private static String boxParagraph(String text, boolean bold) {
        return "<w:p><w:pPr><w:pStyle w:val=\"ExamBoxCell\"/></w:pPr>" + (text.isEmpty() ? "" : run(text, bold, false))
                + "</w:p>";
    }

    /** Khoảng trống giữa hai bảng đầu đề (Word gộp hai bảng kề nhau nếu giữa không có đoạn nào). */
    private static String gap() {
        return "<w:p><w:pPr><w:pStyle w:val=\"ExamGap\"/></w:pPr></w:p>";
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
