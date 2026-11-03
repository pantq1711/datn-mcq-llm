package com.datn.mcq.llm.client;

import com.datn.mcq.common.exception.UpstreamServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-18 (c): HTTP 429 của Gemini có dấu hiệu hết hạn mức NGÀY (thân chứa {@code PerDay} / {@code RequestsPerDay}) thì
 * {@link GeminiRateLimiter} được đánh dấu hết hạn mức ngày (không nhận job mới, tạo đề trả 429 sớm); 429 theo phút chỉ tạm
 * dừng như cũ. Chỉ nói chuyện với máy chủ giả trên loopback.
 */
class LlmClientDailyQuota429Test {

    private static final String DAILY_429 = """
            {"error":{"code":429,"message":"You exceeded your current quota","status":"RESOURCE_EXHAUSTED","details":[
            {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaMetric":
            "generativelanguage.googleapis.com/generate_content_free_tier_requests","quotaId":
            "GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]},
            {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"37s"}]}}
            """;
    private static final String MINUTE_429 = DAILY_429.replace("PerDay", "PerMinute");

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>(DAILY_429);

    @BeforeEach
    void startFakeGemini() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeGemini() {
        server.stop(0);
    }

    private String fakeBase() {
        return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort()
                + "/v1beta/models/";
    }

    private static GeminiRateLimiter limiter() {
        return new GeminiRateLimiter(0L, 480, 60_000L, Clock.systemDefaultZone(), d -> { }, s -> 0);
    }

    @Test
    void tra429HetHanMucNgayThiDanhDauHetHanMucNgay() {
        GeminiRateLimiter limiter = limiter();
        assertTrue(limiter.hasDailyQuota());
        LlmClient client = new LlmClient("khoa-gia", "m", fakeBase(), limiter);

        assertThrows(UpstreamServiceException.class, () -> client.callJsonMode("xin chào", "gemini-gia", 0.0));

        assertFalse(limiter.hasDailyQuota(), "429 PerDay: coi như hết hạn mức ngày tới 0h giờ Thái Bình Dương");
        assertThrows(QuotaExhaustedException.class, limiter::acquire, "lượt gọi sau ném QuotaExhaustedException ngay");
    }

    @Test
    void tra429TheoPhutThiChiTamDungKhongDanhDauHetHanMucNgay() {
        body.set(MINUTE_429);
        GeminiRateLimiter limiter = limiter();
        LlmClient client = new LlmClient("khoa-gia", "m", fakeBase(), limiter);

        assertThrows(UpstreamServiceException.class, () -> client.callJsonMode("xin chào", "gemini-gia", 0.0));

        assertTrue(limiter.hasDailyQuota(), "429 theo phút không phải hết hạn mức ngày");
    }
}
