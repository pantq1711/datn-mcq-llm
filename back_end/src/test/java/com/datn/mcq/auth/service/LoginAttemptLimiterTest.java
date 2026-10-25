package com.datn.mcq.auth.service;

import com.datn.mcq.config.SecurityProperties;
import com.datn.mcq.config.SecurityProperties.LoginRateLimit;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W1-1: thời gian của bộ giới hạn đăng nhập sai (cửa sổ đếm và thời gian khóa), dùng đồng hồ giả. */
class LoginAttemptLimiterTest {

    /** Đồng hồ chỉnh tay. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-24T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(new SecurityProperties(null, null, null, null,
            null, new LoginRateLimit(3, Duration.ofMinutes(10), Duration.ofMinutes(5))), clock);
    private final String key = LoginAttemptLimiter.key("gv@test.vn", "10.0.0.1");

    @Test
    void khoaKhiDuSoLanSaiVaMoKhiHetThoiGianKhoa() {
        limiter.recordFailure(key);
        limiter.recordFailure(key);
        assertTrue(limiter.lockedFor(key).isZero());
        limiter.recordFailure(key);
        assertEquals(Duration.ofMinutes(5), limiter.lockedFor(key));

        clock.advance(Duration.ofMinutes(4));
        assertEquals(Duration.ofMinutes(1), limiter.lockedFor(key));
        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.lockedFor(key).isZero(), "hết thời gian khóa thì mở");

        limiter.recordFailure(key);
        assertTrue(limiter.lockedFor(key).isZero(), "sau khi mở, đếm lại từ đầu");
    }

    @Test
    void lanSaiCuNgoaiCuaSoKhongDuocTinh() {
        limiter.recordFailure(key);
        limiter.recordFailure(key);
        clock.advance(Duration.ofMinutes(11));
        limiter.recordFailure(key);
        limiter.recordFailure(key);
        assertTrue(limiter.lockedFor(key).isZero(), "cửa sổ 10 phút đã qua, chỉ còn 2 lần sai");
        limiter.recordFailure(key);
        assertEquals(Duration.ofMinutes(5), limiter.lockedFor(key));
    }

    @Test
    void dangNhapDungXoaBoDemVaKhoaKhacNhauDocLap() {
        String other = LoginAttemptLimiter.key("gv@test.vn", "10.0.0.2");
        limiter.recordFailure(key);
        limiter.recordFailure(key);
        limiter.recordSuccess(key);
        limiter.recordFailure(key);
        limiter.recordFailure(key);
        assertTrue(limiter.lockedFor(key).isZero());
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(other);
        }
        assertTrue(limiter.lockedFor(key).isZero(), "khóa IP khác không ảnh hưởng");
        assertEquals(Duration.ofMinutes(5), limiter.lockedFor(other));
    }
}
