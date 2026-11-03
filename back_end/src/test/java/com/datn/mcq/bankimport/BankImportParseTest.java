package com.datn.mcq.bankimport;

import com.datn.mcq.bankimport.docx.DocxReader;
import com.datn.mcq.bankimport.dto.BankImportDtos.ImportItem;
import com.datn.mcq.bankimport.dto.BankImportDtos.PreviewData;
import com.datn.mcq.bankimport.dto.BankImportDtos.SkippedItem;
import com.datn.mcq.bankimport.parse.DocParser;
import com.datn.mcq.bankimport.parse.LessonName;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import com.datn.mcq.bankimport.service.ImportFlags;
import com.datn.mcq.bankimport.service.ImportPreviewBuilder;
import com.datn.mcq.qc.dto.DedupCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lõi nhập ngân hàng từ Word (chuyển từ tools/bank-import vào backend) và bước ánh xạ sang bản xem trước: dạng câu, đáp
 * án, mức (NB/TH/VD/VDC), câu bị bỏ theo lý do, cờ chặn và cờ thông tin. Không cần DB, không gọi mạng. Fixture tự dựng
 * bằng {@link DocxBuilder}, không dùng bộ đề thương mại.
 */
class BankImportParseTest {

    @TempDir
    Path tmp;

    private ParsedDoc parse(byte[] docx, String fileName) throws IOException {
        Path f = tmp.resolve("in-" + System.nanoTime() + ".docx");
        Files.write(f, docx);
        return DocParser.parse(DocxReader.read(f, DocxReader.Limits.defaults()), LessonName.parse(fileName, "0123456789abcdef"));
    }

    private PreviewData preview(byte[] docx) throws IOException {
        return ImportPreviewBuilder.build(parse(docx, FixtureDoc.FILE_NAME), List.of());
    }

    private static ImportItem item(PreviewData p, String key) {
        return p.items().stream().filter(i -> i.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("không có " + key + " trong " + p.items().stream()
                        .map(ImportItem::key).toList()));
    }

    // ---------- tên file ----------

    @Test
    void tenFileTachKhoiChuongBaiVaCatPhanGiaoVien() {
        LessonName.Parsed ln = LessonName.parse(FixtureDoc.FILE_NAME, "0123456789abcdef");
        assertEquals(8, ln.source().grade());
        assertEquals(9, ln.source().chapter());
        assertEquals(9, ln.source().lesson());
        assertEquals(FixtureDoc.TOPIC, ln.source().lessonTitle());
        assertEquals(FixtureDoc.DISPLAY_NAME, LessonName.displayName(ln));
        assertEquals("NGUYỄN VĂN GIẢ", ln.teacher(), "chỉ để tự kiểm rò rỉ");
        assertFalse(LessonName.displayName(ln).contains("GV"));
    }

    @Test
    void tenFileKhongTheoMauThiKhongCoKhoiVaChuDe() {
        LessonName.Parsed ln = LessonName.parse("bo cau hoi cua toi - GV Ai Do.docx", "0123456789abcdef");
        assertNull(ln.source().grade());
        assertNull(ln.source().lessonTitle());
        assertNull(LessonName.displayName(ln), "không dựng tên từ tên file lạ (có thể chứa tên người)");
    }

    // ---------- bộ đầy đủ ----------

    @Test
    void demDungSoCauTheoDangVaCauBiBoTheoLyDo() throws IOException {
        PreviewData p = preview(FixtureDoc.full());
        Map<String, Long> byType = p.items().stream().collect(Collectors.groupingBy(ImportItem::type, Collectors.counting()));
        assertEquals(10L, byType.get("mcq"), "12 mcq, 2 câu có ảnh bị bỏ");
        assertEquals(6L, byType.get("tf"));
        assertEquals(2L, byType.get("sa"), "4 sa, 1 có ảnh, 1 công thức chưa hỗ trợ");
        Map<String, Long> skipped = p.skipped().stream()
                .collect(Collectors.groupingBy(SkippedItem::reason, Collectors.counting()));
        assertEquals(Map.of(ImportFlags.SKIP_HAS_IMAGE, 3L, ImportFlags.SKIP_FORMULA_UNSUPPORTED, 1L), skipped);
        assertEquals(22, p.items().size() + p.skipped().size());
        for (SkippedItem s : p.skipped()) {
            assertTrue(s.locator().startsWith("Phần "), "chỉ vị trí, không nội dung: " + s.locator());
        }
    }

