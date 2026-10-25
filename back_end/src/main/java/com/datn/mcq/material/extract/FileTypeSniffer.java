package com.datn.mcq.material.extract;

import java.nio.charset.StandardCharsets;

/**
 * Nhận dạng loại file CHỈ từ nội dung (magic bytes): đuôi file và Content-Type do client gửi không được dùng. Chỉ phân
 * ra ba họ: PDF, ZIP (ứng viên docx, kiểm tiếp bên trong) và văn bản. File nhị phân khác, file Word cũ (.doc), RTF,
 * văn bản UTF-16 hoặc có byte NUL đều bị từ chối.
 */
final class FileTypeSniffer {

    enum Kind { PDF, ZIP, TEXT }

    static final String UNSUPPORTED_MESSAGE = "Chỉ hỗ trợ file .docx, .pdf và .txt (UTF-8).";

    private static final byte[] PDF = ascii("%PDF-");
    private static final byte[] ZIP_LOCAL = {'P', 'K', 3, 4};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1};
    private static final byte[][] OTHER_BINARY = {
            {(byte) 0x89, 'P', 'N', 'G'},
            {'M', 'Z'},
            {0x7F, 'E', 'L', 'F'},
            ascii("GIF8"),
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            ascii("Rar!"),
            {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C},
            {0x1F, (byte) 0x8B},
            ascii("{\\rtf"),
            // zip rỗng hoặc zip nhiều phần: không phải docx hợp lệ
            {'P', 'K', 5, 6},
            {'P', 'K', 7, 8},
    };

    private FileTypeSniffer() {}

    static Kind sniff(byte[] b) {
        if (b == null || b.length == 0) {
            throw MaterialErrors.empty();
        }
        if (startsWith(b, PDF)) {
            return Kind.PDF;
        }
        if (startsWith(b, ZIP_LOCAL)) {
            return Kind.ZIP;
        }
        if (startsWith(b, OLE)) {
            throw MaterialErrors.unsupported("Không hỗ trợ file Word cũ (.doc). Hãy mở bằng Word, lưu thành .docx rồi "
                    + "tải lại.");
        }
        for (byte[] magic : OTHER_BINARY) {
            if (startsWith(b, magic)) {
                throw MaterialErrors.unsupported(UNSUPPORTED_MESSAGE);
            }
        }
        if (b.length >= 2 && ((b[0] == (byte) 0xFF && b[1] == (byte) 0xFE) || (b[0] == (byte) 0xFE
                && b[1] == (byte) 0xFF))) {
            throw MaterialErrors.unsupported("File văn bản phải lưu dạng UTF-8 (không phải UTF-16).");
        }
        int controls = 0;
        for (byte raw : b) {
            int x = raw & 0xFF; // byte của Java có dấu: bỏ dấu để byte >= 0x80 (UTF-8 tiếng Việt) không bị tính là điều khiển
            if (x == 0) {
                throw MaterialErrors.unsupported(UNSUPPORTED_MESSAGE);
            }
            if ((x < 0x20 && x != '\t' && x != '\n' && x != '\r') || x == 0x7F) {
                controls++;
            }
        }
        if (controls * 100L > b.length) {
            throw MaterialErrors.unsupported(UNSUPPORTED_MESSAGE);
        }
        return Kind.TEXT;
    }

    private static boolean startsWith(byte[] b, byte[] prefix) {
        if (b.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (b[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
