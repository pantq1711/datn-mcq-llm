package com.datn.mcq.bankimport.out;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.model.Question.Formula;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * formulas.html: bảng đối chiếu để người kiểm bằng mắt. Mỗi công thức được lấy mẫu phân tầng theo loại cấu trúc, hiện ảnh
 * xem trước MathType (WMF/EMF lưu sẵn trong docx) cạnh ảnh JLaTeXMath vẽ từ LaTeX do công cụ sinh ra và mã LaTeX.
 */
public final class FormulaSheet {

    /** Số công thức tối đa trong bảng. */
    static final int SAMPLE_SIZE = 60;

    private record Item(String docId, Question q, Formula f, byte[] preview, String cls) {}

    static String classify(String latex) {
        if (latex.contains("\\begin{cases}") || latex.contains("\\begin{array}")) {
            return "he_phuong_trinh_nhieu_dong";
        }
        if (latex.contains("\\frac")) {
            return "phan_so";
        }
        if (latex.contains("\\sqrt")) {
            return "can";
        }
        if (latex.contains("\\widehat") || latex.contains("\\hat") || latex.contains("\\overrightarrow")) {
            return "goc_dau_mu";
        }
        if (latex.contains("\\left")) {
            return "ngoac_to";
        }
        if (latex.contains("\\text")) {
            return "chu_va_don_vi";
        }
        if (latex.contains("^") || latex.contains("_")) {
            return "luy_thua_chi_so";
        }
        if (latex.contains("\\Delta") || latex.contains("\\triangle") || latex.contains("\\perp") || latex.contains("\\angle")) {
            return "ky_hieu_hinh_hoc";
        }
        return "co_ban";
    }

    /**
     * Ghi bảng. {@code created} nhận mọi tệp/thư mục đã tạo (để xóa sạch nếu phát hiện rò rỉ).
     * {@code convertImages}: đổi ảnh xem trước WMF/EMF sang PNG (chỉ Windows).
     */
    public static void write(Path outDir, List<ParsedDoc> docs, boolean convertImages, Consumer<Path> created) throws IOException {
        Map<String, List<Item>> byClass = new LinkedHashMap<>();
        for (ParsedDoc d : docs) {
            for (Question q : d.questions()) {
                for (Formula f : q.formulas) {
                    if (f.latex != null) {
                        byte[] preview = f.previewRef == null ? null : d.binaries().get(f.previewRef);
                        byClass.computeIfAbsent(classify(f.latex), k -> new ArrayList<>())
                                .add(new Item(d.source().docId(), q, f, preview, classify(f.latex)));
                    }
                }
            }
        }
        // mỗi nhóm cấu trúc lấy cùng một hạn mức, cách đều trong nhóm để không chỉ lấy những công thức đầu file
        List<Item> unique = new ArrayList<>();
        int quota = (int) Math.ceil(SAMPLE_SIZE / (double) Math.max(1, byClass.size()));
        for (List<Item> items : byClass.values()) {
            int n = items.size();
            int take = Math.min(quota, n);
            for (int k = 0; k < take && unique.size() < SAMPLE_SIZE; k++) {
                unique.add(items.get((int) ((long) k * n / take)));
            }
        }

        Path dir = outDir.resolve("formulas");
        Files.createDirectories(dir);
        created.accept(dir);
        List<Path[]> toConvert = new ArrayList<>();
        StringBuilder rows = new StringBuilder();
        for (Item it : unique) {
            String base = it.q().id + "-" + it.f().id;
            String rendered = render(it.f().latex, dir.resolve(base + ".png"), created) ? "formulas/" + base + ".png" : null;
            String preview = null;
            if (it.preview() != null) {
                String ext = it.f().previewRef.substring(it.f().previewRef.lastIndexOf('.') + 1);
                Path orig = dir.resolve(base + "-mathtype." + ext);
                Files.write(orig, it.preview());
                created.accept(orig);
                preview = "formulas/" + orig.getFileName();
                if (convertImages) {
                    toConvert.add(new Path[]{orig, dir.resolve(base + "-mathtype.png")});
                }
            }
            rows.append("<tr><td>").append(esc(it.q().id)).append("<br>").append(esc(it.f().id)).append("<br><small>")
                    .append(esc(it.cls())).append("</small></td><td>")
                    .append(preview == null ? "—" : "<a href=\"" + preview + "\" data-mt=\"" + base + "\">"
                            + "<img src=\"" + (convertImages ? "formulas/" + base + "-mathtype.png" : preview)
                            + "\" alt=\"MathType\" onerror=\"this.replaceWith('mở tệp gốc')\"></a>")
                    .append("</td><td>").append(rendered == null ? "không vẽ được" : "<img src=\"" + rendered + "\" alt=\"LaTeX\">")
                    .append("</td><td><code>").append(esc(it.f().latex)).append("</code></td><td></td></tr>\n");
        }
        if (convertImages) {
            for (Path p : ImageConverter.convert(toConvert, dir)) {
                created.accept(p);
            }
        }
        String html = "<!doctype html><html lang=\"vi\"><meta charset=\"utf-8\"><title>Đối chiếu công thức</title>"
                + "<style>body{font-family:sans-serif;margin:16px}table{border-collapse:collapse}td,th{border:1px solid #888;"
                + "padding:6px;vertical-align:middle}img{max-height:70px}code{font-size:13px}</style>"
                + "<h1>Đối chiếu công thức MathType → LaTeX</h1><p>Mẫu phân tầng " + unique.size() + " công thức. Cột 2: ảnh "
                + "xem trước do MathType lưu trong docx (WMF/EMF; trình duyệt không mở được thì bấm để mở tệp gốc hoặc "
                + "chạy với <code>--convert-images</code> trên Windows). Cột 3: ảnh vẽ từ LaTeX. Hai ảnh phải trùng nhau; "
                + "ghi nhận xét vào cột cuối.</p><table><tr><th>Câu / công thức</th><th>MathType</th><th>LaTeX vẽ lại</th>"
                + "<th>Mã LaTeX</th><th>Nhận xét</th></tr>\n" + rows + "</table></html>";
        Path htmlFile = outDir.resolve("formulas.html");
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
        created.accept(htmlFile);
    }

    private static boolean render(String latex, Path file, Consumer<Path> created) {
        try {
            TeXFormula tf = new TeXFormula(latex);
            BufferedImage img = (BufferedImage) tf.createBufferedImage(TeXConstants.STYLE_DISPLAY, 28f, Color.BLACK, Color.WHITE);
            if (img == null) {
                return false;
            }
            ImageIO.write(img, "png", file.toFile());
            created.accept(file);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private FormulaSheet() {}
}
