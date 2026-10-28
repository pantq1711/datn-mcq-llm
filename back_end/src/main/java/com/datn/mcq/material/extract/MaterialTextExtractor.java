package com.datn.mcq.material.extract;

import com.datn.mcq.config.PandocLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Cửa vào trích văn bản tài liệu bài học (W2-3): nhận dạng loại file CHỈ từ nội dung, rồi giao cho bộ đọc tương ứng
 * (PDF, docx qua pandoc, txt). Dùng chung Semaphore {@link PandocLimiter} với PandocRunner để giới hạn tiến trình nặng
 * chạy đồng thời bảo vệ bộ nhớ container (W2-4a Mục 1); hết chỗ sau {@code queue-wait} thì 503.
 * Không lưu gì và không đụng DB: nơi gọi (service tải lên) tính sha256, kiểm quyền và lưu.
 */
@Service
public class MaterialTextExtractor {

    private final MaterialProperties props;
    private final PlainTextExtractor plain;
    private final DocxTextExtractor docx;
    private final PdfTextExtractor pdf;
    private final PandocLimiter limiter;

    @Autowired
    public MaterialTextExtractor(MaterialProperties props, PlainTextExtractor plain, DocxTextExtractor docx,
                                 PdfTextExtractor pdf, PandocLimiter limiter) {
        this.props = props;
        this.plain = plain;
        this.docx = docx;
        this.pdf = pdf;
        this.limiter = limiter;
    }

    public MaterialTextExtractor(MaterialProperties props, PlainTextExtractor plain, DocxTextExtractor docx,
                                 PdfTextExtractor pdf) {
        this(props, plain, docx, pdf, new PandocLimiter(props.getMaxConcurrentExtractions(), props.getQueueWait(),
                props.getPandocHeapMb()));
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
        boolean acquired = false;
        if (limiter != null) {
            try {
                acquired = limiter.tryAcquire(props.getQueueWait());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw MaterialErrors.busy();
            }
            if (!acquired) {
                throw MaterialErrors.busy();
            }
        }
        boolean releaseInFinally = true;
        try {
            if (kind == FileTypeSniffer.Kind.PDF) {
                releaseInFinally = false;
                return pdf.extract(content, limiter != null && acquired ? limiter::release : null);
            }
            return switch (kind) {
                case ZIP -> docx.extract(content);
                case TEXT -> plain.extract(content);
                default -> throw new IllegalStateException();
            };
        } finally {
            if (releaseInFinally && limiter != null && acquired) {
                limiter.release();
            }
        }
    }
}
