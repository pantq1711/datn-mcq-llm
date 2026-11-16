package com.datn.mcq.material.service;

/**
 * Cho module material biết một tài liệu đang được dùng cho bao nhiêu đề, để chặn xóa (409 MATERIAL_IN_USE). Module
 * {@code material} không được phụ thuộc {@code exam}, nên chỉ khai báo interface; {@code exam} cài đặt (cùng mẫu với
 * MatrixUsageLookup của module matrix).
 */
public interface MaterialUsageLookup {

    /** Số đề CHƯA XÓA đang dùng tài liệu {@code materialId} (V31: đề đã xóa không tính). */
    int examCount(Integer materialId);

    /** V31: số đề đã TỪNG gắn tài liệu, kể cả đề đã xóa (quyết định xóa mềm hay xóa vật lý). */
    int everExamCount(Integer materialId);
}
