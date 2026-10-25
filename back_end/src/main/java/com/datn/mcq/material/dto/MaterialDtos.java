package com.datn.mcq.material.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** DTO của API tài liệu bài học (W2-3). Không bao giờ trả văn bản đầy đủ, chủ sở hữu hay file gốc. */
public final class MaterialDtos {

    private MaterialDtos() {}

    /**
     * Cảnh báo lúc trích văn bản (ví dụ công thức MathType bị bỏ).
     *
     * @param code    MATHTYPE_FORMULAS_DROPPED, FORMULA_LOSS_HIGH, FORMULA_IMAGES_NOT_READ, PDF_TEXT_QUALITY_LOW
     * @param count   số đối tượng bị ảnh hưởng; bỏ khỏi JSON khi không áp dụng
     * @param total   tổng số đối tượng cùng loại để so (ví dụ tổng số công thức); bỏ khỏi JSON khi không áp dụng
     * @param message lời giải thích cho giáo viên
     */
    public record WarningDto(String code, @JsonInclude(JsonInclude.Include.NON_NULL) Integer count,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Integer total, String message) {}

    /**
     * Một tài liệu.
     *
     * @param kind        "docx", "pdf" hoặc "txt" (do server nhận dạng từ nội dung)
     * @param fileName    tên để hiển thị (đã bỏ đường dẫn và ký tự điều khiển)
     * @param charCount   số ký tự văn bản đã trích
     * @param pageCount   chỉ pdf
     * @param textPreview chỉ ở chi tiết: tối đa 500 ký tự đầu của văn bản đã trích
     */
    public record MaterialDto(
            Integer id,
            String fileName,
            String kind,
            Long sizeBytes,
            Integer charCount,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer pageCount,
            String sha256,
            String extractMethod,
            List<WarningDto> warnings,
            LocalDateTime createdAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String textPreview
    ) {}

    /** @param page trang hiện tại (từ 0) */
    public record MaterialPageDto(List<MaterialDto> items, int page, int size, long totalItems, int totalPages) {}
}
