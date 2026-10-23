package com.datn.mcq.qc.dto;

/**
 * Kết quả bước dedup trong một lần chạy QC (chỉ có trong báo cáo, không lưu DB dưới dạng enum).
 *
 * <p>DUPLICATE là dedup đã chạy thành công với kết quả không đạt (có dòng dedup passed = false).
 * ERROR là chưa có kết luận (không ghi dòng dedup). Chỉ NO_DUPLICATE mới góp phần cho QC_PASSED (Q-2).
 */
public enum DedupStatus {
    /** Dedup không chạy: format lỗi nên pipeline dừng sớm. */
    NOT_RUN,
    /** Dedup đã chạy và không phát hiện câu trùng. */
    NO_DUPLICATE,
    /** Dedup đã chạy và phát hiện câu trùng; trạng thái cuối, không gọi Solver. */
    DUPLICATE,
    /** Dedup không kết luận được (thiếu phạm vi, lỗi đọc ứng viên, lỗi ghi kết quả); giữ generated. */
    ERROR
}
