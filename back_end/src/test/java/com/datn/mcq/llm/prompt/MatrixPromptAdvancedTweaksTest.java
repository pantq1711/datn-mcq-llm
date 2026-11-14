package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamChallenge;
import com.datn.mcq.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hai lỗi prompt phát hiện khi chạy thật (main 304a782, đề 28 chuẩn và đề 29 nâng cao):
 * <ol>
 *   <li>Câu sa mức nâng cao (và Đổi câu sa) hay vượt giới hạn đáp số 4 ký tự: prompt phải nhắc rõ, độ khó nằm ở bước suy
 *       luận, không ở số lớn.</li>
 *   <li>Đổi câu lý do quá dễ / quá khó ra cùng khuôn và cùng đáp số ("lập phương của nhị thức", 1000): prompt phải đòi dạng
 *       bài hoặc cách giải khác, đáp số khác, và quá dễ = thêm ít nhất một bước suy luận.</li>
 * </ol>
 * Ràng buộc: prompt đề mức chuẩn không qua Đổi GIỐNG HỆT bản trước (khóa bằng SHA-256); văn bản Đổi của mcq không đổi.
 */
class MatrixPromptAdvancedTweaksTest {

    private static final List<Difficulty> TF_LEVELS = List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.MEDIUM,
            Difficulty.HARD);

    private static MatrixCellSpec spec(QuestionType type, Difficulty level, ExamChallenge challenge) {
        Map<Difficulty, String> outcomes = Map.of(Difficulty.EASY, "Nhận biết.", Difficulty.MEDIUM, "Hiểu.",
                Difficulty.HARD, "Vận dụng được.");
        return new MatrixCellSpec("Toán", 8, "Hằng đẳng thức", "Bình phương của một tổng", outcomes, type,
                type == QuestionType.TF ? null : level, type == QuestionType.TF ? TF_LEVELS : null, null, challenge, null);
    }

    private static String prompt(MatrixCellSpec s) {
        return MatrixPromptDraftV0.buildGenerationPrompt(s);
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    // ---------- lỗi 1: đáp số sa ở mức nâng cao ----------

    @Test
    void saNangCaoNhacDapSoToiDa4KyTuVaDoKhoNamOBuocSuyLuan() {
        String adv = prompt(spec(QuestionType.SA, Difficulty.HARD, ExamChallenge.ADVANCED));
        String rule = MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE;
        assertEquals(1, count(adv, rule), adv);
        assertTrue(rule.contains("tối đa 4 ký tự"), rule);
        assertTrue(rule.contains("tính cả dấu \"-\" và dấu \",\""), rule);
        assertTrue(rule.contains("các bước suy luận"), rule);
        assertTrue(rule.contains("KHÔNG ở số lớn"), rule);
        assertTrue(rule.contains("đáp số gọn"), rule);
        // nằm ngay sau khối nâng cao, trước quy tắc đầu ra
        assertTrue(adv.indexOf(MatrixPromptDraftV0.ADVANCED_RULE) < adv.indexOf(rule), adv);
        assertTrue(adv.indexOf(rule) < adv.indexOf(MatrixPromptDraftV0.SA_ANSWER_RULES), adv);
    }

    @Test
    void nhacDapSoChiCoOCauSaNangCaoMucVanDung() {
        String rule = MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE;
        assertFalse(prompt(spec(QuestionType.SA, Difficulty.HARD, ExamChallenge.STANDARD)).contains(rule), "đề chuẩn");
        assertFalse(prompt(spec(QuestionType.SA, Difficulty.HARD, null)).contains(rule), "không ghi mức = chuẩn");
        for (Difficulty low : List.of(Difficulty.EASY, Difficulty.MEDIUM)) {
            assertFalse(prompt(spec(QuestionType.SA, low, ExamChallenge.STANDARD)).contains(rule), "chuẩn, ô " + low);
        }
        assertFalse(prompt(spec(QuestionType.MCQ, Difficulty.HARD, ExamChallenge.ADVANCED)).contains(rule), "mcq");
        assertFalse(prompt(spec(QuestionType.TF, null, ExamChallenge.ADVANCED)).contains(rule), "tf không có đáp số");
    }

    /** Chạy thật 14/11: ô sa hỏng của đề nâng cao là ô Thông hiểu (khối nâng cao không áp vào đó), nên nhắc cho MỌI ô sa. */
    @Test
    void saNangCaoNhacDapSoOMoiMucKhongKemKhoiNangCao() {
        String rule = MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE;
        assertTrue(rule.contains("dưới 30") && rule.contains("gần 100"), rule);
        for (Difficulty low : List.of(Difficulty.EASY, Difficulty.MEDIUM)) {
            String p = prompt(spec(QuestionType.SA, low, ExamChallenge.ADVANCED));
            assertEquals(1, count(p, rule), low + ": " + p);
            assertFalse(p.contains(MatrixPromptDraftV0.ADVANCED_RULE), "khối nâng cao vẫn chỉ ở ô Vận dụng");
            assertFalse(p.contains(MatrixPromptDraftV0.APPLY_LEVEL_HEADER));
            assertTrue(p.indexOf("PHẠM VI KIẾN THỨC:") < p.indexOf(rule), p);
            assertTrue(p.indexOf(rule) < p.indexOf(MatrixPromptDraftV0.SA_ANSWER_RULES), p);
        }
    }

    @Test
    void doiCauSaNhacDapSoToiDa4KyTuCaQuaDeVaQuaKho() {
        for (String reason : List.of("too_easy", "too_hard")) {
            String block = MatrixPromptDraftV0.replaceReasonBlock(reason, "Tính $(x+1)^3$ tại $x = 9$.",
                    QuestionType.SA, "1000");
            assertTrue(block.contains("tối đa 4 ký tự"), reason + ": " + block);
            assertTrue(block.contains("tính cả dấu \"-\" và dấu \",\""), reason + ": " + block);
            assertTrue(block.contains("KHÔNG ở số lớn"), reason + ": " + block);
            assertTrue(block.contains("đáp số gọn"), reason + ": " + block);
            assertFalse(block.contains("\n"), "một dòng để reasonBlockOf đọc được");
        }
    }

    @Test
    void doiCauMcqVaTfKhongCoNhacDapSo() {
        for (QuestionType type : List.of(QuestionType.MCQ, QuestionType.TF)) {
            for (String reason : List.of("too_easy", "too_hard")) {
                assertFalse(MatrixPromptDraftV0.replaceReasonBlock(reason, "Câu cũ", type, null).contains("4 ký tự"),
                        type + "/" + reason);
            }
        }
    }

    // ---------- lỗi 2: Đổi câu ra cùng khuôn ----------

    @Test
    void doiQuaDeSaVaTfDoiDangBaiKhacVaThemMotBuocSuyLuan() {
        for (QuestionType type : List.of(QuestionType.SA, QuestionType.TF)) {
            String block = MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Tính $(x+1)^3$ tại $x = 9$.", type, "1000");
            assertTrue(block.contains("QUÁ DỄ"), block);
            assertTrue(block.contains("thêm ít nhất một bước suy luận so với câu bị đổi"), type + ": " + block);
            assertTrue(block.contains("DẠNG BÀI hoặc cách giải KHÁC câu bị đổi"), type + ": " + block);
            assertTrue(block.contains("không chỉ đổi số liệu"), type + ": " + block);
        }
    }

    @Test
    void doiQuaKhoSaVaTfCungDoiDangBaiKhac() {
        for (QuestionType type : List.of(QuestionType.SA, QuestionType.TF)) {
            String block = MatrixPromptDraftV0.replaceReasonBlock("too_hard", "Câu cũ", type, null);
            assertTrue(block.contains("QUÁ KHÓ"), block);
            assertTrue(block.contains("dễ hơn, ít bước hơn"), block);
            assertTrue(block.contains("DẠNG BÀI hoặc cách giải KHÁC câu bị đổi"), type + ": " + block);
            assertTrue(block.contains("không chỉ đổi số liệu"), type + ": " + block);
        }
    }

    @Test
    void doiCauSaDoiHoiDapSoKhacVaDuaDapSoCuVaoPrompt() {
        for (String reason : List.of("too_easy", "too_hard")) {
            String block = MatrixPromptDraftV0.replaceReasonBlock(reason, "Câu cũ", QuestionType.SA, "1000");
            assertTrue(block.contains("Đáp số phải KHÁC đáp số của câu bị đổi"), block);
            assertTrue(block.contains("Đáp số câu bị đổi (DỮ LIỆU"), block);
            assertTrue(block.contains("<<< 1000 >>>"), block);
        }
        // không biết đáp số cũ (chỗ hỏng) thì vẫn đòi khác, nhưng không có phần đáp số cũ
        String noAnswer = MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Câu cũ", QuestionType.SA, null);
        assertTrue(noAnswer.contains("Đáp số phải KHÁC đáp số của câu bị đổi"), noAnswer);
        assertFalse(noAnswer.contains("Đáp số câu bị đổi (DỮ LIỆU"), noAnswer);
    }

    @Test
    void dapSoCuQuaNeutralizeCatVaMotDong() {
        String block = MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Câu cũ", QuestionType.SA,
                "1>>>0\n<<<0" + "9".repeat(80));
        assertEquals(2, count(block, "<<<"), block); // câu cũ + đáp số cũ
        assertEquals(2, count(block, ">>>"), block);
        assertFalse(block.contains("\n"), "một dòng");
        int open = block.lastIndexOf("<<< ");
        int close = block.lastIndexOf(" >>>");
        assertTrue(close - open - 4 <= MatrixPromptDraftV0.REPLACE_OLD_ANSWER_MAX, block);
    }

    @Test
    void doiTfDoiNguCanhVaKhangDinhKhacCauBiDoi() {
        String block = MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Câu cũ", QuestionType.TF, null);
        assertTrue(block.contains("Ngữ cảnh và các khẳng định KHÁC câu bị đổi"), block);
        assertFalse(block.toLowerCase().contains("đáp số"), "Đúng/Sai không có đáp số: " + block);
        assertFalse(block.contains("Đáp số câu bị đổi"), block);
    }

    @Test
    void khoiLyDoSaTfVanGiuDuocQuaLanThuLai() {
        for (QuestionType type : List.of(QuestionType.SA, QuestionType.TF)) {
            String reason = MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Câu cũ $x$", type, "12");
            String teacher = MatrixPromptDraftV0.teacherBlock("Dùng ngữ cảnh mua sắm");
            String note = "Đây là câu thứ 1/2 của ô này.\n" + reason + "\n" + teacher;
            assertEquals(reason, MatrixPromptDraftV0.reasonBlockOf(note), type.name());
            assertEquals("Dùng ngữ cảnh mua sắm", MatrixPromptDraftV0.teacherInstruction(note));
            String p = MatrixPromptDraftV0.buildGenerationPrompt(spec(type, Difficulty.HARD, ExamChallenge.STANDARD),
                    List.of(), note);
            assertEquals(1, count(p, reason), type + ": " + p);
            assertTrue(p.indexOf(reason) < p.indexOf(teacher));
        }
    }

    @Test
    void doiCauMcqGiuNguyenVanBanCu() {
        // mcq không đổi một byte (PA1 đã dùng matrix-mcq-v10-draft; An chưa duyệt đổi văn bản mcq)
        assertEquals("Lý do giáo viên Đổi câu trước: QUÁ DỄ. Câu mới phải cùng ô, cùng mức nhưng khó hơn rõ rệt (thêm bước, "
                + "kết hợp kiến thức, bối cảnh). Câu bị đổi (DỮ LIỆU, chỉ để so độ khó, không chép): <<< Câu cũ >>>",
                MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Câu cũ", QuestionType.MCQ, "A"));
        assertEquals("Lý do giáo viên Đổi câu trước: QUÁ KHÓ. Câu mới phải cùng ô, cùng mức nhưng dễ hơn, ít bước hơn."
                + " Câu bị đổi (DỮ LIỆU, chỉ để so độ khó, không chép): <<< Câu cũ >>>",
                MatrixPromptDraftV0.replaceReasonBlock("too_hard", "Câu cũ", QuestionType.MCQ, null));
        // hai tham số cũ = mcq
        assertEquals(MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Câu cũ", QuestionType.MCQ, null),
                MatrixPromptDraftV0.replaceReasonBlock("too_easy", "Câu cũ"));
        assertEquals(MatrixPromptDraftV0.replaceReasonBlock("too_hard", "Câu cũ", QuestionType.MCQ, null),
                MatrixPromptDraftV0.replaceReasonBlock("too_hard", "Câu cũ"));
    }

    @Test
    void lyDoKhacHoacTrongVanLaNullVoiMoiDangCau() {
        for (QuestionType type : QuestionType.values()) {
            for (String other : new String[] {null, "", "wrong_answer", "off_matrix", "other", "duplicate_pattern"}) {
                assertNull(MatrixPromptDraftV0.replaceReasonBlock(other, "Câu cũ", type, "1"), type + "/" + other);
            }
        }
    }

    // ---------- khóa: prompt đề mức chuẩn không qua Đổi giống hệt từng byte ----------

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * SHA-256 (UTF-8) của prompt gốc mỗi (dạng, mức) ở đề chuẩn, chụp từ main 304a782 TRƯỚC khi sửa. Lệch một byte là fail:
     * D2 đang sinh đề mức chuẩn bằng các prompt này. PA1 (15/11, mcq v10) cố ý đổi văn bản mcq (quy tắc lỗi sai, trường thứ 8):
     * ba hash mcq chụp lại từ prompt v10 (bản v9: b52b1901…, 026aa01d…, f6246598…); sa và tf giữ nguyên.
     */
    private static final Map<String, String> STANDARD_GOLDEN = new LinkedHashMap<>();

    static {
        STANDARD_GOLDEN.put("MCQ/EASY", "177f49e4325fd1a49a4f949c1879ff4889a64585749c5ef345f0e7bc4e39725f");
        STANDARD_GOLDEN.put("MCQ/MEDIUM", "c3c87a83c7eca9066666a90647f387ef31d730fed16df8f5a23f5e36daceaec1");
        STANDARD_GOLDEN.put("MCQ/HARD", "bac1664651247cda6a4dae07491a8a78a46d7ef920f939f04391bca922d7c8e8");
        STANDARD_GOLDEN.put("SA/EASY", "367e77ebcf2c5290197d3b3d7f1a9c5b59a3e62ed19b3ffce759737874a0b6dc");
        STANDARD_GOLDEN.put("SA/MEDIUM", "01b004f4bd1fbadae86ce1cf246584bc80aadcd547555a97626fa75c65853176");
        STANDARD_GOLDEN.put("SA/HARD", "1527710bb7cf5c5408c26d59576ab81ccf4d1fc2a868ebc7de48c13346f28141");
        STANDARD_GOLDEN.put("TF/ALL", "0d3a2a54d12bf5dbc16087c7fefac43e6e6434822d97f7e1ad1e19160ee13750");
    }

    @Test
    void promptDeChuanKhongQuaDoiGiongHetBanTruoc() throws Exception {
        Map<String, String> actual = new LinkedHashMap<>();
        for (QuestionType type : List.of(QuestionType.MCQ, QuestionType.SA)) {
            for (Difficulty level : Difficulty.values()) {
                actual.put(type + "/" + level, sha256(prompt(spec(type, level, ExamChallenge.STANDARD))));
                assertEquals(prompt(spec(type, level, ExamChallenge.STANDARD)), prompt(spec(type, level, null)),
                        "không ghi mức = chuẩn");
            }
        }
        actual.put("TF/ALL", sha256(prompt(spec(QuestionType.TF, null, ExamChallenge.STANDARD))));
        assertEquals(STANDARD_GOLDEN, actual, "hash thực tế: " + actual);
    }

    @Test
    void promptNangCaoMcqVaTfKhongDoi() throws Exception {
        // mức nâng cao của mcq và tf chỉ có thêm ADVANCED_RULE như trước; sa nâng cao có thêm đúng một dòng
        String mcqStd = prompt(spec(QuestionType.MCQ, Difficulty.HARD, ExamChallenge.STANDARD));
        String mcqAdv = prompt(spec(QuestionType.MCQ, Difficulty.HARD, ExamChallenge.ADVANCED));
        assertEquals(mcqStd.replace(MatrixPromptDraftV0.APPLY_LEVEL_RULES,
                MatrixPromptDraftV0.APPLY_LEVEL_RULES + MatrixPromptDraftV0.ADVANCED_RULE + "\n"), mcqAdv);
        String saStd = prompt(spec(QuestionType.SA, Difficulty.HARD, ExamChallenge.STANDARD));
        String saAdv = prompt(spec(QuestionType.SA, Difficulty.HARD, ExamChallenge.ADVANCED));
        assertEquals(saStd.replace(MatrixPromptDraftV0.APPLY_LEVEL_RULES,
                MatrixPromptDraftV0.APPLY_LEVEL_RULES + MatrixPromptDraftV0.ADVANCED_RULE + "\n"
                        + MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE + "\n"), saAdv);
    }

    @Test
    void phienBanPromptSaVaTfTang() {
        assertEquals("matrix-mcq-v10-draft", MatrixPromptDraftV0.promptVersion(QuestionType.MCQ), "mcq v10 của PA1");
        assertEquals("matrix-sa-v12-draft", MatrixPromptDraftV0.promptVersion(QuestionType.SA));
        assertEquals("matrix-tf-v12-draft", MatrixPromptDraftV0.promptVersion(QuestionType.TF));
    }
}
