package com.datn.mcq.llm.client;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Giám sát (08/11): số liệu Micrometer của các lượt gọi Gemini, ghi tại phễu {@link LlmClient}. Thẻ {@code result} chỉ
 * nhận bốn giá trị cố định ({@code ok, http_429, http_error, network_error}) để số chuỗi thời gian không tăng theo dữ liệu.
 * Prometheus hiển thị: {@code datn_gemini_calls_total}, {@code datn_gemini_call_seconds},
 * {@code datn_gemini_tokens_total}, {@code datn_gemini_limiter_wait_seconds}.
 */
@Component
public class GeminiMetrics {

    public static final String OK = "ok";
    public static final String HTTP_429 = "http_429";
    public static final String HTTP_ERROR = "http_error";
    public static final String NETWORK_ERROR = "network_error";

    private final MeterRegistry registry;
    private final Counter tokens;
    private final Timer limiterWait;

    public GeminiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.tokens = Counter.builder("datn.gemini.tokens")
                .description("Tổng token Gemini báo về (totalTokenCount) của các lượt gọi thành công")
                .register(registry);
        this.limiterWait = Timer.builder("datn.gemini.limiter.wait")
                .description("Thời gian một lượt gọi chờ ở GeminiRateLimiter trước khi được gửi")
                .register(registry);
        // Khởi tạo sẵn ở giá trị 0 để lần đầu tiên cũng tính là mức tăng (xem ExportMetrics).
        for (String result : new String[]{OK, HTTP_429, HTTP_ERROR, NETWORK_ERROR}) {
            calls(result);
            callTimer(result);
        }
    }

    private Counter calls(String result) {
        return Counter.builder("datn.gemini.calls")
                .description("Số lượt gọi Gemini theo kết quả")
                .tag("result", result).register(registry);
    }

    private Timer callTimer(String result) {
        return Timer.builder("datn.gemini.call")
                .description("Thời gian một lượt gọi Gemini (không gồm thời gian chờ bộ điều tiết)")
                .tag("result", result).register(registry);
    }

    /** Ghi một lượt gọi HTTP đã kết thúc (kể cả lỗi); {@code nanos} tính từ lúc gửi tới lúc có kết quả. */
    public void recordCall(String result, long nanos) {
        calls(result).increment();
        callTimer(result).record(Duration.ofNanos(Math.max(0L, nanos)));
    }

    public void recordTokens(int count) {
        if (count > 0) {
            tokens.increment(count);
        }
    }

    public void recordLimiterWait(long nanos) {
        limiterWait.record(Duration.ofNanos(Math.max(0L, nanos)));
    }
}
