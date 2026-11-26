package com.datn.mcq.exam.export;

import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.mathtype.docx.DocxPackage;
import com.datn.mcq.questionimage.service.QuestionImageToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hình của câu hỏi trong file Word (O2, G0 H2 và mục 4.2 của {@code docs/design/cau-co-hinh.md}).
 * <ul>
 *   <li>Token {@code ⟦img:<id>⟧} thành ảnh data URI {@code ![qimg:<id>](data:<mime>;base64,…){width=<W>mm}} (không dùng
 *       URL, không dùng file: dưới {@code --sandbox} pandoc bỏ ảnh dạng file mà vẫn exit 0). Alt {@code qimg:<id>} thành
 *       {@code descr} của {@code wp:docPr} (pandoc 3.12.1, đã kiểm) để đếm lại từng id.</li>
 *   <li>Token không có ảnh, token trong công thức hay trong đáp số, hoặc chuỗi {@code ⟦img:} dở dang: dừng với 500
 *       {@value #MISSING_CODE}; token không bao giờ ra Word dưới dạng chữ.</li>
 *   <li>Sau pandoc và sau bước MathType: số {@code w:drawing} và số {@code wp:docPr descr="qimg:<id>"} theo từng id phải
 *       đúng bằng số lần chèn; lệch thì {@value #MISSING_CODE}, không trả file.</li>
 *   <li>Trần tổng dung lượng ảnh một lần xuất (D5): vượt thì 422 {@value #TOO_LARGE_CODE}, kiểm trước khi nạp byte và
 *       trước khi chạy pandoc.</li>
 * </ul>
 */
final class ExamFigures {

    private static final Logger log = LoggerFactory.getLogger(ExamFigures.class);

    static final String MISSING_CODE = "EXPORT_IMAGE_MISSING";
    static final String TOO_LARGE_CODE = "EXPORT_IMAGES_TOO_LARGE";
    static final String ALT_PREFIX = "qimg:";
    /** Ảnh khối (câu dẫn, ý đúng/sai, lời giải): rộng tối đa 120 mm (lề chữ 18,0 cm, H2). */
    static final int BLOCK_MAX_MM = 120;
    /** Ảnh trong phương án: rộng tối đa 40 mm, bốn phương án xếp 2×2 (H2). */
    static final int OPTION_MAX_MM = 40;
    static final long MB = 1024L * 1024;

    private static final Pattern EMBEDDED = Pattern.compile("!\\[" + ALT_PREFIX + "(\\d+)]\\(");
    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";

    private ExamFigures() {}

    /** Byte ảnh để nhúng; {@code displayWidthMm} là bề rộng trong Word gốc (5–120). */
    record Figure(long id, String mime, byte[] data, int displayWidthMm) {}

    /** Token của đúng một dòng (sau khi bỏ khoảng trắng hai đầu) thì trả id, không thì null. */
    static Long figureOnly(String line) {
        Matcher m = QuestionImageToken.TOKEN_PATTERN.matcher(line.strip());
        return m.matches() ? Long.parseLong(m.group(1)) : null;
    }

    static boolean hasToken(String text) {
        return QuestionImageToken.containsToken(text);
    }

    /** Markdown của một ảnh: {@code ![qimg:<id>](data:…){width=<W>mm}}, {@code W = min(displayWidthMm, maxMm)}. */
    static String markdown(Figure f, int maxMm) {
        int width = Math.max(1, Math.min(f.displayWidthMm(), maxMm));
        return "![" + ALT_PREFIX + f.id() + "](data:" + f.mime() + ";base64," + Base64.getEncoder().encodeToString(f.data())
                + "){width=" + width + "mm}";
    }

    /**
     * Thay token trong chuỗi đã qua {@link ExamMarkdownBuilder#inline} bằng ảnh (token không bị thoát ký tự). Token không có
     * trong {@code figures}, hoặc còn chuỗi {@code ⟦img:} sau khi thay (token sai dạng): 500 {@value #MISSING_CODE}.
     */
    static String embed(String inlined, Map<Long, Figure> figures, int maxMm) {
        Matcher m = QuestionImageToken.TOKEN_PATTERN.matcher(inlined);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            long id = Long.parseLong(m.group(1));
            Figure f = figures.get(id);
            if (f == null) {
                throw missing("token ảnh " + id + " không có ảnh được phép xuất");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(markdown(f, maxMm)));
        }
        m.appendTail(out);
        String s = out.toString();
        if (s.contains(QuestionImageToken.PREFIX)) {
            throw missing("chuỗi token ảnh sai dạng");
        }
        return s;
    }

    /** Token chỉ hợp lệ ở ngoài công thức (H2): token trong {@code $...$} không vẽ được, dừng thay vì in chữ. */
    static void checkOutsideMath(String source) {
        if (source == null || !source.contains(QuestionImageToken.PREFIX)) {
            return;
        }
        for (ExamMarkdownBuilder.Piece piece : ExamMarkdownBuilder.pieces(source)) {
            if (piece.math() && piece.text().contains(QuestionImageToken.PREFIX)) {
                throw missing("token ảnh nằm trong công thức");
            }
        }
    }

    /** Trường không được có hình (đáp số sa, H2): có {@code ⟦img:} thì dừng. */
    static void checkNoToken(String text) {
        if (text != null && text.contains(QuestionImageToken.PREFIX)) {
            throw missing("token ảnh ở trường không có hình");
        }
    }

    /**
     * Số lần mỗi id ảnh được in trong file theo {@code content}: câu dẫn, phương án, ý đúng/sai; lời giải chỉ khi file có lời
     * giải ({@link ExportContent#ALL}; không có ở chỉ đề và bản YoungMix). Bản đặc tả không in chữ câu hỏi nên rỗng. Chỗ chưa
     * có câu bỏ qua.
     */
    static Map<Long, Integer> referenced(ExamViewDto view, ExportContent content) {
        Map<Long, Integer> out = new TreeMap<>();
        if (content == ExportContent.SPEC) {
            return out;
        }
        for (PartDto part : view.parts()) {
            for (ExamItemDto item : part.items()) {
                if (item.questionText() == null) {
                    continue;
                }
                List<String> fields = new ArrayList<>(List.of(nz(item.questionText()), nz(item.optionA()),
                        nz(item.optionB()), nz(item.optionC()), nz(item.optionD())));
                if (item.statements() != null) {
                    item.statements().stream().map(ItemStatementDto::text).map(ExamFigures::nz).forEach(fields::add);
                }
                // chỉ đề và bản YoungMix (26/11) không có lời giải
                if (content != ExportContent.EXAM && content != ExportContent.YOUNGMIX) {
                    fields.add(nz(item.explanation()));
                }
                for (String f : fields) {
                    QuestionImageToken.extractIds(f).forEach(id -> out.merge(id, 1, Integer::sum));
                }
            }
        }
        return out;
    }

    /** Tổng dung lượng theo số lần chèn (pandoc đọc từng lần chèn trong Markdown); id không có kích thước tính 0. */
    static long totalBytes(Map<Long, Integer> counts, Map<Long, Integer> sizes) {
        long total = 0;
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            total += (long) e.getValue() * sizes.getOrDefault(e.getKey(), 0);
        }
        return total;
    }

    /** 422: "Tổng hình trong đề quá lớn để xuất Word (X MB, tối đa Y MB). Hãy bỏ bớt câu có hình." */
    static DocumentExportException tooLarge(long totalBytes, long maxBytes) {
        String x = BigDecimal.valueOf(totalBytes).divide(BigDecimal.valueOf(MB), 1, RoundingMode.CEILING)
                .toPlainString().replace('.', ',');
        String y = BigDecimal.valueOf(maxBytes).divide(BigDecimal.valueOf(MB), 1, RoundingMode.FLOOR)
                .stripTrailingZeros().toPlainString().replace('.', ',');
        return new DocumentExportException(422, TOO_LARGE_CODE, "Tổng hình trong đề quá lớn để xuất Word (" + x
                + " MB, tối đa " + y + " MB). Hãy bỏ bớt câu có hình.");
    }

    /** Số lần chèn mỗi id ảnh trong Markdown (alt {@code qimg:<id>}); nội dung đề không tạo được chuỗi này vì "[" bị thoát. */
    static Map<Long, Integer> expectedInMarkdown(String markdown) {
        Map<Long, Integer> out = new TreeMap<>();
        Matcher m = EMBEDDED.matcher(markdown);
        while (m.find()) {
            out.merge(Long.parseLong(m.group(1)), 1, Integer::sum);
        }
        return out;
    }

    /**
     * Đếm trong {@code word/document.xml}: tổng {@code w:drawing} phải bằng tổng số lần chèn (file mẫu không có drawing nào)
     * và số {@code wp:docPr descr="qimg:<id>"} của từng id phải bằng số lần chèn id đó. Lệch thì ghi log số kỳ vọng, số đếm,
     * id thiếu (không ghi chữ đề) và ném 500 {@value #MISSING_CODE}.
     */
    static void verifyDocx(byte[] docx, Map<Long, Integer> expected, String stage) {
        Map<Long, Integer> found = new TreeMap<>();
        int drawings = 0;
        try {
            Document doc = DocxPackage.read(docx).document();
            NodeList all = doc.getElementsByTagName("*");
            for (int k = 0; k < all.getLength(); k++) {
                Element e = (Element) all.item(k);
                if (W_NS.equals(e.getNamespaceURI()) && "drawing".equals(e.getLocalName())) {
                    drawings++;
                } else if (WP_NS.equals(e.getNamespaceURI()) && "docPr".equals(e.getLocalName())) {
                    String descr = e.getAttribute("descr");
                    if (descr.startsWith(ALT_PREFIX) && descr.substring(ALT_PREFIX.length()).matches("\\d+")) {
                        found.merge(Long.parseLong(descr.substring(ALT_PREFIX.length())), 1, Integer::sum);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            log.error("Xuất Word ({}): không đọc được docx để đếm ảnh câu hỏi: {}", stage, e.toString());
            throw missing("không đếm được ảnh trong docx");
        }
        int expectedTotal = expected.values().stream().mapToInt(Integer::intValue).sum();
        if (drawings == expectedTotal && found.equals(new TreeMap<>(expected))) {
            return;
        }
        TreeSet<Long> shortIds = new TreeSet<>();
        expected.forEach((id, n) -> {
            if (found.getOrDefault(id, 0) < n) {
                shortIds.add(id);
            }
        });
        int missingCount = Math.max(0, expectedTotal - drawings);
        log.error("Xuất Word ({}): thiếu {} ảnh câu hỏi: kỳ vọng {} ảnh {}, đếm được {} w:drawing và descr {}; id thiếu {}",
                stage, missingCount, expectedTotal, expected, drawings, found, shortIds);
        throw missing("docx thiếu " + missingCount + " ảnh câu hỏi (" + stage + ")");
    }

    static DocumentExportException missing(String detail) {
        return new DocumentExportException(500, MISSING_CODE, "Thiếu ảnh câu hỏi khi xuất Word: " + detail);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
