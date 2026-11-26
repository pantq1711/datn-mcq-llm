package com.datn.mcq.exam.export;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.service.ExamScore;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.datn.mcq.exam.export.ExamMarkdownBuilder.TABLE_BORDERS;
import static com.datn.mcq.exam.export.ExamMarkdownBuilder.gap;
import static com.datn.mcq.exam.export.ExamMarkdownBuilder.headerParagraph;
import static com.datn.mcq.exam.export.ExamMarkdownBuilder.raw;
import static com.datn.mcq.exam.export.ExamMarkdownBuilder.run;
import static com.datn.mcq.exam.export.ExamMarkdownBuilder.spacer;

/**
 * Bản đặc tả đề kiểm tra (18/11, tham số {@code content=spec}) theo mẫu Phụ lục 2 Công văn 7991/BGDĐT-GDTrH ngày 17/12/2024,
 * quyết định D1–D7 của chủ dự án; Markdown (khối openxml thô) để {@link PandocRunner} chuyển sang Word, một section A4 ngang.
 * <ul>
 *   <li>đầu đề như file đề nhưng không có bảng "Mã đề | Điểm bài thi | Mã phách" và dòng ngày (D7), rồi tiêu đề
 *       {@value #TITLE};</li>
 *   <li>bảng: TT | Chủ đề/Chương | Nội dung/đơn vị kiến thức | Yêu cầu cần đạt | "Số câu hỏi ở các mức độ đánh giá" gồm TNKQ
 *       (Nhiều lựa chọn, "Đúng – Sai", Trả lời ngắn) và Tự luận, mỗi dạng Biết / Hiểu / Vận dụng. Tiêu đề bảng lặp lại ở mỗi
 *       trang. 3 cột Tự luận để trống (D3);</li>
 *   <li>mỗi hàng là một (chủ đề, nội dung) của ma trận, theo lần xuất hiện đầu (thứ tự ô); ô chủ đề gộp dọc, TT đánh theo chủ
 *       đề; ô yêu cầu cần đạt liệt kê "- Biết: …" / "- Hiểu: …" / "- Vận dụng: …" cho các mức hàng có câu, câu trùng hệt nhau ghi
 *       một lần (D4);</li>
 *   <li>ô số câu ghi "số câu (C…)": câu đánh lại từ 1 mỗi phần đúng như file đề (ô theo vị trí, rồi số chỗ), 3 câu liên tiếp
 *       trở lên ghi dải "C1–C4" (D2); câu Đúng – Sai đếm theo ý, mỗi ý ở mức của ý trong ma trận ("2 (C1a, C1b)", D1). Mức lấy
 *       từ ô ma trận (câu ngân hàng được chọn theo đúng mức của ô, Sửa câu không đổi mức). Chỗ chưa có câu (đang soạn, hỏng)
 *       vẫn được đếm, như file đề vẫn in "Câu k.";</li>
 *   <li>chân bảng: Tổng số câu theo cột; Tổng số điểm và Tỉ lệ % theo dạng (điểm một câu × số chỗ, {@link ExamScore});
 *       dưới bảng là ghi chú cách đọc và, nếu tổng dưới 10, câu "Phần tự luận (nếu có) do giáo viên bổ sung.".</li>
 * </ul>
 * Chữ (cả công thức $...$ trong yêu cầu cần đạt, nếu có) được ghi nguyên dạng chữ, đã thoát XML.
 */
public final class ExamSpecBuilder {

    static final String TITLE = "BẢN ĐẶC TẢ ĐỀ KIỂM TRA ĐỊNH KÌ";
    static final String NOTE_REFS = "Ghi chú: mỗi ô ghi số câu, trong ngoặc là câu số mấy trong phần tương ứng của đề: cột"
            + " Nhiều lựa chọn là Phần I, cột “Đúng – Sai” là Phần II, cột Trả lời ngắn là Phần III (ví dụ C3 ở cột Nhiều"
            + " lựa chọn là Phần I – Câu 3).";
    static final String NOTE_TF = "Câu “Đúng – Sai” đếm theo ý: mỗi câu có 4 ý a), b), c), d), mỗi ý tính ở mức của ý đó"
            + " (ví dụ C1a là ý a) của Phần II – Câu 1).";
    static final String NONE = "—";

