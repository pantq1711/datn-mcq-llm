package com.datn.mcq.qc.checker;

import com.datn.mcq.common.util.LatexValidationRules;
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
    public record FormatCheckOutcome(boolean passed, List<String> errors, List<String> warnings) {}

    public FormatCheckOutcome check(QuestionQcInput input) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!input.parseSuccess()) {
            errors.add("Không giải mã được JSON hợp lệ từ phản hồi LLM");
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

        if (input.validateLatex()) {
            validateFieldLatex("question_text", input.questionText(), errors, warnings);
            validateFieldLatex("option_a", input.optionA(), errors, warnings);
            validateFieldLatex("option_b", input.optionB(), errors, warnings);
            validateFieldLatex("option_c", input.optionC(), errors, warnings);
            validateFieldLatex("option_d", input.optionD(), errors, warnings);
            // Việc 2 (22/10): lỗi LaTeX của lời giải chỉ là cảnh báo, không làm câu format_failed;
            // khi chép vào ngân hàng, lời giải còn lỗi nghiêm trọng được lưu NULL (TeacherReviewService).
            validateFieldLatex("explanation", input.explanation(), warnings, warnings);
        }

        return new FormatCheckOutcome(errors.isEmpty(), errors, warnings);
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

