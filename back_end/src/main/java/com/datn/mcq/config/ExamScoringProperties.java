package com.datn.mcq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bảng điểm đề Toán THCS (V4, theo đề và ma trận thật). Chỉ là cấu hình cho phần tạo đề sau này,
 * luồng sinh câu không dùng. Dùng BigDecimal để 0,1 và 0,25 được giữ chính xác.
 *
 * @param mcqPerQuestion   điểm mỗi câu trắc nghiệm 4 phương án (12 câu = 3 điểm)
 * @param saPerQuestion    điểm mỗi câu trả lời ngắn
 * @param tfByCorrectCount điểm của một câu Đúng/Sai theo số ý trả lời đúng; phần tử thứ k ứng với k ý đúng (k = 0..4)
 */
@ConfigurationProperties(prefix = "app.exam.scoring")
public record ExamScoringProperties(
        @DefaultValue("0.25") BigDecimal mcqPerQuestion,
        @DefaultValue("0.5") BigDecimal saPerQuestion,
        @DefaultValue({"0", "0.1", "0.25", "0.5", "1.0"}) List<BigDecimal> tfByCorrectCount
) {

    public ExamScoringProperties {
        if (mcqPerQuestion == null || mcqPerQuestion.signum() < 0
                || saPerQuestion == null || saPerQuestion.signum() < 0) {
            throw new IllegalArgumentException("Điểm mỗi câu MCQ/SA phải là số không âm");
        }
        if (tfByCorrectCount == null || tfByCorrectCount.size() != 5) {
            throw new IllegalArgumentException("tf-by-correct-count phải có đúng 5 giá trị (0..4 ý đúng)");
        }
        for (int i = 0; i < tfByCorrectCount.size(); i++) {
            BigDecimal v = tfByCorrectCount.get(i);
            if (v == null || v.signum() < 0 || (i > 0 && v.compareTo(tfByCorrectCount.get(i - 1)) < 0)) {
                throw new IllegalArgumentException("tf-by-correct-count phải không âm và không giảm theo số ý đúng");
            }
        }
        tfByCorrectCount = List.copyOf(tfByCorrectCount);
    }

    /** Điểm một câu Đúng/Sai khi có {@code correctCount} ý đúng (0..4). */
    public BigDecimal tfScore(int correctCount) {
        if (correctCount < 0 || correctCount > 4) {
            throw new IllegalArgumentException("Số ý đúng phải từ 0 đến 4, nhận " + correctCount);
        }
        return tfByCorrectCount.get(correctCount);
    }
}
