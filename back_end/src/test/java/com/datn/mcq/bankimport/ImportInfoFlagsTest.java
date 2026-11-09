package com.datn.mcq.bankimport;

import com.datn.mcq.bankimport.dto.BankImportDtos.ImportItem;
import com.datn.mcq.bankimport.dto.BankImportDtos.PreviewData;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import com.datn.mcq.bankimport.service.ImportFlags;
import com.datn.mcq.bankimport.service.ImportPreviewBuilder;
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
 * Bốn cờ mới của nhóm E, B, D là cờ THÔNG TIN: không bao giờ chặn câu khi xác nhận (422 IMPORT_ITEM_BLOCKED), và tên
 * cờ do bộ phân tích gắn phải khớp hằng số của {@link ImportFlags}. Tài liệu giả, nội dung tự bịa.
 */
class ImportInfoFlagsTest {

    private static final List<String> NEW_FLAGS = List.of(ImportFlags.STEM_EXTRA_TEXT,
            ImportFlags.FIGURE_FROM_PREVIOUS_QUESTION, ImportFlags.MENTIONS_FIGURE_NO_IMAGE, ImportFlags.SHARED_CONTEXT);

    @TempDir
    static Path dir;
    static ParsedDoc doc;
    static PreviewData preview;

    @BeforeAll
    static void parse() throws IOException {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN II. CÂU TRẮC NGHIỆM ĐÚNG SAI");
        // chữ giữ chỗ sau nhãn mức: cờ stem_extra_text
        d.table(List.of(List.of(merged(3, cellParas(cy("[NB]"), t(" Câu hỏi…")), cellParas(t("Đề dẫn giả.")))),
                List.of(cell(cellParas(t("Xét tính đúng sai các mệnh đề sau:"))), cell(cellParas(t("Đúng"))),
                        cell(cellParas(t("Sai")))),
                stmt("a) ", "ý a", true), stmt("b) ", "ý b", false), stmt("c) ", "ý c", false), stmt("d) ", "ý d", true)));
        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");
        // đoạn giới thiệu chỉ có chữ đứng trước câu đầu: ngữ cảnh dùng chung (không ảnh, không bảng nên không bị bỏ)
        d.p("Cho dữ kiện giả sau:");
        d.numbered(cy("[NB]"), t(" Câu hỏi giả một."));
        d.p("Trả lời: ").p("Đáp số: 1");
        // đề nhắc hình mà không có ảnh: cờ mentions_figure_no_image
        d.numbered(cy("[TH]"), t(" Cho hình vẽ bên, tính giá trị giả."));
        d.p("Trả lời: ").p("Đáp số: 2");
        doc = ParseSupport.parse(d, dir);
        preview = ImportPreviewBuilder.build(doc, List.of());
    }

    private static List<DocxBuilder.Cell> stmt(String label, String text, boolean truth) {
        return List.of(cell(cellParas(t(label), truth ? y(text) : t(text))), cell(cellParas(sym())), cell(cellParas(sym())));
    }

    private static ImportItem item(String type, int ordinal) {
        return preview.items().stream().filter(i -> i.type().equals(type))
                .skip(ordinal - 1).findFirst()
                .orElseThrow(() -> new AssertionError("không có câu " + type + " #" + ordinal));
    }

    @Test
    void tenCoLaHangSoDuocKhoa() {
        assertEquals(List.of("stem_extra_text", "figure_from_previous_question", "mentions_figure_no_image",
                "shared_context"), NEW_FLAGS);
    }

    @Test
    void bonCoMoiKhongBaoGioLaCoChan() {
        for (String f : NEW_FLAGS) {
            assertFalse(ImportFlags.isBlocking(f), f);
        }
    }

    @Test
    void boPhanTichGanDungTenCoCuaHangSo() {
        var tf = doc.questions().stream().filter(q -> q.type.equals("tf")).findFirst().orElseThrow();
        assertTrue(tf.flags.contains(ImportFlags.STEM_EXTRA_TEXT), tf.flags.toString());
        var sa = doc.questions().stream().filter(q -> q.type.equals("sa")).toList();
        assertEquals(2, sa.size());
        assertTrue(sa.get(0).flags.contains(ImportFlags.SHARED_CONTEXT), sa.get(0).flags.toString());
        assertTrue(sa.get(1).flags.contains(ImportFlags.SHARED_CONTEXT), sa.get(1).flags.toString());
        assertTrue(sa.get(1).flags.contains(ImportFlags.MENTIONS_FIGURE_NO_IMAGE), sa.get(1).flags.toString());
    }

    @Test
    void cauMangCoThongTinVanVaoBanXemTruocVaChonDuoc() {
        assertEquals(3, preview.items().size(), preview.skipped().toString());
        assertTrue(preview.skipped().isEmpty(), preview.skipped().toString());
        ImportItem tf = item("tf", 1);
        assertTrue(tf.flags().contains(ImportFlags.STEM_EXTRA_TEXT), tf.flags().toString());
        assertFalse(tf.blocking(), tf.flags().toString());
        ImportItem sa1 = item("sa", 1);
        assertTrue(sa1.flags().contains(ImportFlags.SHARED_CONTEXT), sa1.flags().toString());
        assertFalse(sa1.blocking(), sa1.flags().toString());
        ImportItem sa2 = item("sa", 2);
        assertTrue(sa2.flags().contains(ImportFlags.MENTIONS_FIGURE_NO_IMAGE), sa2.flags().toString());
        assertFalse(sa2.blocking(), sa2.flags().toString());
    }
}
