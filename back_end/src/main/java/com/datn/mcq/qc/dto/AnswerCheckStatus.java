package com.datn.mcq.qc.dto;

/**
 * Kết quả bước answer_check trong một lần chạy QC (chỉ có trong báo cáo, không lưu DB).
 */
public enum AnswerCheckStatus {
    /** Không chạy: bị tắt, thiếu API key, hoặc format lỗi nên dừng sớm. */
    NOT_RUN,
    /** Đã gọi nhưng không có kết quả hợp lệ: lỗi mạng/HTTP, JSON hỏng, thiếu hoặc sai judge_answer. */
    ERROR,
    /** Solver trả kết quả hợp lệ nhưng khác correct_option (kể cả multiple, none). */
    INCONSISTENT,
    /** Solver trả kết quả hợp lệ và khớp correct_option. */
    CONSISTENT
}
