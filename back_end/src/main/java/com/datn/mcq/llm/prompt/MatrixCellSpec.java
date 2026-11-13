package com.datn.mcq.llm.prompt;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamChallenge;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.TeacherRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thông số một ô ma trận để dựng prompt sinh câu (phần C mục 2). Lưu ở experiment_run.hyperparams của run sinh câu cho ô,
 * để worker dựng lại prompt mà module generation không cần biết module exam/matrix.
 *
 * @param learningOutcomes      yêu cầu cần đạt theo mức (chỉ các mức ô dùng)
 * @param difficulty            mức của câu mcq/sa; null với tf
 * @param statementDifficulties 4 mức của ý a-d (tf); rỗng với mcq/sa
 * @param extraInstruction      (W2-3) "yêu cầu thêm cho AI" của giáo viên cho cả bài hoặc ô này, đã chuẩn hóa; null khi
 *                              không có. Luôn được đưa vào prompt như DỮ LIỆU ({@link MatrixPromptDraftV0#extraBlock})
 * @param challenge             (12/11) mức thử thách của đề; null là {@link ExamChallenge#STANDARD}. Nâng cao chỉ thêm khối
 *                              vào ô (hoặc ý tf) mức Vận dụng ({@link MatrixPromptDraftV0#ADVANCED_RULE})
 * @param depthExamples         (12/11) câu mẫu độ sâu chọn cho ô lúc tạo đề ({@link DepthExampleCatalog}); rỗng khi không có
 * @param requestCodes          (12/11, PR B) mã chip yêu cầu của giáo viên hiệu lực cho ô (của ô, không có thì của đề), đã
 *                              lọc theo dạng câu, không trùng, theo thứ tự danh mục ({@link TeacherRequest}); rỗng khi không có.
 *                              Đi cùng {@code extraInstruction} của CÙNG mức. Prompt có khối
 *                              {@link TeacherRequestPrompt#GENERATION_HEADER}
 */
public record MatrixCellSpec(
        String subjectName,
        Integer gradeLevel,
        String topic,
        String content,
        Map<Difficulty, String> learningOutcomes,
        QuestionType questionType,
        Difficulty difficulty,
        List<Difficulty> statementDifficulties,
        String extraInstruction,
        ExamChallenge challenge,
        List<DepthExample> depthExamples,
        List<String> requestCodes
) {
    public MatrixCellSpec {
        learningOutcomes = learningOutcomes == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(learningOutcomes));
        statementDifficulties = statementDifficulties == null ? List.of() : List.copyOf(statementDifficulties);
        extraInstruction = extraInstruction == null || extraInstruction.isBlank() ? null : extraInstruction;
        challenge = challenge == null ? ExamChallenge.STANDARD : challenge;
        depthExamples = depthExamples == null ? List.of() : List.copyOf(depthExamples);
        requestCodes = TeacherRequest.applicable(requestCodes, questionType);
    }

    /** Chữ ký trước PR B (không có mã yêu cầu). */
    public MatrixCellSpec(String subjectName, Integer gradeLevel, String topic, String content,
                          Map<Difficulty, String> learningOutcomes, QuestionType questionType, Difficulty difficulty,
                          List<Difficulty> statementDifficulties, String extraInstruction, ExamChallenge challenge,
                          List<DepthExample> depthExamples) {
        this(subjectName, gradeLevel, topic, content, learningOutcomes, questionType, difficulty, statementDifficulties,
                extraInstruction, challenge, depthExamples, null);
    }

    /**
     * Bản sao với yêu cầu khác (PR B): job Đổi câu tự đặt yêu cầu thì worker THAY HẲN mã và văn bản tự do của ô hay đề
     * bằng của lượt Đổi (văn bản của lượt Đổi nằm trong ghi chú của job, nên {@code extra} thường là null).
     */
    public MatrixCellSpec withRequest(List<String> codes, String extra) {
        return new MatrixCellSpec(subjectName, gradeLevel, topic, content, learningOutcomes, questionType, difficulty,
                statementDifficulties, extra, challenge, depthExamples, codes);
    }

    /** Chữ ký trước 12/11 (đề chuẩn, không câu mẫu). */
    public MatrixCellSpec(String subjectName, Integer gradeLevel, String topic, String content,
                          Map<Difficulty, String> learningOutcomes, QuestionType questionType, Difficulty difficulty,
                          List<Difficulty> statementDifficulties, String extraInstruction) {
        this(subjectName, gradeLevel, topic, content, learningOutcomes, questionType, difficulty,
                statementDifficulties, extraInstruction, null, null);
    }

    /** Các mức ô dùng: mức của câu (mcq/sa) hoặc mức của 4 ý (tf), không lặp. */
    public java.util.Set<Difficulty> levels() {
        java.util.Set<Difficulty> out = new java.util.LinkedHashSet<>();
        if (questionType == QuestionType.TF) {
            out.addAll(statementDifficulties);
        } else if (difficulty != null) {
            out.add(difficulty);
        }
        return out;
    }

    /** Chữ ký trước W2-3 (không có yêu cầu thêm). */
    public MatrixCellSpec(String subjectName, Integer gradeLevel, String topic, String content,
                          Map<Difficulty, String> learningOutcomes, QuestionType questionType, Difficulty difficulty,
                          List<Difficulty> statementDifficulties) {
        this(subjectName, gradeLevel, topic, content, learningOutcomes, questionType, difficulty,
                statementDifficulties, null);
    }

    /** JSON lưu vào experiment_run.hyperparams. */
    public String toJson() {
        StringBuilder outcomes = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Difficulty, String> e : learningOutcomes.entrySet()) {
            outcomes.append(first ? "" : ",").append(SimpleJson.quote(e.getKey().getDbValue())).append(":")
                    .append(SimpleJson.quote(e.getValue()));
            first = false;
        }
        outcomes.append("}");
        List<String> levels = new ArrayList<>();
        for (Difficulty d : statementDifficulties) {
            levels.add(SimpleJson.quote(d.getDbValue()));
        }
        return "{\"subjectName\":" + quoteOrNull(subjectName)
                + ",\"gradeLevel\":" + (gradeLevel == null ? "null" : gradeLevel)
                + ",\"topic\":" + quoteOrNull(topic)
                + ",\"content\":" + quoteOrNull(content)
                + ",\"learningOutcomes\":" + outcomes
                + ",\"questionType\":" + SimpleJson.quote(questionType.getDbValue())
                + ",\"difficulty\":" + (difficulty == null ? "null" : SimpleJson.quote(difficulty.getDbValue()))
                + ",\"statementDifficulties\":[" + String.join(",", levels) + "]"
                // Chỉ thêm khóa khi có yêu cầu: JSON của run cũ và của ô không có yêu cầu giữ nguyên.
                + (extraInstruction == null ? "" : ",\"extraInstruction\":" + SimpleJson.quote(extraInstruction))
                // 12/11: như trên, chỉ thêm khóa khi khác mặc định (đề chuẩn, không câu mẫu).
                + (challenge == ExamChallenge.STANDARD ? "" : ",\"challenge\":" + SimpleJson.quote(challenge.getDbValue()))
                + (depthExamples.isEmpty() ? "" : ",\"depthExamples\":" + examplesJson())
                // PR B: như trên, chỉ thêm khóa khi có mã.
                + (requestCodes.isEmpty() ? "" : ",\"requestCodes\":[" + String.join(",",
                        requestCodes.stream().map(SimpleJson::quote).toList()) + "]")
                + "}";
    }

    private String examplesJson() {
        List<String> items = new ArrayList<>();
        for (DepthExample e : depthExamples) {
            items.add("{\"level\":" + SimpleJson.quote(e.level().getDbValue()) + ",\"text\":" + SimpleJson.quote(e.text())
                    + "}");
        }
        return "[" + String.join(",", items) + "]";
    }

    public static MatrixCellSpec fromJson(String json) {
        Map<String, Object> m = SimpleJson.parseObject(json);
        Map<Difficulty, String> outcomes = new LinkedHashMap<>();
        if (m.get("learningOutcomes") instanceof Map<?, ?> lo) {
            lo.forEach((k, v) -> outcomes.put(DbEnum.fromDbValue(Difficulty.class, String.valueOf(k)),
                    String.valueOf(v)));
        }
        List<Difficulty> levels = new ArrayList<>();
        if (m.get("statementDifficulties") instanceof List<?> l) {
            for (Object v : l) {
                levels.add(DbEnum.fromDbValue(Difficulty.class, String.valueOf(v)));
            }
        }
        return new MatrixCellSpec(
                (String) m.get("subjectName"),
                m.get("gradeLevel") instanceof Number n ? n.intValue() : null,
                (String) m.get("topic"),
                (String) m.get("content"),
                outcomes,
                DbEnum.fromDbValue(QuestionType.class, String.valueOf(m.get("questionType"))),
                m.get("difficulty") == null ? null : DbEnum.fromDbValue(Difficulty.class, String.valueOf(m.get("difficulty"))),
                levels,
                m.get("extraInstruction") instanceof String extra ? extra : null,
                m.get("challenge") instanceof String c ? DbEnum.fromDbValue(ExamChallenge.class, c) : null,
                examplesFrom(m.get("depthExamples")),
                m.get("requestCodes") instanceof List<?> codes ? codes.stream().map(String::valueOf).toList() : null);
    }

    private static List<DepthExample> examplesFrom(Object raw) {
        List<DepthExample> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> e && e.get("level") != null && e.get("text") instanceof String text) {
                    out.add(new DepthExample(DbEnum.fromDbValue(Difficulty.class, String.valueOf(e.get("level"))), text));
                }
            }
        }
        return out;
    }

    private static String quoteOrNull(String s) {
        return s == null ? "null" : SimpleJson.quote(s);
    }
}
