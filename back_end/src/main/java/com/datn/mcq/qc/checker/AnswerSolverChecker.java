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
import com.datn.mcq.llm.dto.LlmUsage;
import com.datn.mcq.llm.prompt.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AnswerSolverChecker {

    private static final Logger log = LoggerFactory.getLogger(AnswerSolverChecker.class);

    /**
     * @param usage         token và độ trễ của lượt gọi Solver; null khi kết quả đến từ phân tích thuần (không có lượt gọi)
     * @param optionCorrect (v3) kết luận Solver cho từng phương án theo thứ tự A-D; null khi phản hồi không có
     *                      {@code options_verdict} hợp lệ (Solver cũ, hoặc thiếu/hỏng: kết luận theo judge_answer như cũ)
     */
    public record SolverCheckOutcome(
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel,
            String reasoning,
            LlmUsage usage,
            List<Boolean> optionCorrect
    ) {
        public SolverCheckOutcome {
            optionCorrect = optionCorrect == null ? null : List.copyOf(optionCorrect);
        }

        public SolverCheckOutcome(boolean passed, AnswerChoice judgeAnswer, String judgeModel, String reasoning) {
            this(passed, judgeAnswer, judgeModel, reasoning, null, null);
        }

        public SolverCheckOutcome(boolean passed, AnswerChoice judgeAnswer, String judgeModel, String reasoning,
                                  LlmUsage usage) {
            this(passed, judgeAnswer, judgeModel, reasoning, usage, null);
        }

        /** Số phương án Solver cho là đúng; null khi không có kết quả từng phương án. */
        public Integer correctCount() {
            return optionCorrect == null ? null : (int) optionCorrect.stream().filter(Boolean::booleanValue).count();
        }

        SolverCheckOutcome withUsage(LlmUsage newUsage) {
            return new SolverCheckOutcome(passed, judgeAnswer, judgeModel, reasoning, newUsage, optionCorrect);
        }
    }

    /** Phiên bản prompt Solver mcq mặc định (thẩm định 31/10: xét từng phương án). */
    public static final String SOLVER_PROMPT_VERSION = PromptBuilder.SOLVER_PROMPT_VERSION;

    /**
     * Dòng đầu của ghi chú Solver ({@code solver_reasoning}, V14) khi Solver đã xét từng phương án. QcSummaryService dựa
     * vào dòng này để tách lý do {@code solver_multiple_correct}/{@code solver_no_correct} khỏi {@code solver_inconsistent}.
     */
    public static final String OPTIONS_VERDICT_PREFIX = "[Xét từng phương án]";

    /** Cấu trúc JSON bắt buộc cho phản hồi Solver mcq v3 (Gemini responseSchema). Giữ đủ các trường của v2. */
    public static final String SOLVER_RESPONSE_SCHEMA_JSON = """
            {"type":"OBJECT","properties":{
              "reasoning":{"type":"STRING"},
              "options_verdict":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
                "option":{"type":"STRING","enum":["A","B","C","D"]},
                "correct":{"type":"BOOLEAN"},
                "reason":{"type":"STRING"}
              },"required":["option","correct","reason"],"propertyOrdering":["option","correct","reason"]}},
              "correct_count":{"type":"INTEGER"},
              "judge_answer":{"type":"STRING","enum":["A","B","C","D","multiple","none"]}
            },"required":["reasoning","options_verdict","correct_count","judge_answer"],
            "propertyOrdering":["reasoning","options_verdict","correct_count","judge_answer"]}
            """;

    /** Cấu trúc JSON cho phản hồi Solver mcq v2 ({@link PromptBuilder#SOLVER_PROMPT_VERSION_V2}). */
    public static final String SOLVER_RESPONSE_SCHEMA_JSON_V2 = """
            {"type":"OBJECT","properties":{
              "reasoning":{"type":"STRING"},
              "judge_answer":{"type":"STRING","enum":["A","B","C","D","multiple","none"]}
            },"required":["reasoning","judge_answer"],"propertyOrdering":["reasoning","judge_answer"]}
            """;

    private static final List<String> OPTION_LETTERS = List.of("A", "B", "C", "D");

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final String solverModel;
    private final String promptVersion;

    public AnswerSolverChecker(LlmClient llmClient, PromptBuilder promptBuilder, String solverModel) {
        this(llmClient, promptBuilder, solverModel, SOLVER_PROMPT_VERSION);
    }

    /**
     * @param promptVersion phiên bản prompt Solver mcq; để trống là mặc định. Mặc định của cấu hình lấy
     *                      {@code app.solver-probe.prompt-version} khi chạy công cụ đo, để lệnh đo chỉ cần một tham số.
     */
    @Autowired
    public AnswerSolverChecker(
            LlmClient llmClient,
            PromptBuilder promptBuilder,
            @Value("${app.llm.solver-model:gemini-3.5-flash-lite}") String solverModel,
            @Value("${app.llm.solver-prompt-version:${app.solver-probe.prompt-version:}}") String promptVersion) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.solverModel = (solverModel != null && !solverModel.isBlank())
                ? solverModel.trim()
                : "gemini-3.5-flash-lite";
        this.promptVersion = promptVersion == null || promptVersion.isBlank()
                ? SOLVER_PROMPT_VERSION
                : PromptBuilder.requireSolverPromptVersion(promptVersion);
    }

    /** Phiên bản prompt Solver mcq đang dùng. */
    public String promptVersion() {
        return promptVersion;
    }

    private boolean optionCheckPrompt() {
        return !PromptBuilder.SOLVER_PROMPT_VERSION_V2.equals(promptVersion);
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
        String prompt = promptBuilder.buildSolverPrompt(promptVersion, questionText, optionA, optionB, optionC, optionD);
        LlmCallResult result = llmClient.callJsonModeWithSchema(prompt, solverModel, 0.0,
                optionCheckPrompt() ? SOLVER_RESPONSE_SCHEMA_JSON : SOLVER_RESPONSE_SCHEMA_JSON_V2);
        return parseAndCompare(result.rawText(), result.modelName(), generatorCorrectOption)
                .withUsage(LlmUsage.of(result));
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
            String reasoning,
            LlmUsage usage
    ) {
        public SaSolverOutcome(boolean passed, SaJudgeKind judgeKind, String judgeAnswer, String judgeModel,
                               String reasoning) {
            this(passed, judgeKind, judgeAnswer, judgeModel, reasoning, null);
        }

        SaSolverOutcome withUsage(LlmUsage newUsage) {
            return new SaSolverOutcome(passed, judgeKind, judgeAnswer, judgeModel, reasoning, newUsage);
        }
    }

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
            String reasoning,
            LlmUsage usage
    ) {
        public TfSolverOutcome {
            judgeLabels = List.copyOf(judgeLabels);
        }

        public TfSolverOutcome(boolean passed, List<TruthChoice> judgeLabels, String judgeModel, String reasoning) {
            this(passed, judgeLabels, judgeModel, reasoning, null);
        }

        TfSolverOutcome withUsage(LlmUsage newUsage) {
            return new TfSolverOutcome(passed, judgeLabels, judgeModel, reasoning, newUsage);
        }
    }

    private static final List<String> TF_KEYS = List.of("a", "b", "c", "d");

    /** Gọi Solver cho câu tf. Solver chỉ thấy đề dẫn và 4 ý, không thấy nhãn và lời giải của người soạn. */
    public TfSolverOutcome checkTrueFalse(String questionText, List<String> statements, List<String> llmLabels) {
        String prompt = TfPromptDraftV0.buildSolverPrompt(questionText, statements);
        LlmCallResult result = llmClient.callJsonModeWithSchema(
                prompt, solverModel, 0.0, TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
        return parseAndCompareTrueFalse(result.rawText(), result.modelName(), llmLabels).withUsage(LlmUsage.of(result));
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
        return parseAndCompareShortAnswer(result.rawText(), result.modelName(), llmAnswer).withUsage(LlmUsage.of(result));
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

    /**
     * So kết quả Solver mcq với đáp án của câu.
     * <ul>
     *   <li>Có {@code options_verdict} hợp lệ (v3, đủ 4 phương án A-D, mỗi phương án một lần, {@code correct} là boolean):
     *       đếm số phương án đúng từ chính mảng này (không tin {@code correct_count} và {@code judge_answer} khi chúng
     *       lệch, chỉ ghi cảnh báo). Từ 2 trở lên: {@code multiple}; 0: {@code none}; đều không qua (theo đường Q-2 của
     *       INCONSISTENT). Đúng 1: qua khi trùng đáp án, không trùng là INCONSISTENT như cũ. Ghi chú Solver bắt đầu bằng
     *       khối {@link #OPTIONS_VERDICT_PREFIX}.</li>
     *   <li>Thiếu hoặc hỏng {@code options_verdict}: như Solver cũ, so {@code judge_answer} với đáp án, ghi chú giữ
     *       nguyên (không có khối xét từng phương án, nên không gắn lý do mới). Prompt v3 mà thiếu thì ghi cảnh báo.</li>
     * </ul>
     * Ném IllegalArgumentException (nơi gọi coi là ERROR) khi JSON hỏng, thiếu correct_option của câu, hoặc không có
     * {@code options_verdict} hợp lệ mà {@code judge_answer} thiếu hay sai.
     */
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
        if (generatorCorrectOption == null) {
            throw new IllegalArgumentException("Thiếu correct_option của câu hỏi để so với Solver");
        }
        String model = modelUsed != null && !modelUsed.isBlank() ? modelUsed : solverModel;

        List<OptionVerdict> verdicts = readOptionsVerdict(map);
        if (verdicts != null) {
            return fromOptionsVerdict(verdicts, map, judgeStr, reasoning, model, generatorCorrectOption);
        }
        if (optionCheckPrompt()) {
            log.warn("Phản hồi Solver {} không có options_verdict hợp lệ: kết luận theo judge_answer như Solver cũ",
                    promptVersion);
        }
        // Structured output không phải bảo đảm tuyệt đối: vẫn kiểm tra kết quả sau khi parse.
        // Thiếu judge_answer hoặc giá trị lạ -> ném lỗi (không coi là kết quả hợp lệ).
        if (judgeStr.isEmpty()) {
            throw new IllegalArgumentException("Phản hồi Solver thiếu judge_answer");
        }
        AnswerChoice judgeChoice = DbEnum.fromDbValue(AnswerChoice.class, judgeStr);
        boolean passed = judgeChoice.getDbValue().equals(generatorCorrectOption.getDbValue());
        return new SolverCheckOutcome(passed, judgeChoice, model, reasoning);
    }

    /** Kết luận của Solver cho một phương án (v3). */
    private record OptionVerdict(String option, boolean correct, String reason) {}

    private SolverCheckOutcome fromOptionsVerdict(List<OptionVerdict> verdicts, Map<String, Object> map,
                                                  String judgeStr, String reasoning, String model,
                                                  Option generatorCorrectOption) {
        List<Boolean> correct = verdicts.stream().map(OptionVerdict::correct).toList();
        List<String> correctLetters = verdicts.stream().filter(OptionVerdict::correct).map(OptionVerdict::option).toList();
        int count = correctLetters.size();
        AnswerChoice judge = count == 0 ? AnswerChoice.NONE
                : count >= 2 ? AnswerChoice.MULTIPLE
                : DbEnum.fromDbValue(AnswerChoice.class, correctLetters.get(0));

        Object declaredCount = map.get("correct_count");
        if (!(declaredCount instanceof Number n) || n.doubleValue() != count) {
            log.warn("Solver ghi correct_count = {} nhưng options_verdict có {} phương án đúng: dùng options_verdict",
                    declaredCount, count);
        }
        if (!judge.getDbValue().equalsIgnoreCase(judgeStr)) {
            log.warn("Solver ghi judge_answer = '{}' nhưng options_verdict cho '{}': dùng options_verdict",
                    judgeStr, judge.getDbValue());
        }

        StringBuilder notes = new StringBuilder(OPTIONS_VERDICT_PREFIX).append(' ')
                .append(count).append("/4 phương án đúng");
        for (OptionVerdict v : verdicts) {
            notes.append('\n').append(v.option()).append(v.correct() ? ": đúng" : ": sai");
            if (!v.reason().isEmpty()) {
                notes.append(" – ").append(v.reason());
            }
        }
        if (!reasoning.isEmpty()) {
            notes.append("\n\n").append(reasoning);
        }
        boolean passed = judge.getDbValue().equals(generatorCorrectOption.getDbValue());
        return new SolverCheckOutcome(passed, judge, model, notes.toString(), null, correct);
    }

    /**
     * Đọc {@code options_verdict} theo thứ tự A-D. Trả null (kèm cảnh báo khi có trường mà hỏng) nếu không phải mảng đúng
     * 4 phần tử, thiếu hoặc trùng chữ cái, hoặc {@code correct} không phải true/false.
     */
    private static List<OptionVerdict> readOptionsVerdict(Map<String, Object> map) {
        Object raw = map.get("options_verdict");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list) || list.size() != OPTION_LETTERS.size()) {
            log.warn("options_verdict của Solver không phải mảng 4 phần tử: bỏ qua");
            return null;
        }
        OptionVerdict[] byLetter = new OptionVerdict[OPTION_LETTERS.size()];
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> item)) {
                log.warn("options_verdict của Solver có phần tử không phải đối tượng: bỏ qua");
                return null;
            }
            String option = item.get("option") != null ? String.valueOf(item.get("option")).trim().toUpperCase() : "";
            int idx = OPTION_LETTERS.indexOf(option);
            Boolean correct = toBoolean(item.get("correct"));
            if (idx < 0 || byLetter[idx] != null || correct == null) {
                log.warn("options_verdict của Solver thiếu, trùng phương án hoặc correct không phải true/false: bỏ qua");
                return null;
            }
            String reason = item.get("reason") != null ? String.valueOf(item.get("reason")).replaceAll("\\s+", " ").trim()
                    : "";
            byLetter[idx] = new OptionVerdict(option, correct, reason);
        }
        return List.of(byLetter);
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s && (s.trim().equalsIgnoreCase("true") || s.trim().equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(s.trim());
        }
        return null;
    }
}

