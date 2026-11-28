package com.datn.mcq.exam.export;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Các dòng đầu đề của file Word (khuôn đề kiểm tra THCS, 4/11): bên phải bảng là tên kì (từ 16/11 theo tên đề, NV-42),
 * năm học, môn và khối, thời gian làm bài; ô "Mã đề 101" nằm ở dòng dưới. Mã hệ thống (MT…) chỉ nằm ở tên file tải về,
 * không in ra nội dung file.
 *
 * <p>Thời gian lấy từ tham số {@code durationMinutes} của API xuất ({@link #parseDuration}); không có thì in "……" để giáo
 * viên điền tay (ma trận chưa có trường thời gian). Mã đề hiển thị là số 3 chữ số: mặc định 101 cho đề gốc; từ 16/11
 * (NV-43) giáo viên đổi được bằng tham số {@code examCode} 100–999 ({@link #parseExamCode}); nếu sau này có trộn mã đề
 * thì lần lượt 101, 102… ({@link #displayCode(int)}).
 *
 * @param periodName      tên kì, chưa in hoa (ví dụ "Giữa kì I"); null hoặc trống thì chỉ in "ĐỀ KIỂM TRA"
 * @param subjectName     tên môn; null hoặc trống thì in "……"
 * @param gradeLevel      khối; null thì không in
 * @param durationMinutes thời gian làm bài (phút); null thì in "……"
 * @param examCode        mã đề in ở ô mã đề, tiêu đề trang đáp án và chân trang; null hoặc trống là {@link #ORIGINAL_CODE}
 */
public record ExamHeader(String periodName, String subjectName, Integer gradeLevel, Integer durationMinutes,
                         String examCode) {

    /** Mã đề hiển thị của đề gốc. */
    public static final String ORIGINAL_CODE = "101";

    /** Khoảng hợp lệ của mã đề (tham số {@code examCode}, số 3 chữ số). */
    public static final int MIN_EXAM_CODE = 100;
    public static final int MAX_EXAM_CODE = 999;

    /** Hậu tố tên của ma trận nhân bản (MatrixService.duplicate), có thể lặp khi nhân bản bản sao. */
    private static final Pattern COPY_SUFFIX = Pattern.compile("(?:\\s*\\(bản sao\\))+\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ASCII_DIGITS = Pattern.compile("[0-9]+");
    /**
     * Đuôi ngày ở cuối tên đề (đợt 3, 28/11): dấu nối (–, — hoặc -) rồi DD/MM hoặc DD/MM/YYYY, như tên mặc định
     * "&lt;tên ma trận&gt; – 28/11" của giao diện. Không có dấu nối ("Đề 28/11") thì không phải đuôi ngày.
     */
    private static final Pattern DATE_SUFFIX = Pattern.compile("\\s*[–—-]\\s*\\d{1,2}/\\d{1,2}(?:/\\d{4})?\\s*$");
    /**
     * Tiền tố "Kiểm tra" / "Đề kiểm tra" của tên đề (đã NFC), kèm dấu nối theo sau: bỏ đi để dòng tên kì không lặp chữ "KIỂM
     * TRA". Chỉ khớp cả từ ("Kiểm tranh…" không phải tiền tố).
     */
    private static final Pattern TEST_PREFIX = Pattern.compile("^(?:đề\\s+)?kiểm\\s+tra(?![\\p{L}\\p{N}])[\\s:.,–—-]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public ExamHeader {
        examCode = examCode == null || examCode.isBlank() ? ORIGINAL_CODE : examCode.strip();
    }

    /** Khoảng hợp lệ của thời gian làm bài (phút). */
    public static final int MIN_DURATION = 5;
    public static final int MAX_DURATION = 180;

    static final String BLANK = "……";
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    /**
     * Tên kì theo tên đề (16/11; trước đó là tên ma trận); bỏ hậu tố "(bản sao)", rồi MỘT đuôi ngày "– DD/MM" ở cuối tên
     * (đợt 3, 28/11), rồi hậu tố "– Môn Khối" thừa (tên ma trận mẫu, cũng là tên đề mặc định, là "Giữa kì I – Toán 8"; tên
     * mặc định từ 28/11 là "Giữa kì I – Toán 8 – 28/11"). Mã đề mặc định 101 ({@link #withExamCode}).
     */
    public static ExamHeader of(String periodName, String subjectName, Integer gradeLevel, Integer durationMinutes) {
        return new ExamHeader(withoutSubjectSuffix(withoutDateSuffix(withoutCopySuffix(periodName)), subjectName,
                gradeLevel), subjectName, gradeLevel, durationMinutes, ORIGINAL_CODE);
    }

    /** Đầu đề khi không có ma trận hoặc thiếu thông tin: chỉ tên đề (bỏ hậu tố "(bản sao)"), môn để trống. */
    public static ExamHeader blank(String periodName) {
        return blank(periodName, null);
    }

    public static ExamHeader blank(String periodName, Integer durationMinutes) {
        String name = withoutCopySuffix(periodName);
        return new ExamHeader(name == null ? null : name.strip(), null, null, durationMinutes, ORIGINAL_CODE);
    }

    /** Cùng đầu đề với mã đề {@code code} (null hoặc trống là {@link #ORIGINAL_CODE}). */
    public ExamHeader withExamCode(String code) {
        return new ExamHeader(periodName, subjectName, gradeLevel, durationMinutes, code);
    }

    /** Mã đề hiển thị của đề thứ {@code variantIndex} (từ 0): 101, 102… */
    public static String displayCode(int variantIndex) {
        return String.valueOf(Integer.parseInt(ORIGINAL_CODE) + variantIndex);
    }

    /**
     * Tham số {@code durationMinutes} của API xuất: số nguyên 5–180 (chỉ chữ số ASCII, 16/11; không nhận dấu "+" hay chữ số
     * Unicode khác như "٤٥"). Không có (null hoặc trống) là null.
     *
     * @throws IllegalArgumentException không phải số nguyên, hoặc ngoài khoảng (API trả 400)
     */
    public static Integer parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.strip();
        String message = "Tham số durationMinutes phải là số nguyên từ " + MIN_DURATION + " đến " + MAX_DURATION;
        if (!ASCII_DIGITS.matcher(digits).matches() || digits.length() > 4) {
            throw new IllegalArgumentException(message);
        }
        int minutes = Integer.parseInt(digits);
        if (minutes < MIN_DURATION || minutes > MAX_DURATION) {
            throw new IllegalArgumentException(message);
        }
        return minutes;
    }

    /**
     * Tham số {@code examCode} của API xuất (16/11, NV-43): số nguyên 100–999 (chữ số ASCII). Không có (null hoặc trống) là
     * {@link #ORIGINAL_CODE}.
     *
     * @throws IllegalArgumentException không phải số nguyên, hoặc ngoài khoảng (API trả 400)
     */
    public static String parseExamCode(String value) {
        if (value == null || value.isBlank()) {
            return ORIGINAL_CODE;
        }
        String digits = value.strip();
        String message = "Tham số examCode phải là số nguyên từ " + MIN_EXAM_CODE + " đến " + MAX_EXAM_CODE;
        if (!ASCII_DIGITS.matcher(digits).matches() || digits.length() > 4) {
            throw new IllegalArgumentException(message);
        }
        int code = Integer.parseInt(digits);
        if (code < MIN_EXAM_CODE || code > MAX_EXAM_CODE) {
            throw new IllegalArgumentException(message);
        }
        return Integer.toString(code);
    }

    /**
     * "ĐỀ KIỂM TRA GIỮA KÌ I". Tên đã mở đầu bằng "Kiểm tra" hoặc "Đề kiểm tra" (không phân biệt hoa thường, 16/11) thì bỏ tiền
     * tố đó để không lặp chữ: "Kiểm tra giữa kì I" cũng in "ĐỀ KIỂM TRA GIỮA KÌ I". Tên chuẩn hóa NFC (tên gõ bằng dấu tổ hợp
     * vẫn nhận ra tiền tố).
     */
    public String titleLine() {
        String name = periodName == null ? "" : Normalizer.normalize(periodName, Normalizer.Form.NFC).strip();
        name = TEST_PREFIX.matcher(name).replaceFirst("").strip();
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
        return examCode;
    }

    /** "Mã đề 101" (chân trang). */
    public String codeLine() {
        return "Mã đề " + examCode;
    }

    /** Bỏ hậu tố "(bản sao)" (kể cả lặp) của tên; bỏ hết thì giữ nguyên tên. */
    private static String withoutCopySuffix(String name) {
        if (name == null) {
            return null;
        }
        String stripped = COPY_SUFFIX.matcher(name).replaceFirst("").strip();
        return stripped.isEmpty() ? name : stripped;
    }

    /** Bỏ một đuôi ngày ({@link #DATE_SUFFIX}) ở cuối tên; tên chỉ có ngày (bỏ hết) thì giữ nguyên tên. */
    private static String withoutDateSuffix(String name) {
        if (name == null) {
            return null;
        }
        String stripped = DATE_SUFFIX.matcher(name).replaceFirst("").strip();
        return stripped.isEmpty() ? name : stripped;
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
