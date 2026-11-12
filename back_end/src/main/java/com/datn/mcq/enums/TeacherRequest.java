package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chip yêu cầu của giáo viên (12/11, PR B, V28): mỗi mã ứng với MỘT đoạn prompt cố định, dùng ở prompt sinh câu và prompt
 * Solver ({@code request_check}). Lưu ở các cột {@code request_codes TEXT[]} bằng {@link #getDbValue()}. Sửa chữ thì sửa ở
 * đây và ở test, không viết rải rác (docs/design/kiem-soat-do-kho.md mục 4.1).
 */
public enum TeacherRequest implements DbEnum {
    REAL_CONTEXT("real_context", "Bối cảnh thực tế",
            "Câu hỏi đặt trong một bối cảnh thực tế quen thuộc với học sinh (đo đạc, mua bán, chuyển động, diện tích…); "
                    + "số liệu hợp lý với bối cảnh.", false),
    MULTI_STEP("multi_step", "Nhiều bước biến đổi",
            "Lời giải cần ít nhất 2 bước biến đổi có ý nghĩa; không giải được bằng cách thay số trực tiếp vào một công "
                    + "thức.", false),
    COMMON_TRAP("common_trap", "Có bẫy lỗi hay gặp",
            "Có ít nhất một bẫy là lỗi sai học sinh hay mắc (sai dấu khi phá ngoặc, nhầm hằng đẳng thức…); với trắc "
                    + "nghiệm, ít nhất một phương án nhiễu ứng với lỗi đó.", false),
    EXTREMUM("extremum", "Tìm giá trị lớn nhất/nhỏ nhất",
            "Câu hỏi yêu cầu tìm giá trị lớn nhất hoặc nhỏ nhất, giải được bằng kiến thức trong lớp.", false),
    GEOMETRY_LINK("geometry_link", "Liên hệ hình học",
            "Câu hỏi gắn với một hình (độ dài, chu vi, diện tích, góc) và dùng kiến thức của bài để giải.", false),
    TF_SHARED_CONTEXT("tf_shared_context", "Đúng/Sai cùng một tình huống",
            "Bốn ý cùng xoay quanh một tình huống của đề dẫn; ý sau dùng kết quả ý trước.", true);

    private final String dbValue;
    private final String label;
    private final String promptLine;
    private final boolean tfOnly;

    TeacherRequest(String dbValue, String label, String promptLine, boolean tfOnly) {
        this.dbValue = dbValue;
        this.label = label;
        this.promptLine = promptLine;
        this.tfOnly = tfOnly;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /** Nhãn hiện cho giáo viên (chip). */
    public String label() {
        return label;
    }

    /** Đoạn prompt cố định của mã. */
    public String promptLine() {
        return promptLine;
    }

    /** Mã có áp dụng cho dạng câu {@code type} không (chỉ {@link #TF_SHARED_CONTEXT} riêng cho Đúng/Sai). */
    public boolean appliesTo(QuestionType type) {
        return !tfOnly || type == QuestionType.TF;
    }

    /** Mã client gửi: không phân biệt hoa thường, bỏ khoảng trắng hai đầu; mã lạ hoặc trống thì null. */
    public static TeacherRequest parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip().toLowerCase(Locale.ROOT);
        for (TeacherRequest r : values()) {
            if (r.dbValue.equals(v)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Các mã đã lưu (giá trị DB) áp dụng cho dạng câu {@code type}, không trùng, theo thứ tự khai báo của enum; mã lạ bị bỏ
     * qua. Null hoặc rỗng thì danh sách rỗng.
     */
    public static List<String> applicable(Collection<String> codes, QuestionType type) {
        Set<TeacherRequest> set = EnumSet.noneOf(TeacherRequest.class);
        if (codes != null) {
            for (String c : codes) {
                TeacherRequest r = parseOrNull(c);
                if (r != null && (type == null || r.appliesTo(type))) {
                    set.add(r);
                }
            }
        }
        List<String> out = new ArrayList<>();
        set.forEach(r -> out.add(r.dbValue));
        return out;
    }

    /** Tên dạng câu trong thông báo cho giáo viên. */
    public static String typeName(QuestionType type) {
        return switch (type) {
            case MCQ -> "Trắc nghiệm";
            case TF -> "Đúng/Sai";
            case SA -> "Trả lời ngắn";
        };
    }
}
