package com.datn.mcq.qc.dto;

/**
 * Kết quả so trùng một câu với tập ứng viên.
 *
 * @param status               NO_DUPLICATE, DUPLICATE hoặc ERROR
 * @param similarity           với DUPLICATE: điểm của câu bị trùng (1.0 nếu cùng dấu vân tay);
 *                             với NO_DUPLICATE: điểm cao nhất trong các ứng viên qua cả hai chốt chặn,
 *                             luôn nhỏ hơn ngưỡng; null nếu không có ứng viên như vậy
 * @param comparedToQuestionId câu ứng với {@code similarity}; null khi không có
 * @param thresholdUsed        ngưỡng tau đã dùng; null khi ERROR
 * @param layer                tầng kết luận trùng
 * @param detail               mô tả ngắn cho báo cáo (lý do lỗi, tầng trùng)
 */
public record DedupOutcome(
        DedupStatus status,
        Double similarity,
        Integer comparedToQuestionId,
        Double thresholdUsed,
        Layer layer,
        String detail
) {
    /**
     * Tầng kết luận: dấu vân tay sau chuẩn hóa, cosine 3-gram có chốt chặn, hoặc (chỉ câu tf) đề dẫn giống nhau và
     * ít nhất 3/4 ý ghép được thành cặp giống nhau.
     */
    public enum Layer {
        NONE,
        FINGERPRINT,
        TRIGRAM,
        TF_STATEMENTS
    }

    public static DedupOutcome error(String detail) {
        return new DedupOutcome(DedupStatus.ERROR, null, null, null, Layer.NONE, detail);
    }
}
