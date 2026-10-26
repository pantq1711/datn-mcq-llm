package com.datn.mcq.qc.checker;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.datn.mcq.qc.checker.TfExplanationLeak.Kind.PATTERN_MENTION;
import static com.datn.mcq.qc.checker.TfExplanationLeak.Kind.SELF_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lời giải câu Đúng/Sai do LLM sinh mà tự thú nhận gán nhãn theo mẫu hay yêu cầu, hoặc tự sửa đề trong lời giải. Các đoạn
 * dương tính là đoạn THẬT trích từ đo baseline 26/10 (6 trong 27 lần sinh); các đoạn âm tính gồm cả lời giải sạch của baseline
 * và những câu toán bình thường có chữ gần giống ("mẫu số").
 */
class TfExplanationLeakTest {

    private static Set<TfExplanationLeak.Kind> kinds(TfExplanationLeak.Kind... kinds) {
        return kinds.length == 0 ? EnumSet.noneOf(TfExplanationLeak.Kind.class) : EnumSet.copyOf(List.of(kinds));
    }

    // ---------- 6 ca thật ở baseline ----------

    @Test
    void bacaThuNhanThatCuaBaseline() {
        // q78: nhãn yêu cầu, theo mẫu T,F,T,T, rồi "Xét lại:"
        assertEquals(kinds(PATTERN_MENTION, SELF_REVISION), TfExplanationLeak.detect(
                "(lưu ý đề ghi 23 là nội dung giải thích sai số liệu so với nhãn yêu cầu T,F,T,T nhưng dựa theo mẫu T,F,T,T "
                        + "thì lời giải phải dẫn đến kết quả khớp mệnh đề c là đúng: $P = x^2 + 10x + 19$. Xét lại: nếu "
                        + "$P = (x+1)^2 + 13$... ta chọn lời giải thích bám sát đề)."));
        // q79
        assertEquals(kinds(PATTERN_MENTION, SELF_REVISION), TfExplanationLeak.detect(
                "Xin lỗi, ý c đúng về hệ số nhưng theo mẫu nhãn yêu cầu c phải Sai, do đó ta xét: $Q = 16x^2 - 16x - 2$."));
        // q81
        assertEquals(kinds(PATTERN_MENTION, SELF_REVISION), TfExplanationLeak.detect(
                "nên thực ra ý c phải sai theo yêu cầu mẫu nhãn (F, F, T, T) thì ta cần cấu trúc lại sao cho c đúng. "
                        + "Xin lỗi, ta làm lại biểu thức để c đúng và d đúng: Cho biểu thức $N = ...$"));
        // q92
        assertEquals(kinds(PATTERN_MENTION), TfExplanationLeak.detect(
                "giá trị nhỏ nhất của $M$ là $-13$ chứ không phải $\\frac{55}{19}$, vậy ý này sai theo mẫu bắt buộc F, T, F, T."));
        // q91
        assertEquals(kinds(PATTERN_MENTION, SELF_REVISION), TfExplanationLeak.detect(
                "Thực chất theo mẫu yêu cầu F,F,T,T thì ý c ở đây nếu xét theo đề bài giả định cần phải đúng cấu trúc. "
                        + "Ta chỉnh lại phát biểu c là: Biểu thức thu gọn của $Q$ có hệ số của $x$ bằng $12$."));
        // q97
        assertEquals(kinds(PATTERN_MENTION), TfExplanationLeak.detect(
                "Vậy giá trị đúng phải là $26$, tuy nhiên vì nhãn yêu cầu F, ta xét theo lời giải chuẩn: giá trị tính được là "
                        + "$26$ nhưng câu c nhận định sai theo mẫu hoặc tính sai."));
    }

    @Test
    void bienTheKhongDauHoaThuongVaXuongDongVanBatDuoc() {
        assertEquals(kinds(PATTERN_MENTION), TfExplanationLeak.detect("THEO MẪU YÊU CẦU, ý b là Sai."));
        assertEquals(kinds(PATTERN_MENTION), TfExplanationLeak.detect("theo mau yeu cau gan nhan F"));
        assertEquals(kinds(PATTERN_MENTION), TfExplanationLeak.detect("ý c đúng nhưng theo mẫu\nyêu cầu thì gán sai"));
        assertEquals(kinds(PATTERN_MENTION), TfExplanationLeak.detect("ý c được   gán  nhãn   F để khớp"));
        assertEquals(kinds(SELF_REVISION), TfExplanationLeak.detect("XIN LỖI, tôi tính nhầm ở bước trên."));
        assertEquals(kinds(SELF_REVISION), TfExplanationLeak.detect("Xin loi, ta lam lai bieu thuc."));
        String decomposed = Normalizer.normalize("Ta chỉnh lại phát biểu c cho đúng.", Normalizer.Form.NFD);
        assertEquals(kinds(SELF_REVISION), TfExplanationLeak.detect(decomposed), "chuỗi NFD (dấu rời) vẫn bắt được");
        assertEquals(kinds(PATTERN_MENTION), TfExplanationLeak.detect("khớp với nhãn mục tiêu T,F,T,F nên chọn T"));
    }

