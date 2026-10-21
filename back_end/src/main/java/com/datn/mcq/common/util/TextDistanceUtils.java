package com.datn.mcq.common.util;

/**
 * Chuẩn hóa văn bản (trim + đưa \r\n về \n) và tính khoảng cách chỉnh sửa Levenshtein
 * theo quy tắc tại docs/contract/enums.md mục 3:
 * "edited = true chỉ khi văn bản sau khi trim và chuẩn hóa xuống dòng thật sự khác bản gốc (edit_distance > 0)."
 */
public final class TextDistanceUtils {

    private TextDistanceUtils() {}

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    public static int levenshteinDistance(String rawA, String rawB) {
        String a = normalize(rawA);
        String b = normalize(rawB);
        if (a.equals(b)) {
            return 0;
        }
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /**
     * Tổng Levenshtein trên 5 trường giáo viên thấy và sửa được: question_text và 4 phương án
     * (comment edit_distance trong V1, quyết định 2 ngày 22/10). Không tính lời giải.
     */
    public static int totalQuestionEditDistance(
            String origQ, String origA, String origB, String origC, String origD,
            String editQ, String editA, String editB, String editC, String editD) {
        return levenshteinDistance(origQ, editQ)
                + levenshteinDistance(origA, editA)
                + levenshteinDistance(origB, editB)
                + levenshteinDistance(origC, editC)
                + levenshteinDistance(origD, editD);
    }
}

