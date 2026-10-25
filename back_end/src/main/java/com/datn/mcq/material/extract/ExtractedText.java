package com.datn.mcq.material.extract;

import java.util.List;

/**
 * Kết quả trích văn bản một tài liệu. {@code text} đã chuẩn hóa (NFC, bỏ ký tự điều khiển và ký tự ẩn) và nằm trong giới
 * hạn độ dài.
 *
 * @param mimeType  loại file do server nhận dạng từ nội dung (hằng MIME_* của LessonMaterial)
 * @param method    cách trích: pandoc, pdfbox hoặc plain (hằng METHOD_* của LessonMaterial)
 * @param pageCount số trang (chỉ PDF), null với loại khác
 */
public record ExtractedText(String text, String mimeType, String method, Integer pageCount,
                            List<ExtractionWarning> warnings) {

    public ExtractedText {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
