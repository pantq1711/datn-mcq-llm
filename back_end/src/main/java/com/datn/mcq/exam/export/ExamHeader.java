package com.datn.mcq.exam.export;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Các dòng đầu đề của file Word (2/11): bên phải bảng là tên kì (theo tên ma trận), môn và khối, thời gian, mã đề hiển thị.
 * Mã hệ thống (MT…) chỉ nằm ở tên file tải về, không in ra nội dung file.
 *
 * <p>Thời gian lấy từ ma trận nếu có; ma trận hiện chưa có trường thời gian nên nơi gọi truyền null và dòng in
 * "…… phút" để giáo viên điền tay. Mã đề hiển thị là số 3 chữ số: 101 cho đề gốc, nếu sau này có trộn mã đề thì lần lượt
 * 101, 102… ({@link #displayCode(int)}).
 *
 * @param periodName      tên kì, chưa in hoa (ví dụ "Giữa kì I"); null hoặc trống thì chỉ in "ĐỀ KIỂM TRA"
 * @param subjectName     tên môn; null hoặc trống thì in "……"
 * @param gradeLevel      khối; null thì không in
 * @param durationMinutes thời gian làm bài (phút); null thì in "……"
 */
public record ExamHeader(String periodName, String subjectName, Integer gradeLevel, Integer durationMinutes) {

    /** Mã đề hiển thị của đề gốc. */
    public static final String ORIGINAL_CODE = "101";

    static final String BLANK = "……";
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    /** Tên kì theo tên ma trận; bỏ hậu tố "– Môn Khối" thừa (tên ma trận mẫu là "Giữa kì I – Toán 8"). */
    public static ExamHeader of(String periodName, String subjectName, Integer gradeLevel, Integer durationMinutes) {
        return new ExamHeader(withoutSubjectSuffix(periodName, subjectName, gradeLevel), subjectName, gradeLevel,
                durationMinutes);
    }

    /** Đầu đề khi không có ma trận hoặc thiếu thông tin: chỉ tên đề, môn và thời gian để trống. */
    public static ExamHeader blank(String periodName) {
        return new ExamHeader(periodName == null ? null : periodName.strip(), null, null, null);
    }

    /** Mã đề hiển thị của đề thứ {@code variantIndex} (từ 0): 101, 102… */
    public static String displayCode(int variantIndex) {
        return String.valueOf(Integer.parseInt(ORIGINAL_CODE) + variantIndex);
    }

    /** "ĐỀ KIỂM TRA GIỮA KÌ I". */
    public String titleLine() {
        String name = periodName == null ? "" : periodName.strip();
        return name.isEmpty() ? "ĐỀ KIỂM TRA" : "ĐỀ KIỂM TRA " + name.toUpperCase(VI);
    }

    /** "Môn: Toán 8 — Thời gian: …… phút". */
    public String subjectLine() {
        String subject = subjectName == null || subjectName.isBlank() ? BLANK
                : subjectName.strip() + (gradeLevel != null ? " " + gradeLevel : "");
        return "Môn: " + subject + " — Thời gian: " + (durationMinutes != null ? durationMinutes : BLANK) + " phút";
    }

    /** "Mã đề: 101". */
    public String codeLine() {
        return "Mã đề: " + ORIGINAL_CODE;
    }

    private static String withoutSubjectSuffix(String periodName, String subjectName, Integer gradeLevel) {
        if (periodName == null) {
            return null;
        }
        String name = periodName.strip();
        if (subjectName == null || subjectName.isBlank()) {
            return name;
        }
        String grade = gradeLevel != null ? "\\s*" + gradeLevel : "(?:\\s*\\d+)?";
        Pattern suffix = Pattern.compile("\\s*[–—-]\\s*" + Pattern.quote(subjectName.strip()) + grade + "\\s*$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        String stripped = suffix.matcher(name).replaceFirst("").strip();
        return stripped.isEmpty() ? name : stripped;
    }
}
