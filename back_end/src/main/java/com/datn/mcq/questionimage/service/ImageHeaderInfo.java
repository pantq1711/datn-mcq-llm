package com.datn.mcq.questionimage.service;

/**
 * Thông tin kích thước và định dạng ảnh đọc từ header nhị phân (trước khi nạp ảnh vào bộ nhớ).
 */
public record ImageHeaderInfo(
        String format,
        String mime,
        int width,
        int height,
        int byteSize
) {
    public long totalPixels() {
        return (long) width * height;
    }
}
