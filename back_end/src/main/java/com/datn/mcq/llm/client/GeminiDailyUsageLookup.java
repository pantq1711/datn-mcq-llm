package com.datn.mcq.llm.client;

import java.time.LocalDateTime;

/**
 * W2-4b (C): đếm số lượt gọi Gemini thành công đã lưu trong DB kể từ mốc 00:00 giờ Thái Bình Dương (đã đổi sang giờ
 * địa phương của JVM) để khởi tạo bộ đếm hạn mức ngày khi ứng dụng khởi động. Khai báo ở module {@code llm} để
 * {@code llm} không phụ thuộc ngược vào {@code generation}/{@code qc} (quy tắc 7 trong backend-structure.md).
 */
@FunctionalInterface
public interface GeminiDailyUsageLookup {

    int countCallsSince(LocalDateTime since);
}

