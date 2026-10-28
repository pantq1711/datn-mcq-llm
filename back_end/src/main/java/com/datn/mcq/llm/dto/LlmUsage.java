package com.datn.mcq.llm.dto;

/**
 * Số liệu của MỘT lượt gọi LLM đã cho ra một kết quả (token và độ trễ), để ghi cạnh kết quả đó khi đo chi phí. Không có đơn
 * giá: {@link LlmCallResult#costUsd()} hiện luôn 0 nên không được lưu.
 *
 * @param tokenCount tổng token của lượt gọi; null khi không biết
 * @param latencyMs  thời gian gọi (mili giây); null khi không biết
 */
public record LlmUsage(Integer tokenCount, Double latencyMs) {

    /** Số liệu của một lượt gọi; null khi {@code result} null. */
    public static LlmUsage of(LlmCallResult result) {
        return result == null ? null : new LlmUsage(result.tokenCount(), result.latencyMs());
    }
}
