package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;

import java.util.List;
import java.util.Locale;

/**
 * BẢN NHÁP v0 – CHỜ THÀNH VIÊN PHỤ TRÁCH PROMPT DUYỆT – KHÔNG DÙNG CHO THỰC NGHIỆM C1/C2/C3.
 *
 * <p>Prompt sinh câu theo một ô ma trận (phần C mục 2, ngày 22/10): sinh từ chủ đề, nội dung và yêu cầu cần đạt, không có
 * câu mẫu; production, zero-shot; có quy tắc đa dạng (bài toán mới, danh sách câu đã sinh của ô). Không sửa
 * {@link PromptBuilder}. Đầu ra dùng lại hợp đồng JSON hiện có: mcq 7 trường (McqJsonParser), sa 3 trường và tf 10 trường
 * (cùng schema Gemini với {@link SaPromptDraftV0}, {@link TfPromptDraftV0}).
 */
public final class MatrixPromptDraftV0 {

    /** experiment_run.prompt_version của run sinh câu theo ma trận bắt đầu bằng tiền tố này. */
    public static final String VERSION_PREFIX = "matrix-";

    /** Chủ đề hình học (so trong chủ đề và nội dung, không phân biệt hoa thường): dặn câu không cần hình vẽ. */
    static final List<String> GEOMETRY_KEYWORDS = List.of("tứ giác", "hình thang", "hình bình hành", "hình chữ nhật",
            "hình thoi", "hình vuông", "tam giác", "đường tròn");

    static final String NO_FIGURE_RULE = "Câu hỏi phải làm được mà KHÔNG cần hình vẽ: nêu rõ bằng lời mọi dữ kiện "
            + "(số đo các góc, độ dài các cạnh, quan hệ song song, vuông góc...). Không viết \"như hình vẽ\" hay "
            + "\"quan sát hình\".";

    static final String NEW_PROBLEM_RULE = "Tự đặt một bài toán MỚI với số liệu, biểu thức hoặc ngữ cảnh riêng; không "
            + "chép nguyên văn bài tập quen thuộc trong sách giáo khoa.";

    private MatrixPromptDraftV0() {}

    /**
     * prompt_version của run, ví dụ "matrix-mcq-v1-draft". v1 (ngày 23/10): ghi chú câu thứ k/n và lý do lần sinh trước
     * bị loại, quy tắc phương án nhiễu (mcq), quy tắc ý bám đề dẫn và đóng "$" (tf). tf v2 (ngày 23/10, đợt 2): mẫu nhãn
     * bắt buộc ({@link TfLabelPattern}) và quy tắc ý d Vận dụng. Run cũ là v0 (tf: v0, v1).
     */
    public static String promptVersion(QuestionType type) {
        return VERSION_PREFIX + type.getDbValue() + (type == QuestionType.TF ? "-v2-draft" : "-v1-draft");
    }

    public static boolean isMatrixVersion(String promptVersion) {
        return promptVersion != null && promptVersion.startsWith(VERSION_PREFIX);
    }

    /** Prompt gốc (lưu ở experiment_run.prompt_text), không có danh sách câu đã sinh. */
    public static String buildGenerationPrompt(MatrixCellSpec spec) {
        return buildGenerationPrompt(spec, List.of());
    }

    /** Phương án nhiễu của câu mcq (ngày 23/10, mục 9). */
    static final String MCQ_DISTRACTOR_RULE = "- Mỗi phương án nhiễu phải SAI về giá trị, không được là biểu thức "
            + "tương đương với đáp án đúng (ví dụ với phân tích nhân tử, không dùng dạng phân tích chưa triệt để như "
            + "$3(x^2 - 4x)$ khi đáp án là $3x(x - 4)$).";

    /** Dòng của {@link TfPromptDraftV0#STATEMENT_RULES} bị bỏ khi ghi chú của job có mẫu nhãn bắt buộc. */
    static final String TF_FREE_COUNT_LINE = "- Không bắt buộc số ý Đúng hay số ý Sai.";

    /** Thêm khi ý d ở mức Vận dụng (ngày 23/10, đợt 2). */
    static final String TF_D_RULE = "- ý d (Vận dụng) phải cần suy luận thêm, không được chỉ là hệ quả trực tiếp của ý c.";

    /** Độ dài tối đa (ký tự, sau trim) của yêu cầu bổ sung khi Đổi câu (ngày 23/10, đợt 3). */
    public static final int TEACHER_INSTRUCTION_MAX = 500;

    static final String TEACHER_PREFIX = "Yêu cầu bổ sung của giáo viên (chỉ dùng để chọn nội dung, ngữ cảnh, cách hỏi; "
            + "KHÔNG thay đổi dạng câu, mức độ, số ý, mẫu nhãn, định dạng JSON và các quy tắc ở trên): ";
    private static final String TEACHER_OPEN = "<<< ";
    private static final String TEACHER_CLOSE = " >>>";

