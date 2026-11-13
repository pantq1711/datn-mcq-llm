package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nạp câu mẫu độ sâu (12/11) từ file JSON do giáo viên chọn (nằm ngoài repo, ví dụ data/private/cau-mau-do-sau.json). Repo
 * chỉ chứa định dạng và cách nạp; thiếu file, đường dẫn trống hay file hỏng thì không có câu mẫu (prompt không có khối).
 */
class DepthExampleCatalogTest {

    private static final String JSON = """
            {"examples": [
              {"subject": "Toán", "grade": 8, "level": "hard", "questionType": "sa", "text": "SA hard 1"},
              {"subject": "Toán", "grade": 8, "level": "hard", "text": "Mọi dạng hard 2"},
              {"subject": "Toán", "grade": 8, "level": "hard", "text": "hard 3"},
              {"subject": "Toán", "grade": 8, "level": "easy", "text": "easy 1"},
              {"subject": "Toán", "grade": 9, "level": "hard", "text": "lớp 9"},
              {"subject": "Ngữ văn", "grade": 8, "level": "hard", "text": "văn"},
              {"level": "medium", "text": "mọi môn, mọi lớp"},
              {"level": "medium", "text": "   "},
              {"level": "khó", "text": "mức lạ bị bỏ"}
            ]}
            """;

    @Test
    void chonTheoMonLopDangVaMucToiDaHaiCauMoiMuc(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cau-mau.json");
        Files.writeString(file, JSON, StandardCharsets.UTF_8);
        DepthExampleCatalog catalog = new DepthExampleCatalog(file.toString());
        assertEquals(List.of(new DepthExample(Difficulty.HARD, "SA hard 1"),
                        new DepthExample(Difficulty.HARD, "Mọi dạng hard 2")),
                catalog.select("Toán", 8, QuestionType.SA, Set.of(Difficulty.HARD)));
        assertEquals(List.of(new DepthExample(Difficulty.HARD, "Mọi dạng hard 2"),
                        new DepthExample(Difficulty.HARD, "hard 3")),
                catalog.select("toán", 8, QuestionType.MCQ, Set.of(Difficulty.HARD)), "câu chỉ cho sa bị loại");
        assertEquals(List.of(new DepthExample(Difficulty.EASY, "easy 1"),
                        new DepthExample(Difficulty.MEDIUM, "mọi môn, mọi lớp"),
                        new DepthExample(Difficulty.HARD, "Mọi dạng hard 2"), new DepthExample(Difficulty.HARD, "hard 3")),
                catalog.select("Toán", 8, QuestionType.TF, Set.of(Difficulty.HARD, Difficulty.EASY, Difficulty.MEDIUM)),
                "theo thứ tự mức");
        assertEquals(List.of(new DepthExample(Difficulty.HARD, "lớp 9")),
                catalog.select("Toán", 9, QuestionType.MCQ, Set.of(Difficulty.HARD)));
        assertTrue(catalog.select("Toán", 8, QuestionType.MCQ, Set.of()).isEmpty());
    }

    @Test
    void thieuFileDuongDanTrongHoacFileHongThiKhongCoCauMau(@TempDir Path dir) throws Exception {
        assertTrue(new DepthExampleCatalog("").select("Toán", 8, QuestionType.MCQ, Set.of(Difficulty.HARD)).isEmpty());
        assertTrue(new DepthExampleCatalog(null).select("Toán", 8, QuestionType.MCQ, Set.of(Difficulty.HARD)).isEmpty());
        assertTrue(new DepthExampleCatalog(dir.resolve("khong-co.json").toString())
                .select("Toán", 8, QuestionType.MCQ, Set.of(Difficulty.HARD)).isEmpty());
        Path broken = dir.resolve("hong.json");
        Files.writeString(broken, "{\"examples\": [", StandardCharsets.UTF_8);
        assertTrue(new DepthExampleCatalog(broken.toString())
                .select("Toán", 8, QuestionType.MCQ, Set.of(Difficulty.HARD)).isEmpty());
        assertEquals(0, new DepthExampleCatalog(broken.toString()).size());
    }
}
