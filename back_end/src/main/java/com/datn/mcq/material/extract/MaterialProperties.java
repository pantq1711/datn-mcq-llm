package com.datn.mcq.material.extract;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Giới hạn khi trích văn bản tài liệu bài học (W2-3), cấu hình ở {@code app.materials.*}. Giá trị mặc định là giới hạn
 * nghiệp vụ; trần cứng ở DB (V18) rộng hơn (10 MiB, 1.000.000 ký tự).
 */
@Component
@ConfigurationProperties("app.materials")
public class MaterialProperties {

    /** Dung lượng tối đa của một file (byte). */
    private long maxFileBytes = 10L * 1024 * 1024;
    /** Số ký tự (điểm mã Unicode) tối đa của văn bản trích; dài hơn thì từ chối, không cắt âm thầm. */
    private int maxChars = 300_000;
    /** Số trang PDF tối đa. */
    private int maxPages = 200;
    /** PDF có trung bình ít hơn số ký tự không-trắng này mỗi trang thì coi là bản scan (không OCR). */
    private int minPdfCharsPerPage = 20;

    /** Số mục tối đa trong file docx (zip). */
    private int zipMaxEntries = 1000;
    /** Dung lượng giải nén tối đa của một mục (byte). */
    private long zipMaxEntryBytes = 100L * 1024 * 1024;
    /** Tổng dung lượng giải nén tối đa của mọi mục (byte). */
    private long zipMaxTotalBytes = 200L * 1024 * 1024;
    /** Tỉ lệ giải nén/nén tối đa của một mục (chỉ xét mục giải nén trên 1 MiB). */
    private int zipMaxRatio = 200;

    /** Số tài liệu tối đa mỗi giáo viên được lưu (tải thêm khi đầy thì 409 MATERIAL_QUOTA_EXCEEDED). */
    private int maxPerOwner = 50;
    /** Số lần tải lên tối đa mỗi giờ của một giáo viên (tính cả lần lỗi, vì mỗi lần đều tốn pandoc/PDFBox); quá thì 429. */
    private int maxUploadsPerHour = 30;

    /** Tỉ lệ công thức MathType bị bỏ từ mức này trở lên thì cảnh báo rõ (FORMULA_LOSS_HIGH). */
    private double formulaLossWarnRatio = 0.3;

    /** Số lần trích chạy đồng thời tối đa (pandoc là tiến trình con cùng cgroup, PDFBox ăn bộ nhớ). */
    private int maxConcurrentExtractions = 1;
    /** Chờ chỗ trích tối đa bao lâu trước khi trả 503. */
    private Duration queueWait = Duration.ofSeconds(30);
    /** Thời gian tối đa cho một lần trích (pandoc hoặc PDFBox). */
    private Duration extractTimeout = Duration.ofSeconds(30);
    /** Trần heap của pandoc (MB, cờ +RTS -M). */
    private int pandocHeapMb = 256;

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getMinPdfCharsPerPage() {
        return minPdfCharsPerPage;
    }

    public void setMinPdfCharsPerPage(int minPdfCharsPerPage) {
        this.minPdfCharsPerPage = minPdfCharsPerPage;
    }

    public int getZipMaxEntries() {
        return zipMaxEntries;
    }

    public void setZipMaxEntries(int zipMaxEntries) {
        this.zipMaxEntries = zipMaxEntries;
    }

    public long getZipMaxEntryBytes() {
        return zipMaxEntryBytes;
    }

    public void setZipMaxEntryBytes(long zipMaxEntryBytes) {
        this.zipMaxEntryBytes = zipMaxEntryBytes;
    }

    public long getZipMaxTotalBytes() {
        return zipMaxTotalBytes;
    }

    public void setZipMaxTotalBytes(long zipMaxTotalBytes) {
        this.zipMaxTotalBytes = zipMaxTotalBytes;
    }

    public int getZipMaxRatio() {
        return zipMaxRatio;
    }

    public void setZipMaxRatio(int zipMaxRatio) {
        this.zipMaxRatio = zipMaxRatio;
    }

    public int getMaxPerOwner() {
        return maxPerOwner;
    }

    public void setMaxPerOwner(int maxPerOwner) {
        this.maxPerOwner = maxPerOwner;
    }

    public int getMaxUploadsPerHour() {
        return maxUploadsPerHour;
    }

    public void setMaxUploadsPerHour(int maxUploadsPerHour) {
        this.maxUploadsPerHour = maxUploadsPerHour;
    }

    public double getFormulaLossWarnRatio() {
        return formulaLossWarnRatio;
    }

    public void setFormulaLossWarnRatio(double formulaLossWarnRatio) {
        this.formulaLossWarnRatio = formulaLossWarnRatio;
    }

    public int getMaxConcurrentExtractions() {
        return maxConcurrentExtractions;
    }

    public void setMaxConcurrentExtractions(int maxConcurrentExtractions) {
        this.maxConcurrentExtractions = maxConcurrentExtractions;
    }

    public Duration getQueueWait() {
        return queueWait;
    }

    public void setQueueWait(Duration queueWait) {
        this.queueWait = queueWait;
    }

    public Duration getExtractTimeout() {
        return extractTimeout;
    }

    public void setExtractTimeout(Duration extractTimeout) {
        this.extractTimeout = extractTimeout;
    }

    public int getPandocHeapMb() {
        return pandocHeapMb;
    }

    public void setPandocHeapMb(int pandocHeapMb) {
        this.pandocHeapMb = pandocHeapMb;
    }
}
