package com.datn.mcq.material.extract;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.material.entity.LessonMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static com.datn.mcq.material.extract.MaterialTestFiles.drawingImage;
import static com.datn.mcq.material.extract.MaterialTestFiles.minimalDocx;
import static com.datn.mcq.material.extract.MaterialTestFiles.oleEquation;
import static com.datn.mcq.material.extract.MaterialTestFiles.omml;
import static com.datn.mcq.material.extract.MaterialTestFiles.paragraph;
import static com.datn.mcq.material.extract.MaterialTestFiles.zipWithZeros;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * W2-3 bước 2: trích văn bản docx bằng pandoc thật (bỏ qua khi máy không có pandoc) và cảnh báo công thức MathType.
 * Pandoc bỏ qua đối tượng OLE MathType, nên mỗi đối tượng được đếm và báo trong warnings.
 */
class DocxTextExtractorTest {

    private final MaterialProperties props = new MaterialProperties();
    private String pandoc;

    @BeforeEach
    void findPandoc() {
        pandoc = PandocTestSupport.find().orElse(null);
    }

    private DocxTextExtractor extractor(String pandocPath) {
        return new DocxTextExtractor(props, pandocPath, new ZipSafetyScanner(props), new DocxFormulaCounter());
    }

    private static ExtractionWarning warning(ExtractedText r, String code) {
        return r.warnings().stream().filter(w -> w.code().equals(code)).findFirst().orElse(null);
    }

    private static long tempDirs() throws Exception {
        try (var s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return s.filter(p -> p.getFileName().toString().startsWith(DocxTextExtractor.TEMP_PREFIX)).count();
        }
    }

    @Test
    void docxCoChuVaEquationGocThanhVanBanCoCongThucDoLa() {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        byte[] docx = new PandocRunner(pandoc).markdownToDocx("# Bài 3. Hằng đẳng thức\n\nBình phương của một tổng: "
                + "$(a+b)^2 = a^2 + 2ab + b^2$.\n\nVí dụ: tính $x^2 - 4$.\n");
        ExtractedText r = extractor(pandoc).extract(docx);
        assertEquals(LessonMaterial.MIME_DOCX, r.mimeType());
        assertEquals("pandoc", r.method());
        assertEquals(null, r.pageCount());
        assertTrue(r.text().contains("Bài 3. Hằng đẳng thức"), r.text());
        assertTrue(r.text().contains("$(a + b)^{2} = a^{2} + 2ab + b^{2}$"), "công thức Word thành $...$: " + r.text());
        assertFalse(r.text().contains("media/"), "không còn tham chiếu ảnh");
        assertTrue(r.warnings().isEmpty(), "không có MathType hay ảnh thì không cảnh báo: " + r.warnings());
    }

    @Test
    void mathTypeBiBoQuaNenCanhBaoSoLuongVaMatCaoThiCanhBaoRo() {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        byte[] docx = minimalDocx(paragraph("Cau 1. Tinh gia tri bieu thuc") + oleEquation("Equation.DSMT4")
                + oleEquation("Equation.DSMT4") + oleEquation("Equation.3") + omml("x+1"));
        ExtractedText r = extractor(pandoc).extract(docx);
        assertTrue(r.text().contains("Cau 1. Tinh gia tri bieu thuc"));
        ExtractionWarning dropped = warning(r, "MATHTYPE_FORMULAS_DROPPED");
        assertNotNull(dropped, r.warnings().toString());
        assertEquals(3, dropped.count());
        assertEquals(4, dropped.total(), "3 MathType + 1 Equation gốc");
        ExtractionWarning high = warning(r, "FORMULA_LOSS_HIGH");
        assertNotNull(high, "3/4 công thức mất: vượt ngưỡng " + props.getFormulaLossWarnRatio());
        assertTrue(high.message().contains("3/4"), high.message());
    }

    @Test
    void matItCongThucThiChiCanhBaoSoLuongKhongBaoMatCao() {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        StringBuilder body = new StringBuilder(paragraph("Bai tap"));
        body.append(oleEquation("Equation.DSMT4"));
        for (int i = 0; i < 9; i++) {
            body.append(omml("x" + i));
        }
        ExtractedText r = extractor(pandoc).extract(minimalDocx(body.toString()));
        ExtractionWarning dropped = warning(r, "MATHTYPE_FORMULAS_DROPPED");
        assertNotNull(dropped);
        assertEquals(1, dropped.count());
        assertEquals(10, dropped.total());
        assertEquals(null, warning(r, "FORMULA_LOSS_HIGH"), "1/10 chưa tới ngưỡng");
    }

    @Test
    void anhNgoaiOleChiCanhBaoKhongOcr() {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        ExtractedText r = extractor(pandoc).extract(minimalDocx(paragraph("Hinh ve") + drawingImage() + drawingImage()));
        ExtractionWarning images = warning(r, "FORMULA_IMAGES_NOT_READ");
        assertNotNull(images, r.warnings().toString());
        assertEquals(2, images.count());
        assertFalse(r.text().contains("media/"));
    }

    @Test
    void docxChiCoDoiTuongKhongCoChuBi422() {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> extractor(pandoc).extract(minimalDocx(oleEquation("Equation.DSMT4"))));
        assertEquals("NO_EXTRACTABLE_TEXT", ex.getCode());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, ex.getStatus());
    }

    @Test
    void zipBombDocxBiChanTruocKhiGoiPandoc() {
        // pandoc không tồn tại: nếu quét zip không chạy trước thì sẽ ra EXTRACTOR_UNAVAILABLE thay vì INVALID_FILE.
        props.setZipMaxEntryBytes(5L << 20);
        byte[] bomb = zipWithZeros("word/document.xml", 40L << 20);
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> extractor("pandoc-khong-co-tren-may-nay").extract(bomb));
        assertEquals("INVALID_FILE", ex.getCode());
    }

    @Test
    void khongCoPandocThiDocxBi503NhungKhongPhaiLoiFile() {
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> extractor("pandoc-khong-co-tren-may-nay").extract(minimalDocx(paragraph("Xin chao"))));
        assertEquals("EXTRACTOR_UNAVAILABLE", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void pandocChayQuaGioThiBao503VaDonThuMucTam() throws Exception {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        props.setExtractTimeout(Duration.ofMillis(1));
        long before = tempDirs();
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> extractor(pandoc).extract(minimalDocx(paragraph("Xin chao"))));
        assertEquals("EXTRACTION_TIMEOUT", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals(before, tempDirs(), "dọn thư mục tạm kể cả khi quá giờ");
    }

    @Test
    void thanhCongCungDonThuMucTam() throws Exception {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        long before = tempDirs();
        extractor(pandoc).extract(minimalDocx(paragraph("Xin chao")));
        assertEquals(before, tempDirs());
    }

    @Test
    void vanBanQuaDaiBi422() {
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        props.setMaxChars(10);
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> extractor(pandoc).extract(minimalDocx(paragraph("Mot doan van kha dai hon muoi ky tu"))));
        assertEquals("TEXT_TOO_LONG", ex.getCode());
    }

    @Test
    void donDepMarkdownBoAnhThuocTinhVaChuThichRong() {
        String md = "Tinh ![hinh](media/image1.png){width=\"1in\" height=\"2in\"} xong\n\n<!-- -->\n\nCuoi ![](x.wmf)\n";
        assertEquals("Tinh  xong\n\nCuoi", DocxTextExtractor.cleanMarkdown(md));
    }
}
