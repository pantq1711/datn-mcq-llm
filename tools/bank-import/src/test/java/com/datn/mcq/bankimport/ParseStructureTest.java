package com.datn.mcq.bankimport;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.parse.DocParser;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tách cấu trúc 3 phần + đáp án trên tài liệu giả có đủ các biến thể (xem {@link FixtureDoc}). */
class ParseStructureTest {

    @TempDir
    static Path dir;
    static ParsedDoc doc;

    @BeforeAll
    static void parse() throws IOException {
        doc = DocParser.parse(FixtureDoc.write(dir));
    }

    private static Question q(String type, int ordinal) {
        return doc.questions().stream().filter(x -> x.type.equals(type) && x.ordinal == ordinal).findFirst()
                .orElseThrow(() -> new AssertionError("không có câu " + type + " #" + ordinal));
    }

    private static String optionText(Question q, String key) {
        return q.options.stream().filter(o -> o.key.equals(key)).findFirst().orElseThrow().text;
    }

    // ---------- tên file, đếm câu ----------

    @Test
    void nguonLayTuTenFileVaKhongCoTenGiaoVien() {
        assertEquals("k8c9b9", doc.source().docId());
        assertEquals(8, doc.source().grade());
        assertEquals(9, doc.source().chapter());
        assertEquals(9, doc.source().lesson());
        assertEquals("ĐỊNH LÝ GIẢ", doc.source().lessonTitle());
        assertFalse(doc.source().toString().contains("NGUYỄN"), doc.source().toString());
        assertFalse(doc.source().toString().contains("GV"), doc.source().toString());
    }

    @Test
    void demDungTungDang() {
        assertEquals(12, doc.questions().stream().filter(x -> x.type.equals("mcq")).count());
        assertEquals(6, doc.questions().stream().filter(x -> x.type.equals("tf")).count());
        assertEquals(4, doc.questions().stream().filter(x -> x.type.equals("sa")).count());
        assertEquals("k8c9b9-mcq-001", q("mcq", 1).id);
        assertEquals("k8c9b9-tf-006", q("tf", 6).id);
        assertEquals("I", q("mcq", 1).part);
        assertEquals("II", q("tf", 1).part);
        assertEquals("III", q("sa", 1).part);
    }

    // ---------- phần I ----------

    @Test
    void mcqMoiPhuongAnMotDoanToVangChiPhanChu() {
        Question x = q("mcq", 1);
        assertEquals("NB", x.levelRaw);
        assertEquals("easy", x.level);
        assertEquals("Câu hỏi giả một có $\\frac{3}{4}$ trong đề.", x.stem);
        assertEquals("phương án A", optionText(x, "A"));
        assertEquals("phương án D", optionText(x, "D"));
        assertEquals("C", x.answer.value);
        assertEquals("highlight_yellow", x.answer.evidence);
        assertEquals(1, x.formulas.size());
        assertEquals("f1", x.formulas.get(0).id);
        assertEquals("ok", x.formulas.get(0).status);
        assertEquals("\\frac{3}{4}", x.formulas.get(0).latex);
    }

    @Test
    void mcqHaiPhuongAnMotDoanToVangBatDauTuDauCham() {
        Question x = q("mcq", 2);
        assertEquals("TH", x.levelRaw);
        assertEquals("medium", x.level);
        assertEquals("Câu hai.", x.stem);
        assertEquals("1", optionText(x, "A"));
        assertEquals("hai", optionText(x, "B"));
        assertEquals("3", optionText(x, "C"));
        assertEquals("4", optionText(x, "D"));
        assertEquals("B", x.answer.value);
    }

    @Test
    void mcqCauGoTayVaPhuongAnTrongBangBoCuc() {
        Question x = q("mcq", 3);
        assertEquals("VD", x.levelRaw);
        assertEquals("hard", x.level);
        assertEquals("Câu ba với bảng bố cục.\n⟦img1⟧", x.stem);
        assertEquals("D", x.answer.value);
        assertEquals("bốn", optionText(x, "D"), "ảnh ở ô bên cạnh không lọt vào phương án D");
        assertTrue(x.hasImage);
        assertEquals(1, x.images.size());
        assertEquals("figure", x.images.get(0).kind);
        assertEquals("stem", x.images.get(0).role);
    }

