package com.datn.mcq.common.security;

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
 * Giới hạn số lần gọi theo IP bằng cửa sổ trượt: tối đa {@code maxCalls} lần trong {@code window} mỗi IP. Cùng thuật toán
 * với {@code auth.service.LoginIpLimiter} (IPv6 gom theo tiền tố /64, lần bị chặn không được ghi vào bộ đếm), nhưng không
 * gắn với đăng nhập: dùng cho endpoint công khai của bài làm trực tuyến. Giữ trong bộ nhớ một tiến trình: khởi động lại thì
 * mất, chạy nhiều bản backend thì mỗi bản đếm riêng.
 */
public final class SlidingWindowIpLimiter {

    /** Quá số khóa này thì dọn các IP đã hết hạn. */
    static final int CLEANUP_THRESHOLD = 10_000;
    static final String UNKNOWN_IP = "unknown";

    private final ConcurrentHashMap<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final int maxCalls;
    private final Duration window;
    private final Clock clock;

    public SlidingWindowIpLimiter(int maxCalls, Duration window) {
        this(maxCalls, window, Clock.systemUTC());
    }

    public SlidingWindowIpLimiter(int maxCalls, Duration window, Clock clock) {
        if (maxCalls < 1) {
            throw new IllegalArgumentException("maxCalls phải từ 1 trở lên");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window phải lớn hơn 0");
        }
        this.maxCalls = maxCalls;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Ghi một lần gọi từ {@code clientIp}.
     *
     * @return 0 khi được phép; ngược lại số giây (ít nhất 1) tới khi được gọi lại, và lần này KHÔNG được ghi
     */
    public long tryAcquire(String clientIp) {
        Instant now = clock.instant();
        Deque<Instant> times = calls.computeIfAbsent(key(clientIp), k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) {
                times.pollFirst();
            }
            if (times.size() >= maxCalls) {
                long wait = Duration.between(now, times.peekFirst().plus(window)).toMillis();
                return Math.max(1, (wait + 999) / 1000);
            }
            times.addLast(now);
        }
        if (calls.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
        return 0;
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
        calls.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Instant last = e.getValue().peekLast();
                return last == null || !last.plus(window).isAfter(now);
            }
        });
    }
}
