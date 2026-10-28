package com.datn.mcq.material.extract;

import java.text.Normalizer;

/**
 * Chuẩn hóa văn bản trích từ tài liệu trước khi lưu và đưa vào prompt: Unicode NFC (tiếng Việt viết tổ hợp hay dựng sẵn
 * đều về một dạng), xuống dòng \n, bỏ ký tự điều khiển, ký tự định dạng ẩn (bidi, độ rộng bằng không, "tag") và ký tự
 * vùng riêng; đổi NBSP thành dấu cách; gọn dòng trống. Không đụng tới ký tự LaTeX ($, \, ^, _, {, }).
 */
final class TextNormalizer {

    private TextNormalizer() {}

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\t') {
                out.appendCodePoint(cp);
            } else if (cp == 0xA0) {
                out.append(' ');
            } else {
                int type = Character.getType(cp);
                if (type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE
                        && type != Character.PRIVATE_USE) {
                    out.appendCodePoint(cp);
                }
            }
        });
        return out.toString().replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n").strip();
    }

    /** Số ký tự (điểm mã Unicode), cùng cách đếm với char_length của PostgreSQL. */
    static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    /**
     * Chuẩn hóa rồi kiểm giới hạn: rỗng thì NO_EXTRACTABLE_TEXT, dài quá thì TEXT_TOO_LONG (không cắt âm thầm).
     */
    static String checked(MaterialProperties props, String raw, String noTextMessage) {
        String text = normalize(raw);
        if (text.isBlank()) {
            throw MaterialErrors.noText(noTextMessage);
        }
        if (length(text) > props.getMaxChars()) {
            throw MaterialErrors.tooLong(props.getMaxChars());
        }
        return text;
    }
}
