package com.datn.mcq.questionimage.service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "app.question-images")
public class QuestionImageProperties {

    /**
     * Khóa bí mật ký URL HMAC-SHA256 (biến QUESTION_IMAGE_URL_KEY), định dạng Base64 giải mã ra >= 32 byte.
     * Tách biệt với JWT_SECRET.
     */
    private String urlSigningKey = "";

    /**
     * Thời gian sống mặc định của URL có chữ ký (mặc định 1 giờ).
     */
    private Duration urlTtl = Duration.ofHours(1);

    private byte[] decodedKeyBytes;

    @PostConstruct
    public void validate() {
        if (urlSigningKey == null || urlSigningKey.isBlank()) {
            throw new IllegalStateException("Cấu hình app.question-images.url-signing-key (QUESTION_IMAGE_URL_KEY) không được để trống");
        }
        try {
            decodedKeyBytes = Base64.getDecoder().decode(urlSigningKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.question-images.url-signing-key phải là chuỗi Base64 hợp lệ", e);
        }
        if (decodedKeyBytes.length < 32) {
            throw new IllegalStateException("app.question-images.url-signing-key sau khi giải mã phải dài ít nhất 32 byte (256 bit)");
        }
    }

    public String getUrlSigningKey() {
        return urlSigningKey;
    }

    public void setUrlSigningKey(String urlSigningKey) {
        this.urlSigningKey = urlSigningKey;
    }

    public Duration getUrlTtl() {
        return urlTtl;
    }

    public void setUrlTtl(Duration urlTtl) {
        this.urlTtl = urlTtl;
    }

    public byte[] getDecodedKeyBytes() {
        return decodedKeyBytes;
    }
}
