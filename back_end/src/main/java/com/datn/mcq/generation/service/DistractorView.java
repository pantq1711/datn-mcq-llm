package com.datn.mcq.generation.service;

import com.datn.mcq.common.util.LetterJson;
import com.datn.mcq.enums.DistractorCheck;
import com.datn.mcq.generation.entity.GeneratedQuestion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Lỗi sai của phương án nhiễu ĐANG HIỂN THỊ của một câu mcq sinh (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md mục 6,
 * H-4). Hàm thuần, không đọc DB. Một nguồn cho xem đề và Giữ câu (Giữ chép đúng trạng thái này sang ngân hàng).
 * <ul>
 *   <li>Chỉ phương án SAI hiện tại có chữ lỗi (lỗi ghi ở phương án nay là đáp án đúng không hiển thị).</li>
 *   <li>Câu có dòng answer_check: kết quả đã lưu; phương án có lỗi mà không có khóa là {@code unchecked}.</li>
 *   <li>Bản giáo viên sửa CHƯA có dòng answer_check (job đang chờ, hoặc thất bại): kết quả TẠM suy từ bản ngay trước — đổi câu
 *       dẫn hoặc đáp án đúng thì mọi lỗi {@code unchecked}; chỉ đổi phương án x thì lỗi của x {@code unchecked}, lỗi khác giữ
 *       kết quả của bản trước. Job xong thì kết quả mới thay hẳn.</li>
 *   <li>Câu gốc chưa có dòng answer_check (Solver chưa chạy hoặc lỗi): mọi lỗi {@code unchecked}.</li>
 * </ul>
 */
public final class DistractorView {

    /** Một lỗi đang hiển thị. */
    public record Item(String text, DistractorCheck check) {}

    /** Giới hạn độ sâu chuỗi bản sửa khi suy kết quả tạm (phòng dữ liệu vòng). */
    static final int MAX_DEPTH = 50;

    private DistractorView() {}

    /**
     * @param q       câu đang hiển thị
     * @param byId    tra bản trước theo id (null khi không có)
     * @param stored  kết quả đã lưu theo id câu: map (có thể rỗng) khi câu CÓ dòng answer_check, null khi không có dòng
     * @return lỗi theo chữ A-D (chỉ phương án sai có chữ); rỗng khi câu không có lỗi nào để hiển thị
     */
    public static Map<String, Item> resolve(GeneratedQuestion q, Function<Integer, GeneratedQuestion> byId,
                                            Function<Integer, Map<String, DistractorCheck>> stored) {
        Map<String, String> texts = shownTexts(q);
        if (texts.isEmpty()) {
            return Map.of();
        }
        Map<String, DistractorCheck> checks = checks(q, byId, stored, 0);
        Map<String, Item> out = new LinkedHashMap<>();
        texts.forEach((letter, text) -> out.put(letter,
                new Item(text, checks.getOrDefault(letter, DistractorCheck.UNCHECKED))));
        return Collections.unmodifiableMap(out);
    }

    /** Chữ lỗi của các phương án sai hiện tại (bỏ phương án đúng, bỏ chữ trống), theo thứ tự A-D. */
    public static Map<String, String> shownTexts(GeneratedQuestion q) {
        Map<String, String> raw = q == null ? null : LetterJson.read(q.getDistractorErrors());
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        String correct = q.getCorrectOption() == null ? null : q.getCorrectOption().getDbValue();
        raw.forEach((letter, text) -> {
            if (!letter.equals(correct) && text != null && !text.isBlank()) {
                out.put(letter, text.strip());
            }
        });
        return out;
    }

    private static Map<String, DistractorCheck> checks(GeneratedQuestion q, Function<Integer, GeneratedQuestion> byId,
                                                       Function<Integer, Map<String, DistractorCheck>> stored, int depth) {
        Map<String, DistractorCheck> own = stored.apply(q.getId());
        if (own != null) {
            return own;
        }
        if (q.getEditedFromQuestionId() == null || depth >= MAX_DEPTH) {
            return Map.of();
        }
        GeneratedQuestion prev = byId.apply(q.getEditedFromQuestionId());
        if (prev == null || !Objects.equals(q.getQuestionText(), prev.getQuestionText())
                || q.getCorrectOption() != prev.getCorrectOption()) {
            return Map.of();
        }
        Map<String, DistractorCheck> prevChecks = checks(prev, byId, stored, depth + 1);
        Map<String, DistractorCheck> out = new LinkedHashMap<>();
        prevChecks.forEach((letter, check) -> {
            if (Objects.equals(option(q, letter), option(prev, letter))) {
                out.put(letter, check);
            }
        });
        return out;
    }

    private static String option(GeneratedQuestion q, String letter) {
        return switch (letter) {
            case "A" -> q.getOptionA();
            case "B" -> q.getOptionB();
            case "C" -> q.getOptionC();
            default -> q.getOptionD();
        };
    }
}
