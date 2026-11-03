package com.datn.mcq.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Log mỗi request API ra terminal ("Before request [GET /api/x?y=1]" và "After request [...]").
 * Chỉ tồn tại khi app.logging.requests.enabled=true (biến môi trường LOG_REQUESTS); mặc định tắt.
 * Không log body và header để không lộ mật khẩu, token, cookie refresh. Filter này không có status và thời gian xử lý.
 */
@Configuration
@ConditionalOnProperty(name = "app.logging.requests.enabled", havingValue = "true")
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(false);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false);
        return filter;
    }
}
