package com.datn.mcq.generation.tool;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.dto.SolverVerdict;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Một dòng CSV của công cụ đo Solver ({@link SolverProbeRunner}). Hàm thuần, không gọi DB hay mạng.
 *
 * @param verdict           consistent, inconsistent hoặc error
 * @param multipleFlag      mcq: "1" khi Solver báo có từ 2 phương án đúng, "0" khi không; rỗng với tf/sa và khi lỗi
 * @param solverCorrectCount mcq: số phương án Solver cho là đúng; Solver v3 đếm chính xác từ options_verdict, Solver cũ
 *                           (hoặc phản hồi thiếu options_verdict) chỉ biết "0", "1" hay "2+"; rỗng khi không có
 * @param optionsVerdict    mcq Solver v3: kết luận từng phương án dạng "A:1|B:0|C:1|D:0" (1 là đúng); rỗng khi không có
 */
public record SolverProbeRow(
        Integer questionId,
        String promptVersion,
        String questionType,
        String questionAnswer,
        String solverAnswer,
        String verdict,
        String multipleFlag,
        String solverCorrectCount,
        String optionsVerdict,
        Integer tokenCount,
        Double latencyMs,
        String solverModel,
        String error
) {

    public static final String HEADER = "question_id,prompt_version,question_type,question_answer,solver_answer,"
            + "verdict,multiple_correct_flag,solver_correct_count,options_verdict,token_count,latency_ms,solver_model,"
            + "error";

    private static final List<String> LETTERS = List.of("A", "B", "C", "D");

    static final int MAX_ERROR_CHARS = 300;

    /** Dòng cho một kết quả Solver hợp lệ. */
    public static SolverProbeRow of(String promptVersion, QuestionQcInput input, SolverVerdict v) {
        QuestionType type = input.questionType();
        String solverAnswer;
        String multiple = "";
        String count = "";
        String options = "";
        switch (type) {
            case TF -> solverAnswer = v.tfLabels() == null ? ""
                    : v.tfLabels().stream().map(TruthChoice::getDbValue).collect(Collectors.joining("|"));
            case SA -> solverAnswer = v.saJudgeAnswer() != null ? v.saJudgeAnswer()
                    : v.saJudgeKind() != null ? v.saJudgeKind().getDbValue() : "";
            default -> {
                AnswerChoice j = v.judgeAnswer();
                solverAnswer = j != null ? j.getDbValue() : "";
                multiple = j == AnswerChoice.MULTIPLE ? "1" : "0";
                List<Boolean> perOption = v.mcqOptionCorrect();
                if (perOption != null) {
                    count = String.valueOf(perOption.stream().filter(Boolean::booleanValue).count());
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < perOption.size() && i < LETTERS.size(); i++) {
                        sb.append(i > 0 ? "|" : "").append(LETTERS.get(i)).append(':')
                                .append(perOption.get(i) ? '1' : '0');
                    }
                    options = sb.toString();
                } else {
                    count = j == null ? "" : j == AnswerChoice.MULTIPLE ? "2+" : j == AnswerChoice.NONE ? "0" : "1";
                }
            }
        }
        Integer tokens = v.usage() != null ? v.usage().tokenCount() : null;
        Double latency = v.usage() != null ? v.usage().latencyMs() : null;
        return new SolverProbeRow(input.questionId(), promptVersion, type.getDbValue(), questionAnswer(input),
                solverAnswer, v.passed() ? "consistent" : "inconsistent", multiple, count, options, tokens, latency,
                v.judgeModel(), "");
    }

    /**
     * Dòng lỗi (Solver lỗi kỹ thuật, phản hồi hỏng, câu không tồn tại...). {@code input} null khi chưa nạp được câu.
     */
    public static SolverProbeRow error(String promptVersion, Integer questionId, QuestionQcInput input,
                                       String message) {
        return new SolverProbeRow(questionId, promptVersion,
                input != null ? input.questionType().getDbValue() : "",
                input != null ? questionAnswer(input) : "", "", "error", "", "", "", null, null, "",
                clipOneLine(message));
    }

    /** Đáp án của câu theo dạng: mcq A-D, tf 4 nhãn nối bằng "|", sa đáp số. */
    static String questionAnswer(QuestionQcInput input) {
        return switch (input.questionType()) {
            case TF -> String.join("|", input.statementLabels());
            case SA -> input.saAnswer() != null ? input.saAnswer() : "";
            default -> input.correctOption() != null ? input.correctOption().getDbValue() : "";
        };
    }

    public String toCsvLine() {
        return List.of(
                str(questionId), promptVersion, questionType, questionAnswer, solverAnswer, verdict, multipleFlag,
                solverCorrectCount, optionsVerdict, str(tokenCount),
                latencyMs == null ? "" : String.valueOf(Math.round(latencyMs)), solverModel, error
        ).stream().map(SolverProbeRow::csv).collect(Collectors.joining(","));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Trích một ô CSV theo RFC 4180: bọc nháy kép khi có dấu phẩy, nháy kép hoặc xuống dòng. */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    static String clipOneLine(String message) {
        if (message == null) {
            return "";
        }
        String oneLine = message.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= MAX_ERROR_CHARS ? oneLine : oneLine.substring(0, MAX_ERROR_CHARS) + "...";
    }
}
