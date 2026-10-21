package com.datn.mcq.qc.dto;

/**
 * Kết quả bước dedup trong một lần chạy QC.
 * Dedup chưa được triển khai nên pipeline hiện luôn báo NOT_RUN.
 * Cách xử lý khi phát hiện trùng sẽ được bổ sung cùng issue triển khai dedup.
 */
public enum DedupStatus {
    /** Dedup chưa chạy (hiện chưa triển khai). */
    NOT_RUN,
    /** Dedup đã chạy và không phát hiện câu trùng. */
    NO_DUPLICATE
}
