package com.datn.mcq.assignment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D3 (docs/design/cau-co-hinh.md mục 4.6, chốt 23/11): module {@code assignment} lấy ảnh câu hỏi qua {@code BankService},
 * KHÔNG phụ thuộc thẳng {@code questionimage}, nên không thêm cạnh phụ thuộc mới (Q-8 giữ nguyên). Repo chưa dùng
 * ArchUnit nên test đọc thẳng mã nguồn của module: không file nào được nhắc tới gói {@code com.datn.mcq.questionimage}
 * (import, tên đầy đủ hay import tĩnh). Surefire chạy từ thư mục {@code back_end}.
 */
class AssignmentModuleBoundaryTest {

    private static final Path MODULE = Path.of("src", "main", "java", "com", "datn", "mcq", "assignment");
    private static final String FORBIDDEN = "com.datn.mcq.questionimage";

    @Test
    void assignmentDoesNotDependOnQuestionImage() throws IOException {
        assertTrue(Files.isDirectory(MODULE), "Không thấy mã nguồn module assignment ở " + MODULE.toAbsolutePath());
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(MODULE)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(sources.size() > 10, "Quá ít file nguồn, có thể sai thư mục: " + sources.size());
        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(FORBIDDEN)) {
                    offenders.add(MODULE.relativize(source) + ":" + (i + 1));
                }
            }
        }
        assertTrue(offenders.isEmpty(), "assignment không được phụ thuộc questionimage (D3): " + offenders);
    }
}
