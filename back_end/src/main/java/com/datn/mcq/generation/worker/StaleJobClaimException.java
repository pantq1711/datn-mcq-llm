package com.datn.mcq.generation.worker;

/**
 * W2-4b (A): worker không còn là chủ của job (job đã bị trả về pending vì quá hạn và nhận lại bởi worker khác, hoặc đã
 * đổi trạng thái). Ném ra từ bước ghi kết quả để transaction của bước đó rollback: kết quả của worker cũ bị bỏ, không để
 * lại câu hay trạng thái nào. Không phải lỗi của job: nơi bắt không được ghi lỗi hay thử lại job.
 */
public class StaleJobClaimException extends RuntimeException {

    private final Integer jobId;
    private final int claimSeq;

    public StaleJobClaimException(Integer jobId, int claimSeq) {
        super("stale finish: job " + jobId + " không còn thuộc claim_seq " + claimSeq, null, false, false);
        this.jobId = jobId;
        this.claimSeq = claimSeq;
    }

    public Integer jobId() {
        return jobId;
    }

    public int claimSeq() {
        return claimSeq;
    }
}

