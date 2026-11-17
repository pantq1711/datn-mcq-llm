package com.datn.mcq.assignment.service;

import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bảng điểm của một bài giao: ảnh chụp {@code app.exam.scoring} lúc giao, lưu ở {@code exam_assignment.scoring} dạng
 * {@code {"mcq": 0.25, "sa": 0.5, "tf": [0, 0.1, 0.25, 0.5, 1.0]}}. Đổi cấu hình sau này không đổi điểm của bài đã giao.
 *
 * @param mcq điểm một câu trắc nghiệm đúng
 * @param sa  điểm một câu trả lời ngắn đúng
 * @param tf  điểm một câu Đúng/Sai theo số ý đúng; phần tử thứ k ứng với k ý đúng (k = 0..4)
 */
public record Scoring(BigDecimal mcq, BigDecimal sa, List<BigDecimal> tf) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public Scoring {
        if (mcq == null || sa == null || tf == null || tf.size() != 5) {
            throw new IllegalArgumentException("Bảng điểm bài giao không hợp lệ");
        }
        tf = List.copyOf(tf);
    }

    public static Scoring of(ExamScoringProperties properties) {
        return new Scoring(properties.mcqPerQuestion(), properties.saPerQuestion(), properties.tfByCorrectCount());
    }

    public static Scoring fromJson(String json) {
        try {
            return JSON.readValue(json, Scoring.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("exam_assignment.scoring không đọc được", e);
        }
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không ghi được bảng điểm bài giao", e);
        }
    }

    /** Điểm một câu Đúng/Sai khi có {@code correctCount} ý đúng (0..4). */
    public BigDecimal tfScore(int correctCount) {
        return tf.get(correctCount);
    }

    /** Điểm tối đa của một câu theo dạng. */
    public BigDecimal max(QuestionType type) {
        return switch (type) {
            case MCQ -> mcq;
            case SA -> sa;
            case TF -> tf.get(4);
        };
    }
}