    /** Dạng câu theo thứ tự cột: 3 dạng TNKQ rồi Tự luận (không có dữ liệu). */
    static final List<String> TYPE_NAMES = List.of("Nhiều lựa chọn", "“Đúng – Sai”", "Trả lời ngắn", "Tự luận");
    static final List<String> LEVEL_NAMES = List.of("Biết", "Hiểu", "Vận dụng");
    /** 4 dạng × 3 mức. */
    static final int COLUMNS = 12;
    private static final List<String> PARTS = List.of("I", "II", "III");
    private static final BigDecimal FULL_SCORE = BigDecimal.TEN;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Bề rộng cột (twips), cộng lại đúng bề rộng chữ của trang ngang. */
    static final int TT_WIDTH = 450;
    static final int TOPIC_WIDTH = 1500;
    static final int CONTENT_WIDTH = 1900;
    static final int LEVEL_WIDTH = 620;
    static final int OUTCOME_WIDTH = ExamPage.LANDSCAPE_TEXT_WIDTH_TWIPS - TT_WIDTH - TOPIC_WIDTH - CONTENT_WIDTH
            - COLUMNS * LEVEL_WIDTH;

    private ExamSpecBuilder() {}

    /**
     * Một ô ma trận, chỉ các trường bản đặc tả cần.
     *
     * @param outcomes        yêu cầu cần đạt theo mức (mức không có thì không có khóa)
     * @param difficulty      mức của câu mcq/sa; null với tf
     * @param statementLevels 4 mức của ý a–d (tf); null với mcq/sa
     */
    public record SpecCell(Integer id, int position, String part, QuestionType type, String topic, String content,
                           Map<Difficulty, String> outcomes, Difficulty difficulty, List<Difficulty> statementLevels) {

        public SpecCell {
            outcomes = outcomes == null ? Map.of() : Map.copyOf(outcomes);
            statementLevels = statementLevels == null ? null : List.copyOf(statementLevels);
        }

        public static SpecCell of(ExamMatrixCell c) {
            Map<Difficulty, String> outcomes = new EnumMap<>(Difficulty.class);
            for (Difficulty level : Difficulty.values()) {
                if (c.learningOutcome(level) != null) {
                    outcomes.put(level, c.learningOutcome(level));
                }
            }
            return new SpecCell(c.getId(), c.getPosition(), c.getPart(), c.getQuestionType(), c.getTopic(),
                    c.getContent(), outcomes, c.getDifficulty(), MatrixService.statementLevels(c));
        }

        /** Các mức ô dùng: 4 mức của ý (tf) hoặc mức của câu; thiếu thì rỗng. */
        List<Difficulty> levels() {
            if (type == QuestionType.TF) {
                return statementLevels == null ? List.of() : statementLevels;
            }
            return difficulty == null ? List.of() : List.of(difficulty);
        }
    }

    /**
     * Một hàng của bảng.
     *
     * @param tt           số thứ tự chủ đề
     * @param firstOfTopic hàng đầu của chủ đề (ô TT và chủ đề bắt đầu gộp dọc)
     * @param topicRowSpan số hàng của chủ đề
     * @param content      nội dung; trống là ""
     * @param outcomes     các dòng "- Biết: …"
     * @param counts       {@value #COLUMNS} ô số câu (dạng × mức), ô không có câu là ""
     */
    record SpecRow(int tt, boolean firstOfTopic, int topicRowSpan, String topic, String content, List<String> outcomes,
                   List<String> counts) {}

    /**
     * @param totalCounts {@value #COLUMNS} ô tổng số câu, 0 là ""
     * @param points      tổng điểm 4 dạng ("3,0"); dạng không có câu và Tự luận là ""
     * @param percents    tỉ lệ % 4 dạng ("30"); như trên
     * @param essayNote   tổng điểm phần trắc nghiệm dưới 10
     */
    record SpecTable(List<SpecRow> rows, List<String> totalCounts, List<String> points, List<String> percents,
                     boolean essayNote) {}

    /** Câu số {@code number} của phần; {@code statement} là ý a–d của câu Đúng – Sai, null với dạng khác. */
    private record Ref(int number, Character statement) {}

    private static final class RowAcc {
        final String topic;
        final String content;
        final List<SpecCell> cells = new ArrayList<>();
        final List<List<Ref>> refs = new ArrayList<>();

        RowAcc(String topic, String content) {
            this.topic = topic;
            this.content = content;
            for (int i = 0; i < COLUMNS; i++) {
                refs.add(new ArrayList<>());
            }
        }
    }

