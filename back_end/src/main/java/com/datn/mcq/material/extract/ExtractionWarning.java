package com.datn.mcq.material.extract;

import com.datn.mcq.common.util.SimpleJson;

import java.util.List;

/**
 * Cảnh báo lúc trích văn bản (không chặn việc lưu tài liệu), ví dụ công thức MathType bị bỏ.
 *
 * @param code    mã máy đọc được: MATHTYPE_FORMULAS_DROPPED, FORMULA_LOSS_HIGH, FORMULA_IMAGES_NOT_READ,
 *                PDF_TEXT_QUALITY_LOW
 * @param count   số đối tượng bị ảnh hưởng; null nếu không áp dụng
 * @param total   tổng số đối tượng cùng loại để so (ví dụ tổng số công thức); null nếu không áp dụng
 * @param message lời giải thích cho giáo viên
 */
public record ExtractionWarning(String code, Integer count, Integer total, String message) {

    /** Mảng JSON cho cột lesson_material.warnings; null khi không có cảnh báo (cột để NULL). */
    public static String toJson(List<ExtractionWarning> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < warnings.size(); i++) {
            ExtractionWarning w = warnings.get(i);
            sb.append(i > 0 ? "," : "").append("{\"code\":").append(SimpleJson.quote(w.code()));
            if (w.count() != null) {
                sb.append(",\"count\":").append(w.count());
            }
            if (w.total() != null) {
                sb.append(",\"total\":").append(w.total());
            }
            sb.append(",\"message\":").append(SimpleJson.quote(w.message())).append("}");
        }
        return sb.append("]").toString();
    }
}