    @Test
    void mcqDapAnMucDeVaCongThuc() throws IOException {
        PreviewData p = preview(FixtureDoc.full());
        ImportItem q1 = item(p, "mcq-001");
        assertEquals("C", q1.correctOption());
        assertEquals("NB", q1.levelRaw());
        assertEquals("easy", q1.level());
        assertTrue(q1.stem().contains("$\\frac{3}{4}$"), q1.stem());
        assertFalse(q1.stem().contains("[NB]"), "nhãn mức bị cắt khỏi đề");
        assertEquals(4, q1.options().size());
        assertNull(q1.explanation());
        assertTrue(q1.flags().contains(ImportFlags.NO_SOLUTION), q1.flags().toString());
        assertFalse(q1.blocking(), "thiếu lời giải chỉ là cờ thông tin");

        assertEquals("B", item(p, "mcq-002").correctOption());
        assertEquals("medium", item(p, "mcq-002").level());
        assertEquals("hard", item(p, "mcq-004").level(), "VẬN DỤNG CAO → hard");
        assertEquals("VDC", item(p, "mcq-004").levelRaw());
        ImportItem q5 = item(p, "mcq-005");
        assertEquals("Bước một.\nBước hai có numPr.", q5.explanation());
        assertFalse(q5.flags().contains(ImportFlags.NO_SOLUTION));
        assertTrue(item(p, "mcq-007").flags().contains("plain_math_vertalign"));
        assertTrue(item(p, "mcq-008").flags().contains("empty_formula"));
        assertEquals("B", item(p, "mcq-012").correctOption(), "chữ giống nhãn trong phương án không bị nhận nhầm");
    }

    @Test
    void coChanMucThieuDapAnThieuHaiDapAn() throws IOException {
        PreviewData p = preview(FixtureDoc.full());
        ImportItem q9 = item(p, "mcq-009");
        assertTrue(q9.flags().contains(ImportFlags.LEVEL_MISSING));
        assertTrue(q9.blocking());
        assertNull(q9.level());
        ImportItem q10 = item(p, "mcq-010");
        assertTrue(q10.flags().contains("answer_missing"));
        assertTrue(q10.blocking());
        ImportItem q11 = item(p, "mcq-011");
        assertTrue(q11.flags().contains("answer_ambiguous"));
        assertTrue(q11.blocking());
        assertFalse(item(p, "mcq-001").blocking());
    }

    @Test
    void tfNhanTungYVaBonYChungMucCuaCau() throws IOException {
        PreviewData p = preview(FixtureDoc.full());
        ImportItem g1 = item(p, "tf-001");
        assertEquals("medium", g1.level());
        assertEquals(List.of(true, false, false, true), g1.statements().stream().map(s -> s.label()).toList());
        assertEquals(List.of("a", "b", "c", "d"), g1.statements().stream().map(s -> s.key()).toList());
        assertEquals("Giải thích ý a.", g1.explanation());
        assertEquals(List.of(false, true, true, false), item(p, "tf-002").statements().stream().map(s -> s.label()).toList(),
                "dòng Đáp án: S Đ Đ S");
        assertEquals(List.of(false, true, false, false), item(p, "tf-003").statements().stream().map(s -> s.label()).toList());
        assertEquals("hard", item(p, "tf-005").level(), "VDC → hard");
        ImportItem g5 = item(p, "tf-006");
        assertTrue(g5.flags().contains("answer_missing"));
        assertTrue(g5.blocking());
    }

    @Test
    void saDapSoQuaStrictKhongDatThiCoChan() throws IOException {
        PreviewData p = preview(FixtureDoc.full());
        ImportItem s1 = item(p, "sa-001");
        assertEquals("7,5", s1.saAnswer());
        assertFalse(s1.blocking(), s1.flags().toString());
        ImportItem s2 = item(p, "sa-002");
        assertTrue(s2.flags().contains(ImportFlags.SA_ANSWER_FORMAT), s2.flags().toString());
        assertTrue(s2.blocking());
        assertEquals("$\\frac{3}{4}$", s2.saAnswer(), "giữ nguyên văn để giáo viên thấy, không âm thầm bỏ");
    }

    @Test
    void khongLotTenGiaoVienQuangCaoVaBangTieuDe() throws IOException {
        PreviewData p = preview(FixtureDoc.full());
        String all = p.toString();
        for (String bad : FixtureDoc.FORBIDDEN) {
            assertFalse(all.contains(bad), "lọt: " + bad);
        }
    }