    @Test
    void mcqBonPhuongAnTrongMotDoanVaNhanMucDayDu() {
        Question x = q("mcq", 4);
        assertEquals("VDC", x.levelRaw);
        assertEquals("hard", x.level);
        assertEquals("a", optionText(x, "A"));
        assertEquals("b", optionText(x, "B"));
        assertEquals("c", optionText(x, "C"));
        assertEquals("d", optionText(x, "D"));
        assertEquals("D", x.answer.value);
    }

    @Test
    void dongNumPrTrongLoiGiaiKhongThanhCauMoi() {
        Question x = q("mcq", 5);
        assertEquals("A", x.answer.value);
        assertEquals("Bước một.\nBước hai có numPr.", x.solution);
        assertEquals(12, doc.questions().stream().filter(y -> y.type.equals("mcq")).count());
    }

    @Test
    void bangDuLieuTrongDeVaAnhCongThucNho() {
        Question x = q("mcq", 6);
        assertEquals(1, x.tables.size());
        assertEquals(List.of(List.of("x", "1"), List.of("y", "2")), x.tables.get(0));
        assertTrue(x.hasImage);
        assertEquals("figure", x.images.stream().filter(i -> i.role.equals("stem")).findFirst().orElseThrow().kind);
        var small = x.images.stream().filter(i -> i.kind.equals("formula_image")).findFirst().orElseThrow();
        assertEquals("option_B", small.role);
        assertTrue(x.flags.contains("formula_image"), x.flags.toString());
        assertEquals("A", x.answer.value);
    }

    @Test
    void vanBanNfdVaChuMuGoTay() {
        Question x = q("mcq", 7);
        assertEquals("Toán học BC$^{2}$ bằng", x.stem);
        assertTrue(x.flags.contains("plain_math_vertalign"), x.flags.toString());
        assertEquals("B", x.answer.value);
    }

    @Test
    void chuThichSauPhuongAnCuoiKhongLotVaoPhuongAn() {
        Question x = q("mcq", 8);
        assertEquals("bốn", optionText(x, "D"));
        assertEquals("D", x.answer.value);
        assertEquals("Câu tám.", x.stem, "công thức rỗng không để lại token");
        assertTrue(x.formulas.isEmpty());
        assertTrue(x.flags.contains("empty_formula"), x.flags.toString());
    }

    @Test
    void thieuNhanMucThiLevelNullVaCoCo() {
        Question x = q("mcq", 9);
        assertNull(x.levelRaw);
        assertNull(x.level);
        assertEquals("Câu chín không có nhãn mức.", x.stem);
        assertTrue(x.flags.contains("level_missing"), x.flags.toString());
        assertEquals("A", x.answer.value);
    }

    @Test
    void khongToDapAnVaToHaiDapAnDeuCoCo() {
        Question none = q("mcq", 10);
        assertNull(none.answer.value);
        assertTrue(none.flags.contains("answer_missing"), none.flags.toString());
        Question two = q("mcq", 11);
        assertNull(two.answer.value);
        assertTrue(two.flags.contains("answer_ambiguous"), two.flags.toString());
    }

    // ---------- phần II ----------

    @Test
    void tfBangVoiToVangVaLoiGiaiSauBang() {
        Question x = q("tf", 1);
        assertEquals("TH", x.levelRaw);
        assertEquals("Đề dẫn giả một.", x.stem);
        assertEquals(4, x.statements.size());
        assertEquals("ý a đúng", x.statements.get(0).text);
        assertEquals(List.of(true, false, false, true), x.statements.stream().map(s -> s.truth).toList());
        assertEquals("highlight_yellow", x.statements.get(0).evidence);
        assertEquals("Giải thích ý a.", x.solution);
    }

    @Test
    void tfDapAnLayTuDongChu() {
        Question x = q("tf", 2);
        assertEquals(List.of(false, true, true, false), x.statements.stream().map(s -> s.truth).toList());
        assertEquals("dap_an_line", x.statements.get(0).evidence);
        assertEquals("ý một", x.statements.get(0).text);
    }

    @Test
    void tfCauDanDungTruocBangKhongCoNhanAD() {
        Question x = q("tf", 3);
        assertEquals("NB", x.levelRaw);
        assertEquals("Đề dẫn giả ba.", x.stem);
        assertEquals(4, x.statements.size());
        assertEquals("ý hai", x.statements.get(1).text);
        assertEquals(List.of(false, true, false, false), x.statements.stream().map(s -> s.truth).toList());
    }

