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
import com.datn.mcq.llm.prompt.TeacherRequestPrompt;
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
    /**
     * @param requestCheck (12/11, PR B) kết luận về yêu cầu của giáo viên; null khi câu không có yêu cầu hoặc phản hồi thiếu,
     *                     hỏng {@code request_check}
     */
    public record SolverCheckOutcome(
            boolean passed,
            AnswerChoice judgeAnswer,
            String judgeModel,
            String reasoning,
            LlmUsage usage,
            List<Boolean> optionCorrect,
            TeacherRequestPrompt.RequestCheck requestCheck
    ) {
        public SolverCheckOutcome {
            optionCorrect = optionCorrect == null ? null : List.copyOf(optionCorrect);
        }

        public SolverCheckOutcome(boolean passed, AnswerChoice judgeAnswer, String judgeModel, String reasoning,
                                  LlmUsage usage, List<Boolean> optionCorrect) {
            this(passed, judgeAnswer, judgeModel, reasoning, usage, optionCorrect, null);
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
            return new SolverCheckOutcome(passed, judgeAnswer, judgeModel, reasoning, newUsage, optionCorrect,
                    requestCheck);
        }

        SolverCheckOutcome withRequestCheck(TeacherRequestPrompt.RequestCheck check) {
            return new SolverCheckOutcome(passed, judgeAnswer, judgeModel, reasoning, usage, optionCorrect, check);
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

    /**
     * Cấu trúc JSON Solver mcq của câu CÓ yêu cầu của giáo viên ({@link PromptBuilder#SOLVER_PROMPT_VERSION_V5}): như
     * {@link #SOLVER_RESPONSE_SCHEMA_JSON}, thêm {@code request_check} ở cuối.
     */
    public static final String SOLVER_RESPONSE_SCHEMA_JSON_REQUEST =
            TeacherRequestPrompt.withRequestCheck(SOLVER_RESPONSE_SCHEMA_JSON);

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
        return check(questionText, optionA, optionB, optionC, optionD, generatorCorrectOption, null, null);
    }

    /**
     * Như trên, kèm yêu cầu của giáo viên (12/11, PR B). Câu không có yêu cầu ({@code requestCodes} rỗng, {@code requestText}
     * trống), hoặc phiên bản prompt không phải v5: prompt và schema y như cũ. Câu có yêu cầu: vẫn MỘT lượt gọi, prompt thêm
     * khối yêu cầu sau đề và phương án, schema thêm {@code request_check}; cách xét đáp án không đổi.
     */
    public SolverCheckOutcome check(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Option generatorCorrectOption,
            List<String> requestCodes,
            String requestText) {
        String prompt = promptBuilder.buildSolverPrompt(promptVersion, questionText, optionA, optionB, optionC, optionD);
        String block = requestBlock(requestCodes, requestText);
        String schema = optionCheckPrompt() ? SOLVER_RESPONSE_SCHEMA_JSON : SOLVER_RESPONSE_SCHEMA_JSON_V2;
        if (twoPass()) {
            // v6 (14/11): lượt 1 đúng prompt và schema v4 cho mọi câu; yêu cầu (nếu có) kiểm ở lượt 2.
            LlmCallResult result = llmClient.callJsonModeWithSchema(prompt, solverModel, 0.0, schema);
            SolverCheckOutcome outcome = parseAndCompare(result.rawText(), result.modelName(), generatorCorrectOption)
                    .withUsage(LlmUsage.of(result));
            if (block == null) {
                return outcome;
            }
            List<String> choices = List.of("A. " + optionA, "B. " + optionB, "C. " + optionC, "D. " + optionD);
            SecondPass second = secondPass(outcome.passed(), "trắc nghiệm", questionText, choices, outcome.reasoning(),
                    block);
            return outcome.withUsage(sum(outcome.usage(), second.usage())).withRequestCheck(second.check());
        }
        if (block != null) {
            prompt = TeacherRequestPrompt.withSolverBlock(prompt, block);
            schema = SOLVER_RESPONSE_SCHEMA_JSON_REQUEST;
        }
        LlmCallResult result = llmClient.callJsonModeWithSchema(prompt, solverModel, 0.0, schema);
        SolverCheckOutcome outcome = parseAndCompare(result.rawText(), result.modelName(), generatorCorrectOption)
                .withUsage(LlmUsage.of(result));
        return block == null ? outcome : outcome.withRequestCheck(TeacherRequestPrompt.parse(result.rawText()));
    }

    /** Khối yêu cầu cho Solver tf/sa (không có phiên bản riêng): theo phiên bản đang dùng, như mcq. */
    private String requestBlock(List<String> requestCodes, String requestText) {
        return PromptBuilder.checksRequests(promptVersion) ? TeacherRequestPrompt.solverBlock(requestCodes, requestText)
                : null;
    }

    /** v6: kiểm yêu cầu ở lượt gọi thứ hai. */
    private boolean twoPass() {
        return PromptBuilder.twoPass(promptVersion);
    }

    /** Kết quả lượt 2: kết luận về yêu cầu và số liệu lượt gọi (null khi không gọi hoặc gọi lỗi). */
    private record SecondPass(TeacherRequestPrompt.RequestCheck check, LlmUsage usage) {}

    /**
     * Lượt 2 (v6): lượt 1 gắn cờ đáp án ({@code firstPassed} false) thì KHÔNG gọi, kết luận {@code unchecked}. Lượt 2 lỗi
     * (mạng, HTTP, hết hạn mức) hoặc trả kết quả hỏng: {@code unchecked} kèm lý do và cảnh báo ở log; không bao giờ ném lỗi
     * (kết luận của lượt 1 giữ nguyên).
     */
    private SecondPass secondPass(boolean firstPassed, String kind, String questionText, List<String> choices,
                                  String reasoning, String block) {
        if (!firstPassed) {
            return new SecondPass(TeacherRequestPrompt.unchecked(TeacherRequestPrompt.UNCHECKED_FLAGGED), null);
        }
        String prompt = TeacherRequestPrompt.secondPassPrompt(kind, questionText, choices, reasoning, block);
        LlmCallResult result;
        try {
            result = llmClient.callJsonModeWithSchema(prompt, solverModel, 0.0, TeacherRequestPrompt.SECOND_PASS_SCHEMA);
        } catch (RuntimeException ex) {
            log.warn("Solver lượt kiểm yêu cầu lỗi, request_check = unchecked: {}", ex.getMessage());
            return new SecondPass(TeacherRequestPrompt.unchecked(TeacherRequestPrompt.UNCHECKED_FAILED_PREFIX + " ("
                    + ex.getClass().getSimpleName() + ")"), null);
        }
        TeacherRequestPrompt.RequestCheck check = TeacherRequestPrompt.parse(result.rawText());
        if (check == null) {
            log.warn("Solver lượt kiểm yêu cầu trả request_check thiếu hoặc hỏng, request_check = unchecked");
            check = TeacherRequestPrompt.unchecked(TeacherRequestPrompt.UNCHECKED_FAILED_PREFIX + " (phản hồi hỏng)");
        }
        return new SecondPass(check, LlmUsage.of(result));
    }

    /** Cộng số liệu hai lượt gọi (token, độ trễ); thiếu một bên thì lấy bên kia. */
    private static LlmUsage sum(LlmUsage a, LlmUsage b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        Integer tokens = a.tokenCount() == null || b.tokenCount() == null
                ? (a.tokenCount() == null ? b.tokenCount() : a.tokenCount()) : a.tokenCount() + b.tokenCount();
        Double latency = a.latencyMs() == null || b.latencyMs() == null
                ? (a.latencyMs() == null ? b.latencyMs() : a.latencyMs()) : a.latencyMs() + b.latencyMs();
        return new LlmUsage(tokens, latency);
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
            LlmUsage usage,
            TeacherRequestPrompt.RequestCheck requestCheck
    ) {
        public SaSolverOutcome(boolean passed, SaJudgeKind judgeKind, String judgeAnswer, String judgeModel,
                               String reasoning, LlmUsage usage) {
            this(passed, judgeKind, judgeAnswer, judgeModel, reasoning, usage, null);
        }

        public SaSolverOutcome(boolean passed, SaJudgeKind judgeKind, String judgeAnswer, String judgeModel,
                               String reasoning) {
            this(passed, judgeKind, judgeAnswer, judgeModel, reasoning, null);
        }

        SaSolverOutcome withUsage(LlmUsage newUsage) {
            return new SaSolverOutcome(passed, judgeKind, judgeAnswer, judgeModel, reasoning, newUsage, requestCheck);
        }

        SaSolverOutcome withRequestCheck(TeacherRequestPrompt.RequestCheck check) {
            return new SaSolverOutcome(passed, judgeKind, judgeAnswer, judgeModel, reasoning, usage, check);
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
            LlmUsage usage,
            TeacherRequestPrompt.RequestCheck requestCheck
    ) {
        public TfSolverOutcome {
            judgeLabels = List.copyOf(judgeLabels);
        }

        public TfSolverOutcome(boolean passed, List<TruthChoice> judgeLabels, String judgeModel, String reasoning,
                               LlmUsage usage) {
            this(passed, judgeLabels, judgeModel, reasoning, usage, null);
        }

        public TfSolverOutcome(boolean passed, List<TruthChoice> judgeLabels, String judgeModel, String reasoning) {
            this(passed, judgeLabels, judgeModel, reasoning, null);
        }

        TfSolverOutcome withUsage(LlmUsage newUsage) {
            return new TfSolverOutcome(passed, judgeLabels, judgeModel, reasoning, newUsage, requestCheck);
        }

        TfSolverOutcome withRequestCheck(TeacherRequestPrompt.RequestCheck check) {
            return new TfSolverOutcome(passed, judgeLabels, judgeModel, reasoning, usage, check);
        }
    }

    private static final List<String> TF_KEYS = List.of("a", "b", "c", "d");

    /** Gọi Solver cho câu tf. Solver chỉ thấy đề dẫn và 4 ý, không thấy nhãn và lời giải của người soạn. */
    public TfSolverOutcome checkTrueFalse(String questionText, List<String> statements, List<String> llmLabels) {
        return checkTrueFalse(questionText, statements, llmLabels, null, null);
    }

    /** Như trên, kèm yêu cầu của giáo viên (12/11, PR B; cách làm như {@link #check} mcq). */
    public TfSolverOutcome checkTrueFalse(String questionText, List<String> statements, List<String> llmLabels,
                                          List<String> requestCodes, String requestText) {
        String prompt = TfPromptDraftV0.buildSolverPrompt(questionText, statements);
        String block = requestBlock(requestCodes, requestText);
        if (twoPass()) {
            LlmCallResult result = llmClient.callJsonModeWithSchema(prompt, solverModel, 0.0,
                    TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
            TfSolverOutcome outcome = parseAndCompareTrueFalse(result.rawText(), result.modelName(), llmLabels)
                    .withUsage(LlmUsage.of(result));
            if (block == null) {
                return outcome;
            }
            List<String> choices = new ArrayList<>();
            for (int i = 0; i < statements.size() && i < TF_KEYS.size(); i++) {
                choices.add(TF_KEYS.get(i) + ") " + statements.get(i));
            }
            SecondPass second = secondPass(outcome.passed(), "đúng/sai", questionText, choices, outcome.reasoning(),
                    block);
            return outcome.withUsage(sum(outcome.usage(), second.usage())).withRequestCheck(second.check());
        }
        LlmCallResult result = llmClient.callJsonModeWithSchema(
                TeacherRequestPrompt.withSolverBlock(prompt, block), solverModel, 0.0,
                block == null ? TfPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON : TeacherRequestPrompt.TF_SOLVER_SCHEMA);
        TfSolverOutcome outcome = parseAndCompareTrueFalse(result.rawText(), result.modelName(), llmLabels)
                .withUsage(LlmUsage.of(result));
        return block == null ? outcome : outcome.withRequestCheck(TeacherRequestPrompt.parse(result.rawText()));
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
        return checkShortAnswer(questionText, llmAnswer, null, null);
    }

    /** Như trên, kèm yêu cầu của giáo viên (12/11, PR B; cách làm như {@link #check} mcq). */
    public SaSolverOutcome checkShortAnswer(String questionText, String llmAnswer, List<String> requestCodes,
                                            String requestText) {
        String prompt = SaPromptDraftV0.buildSolverPrompt(questionText);
        String block = requestBlock(requestCodes, requestText);
        if (twoPass()) {
            LlmCallResult result = llmClient.callJsonModeWithSchema(prompt, solverModel, 0.0,
                    SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON);
            SaSolverOutcome outcome = parseAndCompareShortAnswer(result.rawText(), result.modelName(), llmAnswer)
                    .withUsage(LlmUsage.of(result));
            if (block == null) {
                return outcome;
            }
            SecondPass second = secondPass(outcome.passed(), "trả lời ngắn", questionText, List.of(),
                    outcome.reasoning(), block);
            return outcome.withUsage(sum(outcome.usage(), second.usage())).withRequestCheck(second.check());
        }
        LlmCallResult result = llmClient.callJsonModeWithSchema(
                TeacherRequestPrompt.withSolverBlock(prompt, block), solverModel, 0.0,
                block == null ? SaPromptDraftV0.SOLVER_RESPONSE_SCHEMA_JSON : TeacherRequestPrompt.SA_SOLVER_SCHEMA);
        SaSolverOutcome outcome = parseAndCompareShortAnswer(result.rawText(), result.modelName(), llmAnswer)
                .withUsage(LlmUsage.of(result));
        return block == null ? outcome : outcome.withRequestCheck(TeacherRequestPrompt.parse(result.rawText()));
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
     *   <li>Thiếu hẳn {@code options_verdict}: như Solver cũ, so {@code judge_answer} với đáp án, ghi chú giữ nguyên (không
     *       có khối xét từng phương án, nên không gắn lý do mới). Prompt v3, v4 mà thiếu thì ghi cảnh báo.</li>
     *   <li>(F-03) Prompt xét từng phương án (v3, v4) mà {@code options_verdict} CÓ nhưng hỏng (không đủ 4 phần tử A-D hợp lệ):
     *       đã đọc được từ 2 phương án {@code correct = true} thì vẫn là {@code multiple} (không qua); còn lại là Solver lỗi
     *       (ném IllegalArgumentException), không so {@code judge_answer} rồi cho qua. Prompt v2 không có trường này nên giữ
     *       cách so {@code judge_answer}.</li>
     * </ul>
     * Ném IllegalArgumentException (nơi gọi coi là ERROR) khi JSON hỏng, thiếu correct_option của câu, {@code options_verdict}
     * hỏng mà không đủ căn cứ (prompt xét từng phương án), hoặc không có {@code options_verdict} mà {@code judge_answer}
     * thiếu hay sai.
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

        VerdictRead read = readOptionsVerdict(map);
        if (read.complete() != null) {
            return fromOptionsVerdict(read.complete(), map, judgeStr, reasoning, model, generatorCorrectOption);
        }
        if (read.present() && optionCheckPrompt()) {
            // F-03: mảng options_verdict có nhưng hỏng. Đã đọc được từ 2 phương án correct=true thì đủ căn cứ kết luận nhiều
            // đáp án đúng; không thì Solver lỗi (ném, nơi gọi coi là ERROR và xếp job answer_check), KHÔNG so judge_answer
            // rồi cho qua (judge_answer có thể trùng đáp án trong khi chính Solver xét thêm phương án khác cũng đúng).
            if (read.correctLetters().size() >= 2) {
                return multipleFromPartial(read.correctLetters(), reasoning, model);
            }
            throw new IllegalArgumentException("options_verdict của Solver hỏng (không đủ 4 phương án A-D hợp lệ) và"
                    + " chưa đọc được từ 2 phương án đúng: không đủ căn cứ kết luận");
        }
        if (optionCheckPrompt()) {
            log.warn("Phản hồi Solver {} không có options_verdict: kết luận theo judge_answer như Solver cũ",
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
     * Kết quả đọc {@code options_verdict}.
     *
     * @param present        trường có trong phản hồi (khác null)
     * @param complete       mảng đủ 4 phương án A-D, mỗi phương án một lần, {@code correct} là true/false (theo thứ tự A-D);
     *                       null khi mảng hỏng hoặc thiếu
     * @param correctLetters các chữ cái có {@code correct = true} đọc được từ những phần tử hợp lệ, mỗi chữ một lần (chữ
     *                       xuất hiện nhiều lần với giá trị mâu thuẫn bị loại)
     */
    private record VerdictRead(boolean present, List<OptionVerdict> complete, List<String> correctLetters) {}

    /**
     * Đọc {@code options_verdict}. Hỏng (không phải mảng đúng 4 phần tử A-D, trùng chữ cái, {@code correct} không phải
     * true/false) thì {@code complete} = null nhưng vẫn gom các phương án {@code correct = true} đọc được ở những phần tử
     * hợp lệ để nơi gọi quyết định có đủ căn cứ hay không (F-03).
     */
    private static VerdictRead readOptionsVerdict(Map<String, Object> map) {
        Object raw = map.get("options_verdict");
        if (raw == null) {
            return new VerdictRead(false, null, List.of());
        }
        if (!(raw instanceof List<?> list)) {
            log.warn("options_verdict của Solver không phải mảng: hỏng");
            return new VerdictRead(true, null, List.of());
        }
        OptionVerdict[] byLetter = new OptionVerdict[OPTION_LETTERS.size()];
        Boolean[] seen = new Boolean[OPTION_LETTERS.size()];
        boolean[] conflict = new boolean[OPTION_LETTERS.size()];
        boolean sound = list.size() == OPTION_LETTERS.size();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> item)) {
                sound = false;
                continue;
            }
            String option = item.get("option") != null ? String.valueOf(item.get("option")).trim().toUpperCase() : "";
            int idx = OPTION_LETTERS.indexOf(option);
            Boolean correct = toBoolean(item.get("correct"));
            if (idx < 0 || correct == null) {
                sound = false;
                continue;
            }
            if (byLetter[idx] != null) {
                sound = false;
                if (!correct.equals(seen[idx])) {
                    conflict[idx] = true;
                }
                continue;
            }
            String reason = item.get("reason") != null ? String.valueOf(item.get("reason")).replaceAll("\\s+", " ").trim()
                    : "";
            byLetter[idx] = new OptionVerdict(option, correct, reason);
            seen[idx] = correct;
        }
        List<String> correctLetters = new ArrayList<>();
        for (int i = 0; i < byLetter.length; i++) {
            if (byLetter[i] != null && !conflict[i] && byLetter[i].correct()) {
                correctLetters.add(byLetter[i].option());
            }
        }
        boolean complete = sound && java.util.Arrays.stream(byLetter).allMatch(java.util.Objects::nonNull);
        if (!complete) {
            log.warn("options_verdict của Solver hỏng (thiếu, trùng phương án, correct không phải true/false hoặc không đúng 4"
                    + " phần tử): đọc được {} phương án đúng", correctLetters.size());
        }
        return new VerdictRead(true, complete ? List.of(byLetter) : null, correctLetters);
    }

    /** Mảng hỏng nhưng đã đọc được từ 2 phương án đúng: nhiều đáp án đúng, không qua; ghi chú gắn khối xét từng phương án. */
    private static SolverCheckOutcome multipleFromPartial(List<String> correctLetters, String reasoning, String model) {
        StringBuilder notes = new StringBuilder(OPTIONS_VERDICT_PREFIX).append(' ').append("từ ")
                .append(correctLetters.size()).append(" phương án đúng (")
                .append(String.join(", ", correctLetters))
                .append("); mảng options_verdict của Solver không đủ 4 phương án hợp lệ nên không có kết quả từng phương án");
        if (!reasoning.isEmpty()) {
            notes.append("\n\n").append(reasoning);
        }
        return new SolverCheckOutcome(false, AnswerChoice.MULTIPLE, model, notes.toString());
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

