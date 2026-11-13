package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Kết luận của Solver về yêu cầu của giáo viên (12/11, PR B, {@code quality_check_result.request_verdict}, V28). Chỉ là cờ
 * thông tin ({@code infoReasons}): không đổi trạng thái câu, không bật "cần xem kỹ", không kích hoạt sinh lại.
 */
public enum RequestVerdict implements DbEnum {
    MET("met"),
    PARTIAL("partial"),
    NOT_MET("not_met");

    private final String dbValue;

    RequestVerdict(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /** Giá trị Solver trả (không phân biệt hoa thường); lạ hoặc trống thì null (không kết luận). */
    public static RequestVerdict parseOrNull(Object raw) {
        if (!(raw instanceof String s)) {
            return null;
        }
        String v = s.strip().toLowerCase(Locale.ROOT);
        for (RequestVerdict r : values()) {
            if (r.dbValue.equals(v)) {
                return r;
            }
        }
        return null;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<RequestVerdict> {
        public Conv() {
            super(RequestVerdict.class);
        }
    }
}
