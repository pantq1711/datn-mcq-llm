package com.datn.mcq.matrix.service;

import java.util.Collection;
import java.util.Map;

/**
 * W1-4: số đề đã tạo từ ma trận. Module {@code matrix} không được phụ thuộc {@code exam} (exam đã phụ thuộc matrix), nên
 * module {@code exam} cài interface này, giống {@code SlotDedupContext} của generation.
 */
public interface MatrixUsageLookup {

    /**
     * Số đề CHƯA XÓA tạo từ ma trận (đề của mọi người dùng; ma trận chỉ có một chủ nên thực tế là đề của chủ). V31: đề đã
     * xóa không tính.
     */
    int examCount(Integer matrixId);

    /** V31: số đề đã TỪNG tạo từ ma trận, kể cả đề đã xóa (chỗ của đề đã xóa vẫn trỏ tới ô của ma trận). */
    int everExamCount(Integer matrixId);

    /** Số đề chưa xóa theo từng ma trận; ma trận không có đề thì không có khóa. */
    Map<Integer, Integer> examCounts(Collection<Integer> matrixIds);
}
