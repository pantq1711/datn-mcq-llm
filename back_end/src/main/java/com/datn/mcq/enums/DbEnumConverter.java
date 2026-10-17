package com.datn.mcq.enums;

import jakarta.persistence.AttributeConverter;

/** Chuyển đổi hai chiều giữa enum Java và chuỗi trong DB. */
public abstract class DbEnumConverter<E extends Enum<E> & DbEnum> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected DbEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // CHAR(n) có thể trả về khoảng trắng thừa
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
