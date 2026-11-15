package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Kết quả kiểm lỗi sai của một phương án nhiễu câu {@code mcq} (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md mục 5).
 * Lưu ở {@code quality_check_result.distractor_checks} và {@code bank_item.distractor_checks} (V29) dạng
 * {@code {"B": "match", ...}}; {@link #UNCHECKED} KHÔNG lưu, suy ra khi phương án có chữ lỗi mà không có khóa. Chỉ là thông
 * tin: không đổi trạng thái câu, không bật "cần xem kỹ".
 */
public enum DistractorCheck implements DbEnum {
    /** Solver áp lỗi vào lời giải của nó ra đúng phương án người soạn đã gán. */
    MATCH("match"),
    /** Solver áp lỗi ra phương án khác, hoặc không ra phương án nào ({@code none}). */
    MISMATCH("mismatch"),
    /** Solver không áp được ({@code unclear}), thiếu kết quả hoặc giá trị lạ. */
    UNKNOWN("unknown"),
    /** Chưa kiểm (không lưu): Solver chưa chạy, lỗi, hoặc bản giáo viên sửa đang chờ kiểm lại. */
    UNCHECKED("unchecked");

    private final String dbValue;

    DistractorCheck(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /** Giá trị đã lưu (không phân biệt hoa thường); lạ hoặc trống thì null. */
    public static DistractorCheck parseOrNull(Object raw) {
        if (!(raw instanceof String s)) {
            return null;
        }
        String v = s.strip().toLowerCase(Locale.ROOT);
        for (DistractorCheck c : values()) {
            if (c.dbValue.equals(v)) {
                return c;
            }
        }
        return null;
    }
}
