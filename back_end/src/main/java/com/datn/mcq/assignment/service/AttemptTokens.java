package com.datn.mcq.assignment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mã bài giao, token lượt làm và nhãn lớp (docs/design/lam-bai-truc-tuyen.md mục 1.1, 1.3). Hàm thuần trừ nguồn ngẫu nhiên.
 */
final class AttemptTokens {

    /** 32 ký tự, bỏ 0/O/1/I; 10 ký tự ≈ 50 bit. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    static final int CODE_LENGTH = 10;
    private static final Pattern CODE = Pattern.compile("[A-Z2-9]{10}");
    /** Token thô: 32 byte ngẫu nhiên, base64url không đệm (43 ký tự). */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CLASS_LABEL = Pattern.compile("[A-Z0-9/.\\-]{1,10}");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]+");

    private static final SecureRandom RANDOM = new SecureRandom();

    private AttemptTokens() {}

    static String newPublicCode() {
        char[] out = new char[CODE_LENGTH];
        for (int i = 0; i < out.length; i++) {
            out[i] = CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)];
        }
        return new String(out);
    }

    static boolean looksLikeCode(String code) {
        return code != null && CODE.matcher(code).matches();
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean looksLikeToken(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thiếu SHA-256", e);
        }
    }

    /**
     * Chuẩn hóa nhãn lớp: bỏ mọi khoảng trắng, viết hoa ({@code " 8a 1"} → {@code "8A1"}). Null khi không hợp lệ (rỗng, quá
     * 10 ký tự, có ký tự ngoài chữ Latin, số, {@code / . -}, tức không gõ được họ tên có dấu).
     */
    static String normalizeClassLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String s = WHITESPACE.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
        return CLASS_LABEL.matcher(s).matches() ? s : null;
    }
}
