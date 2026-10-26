package com.datn.mcq.qc.dto;

import com.datn.mcq.llm.dto.LlmUsage;

/**
 * Kết quả một lần chấm "bám tài liệu".
 *
 * @param reason        lý do ngắn (tối đa 600 ký tự); với SUPPORTED bị hạ xuống UNKNOWN thì bắt đầu bằng
 *                      {@code evidence_not_found: }
 * @param evidenceQuote trích dẫn nguyên văn từ tài liệu mà judge đưa ra (tối đa 400 ký tự); null khi không có
 * @param evidenceFound true nếu trích dẫn có thật trong đoạn tài liệu đã đưa vào prompt, false nếu không; null khi judge
 *                      không đưa trích dẫn
 * @param judgeModel    model đã chấm
 * @param usage         token và độ trễ của lượt gọi judge; null khi kết quả được dựng không qua lượt gọi nào
 */
public record GroundingResult(GroundingVerdict verdict, String reason, String evidenceQuote, Boolean evidenceFound,
                              String judgeModel, LlmUsage usage) {

    public GroundingResult(GroundingVerdict verdict, String reason, String evidenceQuote, Boolean evidenceFound,
                           String judgeModel) {
        this(verdict, reason, evidenceQuote, evidenceFound, judgeModel, null);
    }

    public GroundingResult withUsage(LlmUsage newUsage) {
        return new GroundingResult(verdict, reason, evidenceQuote, evidenceFound, judgeModel, newUsage);
    }
}
