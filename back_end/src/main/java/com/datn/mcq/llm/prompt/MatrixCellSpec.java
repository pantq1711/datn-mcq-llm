package com.datn.mcq.llm.prompt;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;

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
 */
public record MatrixCellSpec(
        String subjectName,
        Integer gradeLevel,
        String topic,
        String content,
        Map<Difficulty, String> learningOutcomes,
        QuestionType questionType,
        Difficulty difficulty,
        List<Difficulty> statementDifficulties
) {
    public MatrixCellSpec {
        learningOutcomes = learningOutcomes == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(learningOutcomes));
        statementDifficulties = statementDifficulties == null ? List.of() : List.copyOf(statementDifficulties);
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
                + ",\"statementDifficulties\":[" + String.join(",", levels) + "]}";
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
                levels);
    }

    private static String quoteOrNull(String s) {
        return s == null ? "null" : SimpleJson.quote(s);
    }
}
