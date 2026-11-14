package com.datn.mcq.questionimage;

import com.datn.mcq.questionimage.service.QuestionImageProperties;
import com.datn.mcq.questionimage.service.QuestionImageSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionImageSignerTest {

    // Khóa test 32 byte (256 bit) Base64
    private static final String TEST_KEY_BASE64 = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes());

    private QuestionImageProperties properties;
    private QuestionImageSigner signer;

    @BeforeEach
    void setUp() {
        properties = new QuestionImageProperties();
        properties.setUrlSigningKey(TEST_KEY_BASE64);
        properties.setUrlTtl(Duration.ofHours(1));
        properties.validate();
        signer = new QuestionImageSigner(properties);
    }

    @Test
    @DisplayName("sign: sinh chuỗi hex 64 ký tự thường hợp lệ")
    void signSinhChuKyHexHopLe() {
        String sig = signer.sign(812L, 1794409200L, 27);
        assertEquals(64, sig.length());
        assertTrue(sig.matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("verify: chữ ký khớp khi đúng toàn bộ tham số")
    void verifyDungThamSoTraVeTrue() {
        long imageId = 812L;
        long exp = 1794409200L;
        int userId = 27;
        String sig = signer.sign(imageId, exp, userId);

        assertTrue(signer.verify(imageId, exp, userId, sig));
        assertTrue(signer.verify(imageId, exp, userId, sig.toUpperCase())); // không phân biệt hoa thường
    }

    @Test
    @DisplayName("verify: thất bại khi sai lệch bất kỳ tham số nào (chống sửa đổi)")
    void verifySaiThamSoTraVeFalse() {
        long imageId = 812L;
        long exp = 1794409200L;
        int userId = 27;
        String sig = signer.sign(imageId, exp, userId);

        assertFalse(signer.verify(imageId + 1, exp, userId, sig), "Đổi imageId phải fail");
        assertFalse(signer.verify(imageId, exp + 1, userId, sig), "Đổi exp phải fail");
        assertFalse(signer.verify(imageId, exp, userId + 1, sig), "Đổi userId phải fail");
        assertFalse(signer.verify(imageId, exp, userId, "bad" + sig.substring(3)), "Đổi sig phải fail");
        assertFalse(signer.verify(imageId, exp, userId, null), "Sig null phải fail");
        assertFalse(signer.verify(imageId, exp, userId, "too-short"), "Sig quá ngắn phải fail");
    }

    @Test
    @DisplayName("calculateExp: giữ nguyên mốc exp trong cùng một giờ đồng hồ để hỗ trợ HTTP cache")
    void calculateExpGiuNguyenTrongCungMotGio() {
        // 10:15:00
        Instant t1 = Instant.parse("2026-11-14T10:15:00Z");
        // 10:45:00
        Instant t2 = Instant.parse("2026-11-14T10:45:00Z");
        // 11:05:00 (đã sang giờ mới)
        Instant t3 = Instant.parse("2026-11-14T11:05:00Z");

        long exp1 = signer.calculateExp(t1);
        long exp2 = signer.calculateExp(t2);
        long exp3 = signer.calculateExp(t3);

        assertEquals(exp1, exp2, "Trong cùng một giờ exp phải giống nhau");
        assertNotEquals(exp1, exp3, "Sang giờ mới exp phải thay đổi");

        // Giờ tròn kế tiếp của 10:15 là 11:00 (1794404400), cộng thêm 1h TTL là 12:00
        Instant expectedExp1 = Instant.parse("2026-11-14T12:00:00Z");
        assertEquals(expectedExp1.getEpochSecond(), exp1);
    }

    @Test
    @DisplayName("isExpired: xác định chính xác thời điểm hết hạn")
    void isExpiredXacDinhChinhXac() {
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        long expPast = now.getEpochSecond() - 10;
        long expExact = now.getEpochSecond();
        long expFuture = now.getEpochSecond() + 10;

        assertTrue(signer.isExpired(expPast, now));
        assertTrue(signer.isExpired(expExact, now));
        assertFalse(signer.isExpired(expFuture, now));
    }

    @Test
    @DisplayName("buildSignedUrl: dựng đúng format đường dẫn tương đối")
    void buildSignedUrlDungFormat() {
        Instant now = Instant.parse("2026-11-14T10:00:00Z");
        String url = signer.buildSignedUrl(812L, 27, now);

        assertTrue(url.startsWith("/api/question-images/812?exp="));
        assertTrue(url.contains("&u=27&sig="));
    }

    @Test
    @DisplayName("validate: từ chối khóa rỗng hoặc < 32 byte")
    void validateTuChoiKhoaKhongHopLe() {
        QuestionImageProperties invalidProps = new QuestionImageProperties();

        invalidProps.setUrlSigningKey("");
        assertThrows(IllegalStateException.class, invalidProps::validate);

        invalidProps.setUrlSigningKey("not-base64!!!");
        assertThrows(IllegalStateException.class, invalidProps::validate);

        // Khóa chỉ có 16 byte
        String shortKey = Base64.getEncoder().encodeToString("short-16-bytes!".getBytes());
        invalidProps.setUrlSigningKey(shortKey);
        assertThrows(IllegalStateException.class, invalidProps::validate);
    }
}
