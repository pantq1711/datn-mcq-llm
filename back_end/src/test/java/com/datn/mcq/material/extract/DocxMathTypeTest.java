package com.datn.mcq.material.extract;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.exam.export.PandocTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.datn.mcq.material.extract.MathTypeDocx.bold;
import static com.datn.mcq.material.extract.MathTypeDocx.objectRun;
import static com.datn.mcq.material.extract.MathTypeDocx.oleWithPadding;
import static com.datn.mcq.material.extract.MathTypeDocx.real;
import static com.datn.mcq.material.extract.MathTypeDocx.t;
import static com.datn.mcq.mtef.MtefBuilder.mtef;
import static com.datn.mcq.mtef.MtefBuilder.num;
import static com.datn.mcq.mtef.MtefBuilder.ole;
import static com.datn.mcq.mtef.MtefBuilder.var;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Công thức MathType (đối tượng OLE Equation.DSMT*) trong docx được đổi thành LaTeX {@code $...$} TRƯỚC khi pandoc đọc
 * (pandoc bỏ qua đối tượng OLE): mỗi đối tượng được thay bằng mã giữ chỗ, sau pandoc mã được đổi thành công thức. Dùng
 * pandoc thật (bỏ qua khi máy không có pandoc) và công thức mẫu thật trong src/test/resources/mtef.
 */
class DocxMathTypeTest {

    private static final String UNREADABLE = "[công thức]";

    private final MaterialProperties props = new MaterialProperties();
    private String pandoc;

    @BeforeEach
    void findPandoc() {
        pandoc = PandocTestSupport.find().orElse(null);
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
    }

    private DocxTextExtractor extractor() {
        return new DocxTextExtractor(props, pandoc, new ZipSafetyScanner(props), new DocxFormulaCounter());
    }

    private static ExtractionWarning warning(ExtractedText r, String code) {
        return r.warnings().stream().filter(w -> w.code().equals(code)).findFirst().orElse(null);
    }

