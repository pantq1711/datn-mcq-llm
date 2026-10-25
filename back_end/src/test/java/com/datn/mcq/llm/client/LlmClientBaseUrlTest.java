package com.datn.mcq.llm.client;

import com.datn.mcq.llm.dto.LlmCallResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * W1-5: địa chỉ gốc của Gemini đọc từ cấu hình ({@code app.llm.base-url}) để kiểm chứng end-to-end bằng Gemini giả.
 * Chỉ nói chuyện với máy chủ giả trên loopback; không gọi mạng ngoài.
 */
class LlmClientBaseUrlTest {

    private static final String GEMINI_REPLY = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"ok\\":true}"}]}}],"usageMetadata":{"totalTokenCount":7}}
            """;

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestKey = new AtomicReference<>();

    @BeforeEach
    void startFakeGemini() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = GEMINI_REPLY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeGemini() {
        server.stop(0);
    }

    private String fakeBase() {
        return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort();
    }

    @Test
    void goiGeminiTheoBaseUrlDaCauHinh() {
        LlmClient client = new LlmClient("khoa-gia", "model-mac-dinh", fakeBase() + "/v1beta/models/");

        LlmCallResult result = client.callJsonMode("xin chào", "gemini-gia", 0.0);

        assertEquals("/v1beta/models/gemini-gia:generateContent", requestPath.get());
        assertEquals("khoa-gia", requestKey.get());
        assertEquals("{\"ok\":true}", result.rawText());
    }

    @Test
    void baseUrlThieuDauGachCuoiVanGhepDungDuongDan() {
        LlmClient client = new LlmClient("khoa-gia", "model-mac-dinh", fakeBase() + "/v1beta/models");

        client.callJsonMode("xin chào", "gemini-gia", 0.0);

        assertEquals("/v1beta/models/gemini-gia:generateContent", requestPath.get());
    }

    @Test
    void baseUrlTrongHoacKhongDatThiGiuNguyenDiaChiGeminiThat() {
        String gemini = "https://generativelanguage.googleapis.com/v1beta/models/";
        assertEquals(gemini, LlmClient.DEFAULT_BASE_URL);
        assertEquals(gemini, LlmClient.normalizeBaseUrl(null));
        assertEquals(gemini, LlmClient.normalizeBaseUrl(""));
        assertEquals(gemini, LlmClient.normalizeBaseUrl("   "));
    }
}
