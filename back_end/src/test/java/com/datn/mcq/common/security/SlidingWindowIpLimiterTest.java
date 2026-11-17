package com.datn.mcq.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Giới hạn tần suất theo IP cho endpoint công khai: cửa sổ trượt, IPv6 gom /64, lần bị chặn không tính. */
class SlidingWindowIpLimiterTest {

    /** Đồng hồ chỉnh tay. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-14T08:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToMaxThenBlocksWithRetryAfter() {
        TestClock clock = new TestClock();
        SlidingWindowIpLimiter limiter = new SlidingWindowIpLimiter(3, Duration.ofMinutes(1), clock);
        assertEquals(0, limiter.tryAcquire("10.0.0.1"));
        clock.advance(Duration.ofSeconds(10));
        assertEquals(0, limiter.tryAcquire("10.0.0.1"));
        assertEquals(0, limiter.tryAcquire("10.0.0.1"));
        // Lần thứ tư trong cửa sổ: bị chặn, chờ tới khi lần đầu (cách đây 10 giây) ra khỏi cửa sổ.
        assertEquals(50, limiter.tryAcquire("10.0.0.1"));
        // IP khác không bị ảnh hưởng.
        assertEquals(0, limiter.tryAcquire("10.0.0.2"));
    }

    @Test
    void blockedCallsAreNotCounted() {
        TestClock clock = new TestClock();
        SlidingWindowIpLimiter limiter = new SlidingWindowIpLimiter(2, Duration.ofMinutes(1), clock);
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryAcquire("10.0.0.1") > 0);
        }
        clock.advance(Duration.ofSeconds(61));
        // Nếu lần bị chặn được tính thì cửa sổ vẫn đầy.
        assertEquals(0, limiter.tryAcquire("10.0.0.1"));
        assertEquals(0, limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1") > 0);
    }

    @Test
    void ipv6SharesOneBucketPerSlash64AndMissingIpSharesOne() {
        TestClock clock = new TestClock();
        SlidingWindowIpLimiter limiter = new SlidingWindowIpLimiter(1, Duration.ofMinutes(1), clock);
        assertEquals(0, limiter.tryAcquire("2001:db8:1:2:aaaa::1"));
        assertTrue(limiter.tryAcquire("2001:db8:1:2:bbbb::9") > 0, "cùng /64 dùng chung hạn mức");
        assertEquals(0, limiter.tryAcquire("2001:db8:1:3::1"), "/64 khác có hạn mức riêng");
        assertEquals(0, limiter.tryAcquire(null));
        assertTrue(limiter.tryAcquire("  ") > 0, "IP thiếu dùng chung một khóa");
    }
}
