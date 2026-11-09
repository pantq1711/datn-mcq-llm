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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nhóm D (ngữ cảnh dùng chung ở Phần III), tái hiện bằng tài liệu giả: ngữ cảnh là đoạn + hình đứng trước câu đầu; ngữ
 * cảnh là đoạn + bảng đứng sau "Đáp số" của câu trước; câu có bảng riêng làm dừng ngữ cảnh. Nội dung hoàn toàn tự bịa.
 */
class SaSharedContextTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final List<List<String>> TABLE_B = List.of(List.of("Mục", "x", "y"), List.of("Giá trị", "3", "4"));
    private static final List<List<String>> TABLE_C = List.of(List.of("u", "v"), List.of("5", "6"));

    @TempDir
    static Path dir;
    static ParsedDoc doc;

    @BeforeAll
    static void parse() throws IOException {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");

        // ngữ cảnh A: đoạn giới thiệu + đoạn chỉ có hình, đứng trước câu đầu tiên
        d.p("Cho bảng giả A sau:");
        d.p(d.drawing("png", PNG, 5, 4));

        question(d, "[NB]", " Câu hỏi giả một.", "1");
        question(d, "[NB]", " Câu hỏi giả hai.", "2");
        // sau "Đáp số" câu 2: lời giải thật, rồi ngữ cảnh B (đoạn giới thiệu + bảng) cho các câu sau
        d.p("Hướng dẫn giải: lời giải giả hai.");
        d.p("Cho bảng giả B sau:");
        d.table(rows(TABLE_B));

        question(d, "[TH]", " Câu hỏi giả ba.", "3");
        question(d, "[TH]", " Câu hỏi giả bốn.", "4");

        // câu 5 có bảng riêng trong thân câu: ngữ cảnh B dừng ở đây
        d.numbered(cy("[VD]"), t(" Câu hỏi giả năm có bảng riêng:"));
        d.table(rows(TABLE_C));
        d.p("Trả lời: ").p("Đáp số: 5");

        question(d, "[VD]", " Câu hỏi giả sáu.", "6");

        // đối chứng: lời giải có hình nhưng không có bảng sau "Đáp số" không phải ngữ cảnh
        question(d, "[VDC]", " Câu hỏi giả bảy.", "7");
        d.p("Hướng dẫn giải:");
        d.p(d.drawing("png", PNG, 5, 4));
        d.p("Vậy bằng 7.");

        doc = DocParser.parse(d.write(dir, FixtureDoc.FILE_NAME));
    }

    private static void question(DocxBuilder d, String level, String text, String answer) {
        d.numbered(cy(level), t(text));
        d.p("Trả lời: ").p("Đáp số: " + answer);
    }

    private static List<List<DocxBuilder.Cell>> rows(List<List<String>> table) {
        return table.stream().map(r -> r.stream().map(s -> cell(cellParas(t(s)))).toList()).toList();
    }

    private static Question q(int ordinal) {
        return doc.questions().stream().filter(x -> x.type.equals("sa") && x.ordinal == ordinal).findFirst()
                .orElseThrow(() -> new AssertionError("không có câu sa #" + ordinal));
    }

    @Test
    void demDuBayCauVaDapSoKhongDoi() {
        assertEquals(7, doc.questions().size());
        for (int i = 1; i <= 7; i++) {
            assertEquals(String.valueOf(i), q(i).answer.text, "câu " + i);
        }
    }

    @Test
    void nguCanhHinhTruocCauDauGanChoCau1Va2() {
        for (int i : new int[]{1, 2}) {
            Question x = q(i);
            assertTrue(x.stem.startsWith("Cho bảng giả A sau:\n⟦img1⟧\n"), "câu " + i + ": " + x.stem);
            assertEquals(1, x.images.size(), "câu " + i);
            assertEquals("stem", x.images.get(0).role);
            assertEquals("figure", x.images.get(0).kind);
            assertTrue(x.flags.contains("shared_context"), "câu " + i + ": " + x.flags);
        }
        assertTrue(q(1).stem.endsWith("Câu hỏi giả một."), q(1).stem);
    }

    @Test
    void loiGiaiThatGiuOCau2NguCanhBKhongDinhVaoCau2() {
        Question x = q(2);
        assertEquals("lời giải giả hai.", x.solution);
        assertNull(x.tables, String.valueOf(x.tables));
        assertFalse(x.stem.contains("giả B"), x.stem);
    }

    @Test
    void nguCanhBangSauDapSoGanChoCau3Va4() {
        for (int i : new int[]{3, 4}) {
            Question x = q(i);
            assertTrue(x.stem.startsWith("Cho bảng giả B sau:\n"), "câu " + i + ": " + x.stem);
            assertFalse(x.stem.contains("giả A"), "câu " + i + ": " + x.stem);
            assertEquals(List.of(TABLE_B), x.tables, "câu " + i);
            assertTrue(x.images.isEmpty(), "câu " + i);
            assertTrue(x.flags.contains("shared_context"), "câu " + i + ": " + x.flags);
        }
        assertNull(q(4).solution);
    }

    @Test
    void cauCoBangRiengKhongNhanNguCanhVaLamDungNguCanh() {
        Question x = q(5);
        assertEquals(List.of(TABLE_C), x.tables);
        assertFalse(x.stem.contains("giả B"), x.stem);
        assertFalse(x.flags.contains("shared_context"), x.flags.toString());

        Question y = q(6);
        assertEquals("Câu hỏi giả sáu.", y.stem);
        assertNull(y.tables, String.valueOf(y.tables));
        assertFalse(y.flags.contains("shared_context"), y.flags.toString());
    }

    @Test
    void doiChungLoiGiaiCoHinhKhongBangKhongPhaiNguCanh() {
        Question x = q(7);
        assertEquals("Câu hỏi giả bảy.", x.stem);
        assertEquals("⟦img1⟧\nVậy bằng 7.", x.solution);
        assertEquals("solution", x.images.get(0).role);
        assertFalse(x.flags.contains("shared_context"), x.flags.toString());
    }
}
