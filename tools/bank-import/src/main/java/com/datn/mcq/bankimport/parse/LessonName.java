package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.model.Source;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tên file → nguồn ("KHỐI 8 - CHƯƠNG 3 - BÀI 1 - TÊN BÀI - GV TÊN GIÁO VIÊN pb.docx"). Phần "GV …" và hậu tố
 * "pb" bị cắt và không bao giờ được lưu; chỉ trả riêng {@link Parsed#teacher()} để công cụ tự kiểm tra rò rỉ.
 */
public final class LessonName {

    private static final Pattern NAME = Pattern.compile(
            "^KH[ỐO]I\\s*(\\d+)\\s*-\\s*CH[ƯU][ƠO]NG\\s*(\\d+)\\s*-\\s*B[ÀA]I\\s*(\\d+)\\s*-\\s*(.+?)\\s*(?:-\\s*GV\\b\\s*(.*?))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TRAILING_PB = Pattern.compile("\\s+pb\\s*$", Pattern.CASE_INSENSITIVE);

    /** {@code teacher}: chỉ dùng để kiểm tra rò rỉ, tuyệt đối không ghi ra output. */
    public record Parsed(Source source, String teacher) {}

    public static Parsed parse(String fileName, String sha256Prefix) {
        String base = Normalizer.normalize(fileName, Normalizer.Form.NFC);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        Matcher m = NAME.matcher(base.strip());
        if (!m.matches()) {
            return new Parsed(new Source("doc-" + sha256Prefix.substring(0, 8), null, null, null, null, sha256Prefix), null);
        }
        int grade = Integer.parseInt(m.group(1));
        int chapter = Integer.parseInt(m.group(2));
        int lesson = Integer.parseInt(m.group(3));
        String title = TRAILING_PB.matcher(m.group(4)).replaceAll("").strip();
        String teacher = m.group(5) == null ? null : TRAILING_PB.matcher(m.group(5)).replaceAll("").strip();
        return new Parsed(new Source("k" + grade + "c" + chapter + "b" + lesson, grade, chapter, lesson, title, sha256Prefix),
                teacher == null || teacher.isEmpty() ? null : teacher);
    }

    private LessonName() {}
}
