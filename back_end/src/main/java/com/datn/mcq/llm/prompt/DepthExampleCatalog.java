package com.datn.mcq.llm.prompt;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Câu mẫu độ sâu theo mức (12/11) do giáo viên chọn, nạp MỘT lần lúc khởi động từ file JSON ngoài repo (cấu hình
 * {@code app.prompt.depth-examples-file}, biến môi trường {@code DEPTH_EXAMPLES_FILE}; ví dụ
 * {@code data/private/cau-mau-do-sau.json}). Repo chỉ chứa định dạng và cách nạp, không chứa câu mẫu.
 *
 * <p>Định dạng (docs/contract/enums.md mục "độ sâu"):
 * <pre>{"examples": [{"subject": "Toán", "grade": 8, "level": "hard", "questionType": "sa", "text": "..."}]}</pre>
 * {@code level} (easy | medium | hard) và {@code text} bắt buộc; {@code subject}, {@code grade}, {@code questionType} bỏ
 * trống là áp cho mọi môn, lớp, dạng. Phần tử thiếu hay sai bị bỏ qua. Đường dẫn trống, thiếu file hoặc file hỏng thì
 * không có câu mẫu (chỉ ghi cảnh báo, không in nội dung): prompt không có khối câu mẫu.
 */
@Component
public class DepthExampleCatalog {

    private static final Logger log = LoggerFactory.getLogger(DepthExampleCatalog.class);

    /** Số câu mẫu tối đa mỗi mức đưa vào một prompt. */
    public static final int MAX_PER_LEVEL = 2;

    private record Entry(String subject, Integer grade, QuestionType type, Difficulty level, String text) {}

    private final List<Entry> entries;

    public DepthExampleCatalog(@Value("${app.prompt.depth-examples-file:}") String file) {
        this.entries = load(file);
    }

    /** Số câu mẫu hợp lệ đã nạp. */
    public int size() {
        return entries.size();
    }

    /**
     * Câu mẫu cho một ô: khớp môn (không phân biệt hoa thường), lớp, dạng; chỉ các mức trong {@code levels}; tối đa
     * {@value #MAX_PER_LEVEL} câu mỗi mức, theo thứ tự mức rồi thứ tự trong file.
     */
    public List<DepthExample> select(String subject, Integer grade, QuestionType type, Collection<Difficulty> levels) {
        List<DepthExample> out = new ArrayList<>();
        if (levels == null || levels.isEmpty()) {
            return out;
        }
        String subj = norm(subject);
        for (Difficulty level : Difficulty.values()) {
            if (!levels.contains(level)) {
                continue;
            }
            int taken = 0;
            for (Entry e : entries) {
                if (taken == MAX_PER_LEVEL) {
                    break;
                }
                if (e.level() == level && (e.subject() == null || e.subject().equals(subj))
                        && (e.grade() == null || e.grade().equals(grade)) && (e.type() == null || e.type() == type)) {
                    out.add(new DepthExample(level, e.text()));
                    taken++;
                }
            }
        }
        return out;
    }

    private static List<Entry> load(String file) {
        if (file == null || file.isBlank()) {
            return List.of();
        }
        Path path = Path.of(file.strip());
        if (!Files.isRegularFile(path)) {
            log.warn("Không thấy file câu mẫu độ sâu (app.prompt.depth-examples-file): prompt không có khối câu mẫu");
            return List.of();
        }
        try {
            Map<String, Object> root = SimpleJson.parseObject(Files.readString(path, StandardCharsets.UTF_8));
            List<Entry> out = new ArrayList<>();
            if (root.get("examples") instanceof List<?> list) {
                for (Object item : list) {
                    Entry e = item instanceof Map<?, ?> m ? entry(m) : null;
                    if (e != null) {
                        out.add(e);
                    }
                }
            }
            log.info("Đã nạp {} câu mẫu độ sâu", out.size());
            return List.copyOf(out);
        } catch (Exception ex) {
            log.warn("File câu mẫu độ sâu không đọc được ({}): prompt không có khối câu mẫu", ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private static Entry entry(Map<?, ?> m) {
        Difficulty level = null;
        for (Difficulty d : Difficulty.values()) {
            if (d.getDbValue().equals(String.valueOf(m.get("level")).strip().toLowerCase(Locale.ROOT))) {
                level = d;
            }
        }
        String text = m.get("text") instanceof String s ? s.strip() : "";
        if (level == null || text.isEmpty()) {
            return null;
        }
        QuestionType type = null;
        if (m.get("questionType") instanceof String t && !t.isBlank()) {
            for (QuestionType q : QuestionType.values()) {
                if (q.getDbValue().equals(t.strip().toLowerCase(Locale.ROOT))) {
                    type = q;
                }
            }
            if (type == null) {
                return null;
            }
        }
        Integer grade = m.get("grade") instanceof Number n ? n.intValue() : null;
        String subject = m.get("subject") instanceof String s && !s.isBlank() ? norm(s) : null;
        return new Entry(subject, grade, type, level, text);
    }

    private static String norm(String s) {
        return s == null ? "" : Normalizer.normalize(s.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
