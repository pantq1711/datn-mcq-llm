package com.datn.mcq.qc.checker;

import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.llm.prompt.SaPromptDraftV0;
import com.datn.mcq.llm.prompt.TfPromptDraftV0;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Option;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.llm.prompt.PromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Kết quả Solver cho câu Trả lời ngắn (V4, bản sửa 1 mục 7).
     *
     * @param passed      true khi Solver trả một số bằng giá trị đáp số LLM (CONSISTENT)
     * @param judgeKind   number, ambiguous hoặc unparseable
     * @param judgeAnswer chuỗi gốc Solver trả (trim); null khi ambiguous
     */
    public record SaSolverOutcome(
            boolean passed,
            SaJudgeKind judgeKind,
            String judgeAnswer,
            String judgeModel,
            String reasoning
    ) {}

    /**
     * Kết quả Solver cho câu Đúng/Sai (V4, GĐ3).
     *
     * @param passed      true chỉ khi đủ 4 ý có nhãn T/F và đều khớp nhãn LLM (CONSISTENT)
     * @param judgeLabels nhãn Solver cho 4 ý theo thứ tự a-d (T, F, undecidable)
     */
    public record TfSolverOutcome(
            boolean passed,
            List<TruthChoice> judgeLabels,
            String judgeModel,
            String reasoning
    ) {
        public TfSolverOutcome {
            judgeLabels = List.copyOf(judgeLabels);
        }
    }

    private static final List<String> TF_KEYS = List.of("a", "b", "c", "d");

    /** Gọi Solver cho câu tf. Solver chỉ thấy đề dẫn và 4 ý, không thấy nhãn và lời giải của người soạn. */
    public TfSolverOutcome checkTrueFalse(String questionText, List<String> statements, List<String> llmLabels) {
        String prompt = TfPromptDraftV0.buildSolverPrompt(questionText, statements);
        LlmCallResult result = llmClient.callJsonModeWithSchema(
                prompt, solverModel, 0.0, TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
        return parseAndCompareTrueFalse(result.rawText(), result.modelName(), llmLabels);
    }

    /**
     * So nhãn Solver với nhãn LLM cho từng ý (GĐ3). CONSISTENT chỉ khi đủ 4 ý có nhãn T/F và đều khớp; có ý lệch hoặc
     * undecidable là INCONSISTENT. Ném IllegalArgumentException (nơi gọi coi là ERROR, không ghi dòng) khi JSON hỏng
     * hoặc có khóa trùng, thiếu hoặc sai judge_x, hoặc nhãn LLM không đủ 4 giá trị T/F.
     */
    public TfSolverOutcome parseAndCompareTrueFalse(String rawSolverJson, String modelUsed, List<String> llmLabels) {
        if (llmLabels == null || llmLabels.size() != 4) {
            throw new IllegalArgumentException("Câu đúng/sai phải có đủ 4 nhãn để so với Solver");
        }
        List<TruthValue> expected = new ArrayList<>(4);
        for (String raw : llmLabels) {
            TruthValue v = TruthValue.fromLabel(raw);
            if (v == null) {
                throw new IllegalArgumentException("Nhãn của câu hỏi không hợp lệ để so với Solver");
            }
            expected.add(v);
        }
        Map<String, Object> map = SimpleJson.parseObject(SimpleJson.stripMarkdownFence(rawSolverJson));
        List<TruthChoice> judged = new ArrayList<>(4);
        StringBuilder reasoning = new StringBuilder();
        boolean allMatch = true;
        for (int i = 0; i < TF_KEYS.size(); i++) {
            String key = TF_KEYS.get(i);
            Object raw = map.get("judge_" + key);
            TruthChoice choice = raw != null ? TruthChoice.fromJudge(String.valueOf(raw)) : null;
            if (choice == null) {
                throw new IllegalArgumentException("Phản hồi Solver thiếu hoặc sai judge_" + key);
            }
            judged.add(choice);
            allMatch &= choice.matches(expected.get(i));
            Object why = map.get("reasoning_" + key);
            reasoning.append(i > 0 ? "\n" : "").append(key).append(") ")
                    .append(why != null ? String.valueOf(why).trim() : "");
        }
        return new TfSolverOutcome(allMatch, judged,
                modelUsed != null && !modelUsed.isBlank() ? modelUsed : solverModel, reasoning.toString());
    }

    /** Giá trị judge_answer Solver ghi khi đề có nhiều cách hiểu cho ra đáp số khác nhau. */
    public static final String SA_AMBIGUOUS = "ambiguous";

    static final String SA_NOT_COMPARABLE_PREFIX = "không so tự động được: ";

    /** Gọi Solver cho câu sa. Solver chỉ thấy đề, không thấy đáp số và lời giải của người soạn. */
    public SaSolverOutcome checkShortAnswer(String questionText, String llmAnswer) {
        String prompt = SaPromptDraftV0.buildSolverPrompt(questionText);
        LlmCallResult result = llmClient.callJsonModeWithSchema(
                prompt, solverModel, 0.0, SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
        return parseAndCompareShortAnswer(result.rawText(), result.modelName(), llmAnswer);
    }

    /**
     * So đáp số Solver với đáp số LLM theo giá trị, chế độ LENIENT (nhận dấu chấm thập phân, quyết định 10).
     * <ul>
     *   <li>số phân tích được, cùng giá trị: CONSISTENT; khác giá trị: INCONSISTENT (kind number)</li>
     *   <li>"ambiguous": INCONSISTENT (kind ambiguous)</li>
     *   <li>chuỗi không phân tích được: INCONSISTENT (kind unparseable), lý do "không so tự động được"</li>
     * </ul>
     * Ném IllegalArgumentException (nơi gọi coi là ERROR, không ghi dòng) khi JSON hỏng hoặc có khóa trùng,
     * thiếu hoặc rỗng judge_answer, hoặc đáp số LLM đã lưu không phân tích được.
     */
    public SaSolverOutcome parseAndCompareShortAnswer(String rawSolverJson, String modelUsed, String llmAnswer) {
        Map<String, Object> map = SimpleJson.parseObject(SimpleJson.stripMarkdownFence(rawSolverJson));
        String judgeStr = map.get("judge_answer") != null ? String.valueOf(map.get("judge_answer")).trim() : "";
        String reasoning = map.get("reasoning") != null ? String.valueOf(map.get("reasoning")).trim() : "";
        if (judgeStr.isEmpty()) {
            throw new IllegalArgumentException("Phản hồi Solver thiếu judge_answer");
        }
        ShortAnswerValue.ParseResult expected = ShortAnswerValue.parse(llmAnswer, ShortAnswerValue.Mode.LENIENT);
        if (!expected.ok()) {
            throw new IllegalArgumentException("Đáp số của câu hỏi không phân tích được để so với Solver");
        }
        String model = modelUsed != null && !modelUsed.isBlank() ? modelUsed : solverModel;
        // Không phân biệt hoa thường, sau khi trim (quyết định 2 sau GĐ2).
        if (SA_AMBIGUOUS.equalsIgnoreCase(judgeStr)) {
            return new SaSolverOutcome(false, SaJudgeKind.AMBIGUOUS, null, model, reasoning);
        }
        ShortAnswerValue.ParseResult judged = ShortAnswerValue.parse(judgeStr, ShortAnswerValue.Mode.LENIENT);
        if (!judged.ok()) {
            return new SaSolverOutcome(false, SaJudgeKind.UNPARSEABLE, judgeStr, model,
                    SA_NOT_COMPARABLE_PREFIX + judged.error() + (reasoning.isEmpty() ? "" : ". " + reasoning));
        }
        return new SaSolverOutcome(judged.value().sameValue(expected.value()), SaJudgeKind.NUMBER, judgeStr,
                model, reasoning);
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

