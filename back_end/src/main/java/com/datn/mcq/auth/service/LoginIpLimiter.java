package com.datn.mcq.auth.service;

import com.datn.mcq.common.exception.LoginRateLimitedException;
import com.datn.mcq.config.LoginIpLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn số lần đăng nhập theo IP (D3): tối đa {@code maxAttempts} lần trong cửa sổ trượt {@code window} mỗi IP, tính mọi
 * lần thử (đúng lẫn sai) và gọi TRƯỚC khi so mật khẩu, để một IP không dùng nhiều email khác nhau bắt máy chủ chạy bcrypt
 * liên tục. Độc lập với {@link LoginAttemptLimiter} (khóa theo email + IP). IPv6 gom theo tiền tố /64 vì một thuê bao thường
 * được cấp cả /64. Lần bị chặn không được ghi vào bộ đếm. Giữ trong bộ nhớ một tiến trình (như LoginAttemptLimiter).
 */
@Component
public class LoginIpLimiter {

    /** Quá số khóa này thì dọn các IP đã hết hạn. */
    static final int CLEANUP_THRESHOLD = 10_000;
    static final String UNKNOWN_IP = "unknown";

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public LoginIpLimiter(LoginIpLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginIpLimiter(LoginIpLimitProperties properties, Clock clock) {
        this.maxAttempts = properties.getMaxAttempts();
        this.window = properties.getWindow();
        this.clock = clock;
    }

    /**
     * Ghi một lần đăng nhập từ {@code clientIp}.
     *
     * @throws LoginRateLimitedException khi IP đã đủ {@code maxAttempts} lần trong cửa sổ (kèm số giây tới khi được thử lại)
     */
    public void check(String clientIp) {
        Instant now = clock.instant();
        Deque<Instant> times = attempts.computeIfAbsent(key(clientIp), k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) {
                times.pollFirst();
            }
            if (times.size() >= maxAttempts) {
                long wait = Duration.between(now, times.peekFirst().plus(window)).toMillis();
                throw LoginRateLimitedException.byIp(Math.max(1, (wait + 999) / 1000));
            }
            times.addLast(now);
        }
        if (attempts.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
    }

    /** Khóa của một IP: IPv4 nguyên văn; IPv6 chỉ lấy 64 bit đầu; thiếu thì dùng chung một khóa. */
    static String key(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return UNKNOWN_IP;
        }
        String ip = clientIp.strip();
        int zone = ip.indexOf('%');
        if (zone >= 0) {
            ip = ip.substring(0, zone);
        }
        if (ip.indexOf(':') < 0) {
            return ip;
        }
        try {
            // Có dấu ':' nên là địa chỉ IPv6 dạng chữ: không tra DNS.
            InetAddress address = InetAddress.getByName(ip);
            if (address instanceof Inet6Address) {
                return HexFormat.of().formatHex(address.getAddress(), 0, 8) + "/64";
            }
            return address.getHostAddress(); // IPv6 ánh xạ IPv4 (::ffff:a.b.c.d)
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    private void cleanup(Instant now) {
        attempts.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Instant last = e.getValue().peekLast();
                return last == null || !last.plus(window).isAfter(now);
            }
        });
    }
}
