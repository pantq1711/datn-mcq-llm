package com.datn.mcq.enums;

/**
 * Enum có chuỗi lưu trong DB (và trong JSON gửi frontend) khác với tên hằng Java.
 * Chuỗi này phải khớp đúng cột "Giá trị trong DB" của docs/contract/enums.md.
 */
public interface DbEnum {
    String getDbValue();
}
