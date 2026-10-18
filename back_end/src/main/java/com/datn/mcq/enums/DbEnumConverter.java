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
        return DbEnum.fromDbValue(type, dbData);
    }
}
