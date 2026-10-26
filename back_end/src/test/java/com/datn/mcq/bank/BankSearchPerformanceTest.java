package com.datn.mcq.bank;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-3: hiệu năng lọc ngân hàng với khoảng 3000 câu và 600 đề đã dùng. Gắn tag {@code perf}, KHÔNG chạy trong bộ test
 * mặc định (pom đặt {@code excludedGroups=perf}). Chạy riêng:
 * {@code ./mvnw test -Dtest=BankSearchPerformanceTest -DexcludedGroups=none}.
 *
 * <p>Ngưỡng 1000 ms mỗi lần gọi (lọc, tìm văn bản, sắp xếp theo mức dùng) là mức thoải mái cho máy phát triển; nếu vượt,
 * xem xét thêm chỉ mục (V15) trước khi nới ngưỡng.
 */
@Tag("perf")
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class BankSearchPerformanceTest {

    private static final int ITEMS = 3000;
    private static final long LIMIT_MS = 1000;

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.datn.mcq.exam.repository.ExamSlotRepository slotRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int subject;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "BankSearchPerformanceTest.setUp");
        subject = jdbc.queryForObject("INSERT INTO subject (name) VALUES ('Toán') RETURNING id", Integer.class);
        teacher = jdbc.queryForObject("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.perf@test', 'teacher')"
                + " RETURNING id", Integer.class);
        int run = jdbc.queryForObject("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature,"
                + " prompt_text, purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production') RETURNING id",
                Integer.class, subject);
        // 3000 câu: 50 chủ đề, 4 khối, 3 mức; mỗi câu có template, câu nguồn và đánh giá riêng.
        jdbc.update("INSERT INTO question_template (subject_id, source_question, is_active, topic, grade_level)"
                + " SELECT ?, 'Mẫu', TRUE, 'Chủ đề ' || (g % 50), 6 + (g % 4) FROM generate_series(1, ?) g", subject,
                ITEMS);
        jdbc.update("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, raw_llm_output, status, final_difficulty)"
                + " SELECT t.id, ?, 'Tính giá trị biểu thức số ' || t.id || ' có chứa $x^2$', '$1$', '$2$', '$3$',"
                + " '$4$', 'B', '{}', 'in_bank', (ARRAY['easy', 'medium', 'hard'])[1 + (t.id % 3)]"
                + " FROM question_template t", run);
        jdbc.update("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option)"
                + " SELECT id, ?, 'production', 'B', TRUE, TRUE, 'easy', 'approved_as_is', 'B' FROM generated_question",
                teacher);
        jdbc.update("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, difficulty) SELECT g.id, r.id, g.question_text, '$1$', '$2$', '$3$',"
                + " '$4$', 'B', g.final_difficulty FROM generated_question g JOIN teacher_review r"
                + " ON r.question_id = g.id");
        // 600 đề, mỗi đề 20 câu: tổng 12000 lượt dùng.
        jdbc.update("INSERT INTO exam (exam_code, title, subject_id, created_by, created_at)"
                + " SELECT 'P' || g, 'Đề ' || g, ?, ?, now() - (g || ' hours')::interval FROM generate_series(1, 600) g",
                subject, teacher);
        jdbc.update("INSERT INTO exam_question (exam_id, bank_item_id, position)"
                + " SELECT e.id, b.id, row_number() OVER (PARTITION BY e.id ORDER BY b.id)"
                + " FROM exam e JOIN LATERAL (SELECT id FROM bank_item ORDER BY random() LIMIT 20) b ON TRUE");
        jdbc.execute("ANALYZE");
    }

    private JsonNode timed(String label, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/bank-items").with(AuthTestSupport.teacher(teacher));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        mockMvc.perform(request).andExpect(status().isOk());  // làm nóng
        long start = System.nanoTime();
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[perf] " + label + ": " + ms + " ms");
        assertTrue(ms < LIMIT_MS, label + " mất " + ms + " ms (ngưỡng " + LIMIT_MS + " ms)");
        return objectMapper.readTree(body);
    }

    @Test
    void locTimVaSapXepVoiBaNghinCau() throws Exception {
        assertEquals(ITEMS, timed("không lọc", "size", "50").get("totalItems").asInt());
        timed("lọc khối + chủ đề + mức", "gradeLevel", "7", "topic", "Chủ đề 7", "difficulty", "easy");
        timed("tìm văn bản", "q", "biểu thức số 1234");
        timed("tìm văn bản không có kết quả", "q", "khong-co-chuoi-nay");
        timed("sắp ít dùng trước", "sort", "least_used", "size", "50");
        timed("sắp dùng gần nhất", "sort", "recently_used", "size", "50", "page", "10");
        timed("chỉ câu đã dùng", "usage", "used", "size", "50");
        timed("chỉ câu chưa dùng", "usage", "unused", "sort", "least_used");
        JsonNode topics = mockTopics();
        assertTrue(topics.size() >= 50);
    }

    private JsonNode mockTopics() throws Exception {
        long start = System.nanoTime();
        String body = mockMvc.perform(get("/api/bank-items/topics").with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[perf] topics: " + ms + " ms");
        assertTrue(ms < LIMIT_MS, "topics mất " + ms + " ms");
        return objectMapper.readTree(body);
    }

    @Test
    void chonCauKhiTaoDeVoiBaNghinCau() throws Exception {
        // Truy vấn chọn câu khi tạo đề (có xếp hạng theo mức dùng) cũng phải nhanh.
        slotRepository.findBankCandidates(-1, -1, subject, "mcq", "Chủ đề 7", "", "easy", "", false, 20);  // làm nóng
        long start = System.nanoTime();
        var picked = slotRepository.findBankCandidates(-1, -1, subject, "mcq", "Chủ đề 7", "", "easy", "", false, 20);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[perf] chọn câu khi tạo đề: " + ms + " ms, " + picked.size() + " câu");
        assertTrue(ms < LIMIT_MS, "chọn câu mất " + ms + " ms (ngưỡng " + LIMIT_MS + " ms)");
        assertEquals(picked.size(), picked.stream().distinct().count());
    }
}