    /** Cột của dạng {@code type} và mức {@code level}: dạng × 3 + mức. */
    static int column(QuestionType type, Difficulty level) {
        int t = switch (type) {
            case MCQ -> 0;
            case TF -> 1;
            case SA -> 2;
        };
        int l = switch (level) {
            case EASY -> 0;
            case MEDIUM -> 1;
            case HARD -> 2;
        };
        return t * 3 + l;
    }

    static SpecTable table(ExamViewDto view, List<SpecCell> cells, ExamScoringProperties scoring) {
        List<SpecCell> ordered = cells.stream().sorted(Comparator.comparingInt(SpecCell::position)).toList();
        Map<String, Map<String, RowAcc>> byTopic = new LinkedHashMap<>();
        Map<Integer, RowAcc> rowOfCell = new HashMap<>();
        Map<Integer, SpecCell> cellById = new HashMap<>();
        for (SpecCell c : ordered) {
            String topic = c.topic() == null ? "" : c.topic().strip();
            String content = c.content() == null ? "" : c.content().strip();
            RowAcc row = byTopic.computeIfAbsent(topic, k -> new LinkedHashMap<>())
                    .computeIfAbsent(content, k -> new RowAcc(topic, content));
            row.cells.add(c);
            rowOfCell.put(c.id(), row);
            cellById.put(c.id(), c);
        }

        int[] totals = new int[COLUMNS];
        for (PartDto part : view.parts()) {
            int k = 0;
            for (ExamItemDto item : part.items()) {
                k++;
                SpecCell cell = cellById.get(item.cellId());
                if (cell == null) {
                    continue;
                }
                RowAcc row = rowOfCell.get(cell.id());
                List<Difficulty> levels = cell.levels();
                for (int i = 0; i < levels.size(); i++) {
                    if (levels.get(i) == null) {
                        continue;
                    }
                    int col = column(cell.type(), levels.get(i));
                    row.refs.get(col).add(new Ref(k, cell.type() == QuestionType.TF ? (char) ('a' + i) : null));
                    totals[col]++;
                }
            }
        }

        List<SpecRow> rows = new ArrayList<>();
        int tt = 0;
        for (Map<String, RowAcc> topicRows : byTopic.values()) {
            tt++;
            boolean first = true;
            for (RowAcc row : topicRows.values()) {
                List<String> counts = new ArrayList<>();
                for (List<Ref> refs : row.refs) {
                    counts.add(refs.isEmpty() ? "" : refs.size() + " (" + refsText(refs) + ")");
                }
                rows.add(new SpecRow(tt, first, topicRows.size(), row.topic, row.content, outcomes(row), counts));
                first = false;
            }
        }

        List<String> totalCounts = new ArrayList<>();
        for (int total : totals) {
            totalCounts.add(total == 0 ? "" : Integer.toString(total));
        }
        List<String> points = new ArrayList<>();
        List<String> percents = new ArrayList<>();
        for (String partName : PARTS) {
            int n = view.parts().stream().filter(p -> partName.equals(p.part())).mapToInt(p -> p.items().size()).sum();
            BigDecimal each = ExamScore.perQuestion(partName, scoring);
            if (n == 0 || each == null) {
                points.add("");
                percents.add("");
                continue;
            }
            BigDecimal sum = each.multiply(BigDecimal.valueOf(n));
            points.add(ExamMarkdownBuilder.totalPoints(sum));
            percents.add(ExamMarkdownBuilder.points(sum.multiply(BigDecimal.TEN)));
        }
        points.add("");
        percents.add("");
        boolean essayNote = ExamScore.total(view.parts(), scoring).compareTo(FULL_SCORE) < 0;
        return new SpecTable(rows, totalCounts, points, percents, essayNote);
    }

    /** "- Biết: …" cho từng mức hàng dùng, theo Biết, Hiểu, Vận dụng; câu trùng hệt nhau một lần; thiếu thì "—". */
    private static List<String> outcomes(RowAcc row) {
        List<String> out = new ArrayList<>();
        Difficulty[] levels = Difficulty.values();
        for (int l = 0; l < levels.length; l++) {
            Difficulty level = levels[l];
            Set<String> texts = new LinkedHashSet<>();
            boolean used = false;
            for (SpecCell c : row.cells) {
                if (!c.levels().contains(level)) {
                    continue;
                }
                used = true;
                String text = c.outcomes().get(level);
                if (text != null && !text.isBlank()) {
                    texts.add(text.strip());
                }
            }
            if (!used) {
                continue;
            }
            if (texts.isEmpty()) {
                texts.add(NONE);
            }
            for (String text : texts) {
                out.add("- " + LEVEL_NAMES.get(l) + ": " + text);
            }
        }
        return out;
    }

