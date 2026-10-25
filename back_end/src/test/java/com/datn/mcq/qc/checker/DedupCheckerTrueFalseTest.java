package com.datn.mcq.qc.checker;

import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Dedup câu Đúng/Sai (GĐ3): văn bản chuẩn = đề dẫn + 4 ý đã chuẩn hóa và sắp xếp (không gồm nhãn); chốt chặn số liệu
 * gộp đề dẫn và 4 ý; chốt chặn phủ định so phủ định của đề dẫn và danh sách phủ định của 4 ý đã sắp xếp;
 * chỉ so với câu cùng dạng.
 */
class DedupCheckerTrueFalseTest {

    private static final double TAU = DedupChecker.PROVISIONAL_THRESHOLD;
    private static final String STEM = "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$ với $x \\neq 0$ và $x \\neq 1$.";
    private static final List<String> STATEMENTS = List.of(
            "Rút gọn được $P = \\frac{x+1}{x}$.",
            "Tại $x = 2$ thì $P = 2$.",
            "Có đúng hai giá trị nguyên của $x$ để $P$ nguyên.",
            "Giá trị của $P$ tại $x = -1$ bằng $0$.");

    private static DedupOutcome check(DedupCandidate subject, DedupCandidate... candidates) {
        return DedupChecker.check(subject, List.of(candidates), TAU);
    }

