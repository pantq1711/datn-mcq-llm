package com.datn.mcq.llm.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chữ cái đáp án đúng GIAO TRƯỚC cho từng chỗ mcq của đề theo ma trận (góp ý giáo viên 3/11: đề 14 Phần I có 7/12 đáp án A,
 * câu 7-12 đều A; model tự chọn thì thiên về A). Như {@link TfLabelPattern} với câu Đúng/Sai, hệ thống quyết định phân bố:
 * {@link #assign(int, long)} chia đều A/B/C/D cho các chỗ cần sinh của một đề, thứ tự ngẫu nhiên theo seed của đề, không quá
 * {@value #MAX_RUN} câu liền nhau cùng chữ; {@link #chooseBalanced(List, int, RandomGenerator)} chọn chữ cho một chỗ được sinh
 * lại hay Đổi câu để giữ cân bằng. Chữ được ghi vào prompt bằng {@link #promptLine(String)} và lưu trong
 * generation_job.prompt_note (đọc lại bằng {@link #fromNote(String)}).
 *
 * <p>Khác mẫu nhãn tf: đặt đáp án đúng ở phương án nào không đổi tính đúng của toán học (chỉ là vị trí), nên chữ giao là lệnh
 * ("phải là"). Chữ đúng của câu trả về khác chữ đã giao chỉ là cảnh báo ({@link #mismatchWarning}), chưa sinh lại.
 */
public final class McqAnswerPlan {

    public static final List<String> LETTERS = List.of("A", "B", "C", "D");
    /** Số câu liền nhau tối đa có cùng chữ đúng. */
    public static final int MAX_RUN = 2;

    private static final String LINE_PREFIX = "Phương án đúng phải là ";
    private static final Pattern IN_NOTE = Pattern.compile("Phương án đúng phải là ([A-D]):");
    private static final int SHUFFLE_ATTEMPTS = 200;

    private McqAnswerPlan() {}

    /**
     * Chữ đúng cho {@code count} chỗ theo thứ tự trong đề: mỗi chữ xuất hiện {@code count/4} hoặc {@code count/4 + 1} lần (12 chỗ
     * thì mỗi chữ đúng 3 lần), thứ tự xáo theo {@code seed}, không có {@value #MAX_RUN}+1 chỗ liền nhau cùng chữ. Cùng
     * {@code seed} và {@code count} cho cùng kết quả.
     */
    public static List<String> assign(int count, long seed) {
        return assignAround(Collections.nCopies(Math.max(count, 0), (String) null), seed);
    }

    /**
     * Như {@link #assign} khi đề có cả câu mcq lấy từ ngân hàng (chữ đúng đã có sẵn): {@code fixed} là chữ đúng của MỌI chỗ mcq
     * của đề theo thứ tự trong đề, phần tử null là chỗ cần sinh. Đếm chữ của các câu đã có trước, rồi chia các chữ còn lại cho
     * chỗ cần sinh sao cho toàn đề cân bằng nhất có thể (lần lượt cho chữ đang ít nhất; ví dụ 5 câu ngân hàng đều A + 7 chỗ
     * sinh thì chỗ sinh được 3, 2, 2 chữ B, C, D và không có A), xáo thứ tự theo {@code seed}, và không để chỗ sinh nào tạo ra
     * {@value #MAX_RUN}+1 câu liền nhau cùng chữ (chuỗi chỉ gồm câu ngân hàng thì không can thiệp được). Trả về danh sách đủ
     * chữ, cùng kích thước {@code fixed}; chữ đã có giữ nguyên.
     */
    public static List<String> assignAround(List<String> fixed, long seed) {
        int[] counts = new int[LETTERS.size()];
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < fixed.size(); i++) {
            String letter = fixed.get(i);
            if (letter == null) {
                free.add(i);
            } else if (LETTERS.indexOf(letter) >= 0) {
                counts[LETTERS.indexOf(letter)]++;
            }
        }
        if (free.isEmpty()) {
            return List.copyOf(fixed);
        }
        Random random = new Random(mix(seed));
        // Hòa số đếm thì chữ nào đứng trước theo thứ tự xáo này (ngẫu nhiên theo seed) được chia trước.
        List<String> order = new ArrayList<>(LETTERS);
        Collections.shuffle(order, random);
        List<String> pool = new ArrayList<>(free.size());
        for (int unit = 0; unit < free.size(); unit++) {
            String best = order.get(0);
            for (String letter : order) {
                if (counts[LETTERS.indexOf(letter)] < counts[LETTERS.indexOf(best)]) {
                    best = letter;
                }
            }
            counts[LETTERS.indexOf(best)]++;
            pool.add(best);
        }
        List<String> result = new ArrayList<>(fixed);
        for (int attempt = 0; attempt < SHUFFLE_ATTEMPTS; attempt++) {
            Collections.shuffle(pool, random);
            for (int k = 0; k < free.size(); k++) {
                result.set(free.get(k), pool.get(k));
            }
            if (!hasFreeRun(fixed, result)) {
                return List.copyOf(result);
            }
        }
        return List.copyOf(greedy(fixed, free, pool, random));
    }

    /**
     * Trộn bit của seed (hàm kết thúc của SplitMix64): id đề liên tiếp là seed liên tiếp, mà {@link Random} với seed gần nhau cho
     * những số đầu gần nhau, nên thứ tự các đề liền kề sẽ giống nhau nếu không trộn.
     */
    private static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Dự phòng khi xáo mãi vẫn có chuỗi dài: đặt từng chỗ bằng chữ còn nhiều nhất không tạo chuỗi dài. */
    private static List<String> greedy(List<String> fixed, List<Integer> free, List<String> pool, Random random) {
        List<String> remaining = new ArrayList<>(pool);
        List<String> cur = new ArrayList<>(fixed); // chỗ sinh chưa đặt vẫn là null
        for (int index : free) {
            String best = null;
            int bestCount = -1;
            List<String> letters = new ArrayList<>(LETTERS);
            Collections.shuffle(letters, random);
            for (String letter : letters) {
                int c = Collections.frequency(remaining, letter);
                if (c > 0 && c > bestCount && !makesLongRun(cur, index, letter)) {
                    best = letter;
                    bestCount = c;
                }
            }
            if (best == null) {
                best = remaining.get(0); // không còn chữ hợp lệ: chấp nhận chuỗi dài, vẫn giữ đúng số lượng
            }
            remaining.remove(best);
            cur.set(index, best);
        }
        return cur;
    }

    /** Có 3 chỗ liền nhau cùng chữ mà trong đó ít nhất một chỗ là chỗ sinh ({@code fixed} null) không. */
    private static boolean hasFreeRun(List<String> fixed, List<String> result) {
        for (int i = 2; i < result.size(); i++) {
            boolean same = result.get(i).equals(result.get(i - 1)) && result.get(i).equals(result.get(i - 2));
            if (same && (fixed.get(i) == null || fixed.get(i - 1) == null || fixed.get(i - 2) == null)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chữ cho chỗ {@code index} của {@code sequence} (chữ đúng của mọi chỗ mcq của đề theo thứ tự trong đề; phần tử null là chưa
     * biết, kể cả chính chỗ {@code index} đang được sinh lại). Chọn trong các chữ đang ít nhất (không tính chỗ {@code index}) chữ
     * không làm chuỗi liền nhau cùng chữ dài quá {@value #MAX_RUN}; hòa thì chọn ngẫu nhiên trong số đó. Hết chữ hợp lệ thì
     * bỏ điều kiện chuỗi (không xảy ra với 4 chữ).
     */
    public static String chooseBalanced(List<String> sequence, int index, RandomGenerator random) {
        int[] counts = new int[LETTERS.size()];
        for (int i = 0; i < sequence.size(); i++) {
            String letter = sequence.get(i);
            int at = i == index || letter == null ? -1 : LETTERS.indexOf(letter);
            if (at >= 0) {
                counts[at]++;
            }
        }
        List<String> allowed = new ArrayList<>();
        for (String letter : LETTERS) {
            if (!makesLongRun(sequence, index, letter)) {
                allowed.add(letter);
            }
        }
        if (allowed.isEmpty()) {
            allowed.addAll(LETTERS);
        }
        int min = Integer.MAX_VALUE;
        for (String letter : allowed) {
            min = Math.min(min, counts[LETTERS.indexOf(letter)]);
        }
        List<String> pool = new ArrayList<>();
        for (String letter : allowed) {
            if (counts[LETTERS.indexOf(letter)] == min) {
                pool.add(letter);
            }
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** Đặt {@code letter} ở {@code index} thì chuỗi liền nhau cùng chữ đi qua chỗ đó dài hơn {@value #MAX_RUN}. */
    private static boolean makesLongRun(List<String> sequence, int index, String letter) {
        int run = 1;
        for (int i = index - 1; i >= 0 && letter.equals(sequence.get(i)); i--) {
            run++;
        }
        for (int i = index + 1; i < sequence.size() && letter.equals(sequence.get(i)); i++) {
            run++;
        }
        return run > MAX_RUN;
    }

    /**
     * Dòng ghi chú đưa vào prompt, ví dụ "Phương án đúng phải là C: đặt đáp án đúng vào phương án C (correct_option = "C")...".
     * Kèm lệnh cấm lời giải nhắc chữ cái phương án (góp ý giáo viên 3/11: "Do đó chọn B" làm lời giải lệ thuộc thứ tự phương án).
     */
    public static String promptLine(String letter) {
        return LINE_PREFIX + letter + ": đặt đáp án đúng vào phương án " + letter + " (correct_option = \"" + letter
                + "\"), ba phương án còn lại là phương án nhiễu. Lời giải không được nhắc tới chữ cái của phương án (không viết "
                + "\"Do đó chọn B\", \"đáp án C\", \"phương án A đúng\"); chỉ nêu kết quả hoặc giá trị đúng.";
    }

    /**
     * Chữ đã giao ghi trong ghi chú của job; null khi không có. Chỉ đọc phần hệ thống viết, bỏ qua khối yêu cầu bổ sung của
     * giáo viên (giáo viên không giả mạo được chữ giao).
     */
    public static String fromNote(String note) {
        if (note == null) {
            return null;
        }
        Matcher m = IN_NOTE.matcher(MatrixPromptDraftV0.systemPart(note));
        return m.find() ? m.group(1) : null;
    }

    private static final String DEVIATION_PREFIX = "correct_option: khác chữ đã giao ";

    /**
     * Cảnh báo là "chữ đúng khác chữ đã giao". Chỉ để thống kê mức tuân thủ, không bật "cần xem kỹ" và chưa sinh lại (câu
     * vẫn đúng toán học, chỉ lệch vị trí).
     */
    public static boolean isDeviationWarning(String warning) {
        return warning != null && warning.startsWith(DEVIATION_PREFIX);
    }

    /**
     * Cảnh báo "correct_option: khác chữ đã giao C (nhận A)"; null khi khớp, khi không có chữ đã giao hoặc khi câu không có
     * chữ đúng hợp lệ (đã là lỗi format).
     */
    public static String mismatchWarning(String assigned, String actual) {
        if (assigned == null || actual == null || assigned.equals(actual)) {
            return null;
        }
        return DEVIATION_PREFIX + assigned + " (nhận " + actual + ")";
    }
}
