package com.datn.mcq.material.service;

/**
 * Cho module material biết một tài liệu đang được dùng cho bao nhiêu đề, để chặn xóa (409 MATERIAL_IN_USE). Module
 * {@code material} không được phụ thuộc {@code exam}, nên chỉ khai báo interface; {@code exam} cài đặt (cùng mẫu với
 * MatrixUsageLookup của module matrix).
 */
public interface MaterialUsageLookup {

    /** Số đề đang dùng tài liệu {@code materialId}. */
    int examCount(Integer materialId);
}
