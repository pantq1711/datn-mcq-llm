package com.datn.mcq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Giới hạn số lần đăng nhập theo IP (D3), cấu hình ở {@code app.security.login-ip-limit.*}: tối đa {@code maxAttempts} lần
 * trong {@code window} cho mỗi IP (IPv6 gom theo /64), tính cả lần đúng lẫn sai và chạy trước bcrypt.
 */
@Component
@ConfigurationProperties("app.security.login-ip-limit")
public class LoginIpLimitProperties {

    private int maxAttempts = 20;
    private Duration window = Duration.ofMinutes(1);

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.security.login-ip-limit.max-attempts phải từ 1 trở lên");
        }
        this.maxAttempts = maxAttempts;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("app.security.login-ip-limit.window phải lớn hơn 0");
        }
        this.window = window;
    }
}
