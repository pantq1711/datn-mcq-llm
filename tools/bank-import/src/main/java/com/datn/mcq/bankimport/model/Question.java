package com.datn.mcq.bankimport.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Một câu hỏi đã chuẩn hóa. Trường để mutable và public cho gọn: đây là mô hình dữ liệu của công cụ offline, JSON được ghi
 * trực tiếp từ đây (tên trường snake_case).
 *
 * <p>Trong {@code stem}, {@code options[].text}, {@code statements[].text}, {@code answer.text} và {@code solution}:
 * công thức chuyển được nằm inline {@code $...$}; công thức không chuyển được là token {@code ⟦fN⟧} (bản ghi trong
 * {@link #formulas}); ảnh là token {@code ⟦imgN⟧} (bản ghi trong {@link #images}).
 */
@JsonInclude(Include.NON_NULL)
public class Question {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public String id;
    public String type;               // mcq | tf | sa
    public String part;               // I | II | III
    public int ordinal;               // thứ tự trong cùng dạng, bắt đầu từ 1
    public String locator;            // ví dụ "Phần I, câu thứ 7" (để tìm lại trong file Word)
    @JsonInclude(Include.ALWAYS)
    public String levelRaw;           // NB | TH | VD | VDC | null
    @JsonInclude(Include.ALWAYS)
    public String level;              // easy | medium | hard | null
    public String stem;
    public List<List<List<String>>> tables;      // bảng dữ liệu nằm trong đề (hàng → ô)
    public List<Option> options;                 // mcq
    public List<Statement> statements;           // tf
    public Answer answer;
    @JsonInclude(Include.ALWAYS)
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

    public Set<String> flagSet() {
        return new LinkedHashSet<>(flags);
    }

    @Override
    public String toString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "Question[" + id + "]";
        }
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
        @JsonInclude(Include.ALWAYS)
        public Boolean truth;
        @JsonInclude(Include.ALWAYS)
        public String evidence;

        public Statement(String key, String text) {
            this.key = key;
            this.text = text;
        }
    }

    public static final class Answer {
        @JsonInclude(Include.ALWAYS)
        public String value;          // mcq: A-D
        public String text;           // sa: đáp số dạng chữ
        public String numeric;        // sa: đáp số số thực dạng "7.5" khi là một số thuần
        @JsonInclude(Include.ALWAYS)
        public String evidence;
    }

    public static final class Formula {
        public String id;
        public String status;         // ok | partial | unsupported
        public String latex;
        public String reason;
        public String sourceRef;
        @com.fasterxml.jackson.annotation.JsonIgnore
        public String previewRef;     // ảnh xem trước MathType (WMF/EMF) cho formulas.html
    }

    public static final class ImageRef {
        public String id;
        public String kind;           // figure | formula_image | ole_drawing
        public String role;           // stem | option_A | statement_a | answer | solution
        public String file;           // đường dẫn tương đối trong thư mục output (tệp gốc: png, emf, wmf)
        public String filePng;        // bản PNG khi chạy --convert-images trên Windows
        public String sourceExt;
        public Double widthCm;
        public Double heightCm;
        public String sourceRef;
    }
}
