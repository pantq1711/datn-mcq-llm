package com.datn.mcq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Cấu hình xác thực (W1-1), tiền tố {@code app.security}. Giá trị mặc định đặt ở đây vì file cấu hình test thay thế hẳn
 * application.yaml của main.
 *
 * @param jwt            khóa ký (base64, ít nhất 32 byte; biến JWT_SECRET), issuer, thời hạn access token
 * @param refresh        thời hạn refresh token (trượt) và thời hạn tuyệt đối của một phiên đăng nhập
 * @param cookieSecure   cờ Secure của cookie refresh (mặc định true; dev qua http không phải localhost thì đặt false)
 * @param cors           origin được gọi API (mặc định Vite dev)
 * @param bootstrapAdmin tài khoản admin đầu tiên (ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME)
 * @param loginRateLimit giới hạn đăng nhập sai theo email + IP (mặc định 5 lần / 15 phút, khóa 15 phút)
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(Jwt jwt, Refresh refresh, Boolean cookieSecure, Cors cors,
                                 BootstrapAdmin bootstrapAdmin, LoginRateLimit loginRateLimit) {

    public SecurityProperties {
        jwt = jwt != null ? jwt : new Jwt(null, null, null);
        refresh = refresh != null ? refresh : new Refresh(null, null);
        cookieSecure = cookieSecure == null || cookieSecure;
        cors = cors != null ? cors : new Cors(null);
        bootstrapAdmin = bootstrapAdmin != null ? bootstrapAdmin : new BootstrapAdmin(null, null, null);
        loginRateLimit = loginRateLimit != null ? loginRateLimit : new LoginRateLimit(null, null, null);
    }

    public record LoginRateLimit(Integer maxFailures, Duration window, Duration lock) {
        public LoginRateLimit {
            maxFailures = maxFailures != null && maxFailures > 0 ? maxFailures : 5;
            window = window != null ? window : Duration.ofMinutes(15);
            lock = lock != null ? lock : Duration.ofMinutes(15);
        }
    }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl) {
        public Jwt {
            issuer = issuer == null || issuer.isBlank() ? "datn-mcq" : issuer;
            accessTokenTtl = accessTokenTtl != null ? accessTokenTtl : Duration.ofMinutes(15);
        }
    }

    public record Refresh(Duration ttl, Duration absoluteTtl) {
        public Refresh {
            ttl = ttl != null ? ttl : Duration.ofDays(7);
            absoluteTtl = absoluteTtl != null ? absoluteTtl : Duration.ofDays(30);
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                    ? List.of("http://localhost:5173", "http://127.0.0.1:5173")
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record BootstrapAdmin(String email, String password, String name) {
        public BootstrapAdmin {
            name = name == null || name.isBlank() ? "Quản trị viên" : name.trim();
        }
    }
}
