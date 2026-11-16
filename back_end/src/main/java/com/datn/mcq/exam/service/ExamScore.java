package com.datn.mcq.exam.service;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tổng điểm phần trắc nghiệm của một đề (NV-41, quyết định 16/11 mục 5). Cùng cách tính với tiêu đề từng phần trong file
 * Word ({@code ExamMarkdownBuilder}): điểm tối đa một câu × số chỗ của phần, kể cả chỗ chưa có câu. Phần I là trắc nghiệm,
 * phần II đúng/sai (điểm khi đúng cả 4 ý), phần III trả lời ngắn; phần lạ không tính. Không đổi thang điểm.
 */
public final class ExamScore {

    private ExamScore() {}

    /** Điểm tối đa một câu của phần {@code part}; null với phần lạ. */
    public static BigDecimal perQuestion(String part, ExamScoringProperties scoring) {
        return switch (part) {
            case "I" -> scoring.mcqPerQuestion();
            case "II" -> scoring.tfScore(4);
            case "III" -> scoring.saPerQuestion();
            default -> null;
        };
    }

    public static BigDecimal total(List<PartDto> parts, ExamScoringProperties scoring) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PartDto part : parts) {
            BigDecimal each = perQuestion(part.part(), scoring);
            if (each != null) {
                sum = sum.add(each.multiply(BigDecimal.valueOf(part.items().size())));
            }
        }
        return sum;
    }
}
