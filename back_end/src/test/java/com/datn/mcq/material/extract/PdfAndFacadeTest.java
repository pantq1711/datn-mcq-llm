package com.datn.mcq.material.extract;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.exam.export.PandocRunner;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.material.entity.LessonMaterial;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.datn.mcq.material.extract.MaterialTestFiles.blankPdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.copyProtectedPdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.passwordPdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.pdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** W2-3 bước 2: trích văn bản PDF bằng PDFBox (không OCR) và cửa vào chung {@link MaterialTextExtractor}. */
class PdfAndFacadeTest {

    private final MaterialProperties props = new MaterialProperties();

    private PdfTextExtractor pdfExtractor() {
        return new PdfTextExtractor(props);
    }

    private MaterialTextExtractor facade(String pandocPath) {
        return new MaterialTextExtractor(props, new PlainTextExtractor(props),
                new DocxTextExtractor(props, pandocPath, new ZipSafetyScanner(props), new DocxFormulaCounter()),
                pdfExtractor());
    }

    private static void assertError(ApiErrorException ex, HttpStatus status, String code) {
        assertEquals(code, ex.getCode(), ex.getMessage());
        assertEquals(status, ex.getStatus(), ex.getMessage());
    }

    // ---------- PDF ----------

    @Test
    void pdfCoChuTrichDuocVaCoSoTrang() {
        ExtractedText r = pdfExtractor().extract(pdf("Hang dang thuc dang nho so mot", "Binh phuong cua mot tong so hai"));
        assertEquals(LessonMaterial.MIME_PDF, r.mimeType());
        assertEquals("pdfbox", r.method());
        assertEquals(2, r.pageCount());
        assertTrue(r.text().contains("Hang dang thuc dang nho so mot"), r.text());
        assertTrue(r.text().contains("Binh phuong cua mot tong so hai"), r.text());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void pdfDungBoDemChiDungFileTamVaDonSachSauKhiXong() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        long before = pdfboxTempFiles(tmp);
        ExtractedText r = pdfExtractor().extract(pdf("Hang dang thuc dang nho so mot", "Binh phuong cua mot tong so hai"));
        assertEquals(2, r.pageCount());
        assertEquals(before, pdfboxTempFiles(tmp), "bộ đệm file tạm của PDFBox được xóa khi đóng tài liệu");
        assertThrows(ApiErrorException.class, () -> pdfExtractor().extract(passwordPdf("Noi dung bi mat khau")));
        assertEquals(before, pdfboxTempFiles(tmp), "kể cả khi mở PDF lỗi");
    }

    private static long pdfboxTempFiles(java.nio.file.Path tmp) throws Exception {
        try (var s = java.nio.file.Files.list(tmp)) {
            return s.filter(p -> p.getFileName().toString().startsWith("PDFBox")).count();
        }
    }

