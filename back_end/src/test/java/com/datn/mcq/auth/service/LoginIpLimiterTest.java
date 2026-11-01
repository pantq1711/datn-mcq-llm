package com.datn.mcq.auth.service;

import com.datn.mcq.common.exception.LoginRateLimitedException;
import com.datn.mcq.config.LoginIpLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D3: cửa sổ trượt theo IP, khóa IPv4/IPv6 (/64), lần bị chặn không tính vào bộ đếm. */
class LoginIpLimiterTest {

    /** Đồng hồ chỉnh tay được. */
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-30T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginIpLimiter limiter = new LoginIpLimiter(new LoginIpLimitProperties(), clock);

    @Test
    void lanThu21TrongMotPhutBiChanVaCoRetryAfter() {
        for (int i = 0; i < 20; i++) {
            limiter.check("1.2.3.4");
            clock.now = clock.now.plusSeconds(1);
        }
        // lần đầu ở t0, đã trôi 20 giây: còn 40 giây tới khi lần đầu rời cửa sổ
        LoginRateLimitedException e = assertThrows(LoginRateLimitedException.class, () -> limiter.check("1.2.3.4"));
        assertEquals(40, e.getRetryAfterSeconds());
        assertDoesNotThrow(() -> limiter.check("1.2.3.5"));
    }

    @Test
    void lanBiChanKhongTinhVaoBoDemNenHetCuaSoLaThuLaiDuoc() {
        for (int i = 0; i < 20; i++) {
            limiter.check("9.9.9.9");
        }
        for (int i = 0; i < 100; i++) {
            assertThrows(LoginRateLimitedException.class, () -> limiter.check("9.9.9.9"));
        }
        clock.now = clock.now.plus(Duration.ofSeconds(61));
        assertDoesNotThrow(() -> limiter.check("9.9.9.9"));
    }

    @Test
    void cuaSoTruotChoPhepLaiTungLan() {
        limiter.check("7.7.7.7"); // t0
        clock.now = clock.now.plusSeconds(30);
        for (int i = 0; i < 19; i++) {
            limiter.check("7.7.7.7");
        }
        assertThrows(LoginRateLimitedException.class, () -> limiter.check("7.7.7.7"));
        clock.now = clock.now.plusSeconds(31); // lần t0 rời cửa sổ
        assertDoesNotThrow(() -> limiter.check("7.7.7.7"));
        assertThrows(LoginRateLimitedException.class, () -> limiter.check("7.7.7.7"));
    }

    @Test
    void khoaIpv4NguyenVanIpv6Theo64() {
        assertEquals("1.2.3.4", LoginIpLimiter.key("1.2.3.4"));
        assertEquals(LoginIpLimiter.key("2001:db8:1:2::1"), LoginIpLimiter.key("2001:0db8:0001:0002:aaaa:bbbb:cccc:dddd"));
        assertNotEquals(LoginIpLimiter.key("2001:db8:1:2::1"), LoginIpLimiter.key("2001:db8:1:3::1"));
        assertTrue(LoginIpLimiter.key("2001:db8:1:2::1").endsWith("/64"));
        assertEquals("1.2.3.4", LoginIpLimiter.key("::ffff:1.2.3.4"), "IPv6 ánh xạ IPv4 quy về IPv4");
        assertEquals(LoginIpLimiter.key("fe80::1%eth0"), LoginIpLimiter.key("fe80::2"), "bỏ zone id");
        assertEquals(LoginIpLimiter.UNKNOWN_IP, LoginIpLimiter.key(null));
        assertEquals(LoginIpLimiter.UNKNOWN_IP, LoginIpLimiter.key("  "));
    }
}
