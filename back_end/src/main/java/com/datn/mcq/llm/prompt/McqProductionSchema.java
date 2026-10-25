package com.datn.mcq.llm.prompt;

/**
 * Schema Gemini cho câu trắc nghiệm ở run production (ngày 23/10): mọi luồng sinh MCQ production (job, generate-sync,
 * ma trận) gọi JSON mode kèm schema này, giống sa/tf, để model không viết sai escape LaTeX ngoài cấu trúc JSON. Cùng 7
 * trường của hợp đồng JSON hiện có; lời giải đứng trước correct_option để đáp án dựa vào lời giải vừa viết.
 *
 * <p>Không dùng cho run experiment: C1/C2/C3 giữ cấu hình đầu ra theo strategy (LlmClient#generateMcq), kể cả schema
 * riêng của few-shot-structured.
 */
public final class McqProductionSchema {

    public static final String GENERATION_RESPONSE_SCHEMA_JSON = """
            {"type":"OBJECT","properties":{
              "question_text":{"type":"STRING"},
              "option_a":{"type":"STRING"},
              "option_b":{"type":"STRING"},
              "option_c":{"type":"STRING"},
              "option_d":{"type":"STRING"},
              "explanation":{"type":"STRING"},
              "correct_option":{"type":"STRING","enum":["A","B","C","D"]}
            },"required":["question_text","option_a","option_b","option_c","option_d","explanation","correct_option"],
            "propertyOrdering":["question_text","option_a","option_b","option_c","option_d","explanation",
                                "correct_option"]}
            """;

    private McqProductionSchema() {}
}
