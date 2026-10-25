package com.datn.mcq.migration;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V4 nâng cấp được một database đang ở V3 có dữ liệu mcq đủ vòng đời, và không làm đổi dữ liệu cũ.
 *
 * <p>Chạy Flyway riêng trên schema tạm {@value #SCHEMA} trong datn_mcq_test (không đụng schema public của
 * các test khác): migrate tới V3, chèn dữ liệu, chụp từng dòng, migrate tiếp lên V4, so lại từng cột cũ.
 * Schema tạm do chính test này tạo và xóa, chỉ sau khi guard xác nhận đang ở datn_mcq_test.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V4UpgradeFromV3Test {

    private static final String SCHEMA = "v4_upgrade_test";

    /** Các bảng có từ trước V4; thứ tự cha trước con. */
    private static final List<String> OLD_TABLES = List.of(
            "subject", "users", "question_template", "experiment_run", "generated_question",
            "generation_job", "quality_check_result", "label_assignment", "teacher_review",
            "teacher_review_reason", "bank_item", "exam", "exam_question");

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void dropLeftoverSchema() {
        dropSchema("V4UpgradeFromV3Test.before");
    }

    @AfterEach
    void dropTempSchema() {
        dropSchema("V4UpgradeFromV3Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql.replace("§", SCHEMA + ".") + " RETURNING id", Integer.class, args);
    }

    private void exec(String sql, Object... args) {
        jdbc.update(sql.replace("§", SCHEMA + "."), args);
    }

    @Test
    void v4UpgradesPopulatedV3DatabaseWithoutChangingMcqData() throws Exception {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V4UpgradeFromV3Test.migrateV3");
        assertEquals(3, flyway("3").migrate().migrationsExecuted);

        // Dữ liệu mcq đủ vòng đời ở V3.
        int subject = insert("INSERT INTO §subject (name) VALUES ('Toán nâng cấp')");
        int teacher = insert("INSERT INTO §users (name, email, role) VALUES ('GV', 'gv@test', 'teacher')");
        int template = insert("INSERT INTO §question_template (subject_id, source_question, version, is_active,"
                + " grade_level, topic) VALUES (?, 'Câu mẫu', 1, TRUE, 8, 'Phân thức')", subject);
        int run = insert("INSERT INTO §experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, prompt_version) VALUES (?, 'few-shot-structured', 'gemini-test', 0.7, 'prompt',"
                + " 'production', 'sync-v1')", subject);
        int expRun = insert("INSERT INTO §experiment_run (subject_id, strategy, model_name, temperature, prompt_text)"
                + " VALUES (?, 'zero-shot', 'gemini-test', 0.7, 'prompt')", subject);
        String gq = "INSERT INTO §generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, explanation, raw_llm_output, llm_difficulty, final_difficulty,"
                + " parse_success, token_count, latency_ms, cost_usd, status, created_at)"
                + " VALUES (?, ?, ?, '$1$', '$2$', '$3$', '$4$', ?, 'Lời giải', '{}', 'easy', ?, TRUE, 100, 12.5, 0,"
                + " ?, now())";
        int inBank = insert(gq, template, run, "Tính $1 + 1$", "B", "easy", "in_bank");
        int rejected = insert(gq, template, run, "Tính $1 + 2$", "C", "medium", "rejected");
        int generated = insert(gq, template, run, "Tính $1 + 3$", "D", null, "generated");
        int duplicate = insert(gq, template, run, "Tính $1 + 1$", "B", null, "duplicate");
        int experiment = insert(gq, template, expRun, "Tính $2 + 2$", "A", null, "qc_passed");
        exec("INSERT INTO §generated_question (template_id, run_id, raw_llm_output, parse_success, status)"
                + " VALUES (?, ?, 'không phải JSON', FALSE, 'format_failed')", template, run);

        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, checked_at)"
                + " VALUES (?, 'format', TRUE, now())", inBank);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, similarity_score,"
                + " compared_to_question_id, threshold_used, checked_at) VALUES (?, 'dedup', TRUE, 0.42, ?, 0.9, now())",
                inBank, rejected);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, judge_answer, judge_model,"
                + " checked_at) VALUES (?, 'answer_check', TRUE, 'B', 'solver-test', now())", inBank);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, judge_answer, judge_model,"
                + " checked_at) VALUES (?, 'answer_check', FALSE, 'multiple', 'solver-test', now())", generated);
        exec("INSERT INTO §quality_check_result (question_id, check_type, passed, similarity_score,"
                + " compared_to_question_id, threshold_used, checked_at) VALUES (?, 'dedup', FALSE, 1.0, ?, 0.9, now())",
                duplicate, inBank);

        int reviewApproved = insert("INSERT INTO §teacher_review (question_id, reviewer_id, review_purpose,"
                + " reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option, edited,"
                + " edited_question_text, edited_option_a, edited_option_b, edited_option_c, edited_option_d,"
                + " edit_distance) VALUES (?, ?, 'production', 'B', TRUE, TRUE, 'easy', 'approved_with_edit', 'B',"
                + " TRUE, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 2)", inBank, teacher);
        int reviewRejected = insert("INSERT INTO §teacher_review (question_id, reviewer_id, review_purpose,"
                + " reviewer_answer, answer_correct, format_ok, difficulty_label, decision)"
                + " VALUES (?, ?, 'production', 'none', FALSE, FALSE, 'medium', 'rejected')", rejected, teacher);
        insert("INSERT INTO §teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option)"
                + " VALUES (?, ?, 'experiment', 'A', TRUE, TRUE, 'hard', 'approved_as_is', 'A')", experiment, teacher);
        exec("INSERT INTO §teacher_review_reason (review_id, reason_code) VALUES (?, 'answer_none_correct')",
                reviewRejected);
        exec("INSERT INTO §teacher_review_reason (review_id, reason_code, detail) VALUES (?, 'other', 'Đề sai')",
                reviewRejected);
        exec("INSERT INTO §label_assignment (question_id, reviewer_id, assigned_at) VALUES (?, ?, now())",
                experiment, teacher);
        int bankItem = insert("INSERT INTO §bank_item (source_question_id, review_id, question_text, option_a,"
                + " option_b, option_c, option_d, correct_option, explanation, difficulty)"
                + " VALUES (?, ?, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 'B', NULL, 'easy')", inBank, reviewApproved);
        int exam = insert("INSERT INTO §exam (exam_code, title, subject_id, created_by)"
                + " VALUES ('DE-001', 'Đề thử', ?, ?)", subject, teacher);
        exec("INSERT INTO §exam_question (exam_id, bank_item_id, position, option_order) VALUES (?, ?, 1, 'BADC')",
                exam, bankItem);
        exec("INSERT INTO §generation_job (run_id, template_id, question_id, target_difficulty, status, attempt)"
                + " VALUES (?, ?, ?, 'easy', 'done', 1)", run, template, inBank);
        exec("INSERT INTO §generation_job (run_id, template_id, status) VALUES (?, ?, 'pending')", run, template);

        Map<String, List<Map<String, Object>>> before = snapshot();

        assertEquals(1, flyway("4").migrate().migrationsExecuted); // V5 trở đi có test riêng
        assertEquals("4", jdbc.queryForObject("SELECT version FROM " + SCHEMA
                + ".flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class));

        // Mọi cột cũ của mọi dòng cũ giữ nguyên; cột question_type mới (nếu bảng có) là 'mcq'.
        Map<String, List<Map<String, Object>>> after = snapshot();
        for (String table : OLD_TABLES) {
            List<Map<String, Object>> rowsBefore = before.get(table);
            List<Map<String, Object>> rowsAfter = after.get(table);
            assertEquals(rowsBefore.size(), rowsAfter.size(), "Số dòng " + table);
            for (int i = 0; i < rowsBefore.size(); i++) {
                Map<String, Object> oldRow = rowsBefore.get(i);
                Map<String, Object> newRow = rowsAfter.get(i);
                Map<String, Object> oldColumnsOfNewRow = new LinkedHashMap<>();
                oldRow.keySet().forEach(k -> oldColumnsOfNewRow.put(k, newRow.get(k)));
                assertEquals(oldRow, oldColumnsOfNewRow, "Dòng cũ của " + table + " bị đổi");
                if (newRow.containsKey("question_type")) {
                    assertEquals("mcq", newRow.get("question_type"), table + ".question_type");
                }
                for (String added : List.of("sa_answer", "reviewer_sa_kind", "reviewer_sa_answer", "final_sa_answer",
                        "judge_sa_kind", "judge_sa_answer", "target_statement_difficulties")) {
                    if (newRow.containsKey(added)) {
                        assertEquals(null, newRow.get(added), table + "." + added);
                    }
                }
            }
        }
        for (String table : List.of("experiment_run", "generated_question", "generation_job", "teacher_review",
                "quality_check_result", "bank_item")) {
            assertTrue(after.get(table).get(0).containsKey("question_type"), table + " thiếu question_type");
        }
        for (String table : List.of("generated_question_statement", "teacher_review_statement",
                "answer_check_statement", "bank_item_statement")) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + "." + table, Integer.class));
        }

        // Sau nâng cấp, các ràng buộc mcq vẫn chặn như trước V4.
        assertViolation("chk_bi_type_content", "UPDATE §bank_item SET option_a = NULL WHERE id = ?", bankItem);
        assertViolation("chk_tr_type_columns",
                "UPDATE §teacher_review SET reviewer_answer = NULL WHERE id = ?", reviewApproved);
        assertViolation("chk_gq_in_bank_complete",
                "UPDATE §generated_question SET option_a = NULL WHERE id = ?", inBank);
        assertViolation("chk_qcr_judge_only_answer_check",
                "UPDATE §quality_check_result SET judge_answer = NULL WHERE question_id = ? AND check_type = 'answer_check'",
                inBank);
        assertViolation("chk_tr_final_consistency",
                "UPDATE §teacher_review SET final_option = 'A' WHERE id = ?", reviewRejected);

        // Và đã nhận được dữ liệu dạng mới.
        int saRun = insert("INSERT INTO §experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, prompt_version, question_type) VALUES (?, 'zero-shot', 'gemini-test', 0.7, 'prompt',"
                + " 'production', 'sa-v0-draft', 'sa')", subject);
        int sa = insert("INSERT INTO §generated_question (template_id, run_id, question_type, question_text,"
                + " sa_answer, raw_llm_output) VALUES (?, ?, 'sa', 'Tính', '$-\\frac{3}{4}$', '{}')", template, saRun);
        assertEquals("$-\\frac{3}{4}$", jdbc.queryForObject(
                "SELECT sa_answer FROM " + SCHEMA + ".generated_question WHERE id = ?", String.class, sa));
    }

    private void assertViolation(String constraint, String sql, Object... args) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> exec(sql, args), "Phải vi phạm " + constraint);
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        assertTrue(message.contains(constraint), "Mong đợi " + constraint + ", nhận: " + message);
    }

    private Map<String, List<Map<String, Object>>> snapshot() throws Exception {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : OLD_TABLES) {
            List<String> rows = jdbc.queryForList(
                    "SELECT row_to_json(t)::text FROM " + SCHEMA + "." + table + " t ORDER BY 1", String.class);
            assertFalse(rows.isEmpty(), "Thiếu dữ liệu mẫu cho " + table);
            List<Map<String, Object>> parsed = new java.util.ArrayList<>();
            for (String row : rows) {
                Map<String, Object> map = objectMapper.readValue(row, new TypeReference<LinkedHashMap<String, Object>>() {});
                parsed.add(map);
            }
            result.put(table, parsed);
        }
        return result;
    }
}