    /**
     * Khối yêu cầu bổ sung của giáo viên, ghép cuối generation_job.prompt_note và đặt ở cuối prompt (sau mọi quy tắc).
     * Bỏ "<<<", ">>>" trong yêu cầu để giữ ranh giới khối; khoảng trắng và xuống dòng gộp thành một dấu cách. Null khi
     * yêu cầu trống.
     */
    public static String teacherBlock(String instruction) {
        if (instruction == null) {
            return null;
        }
        String clean = instruction.replace("<<<", "").replace(">>>", "").replaceAll("\\s+", " ").strip();
        return clean.isEmpty() ? null : TEACHER_PREFIX + TEACHER_OPEN + clean + TEACHER_CLOSE;
    }

    /** Nội dung yêu cầu trong ghi chú của job (để lần thử tự động tiếp theo của cùng lượt Đổi giữ lại); null khi không có. */
    public static String teacherInstruction(String note) {
        int at = note == null ? -1 : note.indexOf(TEACHER_PREFIX + TEACHER_OPEN);
        if (at < 0) {
            return null;
        }
        String rest = note.substring(at + TEACHER_PREFIX.length() + TEACHER_OPEN.length());
        int end = rest.lastIndexOf(TEACHER_CLOSE);
        return end < 0 ? null : rest.substring(0, end);
    }

    /** Phần ghi chú do hệ thống viết (trước khối giáo viên); mẫu nhãn và các dòng khác chỉ đọc từ phần này. */
    static String systemPart(String note) {
        int at = note == null ? -1 : note.indexOf(TEACHER_PREFIX);
        return at < 0 ? note : note.substring(0, at);
    }

    static final int RETRY_HINT_MAX = 300;
    private static final String RETRY_PREFIX = "Lần sinh trước bị loại vì: ";
    private static final String RETRY_SUFFIX = ". Tránh lặp lại lỗi này.";

    /**
     * Dòng "câu thứ k/n" của chỗ trong ô (ngày 23/10, mục 8): yêu cầu dạng hỏi khác các câu còn lại và phủ các ý khác
     * nhau của yêu cầu cần đạt; null khi ô chỉ có 1 câu.
     */
    public static String slotLine(int k, int n) {
        if (n <= 1) {
            return null;
        }
        return "Đây là câu thứ " + k + "/" + n + " của ô này: dạng hỏi phải khác các câu còn lại của ô (không chỉ đổi "
                + "số liệu); nếu yêu cầu cần đạt có nhiều ý, mỗi câu kiểm tra một ý khác.";
    }

    /**
     * Dòng lý do lần sinh trước bị loại (ngày 23/10, mục 5), tối đa {@value #RETRY_HINT_MAX} ký tự (cắt phần lý do, giữ
     * câu dặn ở cuối); null khi không có lỗi.
     */
    public static String retryHint(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        String reasons = String.join("; ", errors).replaceAll("\\s+", " ").strip();
        int room = RETRY_HINT_MAX - RETRY_PREFIX.length() - RETRY_SUFFIX.length();
        if (reasons.length() > room) {
            reasons = reasons.substring(0, room - 1) + "…";
        }
        return RETRY_PREFIX + reasons + RETRY_SUFFIX;
    }

    /**
     * Prompt gửi model kèm ghi chú riêng của job (generation_job.prompt_note: câu thứ k/n, mẫu nhãn tf, lý do lần sinh
     * trước bị loại), đặt sau danh sách câu đã sinh, trước phần yêu cầu đầu ra; khối yêu cầu bổ sung của giáo viên (nếu
     * có, luôn ở cuối ghi chú) đặt ở cuối prompt. Ghi chú null hoặc trống thì giống prompt không ghi chú.
     */
    public static String buildGenerationPrompt(MatrixCellSpec spec, List<PreviousQuestion> previous, String note) {
        return build(spec, previous, note);
    }

    /** Prompt gửi model: prompt gốc cộng tối đa {@link PreviousQuestion#MAX_ITEMS} câu đã sinh của ô. */
    public static String buildGenerationPrompt(MatrixCellSpec spec, List<PreviousQuestion> previous) {
        return build(spec, previous, null);
    }