    // ---------- không báo nhầm ----------

    @Test
    void lamSachCauToanBinhThuongKhongBiBaoNham() {
        for (String ok : List.of(
                "Rút gọn phân thức: mẫu số là $x - 1$ nên điều kiện là $x \\neq 1$.",
                "Theo mẫu số chung, quy đồng hai phân thức ta được kết quả đúng.",
                "Theo yêu cầu của đề bài, ta tính giá trị của biểu thức tại $x = 2$.",
                "Ý a đúng vì $(A + B)^2 = A^2 + 2AB + B^2$; ý b sai vì thiếu hạng tử $2AB$.",
                "Thay $x = -1$ vào biểu thức thu gọn ta được $20$, nên khẳng định này là Đúng.",
                "Ta xét lại bài toán bằng cách đặt ẩn phụ $t = x + 1$.",
                "Dấu nhãn hiệu trên sản phẩm không liên quan tới bài này.",
                // lời giải sạch thật của baseline (q93)
                "Ý a: Theo hằng đẳng thức bình phương của một hiệu, ta có $(3x - 2)^2 = (3x)^2 - 2 \\cdot 3x \\cdot 2 + 2^2 = "
                        + "9x^2 - 12x + 4$. Hạng tử thứ hai là $-12x$, do đó khẳng định này là Sai. Ý b: ta có $P = -12x + 8$, "
                        + "nên khẳng định này là Đúng. Ý d: khi $x = 1$, ta tính được $P = -4 < 0$, vậy khẳng định này là Sai.")) {
            assertEquals(kinds(), TfExplanationLeak.detect(ok), ok);
            assertTrue(TfExplanationLeak.errors(ok).isEmpty(), ok);
        }
    }

    @Test
    void trongHoacNullKhongCoGi() {
        assertEquals(kinds(), TfExplanationLeak.detect(null));
        assertEquals(kinds(), TfExplanationLeak.detect("   "));
        assertTrue(TfExplanationLeak.errors(null).isEmpty());
    }

    /** W2-5: Lời giải mẫu theo dạng mới (tính toán trước từng ý rồi kết luận khẳng định là Đúng/Sai) không bị chặn. */
    @Test
    void loiGiaiMauDangMoiKhongBiChan() {
        String newStyleExplanation = "a) Ta có $(2x - 3)^2 = 4x^2 - 12x + 9$. Hệ số của $x$ là $-12$, do đó khẳng định ý a là Đúng.\\n"
                + "b) Khi $x = 1$, ta có $P = 4(1)^2 - 12(1) + 9 = 1 \\\\neq 0$, nên khẳng định ý b là Sai.\\n"
                + "c) Biểu thức $P = (2x - 3)^2 \\\\ge 0$ với mọi $x$, do đó khẳng định giá trị nhỏ nhất của $P$ bằng $0$ là Đúng.\\n"
                + "d) Giá trị nhỏ nhất đạt được khi $2x - 3 = 0 \\\\Leftrightarrow x = \\\\frac{3}{2} \\\\neq 3$, nên khẳng định đạt tại $x = 3$ là Sai.";
        assertEquals(kinds(), TfExplanationLeak.detect(newStyleExplanation));
        assertTrue(TfExplanationLeak.errors(newStyleExplanation).isEmpty());
    }

    // ---------- thông báo lỗi ----------

    @Test
    void thongBaoLoiMoiNhomMotDongBatDauBangTruongExplanation() {
        List<String> both = TfExplanationLeak.errors("Xin lỗi, theo mẫu nhãn yêu cầu thì ý c phải Sai.");
        assertEquals(2, both.size(), both.toString());
        assertEquals("explanation: lời giải nhắc tới mẫu nhãn hoặc việc gán nhãn theo yêu cầu; nhãn phải theo đúng/sai thật của "
                + "từng ý", both.get(0));
        assertEquals("explanation: lời giải tự sửa lại đề hoặc ý (xin lỗi, làm lại, chỉnh lại); hãy viết lời giải cuối cùng "
                + "nhất quán với các ý", both.get(1));
        assertEquals(1, TfExplanationLeak.errors("vậy ý này sai theo mẫu bắt buộc F, T, F, T").size());
    }
}
