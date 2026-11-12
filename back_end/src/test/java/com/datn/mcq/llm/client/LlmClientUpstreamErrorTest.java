package com.datn.mcq.llm.client;

import com.datn.mcq.common.exception.UpstreamServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V3: phân loại lỗi HTTP từ Gemini, không gọi mạng.
 * 429 và 503 thành UpstreamServiceException mang đúng mã; mã khác giữ hành vi cũ.
 */
class LlmClientUpstreamErrorTest {

    private static final String RAW_BODY = "{\"error\":{\"code\":429,\"message\":\"Quota exceeded for metric X\"}}";

    @Test
    void http429BecomesUpstreamExceptionWith429AndFriendlyMessage() {
        RuntimeException ex = LlmClient.errorForStatus(429, RAW_BODY);
        UpstreamServiceException up = assertInstanceOf(UpstreamServiceException.class, ex);
        assertEquals(429, up.getHttpStatus());
        assertTrue(up.getMessage().contains("429"), up.getMessage());
        assertFalse(up.getMessage().contains("Quota exceeded"), "Không đưa body thô của Gemini vào thông báo");
    }

    @Test
    void http503BecomesUpstreamExceptionWith503() {
        RuntimeException ex = LlmClient.errorForStatus(503, "{\"error\":{\"code\":503}}");
        UpstreamServiceException up = assertInstanceOf(UpstreamServiceException.class, ex);
        assertEquals(503, up.getHttpStatus());
        assertTrue(up.getMessage().contains("503"), up.getMessage());
    }

    @Test
    void otherStatusesKeepPreviousBehaviour() {
        RuntimeException ex = LlmClient.errorForStatus(500, "boom");
        assertFalse(ex instanceof UpstreamServiceException);
        assertFalse(ex instanceof NonRetryableLlmException, "5xx là lỗi tạm thời, vẫn thử lại");
        assertTrue(ex.getMessage().startsWith("HTTP 500 từ Gemini API"), ex.getMessage());
    }

    /** C-04: HTTP 4xx khác 429 gọi lại cũng lỗi như cũ: lỗi không thử lại, thông điệp giữ nguyên dạng cũ. */
    @Test
    void http4xxKhac429LaLoiKhongThuLai() {
        for (int status : new int[] {400, 401, 403, 404, 422}) {
            RuntimeException ex = LlmClient.errorForStatus(status, "{\"error\":\"bad\"}");
            assertInstanceOf(NonRetryableLlmException.class, ex, "HTTP " + status);
            assertTrue(ex.getMessage().startsWith("HTTP " + status + " từ Gemini API"), ex.getMessage());
        }
    }
}
