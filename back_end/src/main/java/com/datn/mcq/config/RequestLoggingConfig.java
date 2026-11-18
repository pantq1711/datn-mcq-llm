package com.datn.mcq.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Log mỗi request API ra terminal ("Before request [GET /api/x?y=1]" và "After request [...]").
 * Chỉ tồn tại khi app.logging.requests.enabled=true (biến môi trường LOG_REQUESTS); mặc định tắt.
 * Không log body và header để không lộ mật khẩu, token, cookie refresh.
 * Ẩn tham số sig trong query string để không lộ chữ ký URL ảnh (H3, H10).
 */
@Configuration
@ConditionalOnProperty(name = "app.logging.requests.enabled", havingValue = "true")
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter() {
            @Override
            protected String createMessage(HttpServletRequest request, String prefix, String suffix) {
                String msg = super.createMessage(request, prefix, suffix);
                if (msg.contains("sig=")) {
                    return msg.replaceAll("(?<=sig=)[^&\\]]+", "***");
                }
                return msg;
            }
        };
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(false);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false);
        return filter;
    }
}