    @Test
    void daoThuTuYVanTrungDauVanTay() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM,
                        List.of(STATEMENTS.get(3), STATEMENTS.get(1), STATEMENTS.get(0), STATEMENTS.get(2))),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.FINGERPRINT, out.layer());
    }

    // Sửa theo quyết định Q6-Q7 ngày 22/10: trước đây hai ca dưới là NO_DUPLICATE (chốt chặn số liệu/phủ định áp cho
    // cả câu). Nay cùng đề dẫn và 3/4 ý giống nhau là trùng, không áp chốt chặn ở mức từng ý.
    @Test
    void khacSoLieuTrongMotYVanTrungTheoY() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM,
                        List.of(STATEMENTS.get(0), "Tại $x = 3$ thì $P = 2$.", STATEMENTS.get(2), STATEMENTS.get(3))),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.TF_STATEMENTS, out.layer());
    }

    @Test
    void motYBiPhuDinhVanTrungTheoY() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM,
                        List.of(STATEMENTS.get(0), "Tại $x = 2$ thì $P$ không bằng $2$.", STATEMENTS.get(2),
                                STATEMENTS.get(3))),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.TF_STATEMENTS, out.layer());
    }

    @Test
    void cungMotYPhuDinhVanSoBangCosine() {
        // Hai câu đều có đúng một ý phủ định và cùng số liệu, nên qua hai chốt chặn và so bằng cosine.
        List<String> first = List.of(STATEMENTS.get(0), STATEMENTS.get(1), STATEMENTS.get(2),
                "Giá trị của $P$ tại $x = -1$ không bằng $0$.");
        List<String> second = List.of(STATEMENTS.get(0), STATEMENTS.get(1), STATEMENTS.get(2),
                "Giá trị của $P$ tại $x = -1$ không bằng $0$ đâu.");
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM, second), DedupCandidate.trueFalse(1, STEM, first));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.TRIGRAM, out.layer());
    }

    @Test
    void khongSoVoiCauKhacDang() {
        DedupCandidate mcq = new DedupCandidate(1, STEM, STATEMENTS.get(0), STATEMENTS.get(1), STATEMENTS.get(2),
                STATEMENTS.get(3));
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM, STATEMENTS), mcq);
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
        assertEquals("Không trùng với 0 ứng viên", out.detail());
    }

    @Test
    void cauTfThieuYKhongSoDuoc() {
        assertThrows(IllegalArgumentException.class, () -> check(DedupCandidate.trueFalse(2, STEM,
                List.of("a", "b", "c"))));
    }

    // ---------- quy tắc theo ý (Q6-Q7 ngày 22/10) ----------

    @Test
    void chi2Tren4YGiongThiKhongTrung() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM, List.of(STATEMENTS.get(0), STATEMENTS.get(1),
                        "Biểu thức $P$ luôn dương khi $x > 1$.", "Phương trình $P = 3$ có nghiệm $x = \\frac{1}{2}$.")),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
    }

    @Test
    void ghepYKhongPhuThuocThuTu() {
        DedupOutcome out = check(DedupCandidate.trueFalse(2, STEM, List.of(STATEMENTS.get(3), STATEMENTS.get(0),
                        "Biểu thức $P$ luôn dương khi $x > 1$.", STATEMENTS.get(1))),
                DedupCandidate.trueFalse(1, STEM, STATEMENTS));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.TF_STATEMENTS, out.layer());
        assertEquals(1, out.comparedToQuestionId());
    }

    @Test
    void moiYChiGhepVoiMotY() {
        // Ba ý của câu sau đều gần ý a của câu trước; ghép một-một chỉ được 2 cặp (a và d) nên không trùng.
        List<String> subject = List.of(TF4.get(1),
                "Phương trình đã cho là phương trình bậc nhất một ẩn với ẩn số là $x$.",
                "Phương trình đã cho là một phương trình bậc nhất một ẩn với ẩn là $x$.", TF4.get(4));
        DedupOutcome out = check(DedupCandidate.trueFalse(2, TF4.get(0), subject),
                DedupCandidate.trueFalse(1, TF4.get(0), TF4.subList(1, 5)));
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
    }

    @Test
    void deDanKhacSoLieuThiKhongTrungDu4YGiongHet() {
        String stem = "Cho phương trình bậc nhất một ẩn $2x - 8 = 0$. Xét tính đúng sai của các khẳng định sau:";
        DedupOutcome out = check(DedupCandidate.trueFalse(2, stem, TF8.subList(1, 5)), candidate(TF8));
        assertEquals(DedupStatus.NO_DUPLICATE, out.status());
    }

    @Test
    void deDanLechPhuDinhThiKhongTrung() {
        // Đề dẫn ở đây không có cụm "đúng sai": chữ "sai" trong "Xét tính đúng sai" đã bị tính là phủ định (ca biên).
        String base = "Cho phương trình bậc nhất một ẩn $2x - 6 = 0$, học sinh ";
        List<String> statements = List.of(TF8.get(1), TF8.get(2), TF8.get(3), "Ý khác hẳn về đồ thị hàm số $y = x$.");
        DedupOutcome negated = check(DedupCandidate.trueFalse(2, base + "không được dùng máy tính cầm tay.", statements),
                DedupCandidate.trueFalse(1, base + "được dùng máy tính cầm tay.", TF8.subList(1, 5)));
        assertEquals(DedupStatus.NO_DUPLICATE, negated.status());
        DedupOutcome control = check(DedupCandidate.trueFalse(2, base + "vẫn được dùng máy tính cầm tay.", statements),
                DedupCandidate.trueFalse(1, base + "được dùng máy tính cầm tay.", TF8.subList(1, 5)));
        assertEquals(DedupStatus.DUPLICATE, control.status(), "cùng câu đó, bỏ chữ phủ định thì trùng");
        assertEquals(DedupOutcome.Layer.TF_STATEMENTS, control.layer());
    }

    // ---------- dữ liệu thật: datn_mcq_feature, câu tf id 2, 4, 6, 8, 10 (tf_1..5.json, ngày 22/10) ----------

    /** Phần tử 0 là đề dẫn, 1-4 là ý a-d. */
    private static final List<String> TF2 = List.of(
            "Cho phương trình bậc nhất một ẩn $2x - 6 = 0$. Xét tính đúng sai của các khẳng định sau:",
            "Phương trình đã cho là phương trình bậc nhất một ẩn với ẩn là $x$.",
            "Hạng số tự do chuyển vế đổi dấu ta được phương trình $2x = 6$.",
            "Nghiệm của phương trình là $x = -3$.",
            "Giá trị của biểu thức $A = 5x + 1$ tại nghiệm của phương trình là $16$.");
    private static final List<String> TF4 = List.of(
            "Cho phương trình bậc nhất một ẩn $2x - 6 = 0$.",
            "Phương trình đã cho là phương trình bậc nhất một ẩn với ẩn là $x$.",
            "Chuyển vế số $-6$ sang vế phải ta được phương trình $2x = 6$.",
            "Nghiệm của phương trình là $x = -3$.",
            "Giá trị của biểu thức $A = 3x^2 - 5$ tại nghiệm của phương trình là $22$.");
    private static final List<String> TF6 = List.of(
            "Cho phương trình bậc nhất một ẩn $2x - 6 = 0$. Các khẳng định sau đây là đúng hay sai?",
            "Phương trình đã cho là phương trình bậc nhất một ẩn với ẩn số là $x$.",
            "Hệ số của ẩn $x$ trong phương trình là $2$.",
            "Nghiệm của phương trình $2x - 6 = 0$ là $x = 3$.",
            "Giá trị của biểu thức $A = 3x^2 - 5$ tại nghiệm của phương trình đã cho bằng $22$.");
    private static final List<String> TF8 = List.of(
            "Cho phương trình bậc nhất một ẩn $2x - 6 = 0$. Xét tính đúng sai của các khẳng định sau:",
            "Phương trình đã cho là phương trình bậc nhất một ẩn với ẩn là $x$.",
            "Hạng số tự do chuyển vế đổi dấu ta được phương trình $2x = 6$.",
            "Nghiệm của phương trình là $x = -3$.",
            "Giá trị của biểu thức $A = 3x^2 - 5$ tại nghiệm của phương trình là $22$.");
    private static final List<String> TF10 = List.of(
            "Cho phương trình bậc nhất một ẩn $2x - 6 = 0$. Xét tính đúng sai của các khẳng định sau:",
            "Phương trình $2x - 6 = 0$ là một phương trình bậc nhất một ẩn với ẩn là $x$.",
            "Hằng số tự do khi chuyển vế đổi dấu trong phương trình trên là $6$.",
            "Nghiệm của phương trình đã cho là $x = -3$.",
            "Giá trị của biểu thức $A = 3x^2 - 5$ tại nghiệm của phương trình là $22$.");

    private static DedupCandidate candidate(List<String> q) {
        int id = q == TF2 ? 2 : q == TF4 ? 4 : q == TF6 ? 6 : q == TF8 ? 8 : 10;
        return DedupCandidate.trueFalse(id, q.get(0), q.subList(1, 5));
    }

    @Test
    void duLieuThat_cau8TrungCau2TheoY() {
        // Cùng đề dẫn; a, b, c giống hệt; ý d khác biểu thức và số liệu nên tầng cosine cả câu bị chốt chặn bỏ qua.
        DedupOutcome out = check(candidate(TF8), candidate(TF2), candidate(TF4), candidate(TF6));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.TF_STATEMENTS, out.layer());
        assertEquals(2, out.comparedToQuestionId());
        assertEquals(1.0, out.similarity(), 1e-9);
    }

    /**
     * Quyết định 4 (sau phần A): bỏ câu chỉ dẫn chung khỏi đề dẫn trước khi so. Đề dẫn 4 và 8 khi đó giống nhau, ý a, c, d
     * giống hệt nên trùng theo ý.
     */
    @Test
    void duLieuThat_cau8TrungCau4SauKhiBoCauChiDan() {
        DedupOutcome out = check(candidate(TF8), candidate(TF4));
        assertEquals(DedupStatus.DUPLICATE, out.status());
        assertEquals(DedupOutcome.Layer.TF_STATEMENTS, out.layer());
        assertEquals(4, out.comparedToQuestionId());
    }

    /**
     * Ca biên chấp nhận (quyết định 4): 6-4 chỉ 2 ý >= tau (ý c 0.78: $x = 3$ so với $x = -3$). 8-10 và 2-10: đề dẫn
     * giống nhưng ý a, c chỉ 0.88. Không hạ ngưỡng.
     */
    @Test
    void duLieuThat_caBienKhongTrung() {
        assertEquals(DedupStatus.NO_DUPLICATE, check(candidate(TF6), candidate(TF4)).status(), "6-4");
        assertEquals(DedupStatus.NO_DUPLICATE, check(candidate(TF10), candidate(TF8)).status(), "8-10");
        assertEquals(DedupStatus.NO_DUPLICATE, check(candidate(TF10), candidate(TF2)).status(), "2-10");
    }

    @Test
    void boCauChiDanTruocKhiSoDeDan() {
        String bare = "Cho phương trình bậc nhất một ẩn $2x - 6 = 0$.";
        for (String phrase : McqTextNormalizer.TF_INSTRUCTION_PHRASES) {
            String stem = bare + " " + phrase + ":";
            assertEquals(bare, McqTextNormalizer.stripTfInstruction(stem), phrase);
            assertEquals(bare, McqTextNormalizer.stripTfInstruction(bare + " " + phrase.toUpperCase() + "?"), phrase);
            assertEquals(bare, McqTextNormalizer.stripTfInstruction(bare + "  " + phrase.replace(" ", "  ")), phrase);
        }
        assertEquals("Cho $P = x$.", McqTextNormalizer.stripTfInstruction("Cho $P = x$."), "không có cụm thì giữ");
        assertEquals("Xét tính đúng sai của các khẳng định sau về $P$:",
                McqTextNormalizer.stripTfInstruction("Xét tính đúng sai của các khẳng định sau về $P$:"),
                "cụm không đứng trọn vẹn (còn chữ phía sau trước dấu câu) thì giữ");
    }

    @Test
    void chuSaiTrongCauChiDanKhongTinhLaPhuDinh() {
        String tail = " Xét tính đúng sai của các khẳng định sau:";
        String stem = "Cho phương trình $2x - 6 = 0$, học sinh được dùng máy tính cầm tay." + tail;
        String negated = "Cho phương trình $2x - 6 = 0$, học sinh không được dùng máy tính cầm tay." + tail;
        String control = "Cho phương trình $2x - 6 = 0$, học sinh vẫn được dùng máy tính cầm tay." + tail;
        List<String> statements = List.of(TF8.get(1), TF8.get(2), TF8.get(3), "Ý khác hẳn về đồ thị hàm số $y = x$.");
        // Trước quyết định 4, cả hai đề dẫn đều "có phủ định" (chữ sai) nên chốt chặn không tách được.
        assertEquals(DedupStatus.NO_DUPLICATE, check(DedupCandidate.trueFalse(2, negated, statements),
                DedupCandidate.trueFalse(1, stem, TF8.subList(1, 5))).status());
        assertEquals(DedupStatus.DUPLICATE, check(DedupCandidate.trueFalse(2, control, statements),
                DedupCandidate.trueFalse(1, stem, TF8.subList(1, 5))).status(), "bỏ chữ không thì trùng");
    }
}
