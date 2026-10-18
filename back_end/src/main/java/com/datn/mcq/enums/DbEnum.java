package com.datn.mcq.enums;

/**
 * Enum có chuỗi lưu trong DB (và trong JSON gửi frontend) khác với tên hằng Java.
 * Chuỗi này phải khớp đúng cột "Giá trị trong DB" của docs/contract/enums.md.
 */
public interface DbEnum {
    String getDbValue();

    static <E extends Enum<E> & DbEnum> E fromDbValue(Class<E> type, String dbData) {
        if (dbData == null) {
            return null;
        }
        String value = dbData.trim();
        for (E constant : type.getEnumConstants()) {
            if (constant.getDbValue().equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "Giá trị '" + dbData + "' không hợp lệ cho " + type.getSimpleName());
    }
}
