package com.datn.mcq.bankimport.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Một câu hỏi đã tách từ file Word (chuyển từ tools/bank-import, W2-1). Trường để mutable và public cho gọn: đây là mô hình
 * trung gian của bộ phân tích, chỉ dùng trong bộ nhớ.
 *
 * <p>Trong {@code stem}, {@code options[].text}, {@code statements[].text}, {@code answer.text} và {@code solution}:
 * công thức chuyển được nằm inline {@code $...$}; công thức không chuyển được là token {@code ⟦fN⟧} (bản ghi trong
 * {@link #formulas}); ảnh là token {@code ⟦imgN⟧} (bản ghi trong {@link #images}).
 */
public class Question {

    public String id;
    public String type;               // mcq | tf | sa
    public String part;               // I | II | III
    public int ordinal;               // thứ tự trong cùng dạng, bắt đầu từ 1
    public String locator;            // ví dụ "Phần I, câu thứ 7" (để tìm lại trong file Word)
    public String levelRaw;           // NB | TH | VD | VDC | null
    public String level;              // easy | medium | hard | null
    public String stem;
    public List<List<List<String>>> tables;      // bảng dữ liệu nằm trong đề (hàng → ô)
    public List<Option> options;                 // mcq
    public List<Statement> statements;           // tf
    public Answer answer;
    public String solution;
    public List<Formula> formulas = new ArrayList<>();
    public List<ImageRef> images = new ArrayList<>();
    public boolean hasImage;
    public List<String> flags = new ArrayList<>();
    public String contentHash;

    public void addFlag(String flag) {
        if (!flags.contains(flag)) {
            flags.add(flag);
        }
    }

    /** Không in nội dung câu (tránh lọt nội dung bộ đề vào log). */
    @Override
    public String toString() {
        return "Question[" + id + "]";
    }

    public static final class Option {
        public String key;
        public String text;

        public Option(String key, String text) {
            this.key = key;
            this.text = text;
        }
    }

    public static final class Statement {
        public String key;
        public String text;
        public Boolean truth;
        public String evidence;

        public Statement(String key, String text) {
            this.key = key;
            this.text = text;
        }
    }

    public static final class Answer {
        public String value;          // mcq: A-D
        public String text;           // sa: đáp số dạng chữ
        public String numeric;        // sa: đáp số số thực dạng "7.5" khi là một số thuần
        public String evidence;
    }

    public static final class Formula {
        public String id;
        public String status;         // ok | partial | unsupported
        public String latex;
        public String reason;
        public String sourceRef;
    }

    public static final class ImageRef {
        public String id;
        public String kind;           // figure | formula_image | ole_drawing
        public String role;           // stem | option_A | statement_a | answer | solution
        public String sourceExt;
        public Double widthCm;
        public Double heightCm;
        public String sourceRef;
    }
}