    private static int occurrences(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) {
            n++;
        }
        return n;
    }

    private static byte[] oleOf(String variable) {
        return ole(mtef(var(variable)));
    }

    // ---------- chuyển đổi đúng chỗ ----------

    @Test
    void congThucGiuaCauThanhLatexDungViTri() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Cho tam giác "), d.eq(oleOf("ABC")), t(" vuông tại "), d.eq(oleOf("A")), t("."))
                .build();
        ExtractedText r = extractor().extract(docx);
        assertTrue(r.text().contains("Cho tam giác $ABC$ vuông tại $A$."), r.text());
        assertFalse(r.text().contains("⟦"), "không để lọt mã giữ chỗ: " + r.text());
        assertNull(warning(r, "MATHTYPE_UNREADABLE"), r.warnings().toString());
        assertNull(warning(r, "FORMULA_LOSS_HIGH"));
    }

    @Test
    void congThucMauThatGiuaCau() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Tỉ số bằng "), d.eq(real(1)), t(" và góc "), d.eq(real(5)), t(" xong"))
                .build();
        ExtractedText r = extractor().extract(docx);
        assertTrue(r.text().contains("Tỉ số bằng $\\frac{5}{3}$ và góc $\\widehat{ABC}=60^{\\circ}$ xong"), r.text());
    }

    @Test
    void congThucRiengMotDongThanhHaiDola() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Giải hệ phương trình sau")).p(d.eq(real(9))).p(t("Đáp số cuối cùng")).build();
        ExtractedText r = extractor().extract(docx);
        List<String> lines = r.text().lines().toList();
        assertTrue(lines.contains("$$\\begin{cases}x+y=3\\\\x-y=1\\end{cases}$$"), r.text());
        assertTrue(r.text().contains("Giải hệ phương trình sau"), r.text());
        assertTrue(r.text().contains("Đáp số cuối cùng"), r.text());
    }

    @Test
    void congThucTrongBangVaTrongRunDamToMauKhongBiBienDang() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Bảng giá trị"))
                .tableRow(new String[]{t("Cạnh "), d.eq(real(1))},
                        new String[]{d.eq("Equation.DSMT4", real(3), true, true), t(" khác")},
                        new String[]{bold("đậm "), d.eq("Equation.DSMT4", real(8), true, false)})
                .build();
        ExtractedText r = extractor().extract(docx);
        assertTrue(r.text().contains("$\\frac{5}{3}$"), r.text());
        assertTrue(r.text().contains("$x_{1}$"), r.text());
        assertTrue(r.text().contains("$\\left(x+\\frac{1}{2}\\right)^{2}$"), r.text());
        assertFalse(r.text().contains("⟦"), r.text());
        assertNull(warning(r, "MATHTYPE_UNREADABLE"), r.warnings().toString());
    }

    @Test
    void muoiHaiCongThucMoiMaGiuChoVeDungCongThucCuaNo() {
        MathTypeDocx d = new MathTypeDocx();
        String[] runs = new String[12 * 2];
        StringBuilder expected = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            runs[(i - 1) * 2] = d.eq(ole(mtef(num(String.valueOf(i)))));
            runs[(i - 1) * 2 + 1] = t(i < 12 ? " ; " : "");
            expected.append("$").append(i).append("$").append(i < 12 ? " ; " : "");
        }
        ExtractedText r = extractor().extract(d.p(runs).build());
        assertTrue(r.text().contains(expected), r.text());
    }

    // ---------- công thức không đọc được ----------

    @Test
    void binRacThanhCongThucTrongVanBanTaiLieuVanTrichDuocVaCanhBaoDungSo() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Cho "), d.eq(new byte[]{1, 2, 3, 4}), t(" và "), d.eq(oleOf("x")), t(" rồi "),
                d.eq(oleOf("y")), t(" cùng "), d.eq(oleOf("z")), t(" xong")).build();
        ExtractedText r = extractor().extract(docx);
        assertTrue(r.text().contains("Cho " + UNREADABLE + " và $x$ rồi $y$ cùng $z$ xong"), r.text());
        ExtractionWarning w = warning(r, "MATHTYPE_UNREADABLE");
        assertNotNull(w, r.warnings().toString());
        assertEquals(1, w.count());
        assertEquals(4, w.total());
        assertTrue(w.message().contains("Đã chuyển 3/4 công thức MathType; 1 công thức không đọc được"), w.message());
        assertNull(warning(r, "FORMULA_LOSS_HIGH"), "1/4 chưa tới ngưỡng " + props.getFormulaLossWarnRatio());
        assertNull(warning(r, "MATHTYPE_FORMULAS_DROPPED"), "cảnh báo cũ 'bị bỏ' đã được thay");
    }

    @Test
    void phanLonKhongDocDuocThiCanhBaoMatNhieu() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Bài "), d.eq(new byte[]{9}), t(" "), d.eq(new byte[]{8}), t(" "), d.eq(oleOf("x")))
                .build();
        ExtractedText r = extractor().extract(docx);
        assertEquals(2, occurrences(r.text(), UNREADABLE), r.text());
        ExtractionWarning high = warning(r, "FORMULA_LOSS_HIGH");
        assertNotNull(high, r.warnings().toString());
        assertTrue(high.message().contains("2/3"), high.message());
    }

    @Test
    void equation3VaProgIdKhongHoTroThanhCongThucKhongDoanNoiDung() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("A "), d.eq("Equation.3", real(1), false, false), t(" B "),
                d.eq("Equation.DSMT4", real(1), false, false)).build();
        ExtractedText r = extractor().extract(docx);
        assertTrue(r.text().contains("A " + UNREADABLE + " B $\\frac{5}{3}$"), r.text());
        ExtractionWarning w = warning(r, "MATHTYPE_UNREADABLE");
        assertEquals(1, w.count());
        assertEquals(2, w.total());
    }

    @Test
    void quanHeKhongCoHoacTroRaNgoaiHoacBenNgoaiThanhCongThucKhongHongTaiLieu() {
        MathTypeDocx d = new MathTypeDocx();
        String dangling = objectRun("Equation.DSMT4", "rId77", false, false);
        String escaping = objectRun("Equation.DSMT4", "rId78", false, false);
        String external = objectRun("Equation.DSMT4", "rId79", false, false);
        d.relsXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/"
                + "package/2006/relationships\">"
                + "<Relationship Id=\"rId78\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
                + "oleObject\" Target=\"../../../etc/passwd\"/>"
                + "<Relationship Id=\"rId79\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
                + "oleObject\" Target=\"file:///c:/x.bin\" TargetMode=\"External\"/></Relationships>");
        ExtractedText r = extractor().extract(d.p(t("X "), dangling, t(" Y "), escaping, t(" Z "), external, t(" W"))
                .build());
        assertTrue(r.text().contains("X " + UNREADABLE + " Y " + UNREADABLE + " Z " + UNREADABLE + " W"), r.text());
        assertEquals(3, warning(r, "MATHTYPE_UNREADABLE").count());
    }

    @Test
    void docxChiCoCongThucKhongDocDuocVanLa422KhongPhaiVanBanRong() {
        MathTypeDocx d = new MathTypeDocx();
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> extractor().extract(d.p(d.eq(new byte[]{1})).build()));
        assertEquals("NO_EXTRACTABLE_TEXT", ex.getCode());
    }

    @Test
    void docxChiCoCongThucDocDuocLaVanBanHopLe() {
        MathTypeDocx d = new MathTypeDocx();
        ExtractedText r = extractor().extract(d.p(d.eq(real(1))).build());
        assertEquals("$$\\frac{5}{3}$$", r.text());
    }

    // ---------- giới hạn an toàn ----------

    @Test
    void oleVuotTranMotMbBiBoQuaAnToanConDuoiTranVanDoc() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] big = oleWithPadding(real(1), 1_100_000);
        byte[] ok = oleWithPadding(real(1), 700_000);
        assertTrue(big.length > 1 << 20 && ok.length < 1 << 20, big.length + " / " + ok.length);
        ExtractedText r = extractor().extract(d.p(t("Lớn "), d.eq(big), t(" vừa "), d.eq(ok), t(" hết")).build());
        assertTrue(r.text().contains("Lớn " + UNREADABLE + " vừa $\\frac{5}{3}$ hết"), r.text());
        assertEquals(1, warning(r, "MATHTYPE_UNREADABLE").count());
    }

    @Test
    void quaHaiNganCongThucPhanDuThanhCongThucKhongDoc() {
        props.setZipMaxEntries(5000);
        MathTypeDocx d = new MathTypeDocx();
        byte[] one = oleOf("x");
        String[] runs = new String[2001 * 2];
        for (int i = 0; i < 2001; i++) {
            runs[i * 2] = d.eq(one);
            runs[i * 2 + 1] = t(" ");
        }
        ExtractedText r = extractor().extract(d.p(t("Nhiều công thức: ")).p(runs).build());
        assertEquals(2000, occurrences(r.text(), "$x$"), "đúng 2000 công thức được chuyển");
        assertEquals(1, occurrences(r.text(), UNREADABLE));
        ExtractionWarning w = warning(r, "MATHTYPE_UNREADABLE");
        assertEquals(1, w.count());
        assertEquals(2001, w.total());
    }

    @Test
    void quanHeCoDoctypeBiTuChoiKhongGiaiMaThucThe() {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Có "), d.eq(real(1))).relsXml("<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x \"y\">]>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>").build();
        ApiErrorException ex = assertThrows(ApiErrorException.class, () -> extractor().extract(docx));
        assertEquals("INVALID_FILE", ex.getCode());
    }

    // ---------- không đổi khi không có MathType ----------

    private String pandocThuan(byte[] docx) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("test-pandoc-thuan-");
        try {
            Files.write(dir.resolve("in.docx"), docx);
            Process p = new ProcessBuilder(pandoc, "-f", "docx", "-t", DocxTextExtractor.PANDOC_FORMAT, "--wrap=none",
                    "in.docx", "-o", "out.md").directory(dir.toFile()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            assertTrue(p.waitFor(60, TimeUnit.SECONDS));
            assertEquals(0, p.exitValue());
            return Files.readString(dir.resolve("out.md"), StandardCharsets.UTF_8);
        } finally {
            try (var s = Files.walk(dir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }

    @Test
    void docxKhongCoMathTypeCoKetQuaGiongHetPandocThuan() throws Exception {
        byte[] docx = new PandocRunner(pandoc).markdownToDocx("# Bài 3. Hằng đẳng thức\n\nBình phương của một tổng: "
                + "$(a+b)^2 = a^2 + 2ab + b^2$ và giá $5 / \\$ 3_x$.\n\n| a | b |\n|---|---|\n| 1 | $x^2$ |\n\n"
                + "- một\n- hai\n");
        ExtractedText r = extractor().extract(docx);
        assertEquals(TextNormalizer.normalize(DocxTextExtractor.cleanMarkdown(pandocThuan(docx))), r.text());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void doiTuongOleKhongPhaiCongThucDuocGiuNguyenNhuTruoc() throws Exception {
        MathTypeDocx d = new MathTypeDocx();
        byte[] docx = d.p(t("Hình vẽ "), objectRun("FXDraw.Graphic", "rId50", false, false), t(" và chữ"))
                .p(t("Đoạn sau có $5 và dấu_gạch")).build();
        ExtractedText r = extractor().extract(docx);
        assertEquals(TextNormalizer.normalize(DocxTextExtractor.cleanMarkdown(pandocThuan(docx))), r.text());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }
}
