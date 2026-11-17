package com.datn.mcq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Hạn mức mỗi phút của endpoint công khai cho học sinh làm bài ({@code /api/public/**}). Quyết định 16/11 (rà soát an ninh
 * 07, AN-03): giới hạn theo IP CHỈ áp khi tạo lượt mới và đủ rộng cho nhiều lớp đi chung một IP (wifi trường); mở lại và
 * nộp bài đếm theo LƯỢT LÀM, hai bộ đếm riêng, nên một máy gọi liên tục không làm cả lớp bị 429 lúc nộp. Trang chào không
 * giới hạn (mã 10 ký tự ≈ 50 bit và 404 đồng nhất).
 *
 * @param startPerIpPerMinute        bắt đầu lượt làm (POST /api/public/assignments/{code}/attempts), mỗi IP
 * @param resumePerAttemptPerMinute  mở lại lượt làm (GET /api/public/attempts/current), mỗi lượt
 * @param submitPerAttemptPerMinute  nộp bài (POST /api/public/attempts/current/submit), mỗi lượt
 */
@ConfigurationProperties(prefix = "app.public-limit")
public record PublicLimitProperties(
        @DefaultValue("200") int startPerIpPerMinute,
        @DefaultValue("30") int resumePerAttemptPerMinute,
        @DefaultValue("20") int submitPerAttemptPerMinute
) {
    public PublicLimitProperties {
        if (startPerIpPerMinute < 1 || resumePerAttemptPerMinute < 1 || submitPerAttemptPerMinute < 1) {
            throw new IllegalArgumentException("app.public-limit.*-per-minute phải từ 1 trở lên");
        }
    }
}