    @Test
    void pdfScanKhongCoChuBi422NoiRoKhongOcr() {
        ApiErrorException ex = assertThrows(ApiErrorException.class, () -> pdfExtractor().extract(blankPdf(3)));
        assertError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "NO_EXTRACTABLE_TEXT");
        assertTrue(ex.getMessage().contains("scan") && ex.getMessage().contains("OCR"), ex.getMessage());
    }

    @Test
    void pdfNhieuTrangChiCoVaiChuRatBiXemLaScan() {
        // 1 trang có chữ, 9 trang trắng: trung bình dưới ngưỡng ký tự mỗi trang.
        String[] pages = new String[10];
        pages[0] = "Chi mot dong";
        ApiErrorException ex = assertThrows(ApiErrorException.class, () -> pdfExtractor().extract(pdf(pages)));
        assertEquals("NO_EXTRACTABLE_TEXT", ex.getCode());
    }

    @Test
    void pdfCoMatKhauNguoiDungBi422() {
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> pdfExtractor().extract(passwordPdf("Noi dung bi mat khau")));
        assertError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "MATERIAL_ENCRYPTED");
    }

    @Test
    void pdfCamSaoChepNoiDungBi422() {
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> pdfExtractor().extract(copyProtectedPdf("Noi dung cam sao chep o day")));
        assertError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "MATERIAL_ENCRYPTED");
        assertTrue(ex.getMessage().contains("sao chép"), ex.getMessage());
    }

    @Test
    void pdfQuaNhieuTrangBi422() {
        props.setMaxPages(2);
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> pdfExtractor().extract(pdf("Mot trang co chu du dai de qua nguong", "Hai trang co chu du dai",
                        "Ba trang co chu du dai")));
        assertError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "TOO_MANY_PAGES");
    }

    @Test
    void pdfHongHoacGiaMaoBi422InvalidFile() {
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> pdfExtractor().extract(utf8("%PDF-1.7\nkhong phai pdf that, chi co dau\n")));
        assertError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_FILE");
    }

    @Test
    void nhieuKyTuLoiThiCanhBaoChatLuongVanBan() {
        assertTrue(PdfTextExtractor.isLowQuality("a�".repeat(50)));
        assertTrue(PdfTextExtractor.isLowQuality("x".repeat(30)), "ký tự vùng riêng (font công thức)");
        assertFalse(PdfTextExtractor.isLowQuality("Hang dang thuc dang nho. ".repeat(40)));
        assertFalse(PdfTextExtractor.isLowQuality("a�" + "b".repeat(500)), "ít ký tự lỗi thì không cảnh báo");
    }

    @Test
    void pdfVanBanQuaDaiBi422() {
        props.setMaxChars(15);
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> pdfExtractor().extract(pdf("Mot dong van ban dai hon muoi lam ky tu")));
        assertEquals("TEXT_TOO_LONG", ex.getCode());
    }

    // ---------- MaterialTextExtractor (cửa vào chung) ----------

    @Test
    void cuaVaoChungChonDungBoDocTheoNoiDungBoQuaDuoiFile() {
        MaterialTextExtractor f = facade("pandoc-khong-co-tren-may-nay");
        assertEquals("pdfbox", f.extract(pdf("Noi dung pdf du dai de qua nguong chu")).method());
        ExtractedText txt = f.extract(utf8("Hằng đẳng thức đáng nhớ"));
        assertEquals("plain", txt.method());
        assertEquals("Hằng đẳng thức đáng nhớ", txt.text());
    }

    @Test
    void cuaVaoChungChoDocxQuaPandocKhiCo() {
        String pandoc = PandocTestSupport.find().orElse(null);
        assumeTrue(pandoc != null, "máy không có pandoc: bỏ qua");
        byte[] docx = new PandocRunner(pandoc).markdownToDocx("# Bài 1\n\nNội dung $x^2$.\n");
        ExtractedText r = facade(pandoc).extract(docx);
        assertEquals("pandoc", r.method());
        assertTrue(r.text().contains("Bài 1"));
    }

    @Test
    void fileRongVaFileQuaLon() {
        MaterialTextExtractor f = facade("pandoc");
        assertError(assertThrows(ApiErrorException.class, () -> f.extract(new byte[0])), HttpStatus.BAD_REQUEST,
                "EMPTY_FILE");
        props.setMaxFileBytes(100);
        assertError(assertThrows(ApiErrorException.class, () -> f.extract(new byte[101])),
                HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE");
    }

    @Test
    void zipKhongPhaiDocxBi415() {
        byte[] xlsxLike = MaterialTestFiles.zip(new java.util.LinkedHashMap<>(java.util.Map.of(
                "[Content_Types].xml", MaterialTestFiles.CONTENT_TYPES.getBytes(),
                "xl/workbook.xml", "<workbook/>".getBytes())));
        assertError(assertThrows(ApiErrorException.class, () -> facade("pandoc").extract(xlsxLike)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void quaTaiThiBao503KhiHangDoiBoTrongKhongDu() throws Exception {
        props.setMaxConcurrentExtractions(1);
        props.setQueueWait(Duration.ofMillis(100));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PdfTextExtractor blocking = new PdfTextExtractor(props) {
            @Override
            public ExtractedText extract(byte[] content, Runnable permitReleaser) {
                inside.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (permitReleaser != null) {
                    permitReleaser.run();
                }
                return new ExtractedText("x", LessonMaterial.MIME_PDF, "pdfbox", 1, List.of());
            }
        };
        MaterialTextExtractor f = new MaterialTextExtractor(props, new PlainTextExtractor(props),
                new DocxTextExtractor(props, "pandoc", new ZipSafetyScanner(props), new DocxFormulaCounter()), blocking);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ExtractedText> first = pool.submit(() -> f.extract(pdf("Dang giu cho duy nhat")));
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            ApiErrorException ex = assertThrows(ApiErrorException.class, () -> f.extract(utf8("Van ban khac")));
            assertError(ex, HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTOR_BUSY");
            release.countDown();
            assertNotNull(first.get(10, TimeUnit.SECONDS));
            assertEquals("Van ban khac", f.extract(utf8("Van ban khac")).text(), "nhả chỗ thì nhận việc lại");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // ---------- ExtractionWarning ----------

    @Test
    void canhBaoGhiRaJsonMangDoiTuongCoThoatKyTu() {
        assertEquals(null, ExtractionWarning.toJson(List.of()));
        String json = ExtractionWarning.toJson(List.of(
                new ExtractionWarning("MATHTYPE_FORMULAS_DROPPED", 3, 4, "Có \"3\"/4 công thức"),
                new ExtractionWarning("X", null, null, "m")));
        assertEquals("[{\"code\":\"MATHTYPE_FORMULAS_DROPPED\",\"count\":3,\"total\":4,"
                + "\"message\":\"Có \\\"3\\\"/4 công thức\"},{\"code\":\"X\",\"message\":\"m\"}]", json);
    }
}
