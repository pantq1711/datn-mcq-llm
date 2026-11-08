package com.datn.mcq.llm.client;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Giám sát (08/11): số liệu Micrometer của lượt gọi Gemini. Chỉ nói chuyện với máy chủ giả trên loopback; không gọi mạng
 * ngoài (M-18). Tên meter viết dạng Micrometer; Prometheus đổi thành {@code datn_gemini_calls_total}...
 */
class GeminiMetricsTest {

    private static final String OK = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"ok\\":true}"}]}}],"usageMetadata":{"totalTokenCount":7}}
            """;

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger hits = new AtomicInteger();
    private MeterRegistry registry;

    @BeforeEach
    void start() throws IOException {
        registry = new SimpleMeterRegistry();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            int code = status.get();
            byte[] body = (code == 200 ? OK : "{\"error\":{\"message\":\"x\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmClient client(GeminiRateLimiter limiter) {
        String base = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort()
                + "/v1beta/models/";
        return new LlmClient("khoa-gia", "model-gia", base, limiter, new GeminiMetrics(registry));
    }

    private GeminiRateLimiter limiter() {
        return new GeminiRateLimiter(0, 480, 0, Clock.systemDefaultZone(), d -> { }, null);
    }

    private double calls(String result) {
        var c = registry.find("datn.gemini.calls").tag("result", result).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void chuoiCoSanOGiaTri0ChoBonKetQua() {
        new GeminiMetrics(registry);
        for (String result : new String[]{"ok", "http_429", "http_error", "network_error"}) {
            assertEquals(0, registry.get("datn.gemini.calls").tag("result", result).counter().count(), result);
            assertEquals(0, registry.get("datn.gemini.call").tag("result", result).timer().count(), result);
        }
    }

    @Test
    void lenhGoiThanhCongDemOkVaCongToken() {
        client(limiter()).callJsonMode("xin chào", "m", 0.0);

        assertEquals(1, calls("ok"));
        assertEquals(7, registry.get("datn.gemini.tokens").counter().count());
        assertEquals(1, registry.get("datn.gemini.call").tag("result", "ok").timer().count());
        assertEquals(1, registry.get("datn.gemini.limiter.wait").timer().count(), "thời gian qua bộ điều tiết");
    }

    @Test
    void http429DemRiengVaKhongCongToken() {
        status.set(429);
        assertThrows(RuntimeException.class, () -> client(limiter()).callJsonMode("xin chào", "m", 0.0));

        assertEquals(1, calls("http_429"));
        assertEquals(0, calls("ok"));
        assertEquals(1, registry.get("datn.gemini.call").tag("result", "http_429").timer().count());
    }

    @Test
    void loiHttpKhacDemLaHttpError() {
        status.set(500);
        assertThrows(RuntimeException.class, () -> client(limiter()).callJsonMode("xin chào", "m", 0.0));

        assertEquals(1, calls("http_error"));
    }

    @Test
    void loiKetNoiDemLaNetworkError() {
        int port = server.getAddress().getPort();
        server.stop(0);
        String base = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port + "/v1beta/models/";
        LlmClient dead = new LlmClient("khoa-gia", "model-gia", base, limiter(), new GeminiMetrics(registry));

        assertThrows(RuntimeException.class, () -> dead.callJsonMode("xin chào", "m", 0.0));

        assertEquals(1, calls("network_error"));
    }

    @Test
    void khongCoMetricsThiVanGoiBinhThuong() {
        String base = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort()
                + "/v1beta/models/";
        LlmClient plain = new LlmClient("khoa-gia", "model-gia", base, limiter());

        assertEquals("{\"ok\":true}", plain.callJsonMode("xin chào", "m", 0.0).rawText());
        assertEquals(0, registry.getMeters().size());
    }

    @Test
    void gaugeHanMucNgayPhanAnhBoDieuTiet() {
        GeminiRateLimiter lim = new GeminiRateLimiter(0, 480, 0, Clock.systemDefaultZone(), d -> { }, since -> 12);
        new GeminiRateLimiterMetrics(lim).bindTo(registry);

        assertEquals(12, registry.get("datn.gemini.daily.used").gauge().value());
        assertEquals(480, registry.get("datn.gemini.daily.quota").gauge().value());

        lim.acquire();
        assertEquals(13, registry.get("datn.gemini.daily.used").gauge().value());
    }
}
