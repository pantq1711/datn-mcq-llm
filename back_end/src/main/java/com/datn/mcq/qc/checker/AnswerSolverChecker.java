package com.datn.mcq.qc.checker;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Option;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnswerSolverChecker {

    public record SolverCheckOutcome(
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel,
            String reasoning
    ) {}

    /** Cấu trúc JSON bắt buộc cho phản hồi của Solver (Gemini responseSchema). */
    public static final String SOLVER_RESPONSE_SCHEMA_JSON = """
            {"type":"OBJECT","properties":{
              "reasoning":{"type":"STRING"},
              "judge_answer":{"type":"STRING","enum":["A","B","C","D","multiple","none"]}
            },"required":["reasoning","judge_answer"],"propertyOrdering":["reasoning","judge_answer"]}
            """;

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final String solverModel;

    public AnswerSolverChecker(
            LlmClient llmClient,
            PromptBuilder promptBuilder,
            @Value("${app.llm.solver-model:gemini-3.5-flash-lite}") String solverModel) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.solverModel = (solverModel != null && !solverModel.isBlank())
                ? solverModel.trim()
                : "gemini-3.5-flash-lite";
    }

    public boolean canRunOnline() {
        return llmClient.isConfigured();
    }

    public SolverCheckOutcome check(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Option generatorCorrectOption) {
        String prompt = promptBuilder.buildSolverPrompt(questionText, optionA, optionB, optionC, optionD);
        LlmCallResult result = llmClient.callJsonModeWithSchema(
                prompt, solverModel, 0.0, SOLVER_RESPONSE_SCHEMA_JSON);
        return parseAndCompare(result.rawText(), result.modelName(), generatorCorrectOption);
    }

    public SolverCheckOutcome parseAndCompare(
            String rawSolverJson,
            String modelUsed,
            Option generatorCorrectOption) {
        String cleaned = SimpleJson.stripMarkdownFence(rawSolverJson);
        Map<String, Object> map = SimpleJson.parseObject(cleaned);
        String judgeStr = map.get("judge_answer") != null
                ? String.valueOf(map.get("judge_answer")).trim()
                : "";
        String reasoning = map.get("reasoning") != null
                ? String.valueOf(map.get("reasoning")).trim()
                : "";
        // Structured output không phải bảo đảm tuyệt đối: vẫn kiểm tra kết quả sau khi parse.
        // Thiếu judge_answer, giá trị lạ hoặc thiếu correct_option để so -> ném lỗi (không coi là kết quả hợp lệ).
        if (judgeStr.isEmpty()) {
            throw new IllegalArgumentException("Phản hồi Solver thiếu judge_answer");
        }
        if (generatorCorrectOption == null) {
            throw new IllegalArgumentException("Thiếu correct_option của câu hỏi để so với Solver");
        }
        AnswerChoice judgeChoice = DbEnum.fromDbValue(AnswerChoice.class, judgeStr);
        boolean passed = judgeChoice.getDbValue().equals(generatorCorrectOption.getDbValue());
        return new SolverCheckOutcome(
                passed,
                judgeChoice,
                modelUsed != null && !modelUsed.isBlank() ? modelUsed : solverModel,
                reasoning
        );
    }
}

