package com.datn.mcq.llm.client;

import com.datn.mcq.common.exception.UpstreamServiceException;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.llm.dto.LlmCallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(LlmClient.class);

    /** Địa chỉ gốc của Gemini; {@code app.llm.base-url} chỉ đổi khi kiểm chứng bằng Gemini giả (W1-5). */
    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final Pattern RETRY_DELAY_PATTERN =
            Pattern.compile("\"retryDelay\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient;
    private final String defaultApiKey;
    private final String defaultModel;
    private final String baseUrl;
    private final GeminiRateLimiter rateLimiter;

    public LlmClient(String defaultApiKey, String defaultModel) {
        this(defaultApiKey, defaultModel, null, null);
    }

    public LlmClient(String defaultApiKey, String defaultModel, String baseUrl) {
        this(defaultApiKey, defaultModel, baseUrl, null);
    }

    @Autowired
    public LlmClient(
            @Value("${app.llm.api-key:}") String defaultApiKey,
            @Value("${app.llm.model:gemini-3.5-flash-lite}") String defaultModel,
            @Value("${app.llm.base-url:}") String baseUrl,
            @Autowired(required = false) GeminiRateLimiter rateLimiter) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.defaultApiKey = defaultApiKey != null ? defaultApiKey.trim() : "";
        this.defaultModel = (defaultModel != null && !defaultModel.isBlank())
                ? defaultModel.trim()
                : "gemini-3.5-flash-lite";
        this.rateLimiter = rateLimiter;
    }

    /** Trống thì dùng địa chỉ Gemini thật; luôn kết thúc bằng "/" để ghép tên model. */
    static String normalizeBaseUrl(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        String url = configured.trim();
        return url.endsWith("/") ? url : url + "/";
    }

    public boolean isConfigured() {
        return !resolveApiKey().isBlank();
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public LlmCallResult callJsonMode(String prompt, String modelName, Double temperature) {
        double temp = temperature != null ? temperature : 0.0;
        String generationConfig = "\"generationConfig\":{\"temperature\":" + temp
                + ",\"responseMimeType\":\"application/json\"}";
        return callGemini(prompt, modelName, generationConfig);
    }

    /**
     * Gọi ở JSON mode kèm responseSchema để model trả JSON đúng cấu trúc.
     * Structured output chỉ giảm lỗi định dạng, không thay thế bước kiểm tra kết quả ở phía gọi.
     */
    public LlmCallResult callJsonModeWithSchema(
            String prompt, String modelName, Double temperature, String responseSchemaJson) {
        return callGemini(prompt, modelName, jsonModeGenerationConfig(temperature, responseSchemaJson));
    }

    /** Dựng phần generationConfig cho JSON mode kèm responseSchema (không gọi mạng). */
    public static String jsonModeGenerationConfig(Double temperature, String responseSchemaJson) {
        if (responseSchemaJson == null || responseSchemaJson.isBlank()) {
            throw new IllegalArgumentException("responseSchemaJson không được để trống");
        }
        double temp = temperature != null ? temperature : 0.0;
        return "\"generationConfig\":{\"temperature\":" + temp
                + ",\"responseMimeType\":\"application/json\""
                + ",\"responseSchema\":" + responseSchemaJson.strip() + "}";
    }

    @SuppressWarnings("unchecked")
    private LlmCallResult callGemini(String prompt, String requestedModel, String generationConfigFragment) {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình LLM_API_KEY (hoặc GEMINI_API_KEY) cho LlmClient");
        }
        String model = (requestedModel != null && !requestedModel.isBlank())
                ? requestedModel.trim()
                : defaultModel;

        String body = "{\"contents\":[{\"parts\":[{\"text\":" + SimpleJson.quote(prompt) + "}]}],"
                + generationConfigFragment + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + model + ":generateContent"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        if (rateLimiter != null) {
            rateLimiter.acquire();
        }

        long startNanos = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Lỗi kết nối Gemini API: " + e.getMessage(), e);
        }
        double latencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            if (status == 429 && rateLimiter != null) {
                rateLimiter.onRateLimited(parseRetryDelay(response.body()).orElse(null));
            }
            throw errorForStatus(status, response.body());
        }

        Map<String, Object> root = SimpleJson.parseObject(response.body());
        List<Object> candidates = (List<Object>) root.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Phản hồi Gemini không có candidates");
        }
        Map<String, Object> firstCandidate = (Map<String, Object>) candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
        List<Object> parts = content != null ? (List<Object>) content.get("parts") : null;
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Phản hồi Gemini không có content.parts");
        }
        Map<String, Object> firstPart = (Map<String, Object>) parts.get(0);
        String text = firstPart.get("text") != null ? String.valueOf(firstPart.get("text")) : "";

        Integer totalTokens = null;
        Object usageObj = root.get("usageMetadata");
        if (usageObj instanceof Map<?, ?> usageMap) {
            Object tok = usageMap.get("totalTokenCount");
            if (tok instanceof Number num) {
                totalTokens = num.intValue();
            }
        }
        return new LlmCallResult(text, totalTokens, latencyMs, 0.0, model);
    }

    /**
     * Đọc {@code retryDelay} từ thân phản hồi lỗi HTTP 429 của Gemini (ví dụ {@code "retryDelay": "42s"} hoặc
     * {@code "42.5s"} trong {@code error.details} kiểu {@code RetryInfo}). Trả rỗng khi không có hoặc không đọc được.
     */
    public static Optional<Duration> parseRetryDelay(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = SimpleJson.parseObject(body);
            Object errObj = root.get("error");
            if (errObj instanceof Map<?, ?> errMap) {
                Object direct = errMap.get("retryDelay");
                if (direct instanceof String s) {
                    Optional<Duration> parsed = parseDurationString(s);
                    if (parsed.isPresent()) {
                        return parsed;
                    }
                }
                Object detailsObj = errMap.get("details");
                if (detailsObj instanceof List<?> details) {
                    for (Object item : details) {
                        if (item instanceof Map<?, ?> detailMap) {
                            Object val = detailMap.get("retryDelay");
                            if (val instanceof String s) {
                                Optional<Duration> parsed = parseDurationString(s);
                                if (parsed.isPresent()) {
                                    return parsed;
                                }
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Fallback sang biểu thức chính quy nếu JSON không chuẩn.
        }
        Matcher m = RETRY_DELAY_PATTERN.matcher(body);
        if (m.find()) {
            return parseDurationString(m.group(1));
        }
        return Optional.empty();
    }

    private static Optional<Duration> parseDurationString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (s.endsWith("ms")) {
                long ms = Math.round(Double.parseDouble(s.substring(0, s.length() - 2).trim()));
                return ms >= 0 ? Optional.of(Duration.ofMillis(ms)) : Optional.empty();
            }
            if (s.endsWith("s")) {
                double seconds = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                long ms = (long) Math.ceil(seconds * 1000.0);
                return ms >= 0 ? Optional.of(Duration.ofMillis(ms)) : Optional.empty();
            }
            double seconds = Double.parseDouble(s);
            long ms = (long) Math.ceil(seconds * 1000.0);
            return ms >= 0 ? Optional.of(Duration.ofMillis(ms)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Lỗi cho mã HTTP không thành công từ Gemini (không gọi mạng).
     * 429 và 503 thành {@link UpstreamServiceException} với thông báo ngắn; body thô chỉ ghi vào log máy chủ.
     * Mã khác giữ nguyên thông báo cũ.
     */
    static RuntimeException errorForStatus(int status, String body) {
        if (status == 429) {
            LOG.warn("Gemini trả HTTP 429: {}", abbreviate(body));
            return new UpstreamServiceException(429,
                    "Gemini đang giới hạn tần suất gọi (HTTP 429). Hãy chờ khoảng một phút rồi thử lại.");
        }
        if (status == 503) {
            LOG.warn("Gemini trả HTTP 503: {}", abbreviate(body));
            return new UpstreamServiceException(503,
                    "Gemini tạm thời quá tải hoặc bảo trì (HTTP 503). Hãy thử lại sau ít phút.");
        }
        return new RuntimeException("HTTP " + status + " từ Gemini API: " + body);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }

    private String resolveApiKey() {
        if (!defaultApiKey.isBlank()) {
            return defaultApiKey;
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        String llmKey = System.getenv("LLM_API_KEY");
        return llmKey != null ? llmKey.trim() : "";
    }
}
