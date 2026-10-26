package com.datn.mcq.exam;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.checker.GroundingChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM giả cho bước QC "bám tài liệu" trong test tích hợp (không gọi mạng): trả verdict đã cấu hình kèm trích dẫn thật lấy từ
 * đoạn tài liệu trong prompt, trích dẫn bịa, hoặc không trích dẫn; hoặc ném lỗi. Không phải test (tên không khớp mẫu
 * của surefire).
 */
final class GroundingFake extends LlmClient {

    enum Quote { REAL, FAKE, NONE }

    volatile boolean configured = true;
    volatile String verdict = "SUPPORTED";
    volatile Quote quote = Quote.REAL;
    volatile boolean fail;
    volatile int calls;
    final List<String> prompts = new ArrayList<>();

    GroundingFake() {
        super("FAKE_KEY_FOR_TESTS", "fake-grounding-model");
    }

    void reset() {
        configured = true;
        verdict = "SUPPORTED";
        quote = Quote.REAL;
        fail = false;
        calls = 0;
        prompts.clear();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public LlmCallResult callJsonModeWithSchema(String prompt, String modelName, Double temperature, String schema) {
        if (!GroundingChecker.RESPONSE_SCHEMA_JSON.equals(schema)) {
            throw new IllegalStateException("GroundingFake chỉ trả lời schema grounding");
        }
        calls++;
        prompts.add(prompt);
        if (fail) {
            throw new IllegalStateException("Gemini lỗi giả lập");
        }
        String q = switch (quote) {
            case REAL -> firstContextChars(prompt);
            case FAKE -> "Câu trích bịa đặt hoàn toàn không có trong tài liệu";
            case NONE -> "";
        };
        return new LlmCallResult("{\"reason\":\"Lý do giả lập.\",\"evidence_quote\":" + SimpleJson.quote(q)
                + ",\"verdict\":\"" + verdict + "\"}", 5, 1.0, 0.0, modelName);
    }

    /** 40 ký tự đầu của đoạn tài liệu đầu tiên trong khối tài liệu của prompt. */
    private static String firstContextChars(String prompt) {
        String marker = "<<<TÀI_LIỆU\n[Tài liệu 1, đoạn 1]\n";
        int at = prompt.indexOf(marker);
        if (at < 0) {
            return "";
        }
        String rest = prompt.substring(at + marker.length());
        int nl = rest.indexOf('\n');
        String line = nl < 0 ? rest : rest.substring(0, nl);
        return line.substring(0, Math.min(40, line.length()));
    }
}
