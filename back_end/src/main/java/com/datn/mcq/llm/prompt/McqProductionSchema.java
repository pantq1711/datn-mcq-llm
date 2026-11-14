package com.datn.mcq.llm.prompt;

/**
 * Schema Gemini cho câu trắc nghiệm (ngày 23/10): mọi luồng sinh MCQ (job, generate-sync, ma trận) gọi JSON mode kèm
 * schema này, giống sa/tf, để model không viết sai escape LaTeX ngoài cấu trúc JSON. Cùng 7 trường của hợp đồng JSON
 * hiện có; lời giải đứng trước correct_option để đáp án dựa vào lời giải vừa viết. Từ 25/10 áp dụng cho cả run
 * experiment (cấu hình riêng theo strategy của C1/C2/C3 đã gỡ).
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

    /**
     * Schema của prompt ma trận mcq từ v10 (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md mục 3): 7 trường như trên,
     * thêm {@code distractor_errors} đứng CUỐI (đối tượng A-D, mỗi khóa là chuỗi hoặc null). Không đổi thứ tự 7 trường cũ.
     */
    public static final String GENERATION_RESPONSE_SCHEMA_JSON_V10 = """
            {"type":"OBJECT","properties":{
              "question_text":{"type":"STRING"},
              "option_a":{"type":"STRING"},
              "option_b":{"type":"STRING"},
              "option_c":{"type":"STRING"},
              "option_d":{"type":"STRING"},
              "explanation":{"type":"STRING"},
              "correct_option":{"type":"STRING","enum":["A","B","C","D"]},
              "distractor_errors":{"type":"OBJECT","properties":{
                "A":{"type":"STRING","nullable":true},
                "B":{"type":"STRING","nullable":true},
                "C":{"type":"STRING","nullable":true},
                "D":{"type":"STRING","nullable":true}
              },"required":["A","B","C","D"],"propertyOrdering":["A","B","C","D"]}
            },"required":["question_text","option_a","option_b","option_c","option_d","explanation","correct_option",
                          "distractor_errors"],
            "propertyOrdering":["question_text","option_a","option_b","option_c","option_d","explanation",
                                "correct_option","distractor_errors"]}
            """;

    /** Schema cho prompt mcq {@code prompt}: có yêu cầu {@code distractor_errors} thì bản 8 trường, không thì bản 7 trường. */
    public static String forPrompt(String prompt) {
        return MatrixPromptDraftV0.asksDistractorErrors(prompt) ? GENERATION_RESPONSE_SCHEMA_JSON_V10
                : GENERATION_RESPONSE_SCHEMA_JSON;
    }

    private McqProductionSchema() {}
}
