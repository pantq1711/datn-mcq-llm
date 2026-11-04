package com.datn.mcq.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.filter.AbstractRequestLoggingFilter;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Log request ra terminal (cách 2): bean CommonsRequestLoggingFilter chỉ tồn tại khi app.logging.requests.enabled=true;
 * mặc định tắt. Khi bật, không log body và header (tránh lộ mật khẩu, token, cookie).
 */
class RequestLoggingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RequestLoggingConfig.class);

    @Test
    void macDinhKhongCoBean() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CommonsRequestLoggingFilter.class));
    }

    @Test
    void tatRoRangKhongCoBean() {
        runner.withPropertyValues("app.logging.requests.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CommonsRequestLoggingFilter.class));
    }

    @Test
    void batCoThiCoBeanVaKhongLogBodyHeader() {
        runner.withPropertyValues("app.logging.requests.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(CommonsRequestLoggingFilter.class);
            CommonsRequestLoggingFilter f = ctx.getBean(CommonsRequestLoggingFilter.class);
            assertNotNull(f);
            assertTrue(isTrue(f, "isIncludeQueryString"));
            assertFalse(isTrue(f, "isIncludePayload"));
            assertFalse(isTrue(f, "isIncludeHeaders"));
            assertFalse(isTrue(f, "isIncludeClientInfo"));
        });
    }

    private static boolean isTrue(CommonsRequestLoggingFilter f, String getter) throws Exception {
        var m = AbstractRequestLoggingFilter.class.getDeclaredMethod(getter);
        m.setAccessible(true);
        return (boolean) m.invoke(f);
    }
}
