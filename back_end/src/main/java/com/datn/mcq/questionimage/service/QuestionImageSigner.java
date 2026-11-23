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
 * Cấu trúc thông điệp ký: "<id>|<exp>|<userId>" (giáo viên); "p|<id>|<exp>|<attemptId>" (học sinh, S1).
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
        return hmacHex(buildPayload(imageId, exp, userId));
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

    // ---------- URL cho học sinh, ký theo lượt làm (S1, docs/design/cau-co-hinh.md mục 4.1) ----------

    /** Tiền tố tách miền chữ ký của URL học sinh. */
    static final String ATTEMPT_DOMAIN = "p|";

    /**
     * Chữ ký của URL học sinh: HMAC-SHA256 (cùng khóa) của {@code "p|<id>|<exp>|<attemptId>"}, hex thường. Thông điệp của
     * giáo viên bắt đầu bằng chữ số nên không bao giờ trùng thông điệp có tiền tố {@code p|}: URL giáo viên không dùng được ở
     * endpoint học sinh và ngược lại.
     */
    public String signForAttempt(long imageId, long exp, int attemptId) {
        return hmacHex(ATTEMPT_DOMAIN + imageId + "|" + exp + "|" + attemptId);
    }

    /** Kiểm chữ ký URL học sinh bằng so sánh thời gian hằng. Không kiểm hạn (xem {@link #isExpired}). */
    public boolean verifyForAttempt(long imageId, long exp, int attemptId, String providedSig) {
        if (providedSig == null || providedSig.length() != 64) {
            return false;
        }
        byte[] expected = signForAttempt(imageId, exp, attemptId).getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSig.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    /** {@code "/api/public/question-images/{id}?exp={exp}&a={attemptId}&sig={sig}"}. */
    public String buildAttemptUrl(long imageId, long exp, int attemptId) {
        return "/api/public/question-images/" + imageId + "?exp=" + exp + "&a=" + attemptId + "&sig="
                + signForAttempt(imageId, exp, attemptId);
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getDecodedKeyBytes(), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Lỗi khởi tạo HMAC-SHA256", e);
        }
    }

    private static String buildPayload(long imageId, long exp, int userId) {
        return imageId + "|" + exp + "|" + userId;
    }
}
