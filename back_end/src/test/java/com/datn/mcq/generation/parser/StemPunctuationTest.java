package com.datn.mcq.generation.parser;

import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.dto.ParsedSaDto;
import com.datn.mcq.generation.dto.ParsedTfDto;
import com.datn.mcq.qc.service.QcSummaryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dấu câu cuối câu dẫn và ý Đúng/Sai (góp ý giáo viên 4/11), làm ở bước giải mã của parser (chế độ production) để web và
 * Word thấy cùng một chữ, không dựa vào prompt (đo đề 16: 0/4 câu dẫn dạng hỏi có "?").
 */
class StemPunctuationTest {

    // ---------- câu dẫn dạng hỏi: thêm "?" ----------

    @Test
    void cauDanCoTuHoiKhongCoDauCauThemDauHoi() {
        // đề 16, mcq Phần I
        assertEquals("Phát biểu nào thể hiện tính chất đúng về độ dài hai cạnh bên $EH$ và $FG$ của hình thang cân đó?",
                StemPunctuation.normalizeStem("Phát biểu nào thể hiện tính chất đúng về độ dài hai cạnh bên $EH$ và $FG$ của hình thang cân đó", false));
        assertEquals("Giá trị của $x$ bằng bao nhiêu?", StemPunctuation.normalizeStem("Giá trị của $x$ bằng bao nhiêu", false));
        assertEquals("Cần mấy bước để hoàn thành $ABC$?", StemPunctuation.normalizeStem("Cần mấy bước để hoàn thành $ABC$", false));
        assertEquals("Ta làm gì tiếp theo?", StemPunctuation.normalizeStem("Ta làm gì tiếp theo", false));
        assertEquals("Khẳng định nào sau đây đúng?", StemPunctuation.normalizeStem("Khẳng định nào sau đây đúng", false));
        // "đúng hay sai" và "có phải" không còn là từ hỏi (4/11): câu mệnh lệnh kiểu "Xét tính đúng hay sai…" không thành câu hỏi.
        assertEquals("Mệnh đề này đúng hay sai", StemPunctuation.normalizeStem("Mệnh đề này đúng hay sai", false));
        assertEquals("Đây có phải hình thang cân $ABCD$", StemPunctuation.normalizeStem("Đây có phải hình thang cân $ABCD$", false));
        assertEquals("Số nào lớn hơn $3$?", StemPunctuation.normalizeStem("Số nào lớn hơn $3$", false), "kết thúc bằng công thức");
        assertEquals("PHÁT BIỂU NÀO ĐÚNG?", StemPunctuation.normalizeStem("PHÁT BIỂU NÀO ĐÚNG", false), "không phân biệt hoa thường");
    }

    @Test
    void tuHoiPhaiNamTrongCauCuoiVaLaTuNguyenVen() {
        assertEquals("Cho hình vẽ. Biết $x = 2$, tính $y$",
                StemPunctuation.normalizeStem("Cho hình vẽ. Biết $x = 2$, tính $y$", false), "câu cuối không có từ hỏi");
        assertEquals("Cho đường tròn nào đó. Tính $x$", StemPunctuation.normalizeStem("Cho đường tròn nào đó. Tính $x$", false),
                "\"nào\" ở câu trước, không phải câu cuối");
        assertEquals("Tìm cảnh gìn giữ", StemPunctuation.normalizeStem("Tìm cảnh gìn giữ", false), "so khớp theo từ: \"gì\" nằm trong \"gìn\"");
        assertEquals("Tìm giá trị $nào$ ngay", StemPunctuation.normalizeStem("Tìm giá trị $nào$ ngay", false),
                "từ trong công thức không tính");
    }

    @Test
    void cauDanDaCoDauCauHoacKetThucBangLaGiuNguyen() {
        assertEquals("Số nào lớn hơn?", StemPunctuation.normalizeStem("Số nào lớn hơn?", false));
        assertEquals("Số nào lớn hơn.", StemPunctuation.normalizeStem("Số nào lớn hơn.", false));
        assertEquals("Phát biểu nào sau đây đúng:", StemPunctuation.normalizeStem("Phát biểu nào sau đây đúng:", false));
        assertEquals("Kết quả của phép tính là", StemPunctuation.normalizeStem("Kết quả của phép tính là", false));
        assertEquals("Giá trị nào dưới đây là", StemPunctuation.normalizeStem("Giá trị nào dưới đây là", false), "câu nối kết thúc bằng là giữ nguyên");
        assertEquals("", StemPunctuation.normalizeStem("", false));
        assertEquals(null, StemPunctuation.normalizeStem(null, false));
    }

