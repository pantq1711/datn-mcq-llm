package com.datn.mcq.questionimage.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cấu hình tham số chuẩn hóa và chuyển đổi ảnh câu hỏi (I2, PR #61).
 */
@Component
@ConfigurationProperties(prefix = "app.question-images.import")
public class ImageImportProperties {

    /** Đường dẫn đến binary soffice / libreoffice trên hệ điều hành. */
    private String libreofficePath = "soffice";

    /** Thời gian chờ tối đa khi chạy tiến trình soffice đổi cả lô EMF. */
    private Duration timeout = Duration.ofSeconds(60);

    /** Kích thước byte tối đa của ảnh nguồn thô (mặc định 2 MB = 2.097.152 bytes). */
    private int maxSourceBytes = 2 * 1024 * 1024;

    /** Kích thước pixel tối đa mỗi cạnh của ảnh nguồn (mặc định 4000 px). */
    private int maxDimension = 4000;

    /** Tổng số pixel tối đa của ảnh nguồn (mặc định 12 Megapixels = 12.000.000 px). */
    private long maxPixels = 12_000_000L;

    /** Kích thước byte tối đa của ảnh sau khi chuẩn hóa (mặc định 500 KB = 512.000 bytes). */
    private int maxOutputBytes = 500 * 1024;

    /** Kích thước pixel tối đa cạnh dài sau khi chuẩn hóa (mặc định 1600 px). */
    private int maxOutputDimension = 1600;

    public String getLibreofficePath() {
        return libreofficePath;
    }

    public void setLibreofficePath(String libreofficePath) {
        this.libreofficePath = libreofficePath;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxSourceBytes() {
        return maxSourceBytes;
    }

    public void setMaxSourceBytes(int maxSourceBytes) {
        this.maxSourceBytes = maxSourceBytes;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public void setMaxDimension(int maxDimension) {
        this.maxDimension = maxDimension;
    }

    public long getMaxPixels() {
        return maxPixels;
    }

    public void setMaxPixels(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getMaxOutputDimension() {
        return maxOutputDimension;
    }

    public void setMaxOutputDimension(int maxOutputDimension) {
        this.maxOutputDimension = maxOutputDimension;
    }
}
