package com.datn.mcq.qc.checker;

import com.datn.mcq.common.util.LatexValidationRules;
import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.common.util.TextDistanceUtils;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class FormatChecker {

    /**
     * @param passed   true khi không có lỗi fatal
     * @param errors   lỗi fatal, làm câu format_failed
     * @param warnings cảnh báo LaTeX (lệnh ngoài danh sách cho phép), không ảnh hưởng {@code passed}
     */
    public record FormatCheckOutcome(boolean passed, List<String> errors, List<String> warnings,
                                     List<String> answerWarnings) {
        public FormatCheckOutcome(boolean passed, List<String> errors, List<String> warnings) {
            this(passed, errors, warnings, List.of());
        }
    }

    public FormatCheckOutcome check(QuestionQcInput input) {
        if (input.questionType() == QuestionType.SA) {
            return checkShortAnswer(input);
        }
        if (input.questionType() == QuestionType.TF) {
            return checkTrueFalse(input);
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!input.parseSuccess()) {
            String detail = input.parseError();
            errors.add("Không giải mã được JSON hợp lệ từ phản hồi LLM"
                    + (detail != null && !detail.isBlank() ? ": " + detail : ""));
            return new FormatCheckOutcome(false, errors, warnings);
        }
        checkNonBlank("question_text", input.questionText(), errors);
        checkNonBlank("option_a", input.optionA(), errors);
        checkNonBlank("option_b", input.optionB(), errors);
        checkNonBlank("option_c", input.optionC(), errors);
        checkNonBlank("option_d", input.optionD(), errors);
        checkNonBlank("explanation", input.explanation(), errors);

        if (input.correctOption() == null) {
            errors.add("correct_option không hợp lệ (phải thuộc A, B, C, D)");
        }

        if (errors.isEmpty()) {
            Set<String> distinctOptions = new HashSet<>();
            distinctOptions.add(TextDistanceUtils.normalize(input.optionA()));
            distinctOptions.add(TextDistanceUtils.normalize(input.optionB()));
            distinctOptions.add(TextDistanceUtils.normalize(input.optionC()));
            distinctOptions.add(TextDistanceUtils.normalize(input.optionD()));
            if (distinctOptions.size() < 4) {
                errors.add("4 phương án A, B, C, D có nội dung trùng lặp nhau");
            }
        }
        checkExplanationDraft(input, errors, warnings);

        if (input.validateLatex()) {
            validateFieldLatex("question_text", input.questionText(), errors, warnings);
            validateFieldLatex("option_a", input.optionA(), errors, warnings);
            validateFieldLatex("option_b", input.optionB(), errors, warnings);
            validateFieldLatex("option_c", input.optionC(), errors, warnings);
            validateFieldLatex("option_d", input.optionD(), errors, warnings);
            // Việc 2 (22/10): lỗi LaTeX của lời giải chỉ là cảnh báo, không làm câu format_failed;
            // khi chép vào ngân hàng, lời giải còn lỗi nghiêm trọng được lưu NULL (AssistedKeepService).
            validateFieldLatex("explanation", input.explanation(), warnings, warnings);
        }

        // Ngày 23/10: cảnh báo từ bước giải mã ở chế độ sửa (đã sửa escape LaTeX, đã tự đóng $).
        return new FormatCheckOutcome(errors.isEmpty(), errors, warnings, input.parseWarnings());
    }

    /**
     * Câu Trả lời ngắn (V4, bản sửa 1 mục 5): question_text, explanation, answer không trống; answer là chuỗi JSON
     * và đạt chế độ STRICT (lỗi là fatal): từ 27/10 là số nguyên hoặc thập phân tối đa 4 ký tự theo phiếu trả lời Bộ
     * GD&ĐT 2025, Phần III. Với môn Toán, LaTeX ở question_text và answer là lỗi chặn (quyết định B), ở explanation chỉ
     * là cảnh báo; đáp án đã sai dạng thì chỉ báo lỗi dạng. Không còn quy tắc "đáp số không nguyên phải có yêu cầu làm
     * tròn" (nay là việc của đề bài, xem prompt sa ma trận).
     */
    private FormatCheckOutcome checkShortAnswer(QuestionQcInput input) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> answerWarnings = new ArrayList<>();
        if (!input.parseSuccess()) {
            String detail = input.parseError();
            errors.add("Không giải mã được JSON hợp lệ từ phản hồi LLM"
                    + (detail != null && !detail.isBlank() ? ": " + detail : ""));
            return new FormatCheckOutcome(false, errors, warnings);
        }
        answerWarnings.addAll(input.parseWarnings());
        checkNonBlank("question_text", input.questionText(), errors);
        checkNonBlank("explanation", input.explanation(), errors);
        String answer = input.saAnswer();
        boolean answerRejected = false;
        if (answer == null || answer.isBlank()) {
            errors.add("answer: nội dung trống");
        } else if (!input.saAnswerIsString()) {
            errors.add("answer: phải là chuỗi JSON");
        } else {
            ShortAnswerValue.ParseResult parsed = ShortAnswerValue.parse(answer, ShortAnswerValue.Mode.STRICT);
            if (!parsed.ok()) {
                errors.add("answer: " + parsed.error());
                answerRejected = true;
            }
            for (String w : parsed.warnings()) {
                answerWarnings.add("answer: " + w);
            }
        }
        checkExplanationDraft(input, errors, warnings);
        if (input.validateLatex()) {
            validateFieldLatex("question_text", input.questionText(), errors, warnings);
            // Đáp án đã bị loại vì sai dạng thì không báo thêm "LaTeX ngoài $": ghi chú sinh lại chỉ nói đúng cách sửa.
            if (input.saAnswerIsString() && !answerRejected) {
                validateFieldLatex("answer", answer, errors, warnings);
            }
            validateFieldLatex("explanation", input.explanation(), warnings, warnings);
        }
        return new FormatCheckOutcome(errors.isEmpty(), errors, warnings, answerWarnings);
    }

    private static final List<String> TF_KEYS = List.of("a", "b", "c", "d");

    /**
     * Câu Đúng/Sai (V4, GĐ3): đề dẫn, 4 ý, lời giải không trống; 4 nhãn là T hoặc F (sau trim, không phân biệt hoa
     * thường); 4 ý khác nhau sau chuẩn hóa. Với môn Toán, LaTeX ở đề dẫn và ý là lỗi chặn, ở lời giải chỉ là cảnh
     * báo. 4 nhãn giống nhau chỉ là cảnh báo trong answerWarnings (quyết định ngày 22/10).
     */
    private FormatCheckOutcome checkTrueFalse(QuestionQcInput input) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> answerWarnings = new ArrayList<>();
        if (!input.parseSuccess()) {
            String detail = input.parseError();
            errors.add("Không giải mã được JSON hợp lệ từ phản hồi LLM"
                    + (detail != null && !detail.isBlank() ? ": " + detail : ""));
            return new FormatCheckOutcome(false, errors, warnings);
        }
        // GĐ4: cảnh báo từ bước giải mã (đã bỏ tiền tố nhãn ở đầu ý) đứng trước các cảnh báo khác.
        answerWarnings.addAll(input.parseWarnings());
        List<String> statements = new ArrayList<>(4);
        List<TruthValue> labels = new ArrayList<>(4);
        checkNonBlank("question_text", input.questionText(), errors);
        for (int i = 0; i < TF_KEYS.size(); i++) {
            String statement = i < input.statements().size() ? input.statements().get(i) : null;
            statements.add(statement);
            checkNonBlank("statement_" + TF_KEYS.get(i), statement, errors);
        }
        checkNonBlank("explanation", input.explanation(), errors);
        if (input.llmOutput()) {
            // Đo 26/10: lời giải tự thú nhận gán nhãn theo mẫu hoặc tự sửa đề; chỉ áp cho đầu ra LLM, không áp chữ giáo viên.
            errors.addAll(TfExplanationLeak.errors(input.explanation()));
        }
        checkExplanationDraft(input, errors, warnings);
        for (int i = 0; i < TF_KEYS.size(); i++) {
            String raw = i < input.statementLabels().size() ? input.statementLabels().get(i) : null;
            String field = "label_" + TF_KEYS.get(i);
            if (raw == null || raw.isBlank()) {
                errors.add(field + ": nội dung trống");
                labels.add(null);
                continue;
            }
            TruthValue label = TruthValue.fromLabel(raw);
            if (label == null) {
                errors.add(field + ": phải là T hoặc F");
            }
            labels.add(label);
        }

        if (statements.stream().allMatch(s -> s != null && !s.isBlank())) {
            Set<String> distinct = new HashSet<>();
            for (String s : statements) {
                distinct.add(TextDistanceUtils.normalize(s));
            }
            if (distinct.size() < 4) {
                errors.add("4 ý a, b, c, d có nội dung trùng lặp nhau");
            }
        }
        if (labels.stream().allMatch(l -> l != null) && new HashSet<>(labels).size() == 1) {
            answerWarnings.add("label: 4 ý cùng nhãn " + labels.get(0).getDbValue());
        }

        if (input.validateLatex()) {
            validateFieldLatex("question_text", input.questionText(), errors, warnings);
            for (int i = 0; i < TF_KEYS.size(); i++) {
                validateFieldLatex("statement_" + TF_KEYS.get(i), statements.get(i), errors, warnings);
            }
            validateFieldLatex("explanation", input.explanation(), warnings, warnings);
        }
        return new FormatCheckOutcome(errors.isEmpty(), errors, warnings, answerWarnings);
    }

    /**
     * Lời giải còn phần nháp hoặc tự sửa (31/10, cả 3 dạng). Cụm chắc chắn là nháp ({@link ExplanationDraftLeak#detect}):
     * đầu ra của LLM là lỗi chặn, để ghi chú sinh lại nói đúng cách sửa; chữ giáo viên viết hoặc sửa (và chạy lại QC câu đã
     * lưu) chỉ là cảnh báo. Câu tf mà {@link TfExplanationLeak} đã báo tự sửa thì không báo thêm lần nữa. Cụm đáng nghi
     * ({@link ExplanationDraftLeak#suspect}: "tính lại", "làm lại", "sửa lại"): không chặn ở mọi nguồn, chỉ cảnh báo; lý do
     * "cần xem kỹ" {@code explanation_draft_suspect} do QcSummaryService suy từ cảnh báo này.
     */
    private void checkExplanationDraft(QuestionQcInput input, List<String> errors, List<String> warnings) {
        if (ExplanationDraftLeak.detect(input.explanation())) {
            if (!input.llmOutput()) {
                warnings.add(ExplanationDraftLeak.MESSAGE);
            } else if (input.questionType() != QuestionType.TF
                    || !TfExplanationLeak.detect(input.explanation()).contains(TfExplanationLeak.Kind.SELF_REVISION)) {
                errors.add(ExplanationDraftLeak.MESSAGE);
            }
        } else if (ExplanationDraftLeak.suspect(input.explanation())) {
            warnings.add(ExplanationDraftLeak.SUSPECT_WARNING);
        }
    }

    private void checkNonBlank(String fieldName, String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(fieldName + ": nội dung trống");
        }
    }

    private void validateFieldLatex(String fieldName, String value, List<String> errors, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return;
        }
        LatexValidationRules.Result result = LatexValidationRules.check(value);
        for (String err : result.errors()) {
            errors.add(fieldName + ": " + err);
        }
        for (String warning : result.warnings()) {
            warnings.add(fieldName + ": " + warning);
        }
    }
}

