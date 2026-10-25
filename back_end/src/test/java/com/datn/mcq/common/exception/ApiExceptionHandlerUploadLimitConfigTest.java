package com.datn.mcq.common.exception;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2-3 bước 4: giới hạn trong thông báo 413 được nạp thật từ cấu hình của ứng dụng
 * ({@code spring.servlet.multipart.max-file-size}) vào bean xử lý lỗi.
 */
@SpringBootTest(properties = "spring.servlet.multipart.max-file-size=7MB")
@Import(TestSafetyConfiguration.class)
class ApiExceptionHandlerUploadLimitConfigTest {

    @Autowired
    private ApiExceptionHandler handler;

    @Test
    void docDuocGioiHanTuCauHinhMultipart() {
        String message = ApiExceptionHandlerUploadLimitTest.message(handler, -1);
        assertTrue(message.contains("tối đa 7 MB"), message);
    }
}
