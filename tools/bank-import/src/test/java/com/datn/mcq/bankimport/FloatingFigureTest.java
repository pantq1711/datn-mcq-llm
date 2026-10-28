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
 * Nhóm B (hình không vào đúng câu), tái hiện bằng tài liệu giả: hình nổi (wp:anchor) neo ở đoạn phương án của câu trước
 * nhưng thuộc câu sau; hình nổi neo ở đoạn "Trả lời:"; đề nhắc hình mà hình chỉ có ở lời giải. Nội dung hoàn toàn tự bịa.
 */
class FloatingFigureTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @TempDir
    static Path dir;
    static ParsedDoc doc;

    @BeforeAll
    static void parse() throws IOException {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");

        // mcq 1 (đề không nhắc hình) có hình nổi neo ở đoạn phương án; hình thuộc mcq 2 ("Cho hình vẽ bên")
        d.numbered(cy("[NB]"), t(" Câu giả một không có hình."));
        d.p(t("A. "), y("một")).p(d.anchored("png", PNG, 5, 4), t("B. hai")).p("C. ba").p("D. bốn");
        d.numbered(cy("[TH]"), t(" Cho hình vẽ bên, tính giả hai."));
        d.p("A. một").p(t("B. "), y("hai")).p("C. ba").p("D. bốn");

        // đối chứng: hình nổi ở đoạn phương án của mcq 3, nhưng mcq 4 không nhắc hình → ở lại mcq 3
        d.numbered(cy("[TH]"), t(" Câu giả ba."));
        d.p(t("A. "), y("một")).p("B. hai").p(d.anchored("png", PNG, 5, 4), t("C. ba")).p("D. bốn");
        d.numbered(cy("[TH]"), t(" Câu giả bốn không có hình."));
        d.p("A. một").p("B. hai").p(t("C. "), y("ba")).p("D. bốn");

        // đối chứng: hình cùng dòng (inline) trong phương án B của mcq 5 không chuyển, dù mcq 6 nhắc hình
        d.numbered(cy("[VD]"), t(" Câu giả năm."));
        d.p(t("A. "), y("một")).p(t("B. hai "), d.drawing("png", PNG, 5, 4)).p("C. ba").p("D. bốn");
        d.numbered(cy("[VD]"), t(" Cho hình vẽ sau, tính giả sáu."));
        d.p("A. một").p("B. hai").p("C. ba").p(t("D. "), y("bốn"));

        // đối chứng: hình nổi neo ở đoạn đầu câu của mcq 7 ở lại mcq 7
        d.numbered(cy("[VD]"), t(" Câu giả bảy."), d.anchored("png", PNG, 5, 4));
        d.p(t("A. "), y("một")).p("B. hai").p("C. ba").p("D. bốn");
        d.numbered(cy("[VDC]"), t(" Quan sát hình vẽ, tính giả tám."));
        d.p("A. một").p(t("B. "), y("hai")).p("C. ba").p("D. bốn");

        d.p("PHẦN III. CÂU TRẮC NGHIỆM TRẢ LỜI NGẮN");

        // sa 1: hình nổi neo ở đoạn "Trả lời:" (hiển thị cạnh đề)
        d.numbered(cy("[NB]"), t(" Tính giả một."));
        d.p(t("Trả lời: "), d.anchored("png", PNG, 5, 4));
        d.p("Đáp số: 1");

        // sa 2: đề nhắc "hình dưới đây" nhưng hình chỉ có trong lời giải
        d.numbered(cy("[TH]"), t(" Quan sát hình dưới đây, tính giả hai."));
        d.p("Trả lời: ").p("Đáp số: 2");
        d.p("Hướng dẫn giải:");
        d.p(d.drawing("png", PNG, 5, 4));
        d.p("Vậy bằng 2.");

        doc = DocParser.parse(d.write(dir, FixtureDoc.FILE_NAME));
    }

    private static Question q(String type, int ordinal) {
        return doc.questions().stream().filter(x -> x.type.equals(type) && x.ordinal == ordinal).findFirst()
                .orElseThrow(() -> new AssertionError("không có câu " + type + " #" + ordinal));
    }

    private static List<String> roles(Question x) {
        return x.images.stream().map(i -> i.role).toList();
    }

    @Test
    void demDuCau() {
        assertEquals(8, doc.questions().stream().filter(x -> x.type.equals("mcq")).count());
        assertEquals(2, doc.questions().stream().filter(x -> x.type.equals("sa")).count());
    }

    @Test
    void hinhNoiODoanPhuongAnChuyenSangCauSauNhacHinh() {
        Question a = q("mcq", 1);
        assertTrue(a.images.isEmpty(), a.images.toString());
        assertFalse(a.stem.contains("⟦img"), a.stem);
        assertEquals("A", a.answer.value);
        assertEquals("hai", a.options.get(1).text);

        Question b = q("mcq", 2);
        assertEquals(List.of("stem"), roles(b));
        assertEquals("figure", b.images.get(0).kind);
        assertTrue(b.stem.endsWith("⟦img1⟧"), b.stem);
        assertTrue(b.flags.contains("figure_from_previous_question"), b.flags.toString());
        assertFalse(b.flags.contains("mentions_figure_no_image"), b.flags.toString());
        assertEquals("B", b.answer.value);
    }

    @Test
    void doiChungCauSauKhongNhacHinhThiHinhOLai() {
        assertEquals(List.of("stem"), roles(q("mcq", 3)));
        Question y = q("mcq", 4);
        assertTrue(y.images.isEmpty(), y.images.toString());
        assertFalse(y.flags.contains("figure_from_previous_question"), y.flags.toString());
    }

    @Test
    void doiChungHinhCungDongKhongChuyen() {
        assertEquals(List.of("option_B"), roles(q("mcq", 5)));
        Question y = q("mcq", 6);
        assertTrue(y.images.isEmpty(), y.images.toString());
        assertFalse(y.flags.contains("figure_from_previous_question"), y.flags.toString());
        assertTrue(y.flags.contains("mentions_figure_no_image"), y.flags.toString());
    }

    @Test
    void doiChungHinhNoiODoanDauCauOLai() {
        assertEquals(List.of("stem"), roles(q("mcq", 7)));
        Question y = q("mcq", 8);
        assertTrue(y.images.isEmpty(), y.images.toString());
        assertFalse(y.flags.contains("figure_from_previous_question"), y.flags.toString());
    }

    @Test
    void hinhNoiODoanTraLoiLaHinhCuaDe() {
        Question x = q("sa", 1);
        assertEquals(List.of("stem"), roles(x));
        assertTrue(x.stem.contains("⟦img1⟧"), x.stem);
        assertFalse(x.stem.contains("Trả lời"), x.stem);
        assertEquals("1", x.answer.text);
    }

    @Test
    void deNhacHinhMaHinhChiOLoiGiaiThiCoCo() {
        Question x = q("sa", 2);
        assertEquals(List.of("solution"), roles(x));
        assertTrue(x.flags.contains("mentions_figure_no_image"), x.flags.toString());
        assertEquals("2", x.answer.text);
    }
}
