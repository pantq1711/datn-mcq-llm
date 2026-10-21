package com.datn.mcq.config;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Dọn dữ liệu nghiệp vụ trong database test theo thứ tự khóa ngoại (con trước, cha sau).
 * Luôn kiểm tra guard để chỉ tác động lên 'datn_mcq_test'. Dùng cho các test tích hợp mới.
 */
public final class TestDataCleaner {

    private TestDataCleaner() {}

    public static void cleanAll(DataSource dataSource, JdbcTemplate jdbcTemplate, String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbcTemplate.update("DELETE FROM exam_question");
        jdbcTemplate.update("UPDATE exam SET parent_exam_id = NULL WHERE parent_exam_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM exam");
        jdbcTemplate.update("DELETE FROM bank_item");
        jdbcTemplate.update("DELETE FROM teacher_review_reason");
        jdbcTemplate.update("DELETE FROM teacher_review");
        jdbcTemplate.update("DELETE FROM label_assignment");
        jdbcTemplate.update("DELETE FROM dedup_gold_label");
        jdbcTemplate.update("DELETE FROM dedup_gold_pair");
        jdbcTemplate.update("DELETE FROM quality_check_result");
        jdbcTemplate.update("DELETE FROM question_embedding");
        jdbcTemplate.update("DELETE FROM generation_job");
        jdbcTemplate.update("DELETE FROM generated_question");
        jdbcTemplate.update("DELETE FROM experiment_run");
        jdbcTemplate.update("DELETE FROM question_template");
        jdbcTemplate.update("DELETE FROM subject");
        jdbcTemplate.update("DELETE FROM users");
    }
}
