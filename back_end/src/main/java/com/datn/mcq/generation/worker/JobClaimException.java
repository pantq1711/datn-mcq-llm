package com.datn.mcq.generation.worker;

/**
 * NS-1: lỗi ở bước nhận job sau khi đã khóa được job (đọc run, template, dựng prompt từ {@code hyperparams}...). Transaction
 * nhận job rollback nên job vẫn {@code pending} như trước; ngoại lệ mang {@code jobId} và {@code claim_seq} lúc khóa để
 * poller ghi lỗi cho đúng job đó trong transaction riêng ({@link GenerationJobProcessor#recordClaimFailure}), thay vì để
 * worker nhận lại job đó mãi.
 */
public class JobClaimException extends RuntimeException {

    private final Integer jobId;
    private final int claimSeq;

    public JobClaimException(Integer jobId, int claimSeq, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.jobId = jobId;
        this.claimSeq = claimSeq;
    }

    public Integer jobId() {
        return jobId;
    }

    /** {@code claim_seq} của job lúc bị khóa (trước khi tăng): job chưa bị worker khác nhận thì vẫn giữ giá trị này. */
    public int claimSeq() {
        return claimSeq;
    }
}