    @Test
    void cauNoiKetThucBangLaHaiChamBoHaiCham() {
        assertEquals("Kết quả của phép tính $2 + 3$ là", StemPunctuation.normalizeStem("Kết quả của phép tính $2 + 3$ là:", false));
        assertEquals("Giá trị nào dưới đây là", StemPunctuation.normalizeStem("Giá trị nào dưới đây là :", false));
        assertEquals("Phát biểu nào đúng:", StemPunctuation.normalizeStem("Phát biểu nào đúng:", false), "chỉ \"là:\" mới bị bỏ dấu");
    }

    // ---------- câu dẫn tf: thiếu dấu kết câu thì thêm "." ----------

    @Test
    void cauDanTfThieuDauKetCauThemDauCham() {
        // đề 16, tf Phần II: mất dấu chấm cuối
        assertEquals("Cho biểu thức $N = (6x - 1)^2 - (6x - 5)(6x + 5) + 3x(x - 2)$ với $x$ là số thực.",
                StemPunctuation.normalizeStem(
                        "Cho biểu thức $N = (6x - 1)^2 - (6x - 5)(6x + 5) + 3x(x - 2)$ với $x$ là số thực", true));
        assertEquals("Cho đa thức $M = 2x$.", StemPunctuation.normalizeStem("Cho đa thức $M = 2x$", true), "kết thúc bằng công thức");
    }

    @Test
    void cauDanTfKhongBaoGioThemDauHoi() {
        // Câu mệnh lệnh có "đúng hay sai": thêm ".", không thành "?".
        assertEquals("Xét tính đúng hay sai của các khẳng định sau.",
                StemPunctuation.normalizeStem("Xét tính đúng hay sai của các khẳng định sau", true));
        // Kết thúc bằng ":" thì giữ nguyên.
        assertEquals("Xét tính đúng hay sai của các khẳng định sau:",
                StemPunctuation.normalizeStem("Xét tính đúng hay sai của các khẳng định sau:", true));
        // Kể cả khi có từ hỏi của mcq/sa ("nào", "bao nhiêu"...): tf chỉ thêm ".".
        assertEquals("Với giá trị nào của $m$ thì các khẳng định sau đúng.",
                StemPunctuation.normalizeStem("Với giá trị nào của $m$ thì các khẳng định sau đúng", true));
        assertEquals("Có bao nhiêu khẳng định đúng trong các khẳng định sau.",
                StemPunctuation.normalizeStem("Có bao nhiêu khẳng định đúng trong các khẳng định sau", true));
        // Đã có "?" do model viết thì giữ nguyên (không đổi chữ có sẵn).
        assertEquals("Khẳng định nào đúng?", StemPunctuation.normalizeStem("Khẳng định nào đúng?", true));
    }

    @Test
    void cauDanTfDaCoDauHoacKetThucBangLaHoacHaiChamGiuNguyen() {
        for (String s : new String[] {"Cho $x = 2$.", "Cho $x = 2$?", "Cho $x = 2$!", "Xét các khẳng định sau:",
                "Khi đó, kết quả là", "Cho $x = 2$;", "Cho $x = 2$…", "Cho $$x = 2$$"}) {
            assertEquals(s, StemPunctuation.normalizeStem(s, true), s);
        }
        assertEquals("Xét các khẳng định sau là", StemPunctuation.normalizeStem("Xét các khẳng định sau là:", true));
    }

    @Test
    void cauDanMcqVaSaKhongTuThemDauChamChoCauMenhLenh() {
        assertEquals("Tính giá trị của biểu thức $P$ tại $x = 18$",
                StemPunctuation.normalizeStem("Tính giá trị của biểu thức $P$ tại $x = 18$", false),
                "chỉ câu hỏi mới thêm \"?\"; mcq/sa không thêm \".\"");
    }

    @Test
    void yDungSaiThemDauChamCuoiYGiongBanXuatWord() {
        assertEquals("Biểu thức $A$ luôn dương.", StemPunctuation.endStatement("Biểu thức $A$ luôn dương"));
        assertEquals("Giá trị là $3$.", StemPunctuation.endStatement("Giá trị là $3$"), "kết thúc bằng công thức");
        assertEquals("Đúng không?", StemPunctuation.endStatement("Đúng không?"));
        assertEquals("Đã có dấu.", StemPunctuation.endStatement("Đã có dấu."));
        assertEquals("Chấm than!", StemPunctuation.endStatement("Chấm than!"));
        assertEquals("", StemPunctuation.endStatement(""));
        assertEquals(null, StemPunctuation.endStatement(null));
    }

