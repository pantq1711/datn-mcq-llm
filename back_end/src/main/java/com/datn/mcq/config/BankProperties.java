package com.datn.mcq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;

/**
 * Cấu hình ngân hàng câu (NV-31, quyết định 16/11 mục 3a).
 *
 * @param keepVisibility nơi câu được Giữ ở chế độ giáo viên vào: {@code shared} (mặc định, hành vi cũ: câu không cờ vào ngân
 *                       hàng chung, câu có cờ hoặc sinh từ tài liệu vào ngân hàng riêng của người Giữ) hoặc {@code private}
 *                       (mọi câu Giữ, kể cả câu không cờ, vào ngân hàng riêng của người Giữ). Biến môi trường
 *                       {@code BANK_KEEP_VISIBILITY}. Giá trị khác làm ứng dụng không khởi động.
 */
@ConfigurationProperties(prefix = "app.bank")
public record BankProperties(@DefaultValue(BankProperties.SHARED) String keepVisibility) {

    public static final String SHARED = "shared";
    public static final String PRIVATE = "private";

    public BankProperties {
        String v = keepVisibility == null || keepVisibility.isBlank() ? SHARED
                : keepVisibility.strip().toLowerCase(Locale.ROOT);
        if (!v.equals(SHARED) && !v.equals(PRIVATE)) {
            throw new IllegalArgumentException("app.bank.keep-visibility (BANK_KEEP_VISIBILITY) phải là \"shared\" hoặc"
                    + " \"private\", nhận \"" + keepVisibility + "\"");
        }
        keepVisibility = v;
    }

    /** Mọi câu Giữ vào ngân hàng riêng của người Giữ. */
    public boolean keepPrivate() {
        return PRIVATE.equals(keepVisibility);
    }
}
