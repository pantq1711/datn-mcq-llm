package com.datn.mcq.exam.export;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Các dòng đầu đề của file Word (khuôn đề kiểm tra THCS, 4/11): bên phải bảng là tên kì (theo tên ma trận), năm học, môn
 * và khối, thời gian làm bài; ô "Mã đề 101" nằm ở dòng dưới. Mã hệ thống (MT…) chỉ nằm ở tên file tải về, không in ra
 * nội dung file.
 *
 * <p>Thời gian lấy từ tham số {@code durationMinutes} của API xuất ({@link #parseDuration}); không có thì in "……" để giáo
 * viên điền tay (ma trận chưa có trường thời gian). Mã đề hiển thị là số 3 chữ số: 101 cho đề gốc, nếu sau này có trộn mã
 * đề thì lần lượt 101, 102… ({@link #displayCode(int)}).
 *
 * @param periodName      tên kì, chưa in hoa (ví dụ "Giữa kì I"); null hoặc trống thì chỉ in "ĐỀ KIỂM TRA"
 * @param subjectName     tên môn; null hoặc trống thì in "……"
 * @param gradeLevel      khối; null thì không in
 * @param durationMinutes thời gian làm bài (phút); null thì in "……"
 */
public record ExamHeader(String periodName, String subjectName, Integer gradeLevel, Integer durationMinutes) {

    /** Mã đề hiển thị của đề gốc. */
    public static final String ORIGINAL_CODE = "101";

    /** Khoảng hợp lệ của thời gian làm bài (phút). */
    public static final int MIN_DURATION = 5;
    public static final int MAX_DURATION = 180;

    static final String BLANK = "……";
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    /** Tên kì theo tên ma trận; bỏ hậu tố "– Môn Khối" thừa (tên ma trận mẫu là "Giữa kì I – Toán 8"). */
    public static ExamHeader of(String periodName, String subjectName, Integer gradeLevel, Integer durationMinutes) {
        return new ExamHeader(withoutSubjectSuffix(periodName, subjectName, gradeLevel), subjectName, gradeLevel,
                durationMinutes);
    }

    /** Đầu đề khi không có ma trận hoặc thiếu thông tin: chỉ tên đề, môn để trống. */
    public static ExamHeader blank(String periodName) {
        return blank(periodName, null);
    }

    public static ExamHeader blank(String periodName, Integer durationMinutes) {
        return new ExamHeader(periodName == null ? null : periodName.strip(), null, null, durationMinutes);
    }

    /** Mã đề hiển thị của đề thứ {@code variantIndex} (từ 0): 101, 102… */
    public static String displayCode(int variantIndex) {
        return String.valueOf(Integer.parseInt(ORIGINAL_CODE) + variantIndex);
    }

    /**
     * Tham số {@code durationMinutes} của API xuất: số nguyên 5–180. Không có (null hoặc trống) là null.
     *
     * @throws IllegalArgumentException không phải số nguyên, hoặc ngoài khoảng (API trả 400)
     */
    public static Integer parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String message = "Tham số durationMinutes phải là số nguyên từ " + MIN_DURATION + " đến " + MAX_DURATION;
        int minutes;
        try {
            minutes = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
        if (minutes < MIN_DURATION || minutes > MAX_DURATION) {
            throw new IllegalArgumentException(message);
        }
        return minutes;
    }

    /** "ĐỀ KIỂM TRA GIỮA KÌ I". */
    public String titleLine() {
        String name = periodName == null ? "" : periodName.strip();
        return name.isEmpty() ? "ĐỀ KIỂM TRA" : "ĐỀ KIỂM TRA " + name.toUpperCase(VI);
    }

    /** Tháng bắt đầu năm học (tháng 8). */
    static final int SCHOOL_YEAR_START_MONTH = 8;

    /** "Thứ..... ngày........ tháng..... năm 2026": giáo viên điền tay thứ, ngày, tháng; năm lấy từ ngày xuất. */
    public String dateLine(LocalDate today) {
        return "Thứ..... ngày........ tháng..... năm " + today.getYear();
    }

    /** "NĂM HỌC 2026 - 2027": từ tháng 8 là năm nay – năm sau, trước tháng 8 là năm trước – năm nay. */
    public String yearLine(LocalDate today) {
        int start = today.getMonthValue() >= SCHOOL_YEAR_START_MONTH ? today.getYear() : today.getYear() - 1;
        return "NĂM HỌC " + start + " - " + (start + 1);
    }

    /** "Môn: Toán, Lớp: 9." (thiếu môn hoặc khối thì "……"). */
    public String subjectLine() {
        String subject = subjectName == null || subjectName.isBlank() ? BLANK : subjectName.strip();
        String grade = gradeLevel != null ? gradeLevel.toString() : BLANK;
        return "Môn: " + subject + ", Lớp: " + grade + ".";
    }

    /** "(Thời gian làm bài 90 phút không kể thời gian giao đề)". */
    public String durationLine() {
        return "(Thời gian làm bài " + (durationMinutes != null ? durationMinutes : BLANK)
                + " phút không kể thời gian giao đề)";
    }

    /** "101": mã đề trong ô của bảng "Mã đề | Điểm bài thi | Mã phách". */
    public String code() {
        return ORIGINAL_CODE;
    }

    /** "Mã đề 101" (chân trang). */
    public String codeLine() {
        return "Mã đề " + ORIGINAL_CODE;
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
