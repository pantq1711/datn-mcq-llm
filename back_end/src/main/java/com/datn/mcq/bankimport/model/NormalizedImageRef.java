package com.datn.mcq.bankimport.model;

/**
 * Bản ghi chứa dữ liệu ảnh đã được kiểm tra và chuẩn hóa thành công, sẵn sàng lưu vào bảng question_image.
 */
public record NormalizedImageRef(
        String tempId,
        String role,
        short ordinal,
        String mime,
        byte[] data,
        int byteSize,
        int widthPx,
        int heightPx,
        short displayWidthMm,
        String sha256,
        String sourceFormat
) {}