    @Test
    void tfBangGopTachThanhHaiCau() {
        Question a = q("tf", 4);
        Question b = q("tf", 5);
        assertEquals("VD", a.levelRaw);
        assertEquals("VDC", b.levelRaw);
        assertEquals("Đề dẫn gộp một.", a.stem);
        assertEquals("Đề dẫn gộp hai.", b.stem);
        assertEquals(List.of(true, false, false, false), a.statements.stream().map(s -> s.truth).toList());
        assertEquals(List.of(false, false, true, true), b.statements.stream().map(s -> s.truth).toList());
    }

    @Test
    void tfKhongCoDauHieuDapAnThiCoCo() {
        Question x = q("tf", 6);
        assertEquals(4, x.statements.size());
        assertTrue(x.statements.stream().allMatch(s -> s.truth == null));
        assertTrue(x.flags.contains("answer_missing"), x.flags.toString());
    }

    @Test
    void tfHangMotOByKhongNhanMucLaLoiGoiYKhongPhaiCauMoi() {
        Question x = q("tf", 6);
        assertEquals("Đề dẫn không đáp án.", x.stem);
        assertEquals("Gợi ý: gợi ý giả.", x.solution, "nhóm E: gợi ý sau các ý thuộc lời giải, không thuộc đề");
        assertTrue(x.flags.contains("hint_after_statements"), x.flags.toString());
        assertEquals(6, doc.questions().stream().filter(y -> y.type.equals("tf")).count());
    }

    @Test
    void chuoiGiongNhanBenTrongNoiDungPhuongAnKhongBiNhanLamNhan() {
        Question x = q("mcq", 12);
        assertEquals("Điểm D.", optionText(x, "A"));
        assertEquals("Điểm C.", optionText(x, "B"));
        assertEquals("Điểm B.", optionText(x, "C"));
        assertEquals("Điểm A.", optionText(x, "D"));
        assertEquals("B", x.answer.value);
        assertFalse(x.flags.contains("answer_ambiguous"), x.flags.toString());
    }

    // ---------- phần III ----------

    @Test
    void saDapSoToVangSoThapPhan() {
        Question x = q("sa", 1);
        assertEquals("NB", x.levelRaw);
        assertEquals("Tính giả một.", x.stem);
        assertEquals("7,5", x.answer.text);
        assertEquals("7.5", x.answer.numeric);
        assertEquals("dap_so_line", x.answer.evidence);
    }

    @Test
    void saDapSoLaCongThuc() {
        Question x = q("sa", 2);
        assertEquals("$\\frac{3}{4}$", x.answer.text);
        assertNull(x.answer.numeric);
        assertEquals(1, x.formulas.size());
    }

    @Test
    void saAnhCungDoanVoiDapSoDoiTuongOleVaLoiGiaiKhongNhan() {
        Question x = q("sa", 3);
        assertEquals("5", x.answer.text);
        assertEquals("Ta có A.\nVậy 5.", x.solution);
        assertTrue(x.hasImage);
        assertTrue(x.images.stream().anyMatch(i -> i.kind.equals("figure") && i.role.equals("stem")));
        assertTrue(x.images.stream().anyMatch(i -> i.kind.equals("ole_drawing")), x.images.toString());
        assertEquals("Tính giả ba có hình.", x.stem.replaceAll("\\s*⟦img\\d+⟧\\s*", "").strip());
    }

    @Test
    void saCongThucChuaHoTroGiuTokenKhongDoan() {
        Question x = q("sa", 4);
        assertEquals("VDC", x.levelRaw);
        assertEquals("Tính bốn ⟦f1⟧.", x.stem);
        assertEquals("unsupported", x.formulas.get(0).status);
        assertNull(x.formulas.get(0).latex);
        assertNotNull(x.formulas.get(0).reason);
        assertTrue(x.flags.contains("formula_unsupported"), x.flags.toString());
        assertEquals("11,5%", x.answer.text);
        assertNull(x.answer.numeric);
    }

    // ---------- rác ----------

    @Test
    void bangTieuDeVaDongQuangCaoKhongLotVaoCau() {
        String all = doc.questions().stream().map(Object::toString).reduce("", String::concat);
        for (String bad : FixtureDoc.FORBIDDEN) {
            assertFalse(all.contains(bad), "lọt chuỗi cấm: " + bad);
        }
        assertEquals(22, doc.questions().size());
    }
}
