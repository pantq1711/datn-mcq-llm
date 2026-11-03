package com.datn.mcq.bankimport.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Giới hạn của nhập ngân hàng câu từ Word (V26), cấu hình ở {@code app.bank-import.*}. Kiểm zip (số mục, dung lượng giải
 * nén, tỉ lệ nén) dùng chung giới hạn {@code app.materials.zip-*} của tài liệu bài học.
 */
@Component
@ConfigurationProperties("app.bank-import")
public class BankImportProperties {

    /** Dung lượng tối đa của một file (byte). */
    private long maxFileBytes = 10L * 1024 * 1024;
    /** Số câu tối đa đọc được trong một file (kể cả câu bị bỏ); nhiều hơn thì 422 IMPORT_TOO_MANY_QUESTIONS. */
    private int maxQuestions = 200;
    /** Số lô đang chờ xác nhận (chưa hết hạn) tối đa của một giáo viên; quá thì 409 IMPORT_PENDING_LIMIT. */
    private int maxPendingBatches = 3;
    /** Số lần tải lên tối đa mỗi giờ của một giáo viên (tính cả lần lỗi); quá thì 429 TOO_MANY_UPLOADS. */
    private int maxUploadsPerHour = 30;
    /** Thời hạn của bản xem trước; quá hạn thì 410 IMPORT_EXPIRED và nội dung bị xóa. */
    private Duration previewTtl = Duration.ofHours(24);
    /** Dung lượng tối đa của word/document.xml (sau giải nén) trước khi dựng DOM. */
    private long maxXmlBytes = 16L * 1024 * 1024;
    /** Dung lượng tối đa của một tệp công thức nhúng; lớn hơn thì công thức coi như không chuyển được. */
    private int maxEmbeddingBytes = 1024 * 1024;
    /** Tổng dung lượng tối đa các tệp công thức nhúng được nạp; quá thì 422 INVALID_FILE. */
    private long maxEmbeddingTotalBytes = 64L * 1024 * 1024;
    /** Chờ chỗ xử lý file nặng (dùng chung PandocLimiter) tối đa bao lâu trước khi 503 EXTRACTOR_BUSY. */
    private Duration queueWait = Duration.ofSeconds(30);

    /** Bản sao các giá trị (để test đặt lại sau khi đổi). */
    public BankImportProperties copy() {
        BankImportProperties c = new BankImportProperties();
        c.restore(this);
        return c;
    }

    /** Chép mọi giá trị từ {@code other}. */
    public void restore(BankImportProperties other) {
        maxFileBytes = other.maxFileBytes;
        maxQuestions = other.maxQuestions;
        maxPendingBatches = other.maxPendingBatches;
        maxUploadsPerHour = other.maxUploadsPerHour;
        previewTtl = other.previewTtl;
        maxXmlBytes = other.maxXmlBytes;
        maxEmbeddingBytes = other.maxEmbeddingBytes;
        maxEmbeddingTotalBytes = other.maxEmbeddingTotalBytes;
        queueWait = other.queueWait;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getMaxQuestions() {
        return maxQuestions;
    }

    public void setMaxQuestions(int maxQuestions) {
        this.maxQuestions = maxQuestions;
    }

    public int getMaxPendingBatches() {
        return maxPendingBatches;
    }

    public void setMaxPendingBatches(int maxPendingBatches) {
        this.maxPendingBatches = maxPendingBatches;
    }

    public int getMaxUploadsPerHour() {
        return maxUploadsPerHour;
    }

    public void setMaxUploadsPerHour(int maxUploadsPerHour) {
        this.maxUploadsPerHour = maxUploadsPerHour;
    }

    public Duration getPreviewTtl() {
        return previewTtl;
    }

    public void setPreviewTtl(Duration previewTtl) {
        this.previewTtl = previewTtl;
    }

    public long getMaxXmlBytes() {
        return maxXmlBytes;
    }

    public void setMaxXmlBytes(long maxXmlBytes) {
        this.maxXmlBytes = maxXmlBytes;
    }

    public int getMaxEmbeddingBytes() {
        return maxEmbeddingBytes;
    }

    public void setMaxEmbeddingBytes(int maxEmbeddingBytes) {
        this.maxEmbeddingBytes = maxEmbeddingBytes;
    }

    public long getMaxEmbeddingTotalBytes() {
        return maxEmbeddingTotalBytes;
    }

    public void setMaxEmbeddingTotalBytes(long maxEmbeddingTotalBytes) {
        this.maxEmbeddingTotalBytes = maxEmbeddingTotalBytes;
    }

    public Duration getQueueWait() {
        return queueWait;
    }

    public void setQueueWait(Duration queueWait) {
        this.queueWait = queueWait;
    }
}
