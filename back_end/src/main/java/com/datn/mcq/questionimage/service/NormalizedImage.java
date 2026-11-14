package com.datn.mcq.questionimage.service;

/**
 * Dữ liệu ảnh sau khi đã được chuẩn hóa, xóa metadata và thu nhỏ kích thước.
 */
public record NormalizedImage(
        byte[] data,
        String mime,
        String sourceFormat,
        int width,
        int height,
        int byteSize,
        String sha256
) {
}
