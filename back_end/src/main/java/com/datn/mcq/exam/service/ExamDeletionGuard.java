package com.datn.mcq.exam.service;

/**
 * Điều kiện chặn xóa đề do module khác cài đặt, để {@code exam} không phụ thuộc module đó (cùng mẫu với
 * {@code MatrixUsageLookup}, {@code MaterialUsageLookup}). Quyết định 16/11 (rà soát an ninh 07, AN-04): đề còn bài giao
 * thì không xóa được; module {@code assignment} cài đặt.
 *
 * <p>{@link ExamDeleteService#delete} gọi mọi cài đặt SAU khi đã khóa hàng đề (FOR UPDATE), trong cùng transaction, nên
 * thao tác tạo dữ liệu gắn với đề mà khóa chia sẻ hàng đề trước (giao bài) không lọt vào giữa lúc kiểm và lúc xóa.
 */
public interface ExamDeletionGuard {

    /**
     * Ném {@link com.datn.mcq.common.exception.ApiErrorException} (409, mã riêng) khi đề {@code examId} không được xóa;
     * không ném gì khi được xóa. Không ghi DB.
     */
    void checkDeletable(Integer examId);
}
