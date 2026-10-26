package com.datn.mcq.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2-3 bước 4: thông báo 413 FILE_TOO_LARGE ghi rõ giới hạn lấy từ cấu hình {@code spring.servlet.multipart.max-file-size}
 * (Tomcat/Spring không báo ngưỡng khi từ chối, nên {@code getMaxUploadSize()} là -1).
 */
class ApiExceptionHandlerUploadLimitTest {

    static String message(ApiExceptionHandler handler, long reported) {
        ResponseEntity<Map<String, Object>> r = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(reported));
        assertEquals(413, r.getStatusCode().value());
        assertEquals("FILE_TOO_LARGE", r.getBody().get("code"));
        return String.valueOf(r.getBody().get("error"));
    }

    @Test
    void khongBaoNguongThiDungGioiHanCauHinhMacDinh10MB() {
        assertTrue(message(new ApiExceptionHandler(), -1).contains("tối đa 10 MB"));
    }

    @Test
    void nguongKhongTronMegabyteGhiTheoKB() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        handler.setMaxFileSize(DataSize.ofKilobytes(1536));
        assertTrue(message(handler, -1).contains("tối đa 1536 KB"), message(handler, -1));
    }

    @Test
    void nguongDoMayChuBaoCoUuTienHonCauHinh() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        handler.setMaxFileSize(DataSize.ofMegabytes(10));
        assertTrue(message(handler, 3L << 20).contains("tối đa 3 MB"));
    }
}
