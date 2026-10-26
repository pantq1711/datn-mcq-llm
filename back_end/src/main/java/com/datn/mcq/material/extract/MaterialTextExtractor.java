package com.datn.mcq.material.extract;

import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

/**
 * Cửa vào trích văn bản tài liệu bài học (W2-3): nhận dạng loại file CHỈ từ nội dung, rồi giao cho bộ đọc tương ứng
 * (PDF, docx qua pandoc, txt). Giới hạn số lần trích chạy đồng thời; hết chỗ sau {@code queue-wait} thì 503. Không lưu gì
 * và không đụng DB: nơi gọi (service tải lên) tính sha256, kiểm quyền và lưu.
 */
@Service
public class MaterialTextExtractor {

    private final MaterialProperties props;
    private final PlainTextExtractor plain;
    private final DocxTextExtractor docx;
    private final PdfTextExtractor pdf;
    private final Semaphore permits;

    public MaterialTextExtractor(MaterialProperties props, PlainTextExtractor plain, DocxTextExtractor docx,
                                 PdfTextExtractor pdf) {
        this.props = props;
        this.plain = plain;
        this.docx = docx;
        this.pdf = pdf;
        this.permits = new Semaphore(Math.max(1, props.getMaxConcurrentExtractions()), true);
    }

    /**
     * @throws com.datn.mcq.common.exception.ApiErrorException 400 EMPTY_FILE, 413 FILE_TOO_LARGE, 415
     *         UNSUPPORTED_FILE_TYPE, 422 INVALID_FILE / NO_EXTRACTABLE_TEXT / TEXT_TOO_LONG / MATERIAL_ENCRYPTED /
     *         TOO_MANY_PAGES / EXTRACTION_FAILED, 503 EXTRACTOR_UNAVAILABLE / EXTRACTOR_BUSY / EXTRACTION_TIMEOUT
     */
    public ExtractedText extract(byte[] content) {
        if (content != null && content.length > props.getMaxFileBytes()) {
            throw MaterialErrors.tooLarge(props.getMaxFileBytes());
        }
        FileTypeSniffer.Kind kind = FileTypeSniffer.sniff(content);
        boolean acquired;
        try {
            acquired = permits.tryAcquire(props.getQueueWait().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw MaterialErrors.busy();
        }
        if (!acquired) {
            throw MaterialErrors.busy();
        }
        try {
            return switch (kind) {
                case PDF -> pdf.extract(content);
                case ZIP -> docx.extract(content);
                case TEXT -> plain.extract(content);
            };
        } finally {
            permits.release();
        }
    }
}
