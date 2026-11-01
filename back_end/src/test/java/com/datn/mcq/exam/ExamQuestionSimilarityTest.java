package com.datn.mcq.exam;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.service.ExamQuestionSimilarity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lớp 2 chống câu cùng khuôn trong MỘT đề (thẩm định 31/10): hàm thuần trên danh sách câu của đề theo thứ tự hiển thị,
 * chỉ tìm cặp giống nhau về khuôn (số thay bằng #, tên điểm/hình thay bằng @), không đụng DB. Quy tắc dedup của ngân
 * hàng ("câu chỉ khác số liệu không tính là trùng") không bị chạm. Các câu dựng lại từ nội dung thật của báo cáo thẩm
 * định (câu 57/55, 60/56a, 49/48), đổi tên đỉnh và số.
 */
class ExamQuestionSimilarityTest {

    private static final double T = ExamQuestionSimilarity.DEFAULT_THRESHOLD;

    private static ExamItemDto mcq(String stem, String a, String b, String c, String d) {
        return new ExamItemDto(1, 1, 1, "I", "chủ đề", QuestionType.MCQ, "ready", "generated", null, 1, stem, a, b, c, d,
                Option.B, null, null, "Lời giải.", Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto tf(String stem, String... statements) {
        List<ItemStatementDto> list = new ArrayList<>();
        StatementKey[] keys = StatementKey.values();
        for (int i = 0; i < statements.length; i++) {
            list.add(new ItemStatementDto(keys[i], statements[i], TruthValue.TRUE, Difficulty.EASY));
        }
        return new ExamItemDto(2, 2, 1, "II", "chủ đề", QuestionType.TF, "ready", "generated", null, 2, stem, null, null,
                null, null, null, null, list, "Lời giải.", null, false, List.of());
    }

    private static ExamItemDto sa(String stem, String answer) {
        return new ExamItemDto(3, 3, 1, "III", "chủ đề", QuestionType.SA, "ready", "generated", null, 3, stem, null, null,
                null, null, null, answer, null, "Lời giải.", Difficulty.EASY, false, List.of());
    }

    private static ExamItemDto empty() {
        return new ExamItemDto(4, 4, 1, "I", "chủ đề", QuestionType.MCQ, "generating", null, null, null, null, null, null,
                null, null, null, null, null, null, null, false, List.of());
    }

    // Câu 57 và 55 của đề 4: cùng khuôn hình thang cân, cùng nhiễu, chỉ khác tên đỉnh và số đo.
    private static ExamItemDto trapezoid57() {
        return mcq("Cho hình thang cân $WXYZ$ có đáy $WX$ song song với $YZ$. Biết góc $W$ bằng $75^\\bullet$. "
                        + "Số đo góc $X$ là:", "$75^\\bullet$", "$105^\\bullet$", "$90^\\bullet$", "$115^\\bullet$");
    }

    private static ExamItemDto trapezoid55() {
        return mcq("Cho hình thang cân $PQRS$ có đáy $PQ$ song song với $RS$. Biết góc $P$ bằng $80^\\bullet$. "
                        + "Số đo góc $Q$ là:", "$80^\\bullet$", "$100^\\bullet$", "$90^\\bullet$", "$110^\\bullet$");
    }

    @Test
    void chuanHoaThayCongThucSoVaTenDiem() {
        assertEquals("cho hình thang cân @ có góc w bằng #^\\bullet",
                ExamQuestionSimilarity.pattern("Cho hình thang cân $WXYZ$ có góc $W$ bằng $75^\\bullet$"));
        assertEquals("khai triển (#x-#)^#", ExamQuestionSimilarity.pattern("Khai triển $ (4x - 1)^2 $"));
        assertEquals("# giờ", ExamQuestionSimilarity.pattern("$2{,}5$   giờ"));
        assertEquals("@ và @", ExamQuestionSimilarity.pattern("AB và ABCDE"), "cụm 2-5 chữ in hoa là tên điểm, hình");
        assertEquals("abcdef", ExamQuestionSimilarity.pattern("ABCDEF"), "6 chữ in hoa trở lên không phải tên điểm");
    }

    @Test
    void hinhThangCan57Va55BiGanCoHaiPhia() {
        Map<Integer, List<Integer>> similar = ExamQuestionSimilarity.findSimilar(List.of(trapezoid57(), trapezoid55()), T);
        assertEquals(List.of(2), similar.get(1));
        assertEquals(List.of(1), similar.get(2));
    }

    @Test
    void capCauBacCungKhuonBiGan49Va48() {
        ExamItemDto a = mcq("Bậc của đơn thức $5x^3y^2$ là:", "$5$", "$3$", "$2$", "$6$");
        ExamItemDto b = mcq("Bậc của đơn thức $7x^4y^2$ là:", "$6$", "$4$", "$2$", "$7$");
        Map<Integer, List<Integer>> similar = ExamQuestionSimilarity.findSimilar(List.of(a, b), T);
        assertEquals(List.of(2), similar.get(1));
        assertEquals(List.of(1), similar.get(2));
    }

    @Test
    void cungChuDeNhungKhacDangHoiKhongBiGan() {
        ExamItemDto degree = mcq("Bậc của đơn thức $5x^3y^2$ là:", "$5$", "$3$", "$2$", "$6$");
        ExamItemDto kind = mcq("Biểu thức nào sau đây là đa thức một biến?", "$x^2 + 1$", "$xy + 1$", "$x + y$",
                "$x^2 + y$");
        ExamItemDto expand = mcq("Khai triển biểu thức $(x + 2)^2$ ta được:", "$x^2 + 4x + 4$", "$x^2 + 4$",
                "$x^2 + 2x + 4$", "$x^2 + 4x + 2$");
        ExamItemDto factor = mcq("Phân tích đa thức $x^2 - 9$ thành nhân tử, ta được:", "$(x - 3)(x + 3)$", "$(x - 9)^2$",
                "$(x - 3)^2$", "$(x + 9)(x - 1)$");
        assertEquals(Map.of(), ExamQuestionSimilarity.findSimilar(List.of(degree, kind, expand, factor), T));
    }

    @Test
    void mcqKhaiTrienVaYDungSaiCungBieuThucBiGanLoDapAn() {
        // Câu 60 (MCQ) và ý a của câu 56 (Đ/S) trong đề 4: làm xong câu trắc nghiệm là biết ý a sai.
        ExamItemDto mcq = mcq("Khai triển biểu thức $(4x - 1)^2$ ta được kết quả là:", "$16x^2 - 8x + 1$",
                "$16x^2 + 8x + 1$", "$16x^2 - 4x + 1$", "$4x^2 - 8x + 1$");
        ExamItemDto tf = tf("Cho biểu thức $P = (4x - 1)^2 + 3$. Xét tính đúng sai của các khẳng định sau:",
                "Khai triển biểu thức $(4x - 1)^2$ ta được kết quả là $16x^2 - 4x + 1$.",
                "Biểu thức $P$ luôn dương với mọi giá trị của $x$.", "Giá trị của $P$ tại $x = 1$ bằng $12$.",
                "Giá trị nhỏ nhất của $P$ bằng $3$.");
        Map<Integer, List<Integer>> similar = ExamQuestionSimilarity.findSimilar(List.of(mcq, tf), T);
        assertEquals(List.of(2), similar.get(1));
        assertEquals(List.of(1), similar.get(2));
    }

    @Test
    void mcqKhacHanMoiYDungSaiKhongBiGan() {
        ExamItemDto mcq = mcq("Bậc của đơn thức $5x^3y^2$ là:", "$5$", "$3$", "$2$", "$6$");
        ExamItemDto tf = tf("Cho biểu thức $P = (4x - 1)^2 + 3$. Xét tính đúng sai của các khẳng định sau:",
                "Khai triển biểu thức $(4x - 1)^2$ ta được kết quả là $16x^2 - 4x + 1$.",
                "Biểu thức $P$ luôn dương với mọi giá trị của $x$.", "Giá trị của $P$ tại $x = 1$ bằng $12$.",
                "Giá trị nhỏ nhất của $P$ bằng $3$.");
        assertEquals(Map.of(), ExamQuestionSimilarity.findSimilar(List.of(mcq, tf), T));
    }

    @Test
    void chiSoSanhMcqVoiMcqVaMcqVoiYDungSaiKhongSoDungSaVoiDungSaHayTraLoiNgan() {
        // Hai câu Đ/S cùng khuôn và hai câu trả lời ngắn cùng khuôn không thuộc phạm vi lớp này.
        ExamItemDto tf1 = tf("Cho biểu thức $P = (4x - 1)^2$. Xét các khẳng định:", "Ý một.", "Ý hai.", "Ý ba.", "Ý bốn.");
        ExamItemDto tf2 = tf("Cho biểu thức $P = (3x - 2)^2$. Xét các khẳng định:", "Ý một.", "Ý hai.", "Ý ba.", "Ý bốn.");
        ExamItemDto sa1 = sa("Bậc của đơn thức $5x^3y^2$ là bao nhiêu?", "5");
        ExamItemDto sa2 = sa("Bậc của đơn thức $7x^4y^2$ là bao nhiêu?", "6");
        assertEquals(Map.of(), ExamQuestionSimilarity.findSimilar(List.of(tf1, tf2, sa1, sa2), T));
    }

    @Test
    void soThuTuLaViTriTrongDeKeCaChoChuaCoCau() {
        Map<Integer, List<Integer>> similar = ExamQuestionSimilarity
                .findSimilar(List.of(trapezoid57(), empty(), trapezoid55()), T);
        assertEquals(List.of(3), similar.get(1));
        assertEquals(List.of(1), similar.get(3));
        assertFalse(similar.containsKey(2), "chỗ chưa có câu không được so");
    }

    @Test
    void nguongCaoHonThiKhongGan() {
        assertEquals(Map.of(), ExamQuestionSimilarity.findSimilar(List.of(trapezoid57(), trapezoid55()), 0.9999));
        assertTrue(ExamQuestionSimilarity.findSimilar(List.of(trapezoid57(), trapezoid55()), 0.5).containsKey(1));
    }

    @Test
    void motCauNhieuCauGiongThiLietKeDuSoThuTuTangDan() {
        ExamItemDto a = trapezoid57();
        ExamItemDto b = trapezoid55();
        ExamItemDto c = mcq("Cho hình thang cân $MNPQ$ có đáy $MN$ song song với $PQ$. Biết góc $M$ bằng $70^\\bullet$. "
                + "Số đo góc $N$ là:", "$70^\\bullet$", "$110^\\bullet$", "$90^\\bullet$", "$120^\\bullet$");
        Map<Integer, List<Integer>> similar = ExamQuestionSimilarity.findSimilar(List.of(a, b, c), T);
        assertEquals(List.of(2, 3), similar.get(1));
        assertEquals(List.of(1, 3), similar.get(2));
        assertEquals(List.of(1, 2), similar.get(3));
    }
}