    @Test
    void cauCoBangDuLieuBiBoVaDem() throws IOException {
        PreviewData p = preview(FixtureDoc.withDataTable());
        assertEquals(1, p.items().size());
        assertEquals("sa", p.items().get(0).type());
        assertEquals("5", p.items().get(0).saAnswer());
        assertEquals(List.of(ImportFlags.SKIP_HAS_TABLE), p.skipped().stream().map(SkippedItem::reason).toList());
    }

    // ---------- trùng ----------

    @Test
    void trungTrongLoVaTrungNganHangLaCoKhongChan() throws IOException {
        PreviewData inFile = preview(FixtureDoc.duplicatedInFile());
        assertFalse(inFile.items().get(0).flags().contains(ImportFlags.DUPLICATE_IN_BATCH));
        assertTrue(inFile.items().get(1).flags().contains(ImportFlags.DUPLICATE_IN_BATCH), inFile.items().get(1).flags()
                .toString());
        assertFalse(inFile.items().get(1).blocking());

        ImportItem first = inFile.items().get(0);
        DedupCandidate bank = new DedupCandidate(77, first.stem(), first.options().get(0).text(),
                first.options().get(1).text(), first.options().get(2).text(), first.options().get(3).text());
        PreviewData withBank = ImportPreviewBuilder.build(parse(FixtureDoc.duplicatedInFile(), FixtureDoc.FILE_NAME),
                List.of(bank));
        assertTrue(withBank.items().get(0).flags().contains(ImportFlags.DUPLICATE_IN_BANK));
        assertFalse(withBank.items().get(0).blocking());
    }

    // ---------- đầu vào độc hại ----------

    @Test
    void doctypeTrongDocumentBiTuChoi() throws IOException {
        byte[] docx = new DocxBuilder().documentPrefix("<!DOCTYPE w [<!ENTITY x \"y\">]>").p("PHẦN I").bytes();
        Path f = tmp.resolve("doctype.docx");
        Files.write(f, docx);
        assertThrows(DocxReader.InvalidDocxException.class, () -> DocxReader.read(f, DocxReader.Limits.defaults()));
    }

    @Test
    void documentXmlVuotTranBiTuChoi() throws IOException {
        DocxBuilder d = new DocxBuilder().p("PHẦN I. CÂU TRẮC NGHIỆM");
        for (int i = 0; i < 200; i++) {
            d.p("Đoạn đệm số " + i + " ".repeat(50));
        }
        Path f = tmp.resolve("big.docx");
        Files.write(f, d.bytes());
        assertThrows(DocxReader.InvalidDocxException.class,
                () -> DocxReader.read(f, new DocxReader.Limits(4096, 1 << 20, 8L << 20)));
    }

    @Test
    void embeddingVuotTranThanhCongThucChuaHoTroVaCauBiBo() throws IOException {
        byte[] bigOle = new byte[300_000];
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");
        d.numbered(DocxBuilder.cy("[NB]"), DocxBuilder.t(" Tính "), d.eq(bigOle), DocxBuilder.t("."));
        d.p("Trả lời: ").p(DocxBuilder.t("Đáp số: "), DocxBuilder.y("5"));
        Path f = tmp.resolve("ole.docx");
        Files.write(f, d.bytes());
        PreviewData p = ImportPreviewBuilder.build(DocParser.parse(DocxReader.read(f,
                new DocxReader.Limits(1 << 20, 100_000, 8L << 20)), LessonName.parse(FixtureDoc.FILE_NAME, "0123456789abcdef")),
                List.of());
        assertTrue(p.items().isEmpty());
        assertEquals(ImportFlags.SKIP_FORMULA_UNSUPPORTED, p.skipped().get(0).reason());
    }

    @Test
    void tongEmbeddingVuotTranBiTuChoi() throws IOException {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");
        d.numbered(DocxBuilder.cy("[NB]"), d.eq(new byte[60_000]), d.eq(new byte[60_000]));
        Path f = tmp.resolve("total.docx");
        Files.write(f, d.bytes());
        assertThrows(DocxReader.InvalidDocxException.class,
                () -> DocxReader.read(f, new DocxReader.Limits(1 << 20, 100_000, 100_000)));
    }

    @Test
    void preview_KhongChuaDuLieuNhiPhan() throws IOException {
        ParsedDoc doc = parse(FixtureDoc.full(), FixtureDoc.FILE_NAME);
        assertNotNull(doc.source());
        assertEquals(22, doc.questions().size());
    }
}
