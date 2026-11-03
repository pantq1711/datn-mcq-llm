package com.datn.mcq.exam.export;

import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.entity.ExamSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Markdown (pandoc) của đề ở chế độ giáo viên, để {@link PandocRunner} chuyển sang Word (ngày 23/10, bản cơ bản):
 * đầu đề ({@link ExamHeader}, bảng 2 cột không viền; mã đề in ra là 101, không in mã hệ thống MT…); PHẦN I/II/III với tên phần đầy đủ, "Câu k." đánh số lại từ 1 trong từng phần; mcq mỗi phương án
 * một dòng "A. ..."; tf các ý "a) ..."; sa chỉ có đề. Sau một ngắt trang: "ĐÁP ÁN" (mcq chữ cái, tf chuỗi Đ/S theo thứ
 * tự a–d, sa đáp số) rồi "LỜI GIẢI" từng câu. Đáp án đúng trong phần đề chưa đánh dấu. Chỗ chưa có câu ghi rõ trạng thái.
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
    static final String PAGE_BREAK = "```{=openxml}\n<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>\n```";

    /** Ký tự thoát bằng "\" khi ở ngoài công thức. */
    private static final String ESCAPED = "\\*_[]<>#`~^|@";
    /** Đầu đoạn dạng "1." "a)" "A." sẽ thành danh sách đánh số: thoát dấu chấm/ngoặc. */
    private static final Pattern LIST_START = Pattern.compile("^(\\d+|[A-Za-z])([.)])");
    /** Đầu đoạn "- " "+ " sẽ thành danh sách gạch đầu dòng. */
    private static final Pattern BULLET_START = Pattern.compile("^[-+]\\s");

    private ExamMarkdownBuilder() {}

    /** Đầu đề chỉ có tên đề (không có thông tin ma trận): môn và thời gian để trống. */
    public static String build(ExamViewDto exam) {
        return build(exam, ExamHeader.blank(exam.title()));
    }

    public static String build(ExamViewDto exam, ExamHeader header) {
        List<String> blocks = new ArrayList<>();
        blocks.add(headerBlock(header));
        List<PartDto> parts = exam.parts().stream().filter(p -> !p.items().isEmpty()).toList();
        for (PartDto part : parts) {
            blocks.add("## " + PART_NAMES.getOrDefault(part.part(), "PHẦN " + part.part()));
            int k = 1;
            for (ExamItemDto item : part.items()) {
                question(blocks, k++, item);
            }
        }
        blocks.add(PAGE_BREAK);
        blocks.add("# ĐÁP ÁN");
        for (PartDto part : parts) {
            blocks.add("## PHẦN " + part.part());
            int k = 1;
            for (ExamItemDto item : part.items()) {
                blocks.add("Câu " + k++ + ": " + answer(item));
            }
        }
        blocks.add("# LỜI GIẢI");
        for (PartDto part : parts) {
            blocks.add("## PHẦN " + part.part());
            int k = 1;
            for (ExamItemDto item : part.items()) {
                int no = k++;
                if (!hasQuestion(item)) {
                    blocks.add(label(no) + NONE);
                    continue;
                }
                // Quyết định 6 (nhập từ Word, 3/11): câu không có lời giải thì bỏ hẳn khối lời giải của câu đó.
                String explanation = paragraphs(item.explanation());
                if (!explanation.isEmpty()) {
                    blocks.add(label(no) + explanation);
                }
            }
        }
        return String.join("\n\n", blocks) + "\n";
    }

    static final String HEADER_LEFT_1 = "PHÒNG GD&ĐT ……………";
    static final String HEADER_LEFT_2 = "TRƯỜNG THCS ……………";
    static final String HEADER_NAME_LINE = "Họ và tên: ………………………  Lớp: ………";

    /**
     * Đầu đề (2/11): bảng 2 cột không viền, trái là "PHÒNG GD&ĐT … / TRƯỜNG THCS …", phải là tên kì, môn và thời gian, mã đề
     * hiển thị; dưới bảng là dòng "Họ và tên … Lớp …". Viết bằng khối openxml thô (như {@link #PAGE_BREAK}) vì Markdown của
     * pandoc không tắt được viền bảng; nội dung chữ được thoát XML.
     */
    static String headerBlock(ExamHeader header) {
        String left = headerParagraph(HEADER_LEFT_1, false) + headerParagraph(HEADER_LEFT_2, true);
        String right = headerParagraph(header.titleLine(), true) + headerParagraph(header.subjectLine(), false)
                + headerParagraph(header.codeLine(), true);
        return "```{=openxml}\n"
                + "<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblBorders><w:top w:val=\"nil\"/>"
                + "<w:left w:val=\"nil\"/><w:bottom w:val=\"nil\"/><w:right w:val=\"nil\"/><w:insideH w:val=\"nil\"/>"
                + "<w:insideV w:val=\"nil\"/></w:tblBorders><w:tblLayout w:type=\"fixed\"/><w:tblLook w:val=\"0000\"/>"
                + "</w:tblPr><w:tblGrid><w:gridCol w:w=\"4300\"/><w:gridCol w:w=\"5300\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"4300\" w:type=\"dxa\"/></w:tcPr>" + left + "</w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"5300\" w:type=\"dxa\"/></w:tcPr>" + right + "</w:tc></w:tr></w:tbl>\n"
                + "<w:p><w:pPr><w:spacing w:before=\"240\" w:after=\"240\"/></w:pPr><w:r><w:t xml:space=\"preserve\">"
                + xml(HEADER_NAME_LINE) + "</w:t></w:r></w:p>\n"
                + "```";
    }

    private static String headerParagraph(String text, boolean bold) {
        return "<w:p><w:pPr><w:spacing w:after=\"0\"/><w:jc w:val=\"center\"/></w:pPr><w:r>"
                + (bold ? "<w:rPr><w:b/></w:rPr>" : "") + "<w:t xml:space=\"preserve\">" + xml(text) + "</w:t></w:r></w:p>";
    }

    private static String xml(String text) {
        // F-05: gộp xuống dòng thành khoảng trắng; tên đề nhiều dòng có dòng "```" sẽ đóng sớm khối openxml của đầu đề.
        return text.replaceAll("\\s*\\R\\s*", " ").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String label(int k) {
        return "**Câu " + k + ".** ";
    }

    private static boolean hasQuestion(ExamItemDto item) {
        return item.questionText() != null;
    }

    private static void question(List<String> blocks, int k, ExamItemDto item) {
        if (!hasQuestion(item)) {
            blocks.add(label(k) + inline(ExamSlot.FAILED.equals(item.state()) ? MISSING_FAILED : MISSING_GENERATING));
            return;
        }
        blocks.add(label(k) + paragraphs(item.questionText()));
        switch (item.questionType()) {
            case MCQ -> {
                List<String> options = Arrays.asList(item.optionA(), item.optionB(), item.optionC(), item.optionD());
                for (int i = 0; i < options.size(); i++) {
                    blocks.add((char) ('A' + i) + ". " + inline(oneLine(options.get(i))));
                }
            }
            case TF -> {
                for (ItemStatementDto s : statements(item)) {
                    blocks.add(s.key().getDbValue() + "\\) " + inline(oneLine(s.text())));
                }
            }
            case SA -> {
                // chỉ có đề
            }
        }
    }

    private static List<ItemStatementDto> statements(ExamItemDto item) {
        return item.statements() == null ? List.of() : item.statements().stream()
                .sorted(Comparator.comparing(ItemStatementDto::key)).toList();
    }

    private static String answer(ExamItemDto item) {
        if (!hasQuestion(item)) {
            return NONE;
        }
        return switch (item.questionType()) {
            case MCQ -> item.correctOption() != null ? item.correctOption().name() : NONE;
            case TF -> statements(item).stream()
                    .map(s -> s.label() == TruthValue.TRUE ? "Đ" : s.label() == TruthValue.FALSE ? "S" : "?")
                    .collect(Collectors.joining());
            case SA -> item.saAnswer() != null && !item.saAnswer().isBlank() ? inline(item.saAnswer().strip()) : NONE;
        };
    }

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

    /** Một đoạn văn: giữ công thức $...$, thoát ký tự Markdown ở ngoài công thức. */
    static String inline(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n && text.charAt(i + 1) == '$') {
                out.append("\\$");
                i += 2;
                continue;
            }
            if (c == '$') {
                int close = closingDollar(text, i + 1);
                String inner = close < 0 ? "" : text.substring(i + 1, close).strip();
                if (inner.isEmpty()) {
                    out.append("\\$");
                    i++;
                } else {
                    // F-05: xuống dòng trong công thức thành khoảng trắng; nếu không, một dòng của "công thức" có thể bắt đầu
                    // bằng ```{=openxml} hoặc "<" và thành khối raw thật khi pandoc đọc Markdown.
                    out.append('$').append(inner.replaceAll("\\s*\\R\\s*", " ")).append('$');
                    i = close + 1;
                }
                continue;
            }
            if (ESCAPED.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
            i++;
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
