package com.datn.mcq.migration;

import com.datn.mcq.config.TestSafetyConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V16 (W1-4 bước 2): exam_slot_action.replace_source ('bank' | 'generate', chỉ dòng replace). Mọi dòng replace cũ là
 * 'generate' (trước D5-B Đổi luôn sinh mới); dòng keep/edit giữ NULL. Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V16UpgradeFromV15Test {

    private static final String SCHEMA = "v16_upgrade_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V16UpgradeFromV15Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V16UpgradeFromV15Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    @Test
    void v16BackfillReplaceCuLaGenerateVaChanGiaTriSai() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V16UpgradeFromV15Test.migrate");
        assertEquals(15, flyway("15").migrate().migrationsExecuted);
        int subject = insert("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        int teacher = insert("INSERT INTO " + S + "users (name, email, role) VALUES ('GV', 'gv.v16@test', 'teacher')");
        int tpl = insert("INSERT INTO " + S + "question_template (subject_id, source_question, is_active)"
                + " VALUES (?, 'Mẫu', TRUE)", subject);
        int matrix = insert("INSERT INTO " + S + "exam_matrix (name, subject_id, created_by) VALUES ('M', ?, ?)",
                subject, teacher);
        int cell = insert("INSERT INTO " + S + "exam_matrix_cell (matrix_id, position, part, question_type, topic,"
                + " learning_outcome_easy, difficulty, question_count, template_id) VALUES (?, 1, 'I', 'mcq', 'T',"
                + " 'x', 'easy', 1, ?)", matrix, tpl);
        int exam = insert("INSERT INTO " + S + "exam (exam_code, title, subject_id, created_by, mode, matrix_id)"
                + " VALUES ('V16A', 'Đề', ?, ?, 'teacher', ?)", subject, teacher, matrix);
        int slot = insert("INSERT INTO " + S + "exam_slot (exam_id, cell_id, slot_no, question_type) VALUES (?, ?, 1,"
                + " 'mcq')", exam, cell);
        int replace = insert("INSERT INTO " + S + "exam_slot_action (slot_id, action) VALUES (?, 'replace')", slot);
        int keepOld = insert("INSERT INTO " + S + "exam_slot_action (slot_id, action) VALUES (?, 'keep')", slot);

        assertEquals(1, flyway("16").migrate().migrationsExecuted);

        assertEquals("generate", jdbc.queryForObject("SELECT replace_source FROM " + S + "exam_slot_action WHERE id = ?",
                String.class, replace));
        assertNull(jdbc.queryForObject("SELECT replace_source FROM " + S + "exam_slot_action WHERE id = ?",
                String.class, keepOld));
        assertEquals("bank", jdbc.queryForObject("INSERT INTO " + S + "exam_slot_action (slot_id, action,"
                + " replace_source) VALUES (?, 'replace', 'bank') RETURNING replace_source", String.class, slot));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO " + S
                + "exam_slot_action (slot_id, action, replace_source) VALUES (?, 'replace', 'khac')", slot));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO " + S
                + "exam_slot_action (slot_id, action, replace_source) VALUES (?, 'keep', 'bank')", slot));
    }
}