    // ---------- qua parser (chế độ production) ----------

    @Test
    void parserMcqThemDauHoiVaGhiCanhBaoKhongBatCo() {
        String raw = "{\"question_text\": \"Phát biểu nào thể hiện tính chất đúng của $EH$ và $FG$\","
                + " \"option_a\": \"$EH > FG$\", \"option_b\": \"$EH < FG$\", \"option_c\": \"$EH = FG$\","
                + " \"option_d\": \"$EH \\\\parallel FG$\", \"correct_option\": \"C\", \"explanation\": \"Ta có $EH = FG$.\"}";
        ParsedMcqDto p = McqJsonParser.parse(raw, true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertEquals("Phát biểu nào thể hiện tính chất đúng của $EH$ và $FG$?", p.questionText());
        String warning = "json: đã sửa dấu câu cuối question_text (thêm ?)";
        assertTrue(p.parseWarnings().contains(warning), p.parseWarnings().toString());
        assertTrue(QcSummaryService.isAutoFixedWarning(warning), "tự sửa chắc chắn: không bật cờ");
        assertEquals("Phát biểu nào thể hiện tính chất đúng của $EH$ và $FG$",
                McqJsonParser.parse(raw, false).questionText(), "chế độ chặt (không sửa) giữ nguyên");
    }

    @Test
    void parserSaThemDauHoiVaBoHaiChamSauLa() {
        ParsedSaDto hoi = SaJsonParser.parse("{\"question_text\": \"Giá trị của $x$ bằng bao nhiêu\","
                + " \"explanation\": \"Giải.\", \"answer\": \"3\"}", true);
        assertEquals("Giá trị của $x$ bằng bao nhiêu?", hoi.questionText());
        ParsedSaDto noi = SaJsonParser.parse("{\"question_text\": \"Giá trị của $x$ là:\","
                + " \"explanation\": \"Giải.\", \"answer\": \"3\"}", true);
        assertEquals("Giá trị của $x$ là", noi.questionText());
        assertTrue(noi.parseWarnings().contains("json: đã sửa dấu câu cuối question_text (bỏ : sau là)"),
                noi.parseWarnings().toString());
    }

    @Test
    void parserTfThemDauChamChoCauDanVaBonY() {
        ParsedTfDto p = TfJsonParser.parse("{\"question_text\": \"Cho biểu thức $N = 2x$ với $x$ là số thực\","
                + " \"statement_a\": \"Giá trị $N$ dương\", \"statement_b\": \"Giá trị $N$ bằng $4$\","
                + " \"statement_c\": \"Đã có dấu.\", \"statement_d\": \"Đúng không?\","
                + " \"explanation\": \"a) Đúng\\nb) Sai\","
                + " \"label_a\": \"T\", \"label_b\": \"F\", \"label_c\": \"T\", \"label_d\": \"F\"}", true);
        assertTrue(p.parseSuccess(), p.parseError());
        assertEquals("Cho biểu thức $N = 2x$ với $x$ là số thực.", p.questionText());
        assertEquals("Giá trị $N$ dương.", p.statements().get(0));
        assertEquals("Giá trị $N$ bằng $4$.", p.statements().get(1));
        assertEquals("Đã có dấu.", p.statements().get(2));
        assertEquals("Đúng không?", p.statements().get(3));
        assertEquals("a) Đúng\nb) Sai", p.explanation(), "lời giải không bị thêm dấu");
        assertTrue(p.parseWarnings().contains("json: đã sửa dấu câu cuối question_text (thêm .)"), p.parseWarnings().toString());
        assertTrue(p.parseWarnings().contains("json: đã sửa dấu câu cuối statement_a (thêm .)"), p.parseWarnings().toString());
        assertTrue(p.parseWarnings().stream().allMatch(w -> !w.startsWith("json: đã sửa dấu câu") || QcSummaryService.isAutoFixedWarning(w)));

        ParsedTfDto strict = TfJsonParser.parse("{\"question_text\": \"Cho $N$\", \"statement_a\": \"A\","
                + " \"statement_b\": \"B\", \"statement_c\": \"C\", \"statement_d\": \"D\", \"explanation\": \"E\","
                + " \"label_a\": \"T\", \"label_b\": \"T\", \"label_c\": \"T\", \"label_d\": \"T\"}", false);
        assertEquals("Cho $N$", strict.questionText(), "chế độ chặt (không sửa) giữ nguyên");
        assertEquals("A", strict.statements().get(0));
    }
}
