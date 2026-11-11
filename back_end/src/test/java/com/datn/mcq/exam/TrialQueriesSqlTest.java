package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B7: {@code docs/experiments/trial/queries.sql} chỉ có SELECT, chạy được trên datn_mcq_test, và các số đếm theo
 * {@code replace_reason} đúng với dữ liệu mẫu (hai lần Đổi bằng câu ngân hàng, một có lý do, một không).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class TrialQueriesSqlTest extends ExamFlowTestBase {

    private static final Path FILE = Path.of("..", "docs", "experiments", "trial", "queries.sql");

    /**
     * Các câu lệnh trong file (như psql: bỏ lệnh "\" và thay biến dev_emails bằng mặc định rỗng, N-10; bỏ dòng chú thích "--",
     * tách theo dấu chấm phẩy cuối câu).
     */
    private static List<String> statements() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : PsqlFile.load(FILE).lines().toList()) {
            if (!line.strip().startsWith("--")) {
                sb.append(line).append('\n');
            }
        }
        List<String> out = new ArrayList<>();
        for (String s : sb.toString().split(";\\s*\\n")) {
            if (!s.isBlank()) {
                out.add(s.strip());
            }
        }
        return out;
    }

    @Test
    void chiCoSelectVaMoiCauLenhChayDuoc() throws Exception {
        List<String> all = statements();
        assertEquals(5, all.size(), "Q1 đến Q5");
        for (String sql : all) {
            assertTrue(sql.startsWith("WITH"), sql.substring(0, Math.min(60, sql.length())));
            assertFalse(sql.matches("(?is).*\\b(insert|update|delete|drop|alter|truncate|create|grant)\\b.*"),
                    "file thống kê chỉ được đọc: " + sql.substring(0, Math.min(80, sql.length())));
            jdbc.queryForList(sql);
        }
    }

    @Test
    void demTheoLyDoDoiVaKetCuc() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        for (int i = 1; i <= 3; i++) {
            bankMcq("Đa thức", "easy", "Câu ngân hàng số " + i);
        }
        String url = "/api/exams/" + examId + "/slots/" + slot + "/replace";
        postJson(url, "{\"source\": \"bank\", \"reason\": \"wrong_answer\"}").andExpect(status().isOk());
        postJson(url, "{\"source\": \"bank\", \"reason\": \"wrong_answer\"}").andExpect(status().isOk());
        postJson(url, "{\"source\": \"bank\"}").andExpect(status().isOk());

        List<String> statements = statements();
        Map<String, Object> q1 = jdbc.queryForList(statements.get(0)).get(0);
        assertEquals(1L, ((Number) q1.get("so_de")).longValue());
        assertEquals(1L, ((Number) q1.get("so_cau")).longValue());

        List<Map<String, Object>> q2 = jdbc.queryForList(statements.get(1));
        assertEquals(1, q2.size());
        assertEquals("doi", q2.get(0).get("ket_cuc"));

        List<Map<String, Object>> q3 = jdbc.queryForList(statements.get(2));
        long wrongAnswer = q3.stream().filter(r -> "wrong_answer".equals(r.get("ly_do_doi")))
                .mapToLong(r -> ((Number) r.get("so_lan_doi")).longValue()).sum();
        long none = q3.stream().filter(r -> "(không chọn)".equals(r.get("ly_do_doi")))
                .mapToLong(r -> ((Number) r.get("so_lan_doi")).longValue()).sum();
        assertEquals(2L, wrongAnswer);
        assertEquals(1L, none);

        List<Map<String, Object>> q5 = jdbc.queryForList(statements.get(4));
        assertTrue(q5.stream().allMatch(r -> r.get("ly_do_doi") != null && r.get("ly_do_co") != null), q5.toString());
    }
}