    /** "C1–C4, C6" (3 câu liên tiếp trở lên thành dải); ý Đúng – Sai "C1a, C1b" không gộp. */
    private static String refsText(List<Ref> refs) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < refs.size()) {
            Ref r = refs.get(i);
            if (r.statement() != null) {
                parts.add("C" + r.number() + r.statement());
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < refs.size() && refs.get(j + 1).statement() == null
                    && refs.get(j + 1).number() == refs.get(j).number() + 1) {
                j++;
            }
            if (j - i >= 2) {
                parts.add("C" + r.number() + "–C" + refs.get(j).number());
            } else {
                for (int m = i; m <= j; m++) {
                    parts.add("C" + refs.get(m).number());
                }
            }
            i = j + 1;
        }
        return String.join(", ", parts);
    }

    // ---------------------------------------------------------------- Markdown

    /** Ngày xuất là ngày hiện tại ở Việt Nam (năm học trong đầu đề lấy từ đây). */
    public static String build(ExamViewDto view, List<SpecCell> cells, ExamHeader header, ExamScoringProperties scoring) {
        return build(view, cells, header, scoring, LocalDate.now(VIETNAM));
    }

    /** @param today ngày xuất; cho phép cố định ngày khi kiểm thử */
    public static String build(ExamViewDto view, List<SpecCell> cells, ExamHeader header, ExamScoringProperties scoring,
                               LocalDate today) {
        SpecTable t = table(view, cells, scoring);
        List<String> blocks = new ArrayList<>();
        blocks.add(raw(headerBlock(header, today)));
        blocks.add(raw("<w:p><w:pPr><w:pStyle w:val=\"ExamCenter\"/></w:pPr>" + run(TITLE, true, false) + "</w:p>"));
        blocks.add(raw(tableXml(t)));
        List<String> notes = new ArrayList<>(List.of(NOTE_REFS, NOTE_TF));
        if (t.essayNote()) {
            notes.add(ExamMarkdownBuilder.ESSAY_NOTE);
        }
        for (String note : notes) {
            blocks.add(raw("<w:p><w:pPr><w:pStyle w:val=\"ExamNote\"/></w:pPr>" + run(note, false, true) + "</w:p>"));
        }
        return String.join("\n\n", blocks) + "\n";
    }

    /**
     * Bảng 2 cột không viền như đầu đề của file đề (trái: UBND PHƯỜNG, TRƯỜNG THCS; phải: tên kì, năm học, môn và lớp, thời
     * gian), giãn theo bề rộng trang ngang với cùng tỉ lệ cột; không có dòng ngày và bảng mã đề (D7).
     */
    static String headerBlock(ExamHeader header, LocalDate today) {
        int width = ExamPage.LANDSCAPE_TEXT_WIDTH_TWIPS;
        int left = width * ExamMarkdownBuilder.HEADER_LEFT_WIDTH
                / (ExamMarkdownBuilder.HEADER_LEFT_WIDTH + ExamMarkdownBuilder.HEADER_RIGHT_WIDTH);
        int right = width - left;
        String leftText = headerParagraph(ExamMarkdownBuilder.HEADER_LEFT_1, true, false)
                + headerParagraph(ExamMarkdownBuilder.HEADER_LEFT_2, true, false);
        String rightText = headerParagraph(header.titleLine(), true, false)
                + headerParagraph(header.yearLine(today), true, false)
                + headerParagraph(header.subjectLine(), true, false)
                + headerParagraph(header.durationLine(), false, true);
        String noBorders = "<w:tblBorders><w:top w:val=\"nil\"/><w:left w:val=\"nil\"/><w:bottom w:val=\"nil\"/>"
                + "<w:right w:val=\"nil\"/><w:insideH w:val=\"nil\"/><w:insideV w:val=\"nil\"/></w:tblBorders>";
        return "<w:tbl><w:tblPr><w:tblW w:w=\"" + width + "\" w:type=\"dxa\"/>" + noBorders
                + "<w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:left w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"0\" w:type=\"dxa\"/>"
                + "</w:tblCellMar><w:tblLook w:val=\"0000\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"" + left + "\"/><w:gridCol w:w=\"" + right + "\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"" + left + "\" w:type=\"dxa\"/></w:tcPr>" + leftText + "</w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"" + right + "\" w:type=\"dxa\"/></w:tcPr>" + rightText
                + "</w:tc></w:tr></w:tbl>\n" + gap() + "\n" + spacer();
    }

    // ---------------------------------------------------------------- bảng openxml

    /** Ô gộp dọc: 0 không gộp, 1 bắt đầu, 2 tiếp tục. */
    private static final int NO_MERGE = 0;
    private static final int MERGE_START = 1;
    private static final int MERGE_CONTINUE = 2;

    /** Đoạn trong ô: kiểu ExamSpecCell (giữa) hoặc ExamSpecText (trái); {@code jc} "right" để căn phải; chữ rỗng thì đoạn trống. */
    private static String p(String style, String jc, String text, boolean bold, boolean italic) {
        return "<w:p><w:pPr><w:pStyle w:val=\"" + style + "\"/>" + (jc == null ? "" : "<w:jc w:val=\"" + jc + "\"/>")
                + "</w:pPr>" + (text == null || text.isEmpty() ? "" : run(text, bold, italic)) + "</w:p>";
    }

    private static String center(String text, boolean bold, boolean italic) {
        return p("ExamSpecCell", null, text, bold, italic);
    }

    private static String left(String text, boolean bold) {
        return p("ExamSpecText", null, text, bold, false);
    }

    private static String tc(int width, int span, int vMerge, String paragraphs) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/>"
                + (span > 1 ? "<w:gridSpan w:val=\"" + span + "\"/>" : "")
                + (vMerge == MERGE_START ? "<w:vMerge w:val=\"restart\"/>" : vMerge == MERGE_CONTINUE ? "<w:vMerge/>" : "")
                + "<w:vAlign w:val=\"center\"/></w:tcPr>" + paragraphs + "</w:tc>";
    }

    /** Ô tiếp tục gộp dọc: Word cần một đoạn (trống) trong mỗi ô. */
    private static String continued(int width, int span) {
        return tc(width, span, MERGE_CONTINUE, center("", false, false));
    }

    private static String tr(boolean header, String cells) {
        return "<w:tr><w:trPr><w:cantSplit/>" + (header ? "<w:tblHeader/>" : "") + "</w:trPr>" + cells + "</w:tr>";
    }

    static String tableXml(SpecTable t) {
        int levelsWidth = COLUMNS * LEVEL_WIDTH;
        int typeWidth = 3 * LEVEL_WIDTH;
        StringBuilder sb = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"" + ExamPage.LANDSCAPE_TEXT_WIDTH_TWIPS
                + "\" w:type=\"dxa\"/>" + TABLE_BORDERS + "<w:tblLayout w:type=\"fixed\"/><w:tblCellMar>"
                + "<w:left w:w=\"57\" w:type=\"dxa\"/><w:right w:w=\"57\" w:type=\"dxa\"/></w:tblCellMar>"
                + "<w:tblLook w:val=\"0000\"/></w:tblPr><w:tblGrid>");
        for (int w : List.of(TT_WIDTH, TOPIC_WIDTH, CONTENT_WIDTH, OUTCOME_WIDTH)) {
            sb.append("<w:gridCol w:w=\"").append(w).append("\"/>");
        }
        sb.append(String.join("", Collections.nCopies(COLUMNS, "<w:gridCol w:w=\"" + LEVEL_WIDTH + "\"/>")));
        sb.append("</w:tblGrid>");

        // 4 tầng tiêu đề như mẫu; 4 cột đầu gộp dọc qua cả 4 tầng.
        int[] firstWidths = {TT_WIDTH, TOPIC_WIDTH, CONTENT_WIDTH, OUTCOME_WIDTH};
        String[] firstTitles = {"TT", "Chủ đề/Chương", "Nội dung/đơn vị kiến thức", "Yêu cầu cần đạt"};
        StringBuilder r1 = new StringBuilder();
        StringBuilder lead = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            r1.append(tc(firstWidths[i], 1, MERGE_START, center(firstTitles[i], true, false)));
            lead.append(continued(firstWidths[i], 1));
        }
        r1.append(tc(levelsWidth, COLUMNS, NO_MERGE, center("Số câu hỏi ở các mức độ đánh giá", true, false)));
        sb.append(tr(true, r1.toString()));
        sb.append(tr(true, lead + tc(3 * typeWidth, 9, NO_MERGE, center("TNKQ", true, false))
                + tc(typeWidth, 3, MERGE_START, center(TYPE_NAMES.get(3), true, false))));
        StringBuilder r3 = new StringBuilder(lead);
        for (int i = 0; i < 3; i++) {
            r3.append(tc(typeWidth, 3, NO_MERGE, center(TYPE_NAMES.get(i), false, true)));
        }
        r3.append(continued(typeWidth, 3));
        sb.append(tr(true, r3.toString()));
        StringBuilder r4 = new StringBuilder(lead);
        for (int i = 0; i < COLUMNS; i++) {
            r4.append(tc(LEVEL_WIDTH, 1, NO_MERGE, center(LEVEL_NAMES.get(i % 3), true, false)));
        }
        sb.append(tr(true, r4.toString()));

        for (int r = 0; r < t.rows().size(); r++) {
            SpecRow row = t.rows().get(r);
            StringBuilder cells = new StringBuilder();
            if (row.firstOfTopic()) {
                cells.append(tc(TT_WIDTH, 1, row.topicRowSpan() > 1 ? MERGE_START : NO_MERGE,
                        center(Integer.toString(row.tt()), false, false)));
                cells.append(tc(TOPIC_WIDTH, 1, row.topicRowSpan() > 1 ? MERGE_START : NO_MERGE, left(row.topic(), true)));
            } else {
                cells.append(continued(TT_WIDTH, 1)).append(continued(TOPIC_WIDTH, 1));
            }
            cells.append(tc(CONTENT_WIDTH, 1, NO_MERGE, left(row.content(), false)));
            StringBuilder outcomes = new StringBuilder();
            for (String line : row.outcomes()) {
                outcomes.append(left(line, false));
            }
            cells.append(tc(OUTCOME_WIDTH, 1, NO_MERGE, outcomes.isEmpty() ? left("", false) : outcomes.toString()));
            for (String count : row.counts()) {
                cells.append(tc(LEVEL_WIDTH, 1, NO_MERGE, center(count, false, false)));
            }
            String rowXml = tr(false, cells.toString());
            sb.append(r == t.rows().size() - 1 ? keepWithNext(rowXml) : rowXml);
        }

        // Chân bảng: nhãn trải 3 cột đầu, căn phải, đậm như mẫu; ô yêu cầu cần đạt trống.
        int labelWidth = TT_WIDTH + TOPIC_WIDTH + CONTENT_WIDTH;
        String blankOutcome = tc(OUTCOME_WIDTH, 1, NO_MERGE, left("", false));
        StringBuilder totals = new StringBuilder(tc(labelWidth, 3, NO_MERGE,
                p("ExamSpecText", "right", "Tổng số câu", true, false)) + blankOutcome);
        for (String count : t.totalCounts()) {
            totals.append(tc(LEVEL_WIDTH, 1, NO_MERGE, center(count, false, false)));
        }
        // Hàng cuối của thân bảng và 2 dòng chân đầu giữ liền dòng sau: 3 dòng chân không bị đẩy riêng sang trang mới.
        sb.append(keepWithNext(tr(false, totals.toString())));
        sb.append(keepWithNext(tr(false, byType(labelWidth, blankOutcome, typeWidth, "Tổng số điểm", t.points()))));
        sb.append(tr(false, byType(labelWidth, blankOutcome, typeWidth, "Tỉ lệ %", t.percents())));
        return sb.append("</w:tbl>").toString();
    }

    /** Mọi đoạn của hàng giữ liền với đoạn sau (Word giữ hàng bảng đi cùng hàng kế tiếp); keepNext đứng ngay sau pStyle. */
    private static String keepWithNext(String rowXml) {
        return rowXml.replaceAll("(<w:pStyle w:val=\"[^\"]*\"/>)", "$1<w:keepNext/>");
    }

    private static String byType(int labelWidth, String blankOutcome, int typeWidth, String label, List<String> values) {
        StringBuilder row = new StringBuilder(tc(labelWidth, 3, NO_MERGE, p("ExamSpecText", "right", label, true, false))
                + blankOutcome);
        for (String value : values) {
            row.append(tc(typeWidth, 3, NO_MERGE, center(value, false, false)));
        }
        return row.toString();
    }
}
