package com.datn.mcq.qc.checker;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chuẩn hóa văn bản cho dedup (quyết định 1a): NFC, khoảng trắng, lệnh đồng nghĩa,
 * tập phương án không phụ thuộc thứ tự. Không đổi chữ hoa/thường.
 */
class McqTextNormalizerTest {

    @Test
    void nfcAndNfdFormsNormalizeToTheSameText() {
        String nfc = Normalizer.normalize("Rút gọn biểu thức", Normalizer.Form.NFC);
        String nfd = Normalizer.normalize("Rút gọn biểu thức", Normalizer.Form.NFD);
        assertNotEquals(nfc, nfd);
        assertEquals(McqTextNormalizer.normalize(nfc), McqTextNormalizer.normalize(nfd));
    }

    @Test
    void whitespaceIsCollapsedOutsideMathAndRemovedInsideMath() {
        assertEquals(
                McqTextNormalizer.normalize("Tính   giá trị\n của $x + 1$  khi $x=2$"),
                McqTextNormalizer.normalize("Tính giá trị của $x+1$ khi $x = 2$"));
    }

    @Test
    void synonymousComparisonCommandsAreUnified() {
        assertEquals(McqTextNormalizer.normalize("$x \\leq 2$"), McqTextNormalizer.normalize("$x \\le 2$"));
        assertEquals(McqTextNormalizer.normalize("$x \\geq 2$"), McqTextNormalizer.normalize("$x \\ge 2$"));
        assertEquals(McqTextNormalizer.normalize("$x \\neq 0$"), McqTextNormalizer.normalize("$x \\ne 0$"));
        // Không được đụng tới lệnh khác có cùng tiền tố
        assertTrue(McqTextNormalizer.normalize("$\\left( x \\right)$").contains("\\left("));
    }

    @Test
    void caseIsKeptBecauseMathVariablesAreCaseSensitive() {
        assertNotEquals(McqTextNormalizer.normalize("Cho $A = 2$"), McqTextNormalizer.normalize("Cho $a = 2$"));
    }

    @Test
    void fingerprintIgnoresOptionOrder() {
        String f1 = McqTextNormalizer.fingerprint("Tính $2 + 3$", "$5$", "$6$", "$7$", "$8$");
        String f2 = McqTextNormalizer.fingerprint("Tính $2+3$", "$8$", "$7$", "$5$", "$6$");
        assertEquals(f1, f2);
    }

    @Test
    void fingerprintDiffersWhenStemNumbersDiffer() {
        String f1 = McqTextNormalizer.fingerprint("Tính $2 + 3$", "$5$", "$6$", "$7$", "$8$");
        String f2 = McqTextNormalizer.fingerprint("Tính $2 + 4$", "$5$", "$6$", "$7$", "$8$");
        assertNotEquals(f1, f2);
    }

    @Test
    void signatureCollectsFormulasAndNumbersOfTheStemOnly() {
        assertEquals(
                McqTextNormalizer.stemSignature("Kết quả của phép tính $\\sqrt{(-5)^2}$ là gì?"),
                McqTextNormalizer.stemSignature("Tính $\\sqrt{(-5)^2}$."));
        assertNotEquals(
                McqTextNormalizer.stemSignature("Rút gọn phân thức $\\frac{4x^2y}{8xy^3}$"),
                McqTextNormalizer.stemSignature("Rút gọn phân thức $\\frac{6x^3y}{9xy^2}$"));
        assertNotEquals(
                McqTextNormalizer.stemSignature("Hình chữ nhật dài 12 cm, rộng 5 cm. Tính chu vi."),
                McqTextNormalizer.stemSignature("Hình chữ nhật dài 15 cm, rộng 4 cm. Tính chu vi."));
    }

    @Test
    void negationIsDetectedOutsideMathOnly() {
        assertTrue(McqTextNormalizer.hasNegation("Khẳng định nào sau đây là sai?"));
        assertTrue(McqTextNormalizer.hasNegation("Bất phương trình nào KHÔNG PHẢI là bất phương trình bậc nhất?"));
        assertTrue(McqTextNormalizer.hasNegation("Tất cả đều đúng, ngoại trừ:"));
        assertFalse(McqTextNormalizer.hasNegation("Bất phương trình nào sau đây là bất phương trình bậc nhất một ẩn?"));
        assertFalse(McqTextNormalizer.hasNegation("Với $x$ khác $0$, rút gọn biểu thức"));
    }

    @Test
    void trigramCosineIsOneForIdenticalAndLowerForDifferentText() {
        String a = McqTextNormalizer.canonicalText("Tính $2 + 3$", "$5$", "$6$", "$7$", "$8$");
        String b = McqTextNormalizer.canonicalText("Tính $2+3$", "$8$", "$7$", "$6$", "$5$");
        String c = McqTextNormalizer.canonicalText("Phân tích đa thức thành nhân tử", "A", "B", "C", "D");
        assertEquals(1.0, McqTextNormalizer.trigramCosine(a, b), 1e-9);
        assertTrue(McqTextNormalizer.trigramCosine(a, c) < 0.5);
        assertEquals(McqTextNormalizer.trigramCosine(a, c), McqTextNormalizer.trigramCosine(c, a), 1e-12);
    }
}
