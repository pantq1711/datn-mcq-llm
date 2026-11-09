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

import static com.datn.mcq.bankimport.DocxBuilder.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nhóm E (chữ thừa trong đề dẫn câu Đúng/Sai), tái hiện bằng tài liệu giả: chữ giữ chỗ "Câu hỏi…" sau nhãn mức, hàng
 * "Gợi ý" sau các ý, câu phụ "a/ b/" sót lại cuối đề dẫn. Nội dung hoàn toàn tự bịa.
 */
class TfStemCleanupTest {

    @TempDir
    static Path dir;
    static ParsedDoc doc;

    @BeforeAll
    static void parse() throws IOException {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN II. CÂU TRẮC NGHIỆM ĐÚNG SAI");

        // E1: chữ giữ chỗ của mẫu còn sót ngay sau nhãn mức, đề thật ở đoạn sau
        d.table(tf(merged(3, cellParas(cy("[NB]"), t(" Câu hỏi…")), cellParas(t("Đề dẫn giả một.")))));

        // E2: hàng một ô "Gợi ý" sau các ý, lời giải ở đoạn sau bảng
        d.table(tfWithHint(merged(3, cellParas(cy("[VD]"), t(" Đề dẫn giả hai."))),
                merged(3, cellParas(t("Gợi ý:")), cellParas(t("- gợi ý giả hai.")))));
        d.p(t("Hướng dẫn giải: "), t("Lời giải giả hai."));

        // E3: câu phụ tự luận a/ b/ còn sót ở cuối đề dẫn
        d.table(tf(merged(3, cellParas(cy("[VDC]"), t(" Đề dẫn giả ba.")), cellParas(t("a/ Câu phụ giả một?")),
                cellParas(t("b/ Câu phụ giả hai?")))));

        // E4 (đối chứng): "a/" ở giữa đề dẫn, còn đoạn thường phía sau, và "b/" nằm giữa câu: giữ nguyên
        d.table(tf(merged(3, cellParas(cy("[TH]"), t(" Đề dẫn giả bốn.")), cellParas(t("a/ Đoạn giả ở giữa đề.")),
                cellParas(t("Đoạn giả cuối có chữ b/ ở giữa câu.")))));

        doc = DocParser.parse(d.write(dir, FixtureDoc.FILE_NAME));
    }

    private static List<List<DocxBuilder.Cell>> tf(DocxBuilder.Cell stem) {
        return List.of(List.of(stem), header(), stmt("a) ", "ý a", true), stmt("b) ", "ý b", false),
                stmt("c) ", "ý c", false), stmt("d) ", "ý d", true));
    }

    private static List<List<DocxBuilder.Cell>> tfWithHint(DocxBuilder.Cell stem, DocxBuilder.Cell hint) {
        return List.of(List.of(stem), header(), stmt("a) ", "ý a", true), stmt("b) ", "ý b", false),
                stmt("c) ", "ý c", false), stmt("d) ", "ý d", true), List.of(hint));
    }

    private static List<DocxBuilder.Cell> header() {
        return List.of(cell(cellParas(t("Xét tính đúng sai các mệnh đề sau:"))), cell(cellParas(t("Đúng"))),
                cell(cellParas(t("Sai"))));
    }

    private static List<DocxBuilder.Cell> stmt(String label, String text, boolean truth) {
        return List.of(cell(cellParas(t(label), truth ? y(text) : t(text))), cell(cellParas(sym())), cell(cellParas(sym())));
    }

    private static Question q(int ordinal) {
        return doc.questions().stream().filter(x -> x.type.equals("tf") && x.ordinal == ordinal).findFirst()
                .orElseThrow(() -> new AssertionError("không có câu tf #" + ordinal));
    }

    private static void fourStatementsUnchanged(Question x) {
        assertEquals(List.of("ý a", "ý b", "ý c", "ý d"), x.statements.stream().map(s -> s.text).toList());
        assertEquals(List.of(true, false, false, true), x.statements.stream().map(s -> s.truth).toList());
    }

    @Test
    void demDuBonCauKhongTachThemCau() {
        assertEquals(4, doc.questions().size());
    }

    @Test
    void boChuGiuChoCauHoiSauNhanMuc() {
        Question x = q(1);
        assertEquals("NB", x.levelRaw);
        assertEquals("Đề dẫn giả một.", x.stem);
        assertTrue(x.flags.contains("stem_extra_text"), x.flags.toString());
        fourStatementsUnchanged(x);
    }

    @Test
    void goiYSauCacYChuyenSangLoiGiaiTruocLoiGiaiSauBang() {
        Question x = q(2);
        assertEquals("Đề dẫn giả hai.", x.stem);
        assertEquals("Gợi ý:\n- gợi ý giả hai.\nLời giải giả hai.", x.solution);
        assertTrue(x.flags.contains("hint_after_statements"), x.flags.toString());
        assertFalse(x.flags.contains("stem_extra_text"), x.flags.toString());
        fourStatementsUnchanged(x);
    }

    @Test
    void catCauPhuAbOCuoiDeDan() {
        Question x = q(3);
        assertEquals("VDC", x.levelRaw);
        assertEquals("Đề dẫn giả ba.", x.stem);
        assertTrue(x.flags.contains("stem_extra_text"), x.flags.toString());
        fourStatementsUnchanged(x);
    }

    @Test
    void doiChungAOGiuaDeVaBGiuaCauDuocGiuNguyen() {
        Question x = q(4);
        assertEquals("Đề dẫn giả bốn.\na/ Đoạn giả ở giữa đề.\nĐoạn giả cuối có chữ b/ ở giữa câu.", x.stem);
        assertFalse(x.flags.contains("stem_extra_text"), x.flags.toString());
        fourStatementsUnchanged(x);
    }
}
