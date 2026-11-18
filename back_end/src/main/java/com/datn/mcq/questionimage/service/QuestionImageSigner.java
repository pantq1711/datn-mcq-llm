package com.datn.mcq.questionimage.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Bộ ký và kiểm tra chữ ký HMAC-SHA256 cho URL tải ảnh câu hỏi theo hợp đồng H3 (thiết kế G0).
 * Cấu trúc thông điệp ký: "<id>|<exp>|<userId>"
 */
@Component
public class QuestionImageSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final QuestionImageProperties properties;

    public QuestionImageSigner(QuestionImageProperties properties) {
        this.properties = properties;
    }

    /**
     * Ký thông điệp "<id>|<exp>|<userId>" bằng HMAC-SHA256, trả về chuỗi hex viết thường 64 ký tự.
     */
    public String sign(long imageId, long exp, int userId) {
        String data = buildPayload(imageId, exp, userId);
        byte[] keyBytes = properties.getDecodedKeyBytes();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Lỗi khởi tạo HMAC-SHA256", e);
        }
    }

    /**
     * Xác minh chữ ký bằng so sánh thời gian hằng (MessageDigest.isEqual).
     */
    public boolean verify(long imageId, long exp, int userId, String providedSig) {
        if (providedSig == null || providedSig.length() != 64) {
            return false;
        }
        String expectedSig = sign(imageId, exp, userId);
        byte[] expectedBytes = expectedSig.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = providedSig.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * Tính toán thời điểm hết hạn exp = hết giờ tròn kế tiếp + urlTtl.
     * Đảm bảo exp không đổi trong cùng một giờ đồng hồ, giúp trình duyệt tận dụng cache HTTP.
     */
    public long calculateExp(Instant now) {
        long epochSecond = now.getEpochSecond();
        long nextRoundHour = ((epochSecond / 3600) + 1) * 3600;
        long ttlSeconds = properties.getUrlTtl().toSeconds();
        return nextRoundHour + ttlSeconds;
    }

    /**
     * Kiểm tra URL đã hết hạn hay chưa.
     */
    public boolean isExpired(long exp, Instant now) {
        return now.getEpochSecond() >= exp;
    }

    /**
     * Dựng đường dẫn tương đối có chữ ký hoàn chỉnh:
     * "/api/question-images/{id}?exp={exp}&u={userId}&sig={sig}"
     */
    public String buildSignedUrl(long imageId, int userId, Instant now) {
        long exp = calculateExp(now);
        String sig = sign(imageId, exp, userId);
        return "/api/question-images/" + imageId + "?exp=" + exp + "&u=" + userId + "&sig=" + sig;
    }

    private static String buildPayload(long imageId, long exp, int userId) {
        return imageId + "|" + exp + "|" + userId;
    }
}