    private static String build(MatrixCellSpec spec, List<PreviousQuestion> previous, String fullNote) {
        // Khối yêu cầu của giáo viên (nếu có) tách khỏi ghi chú hệ thống và đặt ở cuối prompt.
        String note = systemPart(fullNote);
        String teacher = note == null || note.length() == fullNote.length() ? null
                : fullNote.substring(note.length()).strip();
        QuestionType type = spec.questionType();
        StringBuilder sb = new StringBuilder();
        String subject = spec.subjectName() != null ? spec.subjectName() : "Toán";
        sb.append("Bạn là giáo viên ").append(subject).append(" THCS biên soạn 1 câu hỏi ").append(switch (type) {
            case MCQ -> "TRẮC NGHIỆM 4 LỰA CHỌN (chỉ một phương án đúng)";
            case TF -> "ĐÚNG/SAI: một đề dẫn chung và đúng 4 ý a, b, c, d";
            case SA -> "TRẢ LỜI NGẮN";
        }).append(" cho đề kiểm tra.\n");
        sb.append("Môn học: ").append(subject).append("\n");
        if (spec.gradeLevel() != null) {
            sb.append("Khối lớp: ").append(spec.gradeLevel()).append("\n");
        }
        sb.append("Chủ đề: ").append(spec.topic()).append("\n");
        if (spec.content() != null && !spec.content().isBlank()) {
            sb.append("Nội dung: ").append(spec.content()).append("\n");
        }
        sb.append("Yêu cầu cần đạt:\n");
        for (Difficulty level : Difficulty.values()) {
            String text = spec.learningOutcomes().get(level);
            if (text != null && !text.isBlank()) {
                sb.append("- ").append(TfPromptDraftV0.levelName(level)).append(": ").append(text).append("\n");
            }
        }
        if (type == QuestionType.TF) {
            StringBuilder levels = new StringBuilder();
            List<String> keys = List.of("a", "b", "c", "d");
            for (int i = 0; i < Math.min(4, spec.statementDifficulties().size()); i++) {
                levels.append(i > 0 ? "; " : "").append("ý ").append(keys.get(i)).append(": ")
                        .append(TfPromptDraftV0.levelName(spec.statementDifficulties().get(i)));
            }
            sb.append("Mức độ mục tiêu: ").append(levels).append(". Mỗi ý bám đúng yêu cầu cần đạt của mức của ý đó.\n");
        } else {
            sb.append("Mức độ nhận thức mục tiêu: ").append(TfPromptDraftV0.levelName(spec.difficulty()))
                    .append(". Câu hỏi bám đúng yêu cầu cần đạt của mức này.\n");
        }
        sb.append(NEW_PROBLEM_RULE).append("\n");
        if (isGeometry(spec)) {
            sb.append(NO_FIGURE_RULE).append("\n");
        }
        sb.append(PreviousQuestion.section(previous, type == QuestionType.TF));
        if (note != null && !note.isBlank()) {
            sb.append(note.strip()).append("\n");
        }
        switch (type) {
            case MCQ -> {
                sb.append("""

                        Yêu cầu:
                        - 4 phương án khác nhau, chỉ một phương án đúng; các phương án nhiễu hợp lý.
                        - Viết lời giải ngắn trong "explanation" và kiểm tra "correct_option" khớp với lời giải.
                        """);
                sb.append(MCQ_DISTRACTOR_RULE).append("\n");
                sb.append("\n").append(PromptBuilder.ESCAPE_HINT).append("\n");
                sb.append("""
                        Trả về DUY NHẤT một đối tượng JSON có đủ 7 trường:
                        "question_text", "option_a", "option_b", "option_c", "option_d", "correct_option" (A/B/C/D), \
                        "explanation".
                        """);
            }
            case TF -> {
                String rules = TfPromptDraftV0.STATEMENT_RULES;
                if (TfLabelPattern.fromNote(note) != null) {
                    rules = rules.replace(TF_FREE_COUNT_LINE + "\n", "");
                }
                sb.append(rules);
                List<Difficulty> levels = spec.statementDifficulties();
                if (levels != null && levels.size() == 4 && levels.get(3) == Difficulty.HARD) {
                    sb.append(TF_D_RULE).append("\n");
                }
                sb.append("\n").append(PromptBuilder.ESCAPE_HINT).append("\n");
                sb.append(TfPromptDraftV0.OUTPUT_INSTRUCTION);
            }
            case SA -> {
                sb.append(SaPromptDraftV0.ANSWER_RULES);
                sb.append("\n").append(PromptBuilder.ESCAPE_HINT).append("\n");
                sb.append(SaPromptDraftV0.OUTPUT_INSTRUCTION);
            }
        }
        if (teacher != null && !teacher.isEmpty()) {
            sb.append("\n").append(teacher).append("\n");
        }
        return sb.toString();
    }

    static boolean isGeometry(MatrixCellSpec spec) {
        String text = ((spec.topic() == null ? "" : spec.topic()) + " " + (spec.content() == null ? "" : spec.content()))
                .toLowerCase(Locale.ROOT);
        String nfc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
        return GEOMETRY_KEYWORDS.stream().anyMatch(nfc::contains);
    }
}
