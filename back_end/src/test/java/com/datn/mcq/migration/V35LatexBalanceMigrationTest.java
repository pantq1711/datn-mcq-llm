package com.datn.mcq.migration;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V35: thay lệnh LaTeX bịa {@code \balance} (có ranh giới: không đứng sau {@code \}, không theo sau bởi chữ cái) thành
 * {@code \parallel} trên mọi cột chứa chữ câu:
 * <ul>
 *   <li>{@code generated_question}: {@code question_text, option_a..d, explanation, sa_answer} (GIỮ NGUYÊN {@code raw_llm_output});</li>
 *   <li>{@code generated_question_statement}: {@code statement_text};</li>
 *   <li>{@code bank_item}: {@code question_text, option_a..d, explanation, sa_answer} (kể cả dòng đã ẩn mềm {@code removed_at});</li>
 *   <li>{@code bank_item_statement}: {@code statement_text};</li>
 *   <li>{@code teacher_review}: {@code edited_question_text, edited_option_a..d, edited_explanation, reviewer_sa_answer, final_sa_answer};</li>
 *   <li>{@code teacher_review_statement}: {@code edited_statement_text}.</li>
 * </ul>
 * Số dòng bị đổi trong kịch bản kiểm thử này: generated_question = 3/4, generated_question_statement = 2/4,
 * bank_item = 3/3, bank_item_statement = 2/4, teacher_review = 2/3, teacher_review_statement = 1/1.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V35LatexBalanceMigrationTest {

    private static final String SCHEMA = "v35_balance_test";
    private static final String S = SCHEMA + ".";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V35LatexBalanceMigrationTest.before");
    }

    @AfterEach
    void after() {
        dropSchema("V35LatexBalanceMigrationTest.after");
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

    private int id(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    @Test
    void thayBalanceThanhParallelTrenCacCotChuCauGiuNguyenRawLlmOutputVaBalanced() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V35LatexBalanceMigrationTest.seed");
        assertEquals(34, flyway("34").migrate().migrationsExecuted);

        int subject = id("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        int reviewer = id("INSERT INTO " + S + "users (name, email, role) VALUES ('GV', 'gv@test.local', 'teacher')");
        int template = id("INSERT INTO " + S + "question_template (subject_id, source_question, is_active, grade_level, topic)"
                + " VALUES (?, 'Mẫu $MN \\balance BC$', TRUE, 8, 'Định lí Thalès')", subject);

        int runMcq = id("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'mcq')", subject);
        int runTf = id("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'tf')", subject);
        int runSa = id("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'sa')", subject);

        String rawMcqJson = "{\"question_text\": \"Cho $MN \\\\balance BC$\", \"raw\": \"$MN \\balance BC$\"}";
        int gqMcq = id("INSERT INTO " + S + "generated_question (template_id, run_id, question_type, question_text,"
                        + " option_a, option_b, option_c, option_d, correct_option, explanation, raw_llm_output,"
                        + " llm_difficulty, final_difficulty, parse_success, status)"
                        + " VALUES (?, ?, 'mcq', ?, ?, ?, ?, ?, 'A', ?, ?, 'easy', 'easy', TRUE, 'in_bank')",
                template, runMcq,
                "Cho $\\triangle ABC$ có $MN \\balance BC$ và $\\balanced$.",
                "$MN \\balance BC$",
                "$MN \\balanced BC$",
                "$EF \\balance{GH}$, $IJ \\balance_1 KL$, $PQ \\balance$",
                "$a \\\\balance$",
                "Vì $MN \\balance BC$ nên tỉ số bằng nhau.",
                rawMcqJson);

        int gqClean = id("INSERT INTO " + S + "generated_question (template_id, run_id, question_type, question_text,"
                        + " option_a, option_b, option_c, option_d, correct_option, explanation, raw_llm_output,"
                        + " llm_difficulty, parse_success, status)"
                        + " VALUES (?, ?, 'mcq', 'Cho $MN \\parallel BC$.', '$1$', '$2$', '$3$', '$4$', 'A',"
                        + " 'Đã đúng.', '{}', 'easy', TRUE, 'qc_passed')",
                template, runMcq);

        int gqTf = id("INSERT INTO " + S + "generated_question (template_id, run_id, question_type, question_text,"
                        + " explanation, raw_llm_output, parse_success, status)"
                        + " VALUES (?, ?, 'tf', 'Hình vẽ có $DE \\balance BC$.', 'a) $DE \\balance BC$.', '{\"tf\": \"\\balance\"}',"
                        + " TRUE, 'in_bank')",
                template, runTf);
        jdbc.update("INSERT INTO " + S + "generated_question_statement (question_id, position, statement_text, llm_label,"
                + " llm_difficulty, final_difficulty) VALUES"
                + " (?, 'a', '$DE \\balance BC$.', 'T', 'easy', 'easy'),"
                + " (?, 'b', '$EF \\balance{AB}$ và $\\balanced$.', 'F', 'easy', 'easy'),"
                + " (?, 'c', '$\\balanced$ giữ nguyên.', 'T', 'medium', 'medium'),"
                + " (?, 'd', '$a \\\\balance$ giữ nguyên.', 'F', 'hard', 'hard')",
                gqTf, gqTf, gqTf, gqTf);

        int gqSa = id("INSERT INTO " + S + "generated_question (template_id, run_id, question_type, question_text,"
                        + " sa_answer, explanation, raw_llm_output, llm_difficulty, final_difficulty, parse_success, status)"
                        + " VALUES (?, ?, 'sa', 'Biết $MN \\balance BC$, tính $x$.', '$MN \\balance BC$',"
                        + " 'Từ $MN \\balance BC$ suy ra $x = 4$.', '{\"sa\": \"\\balance\"}', 'medium', 'medium', TRUE, 'in_bank')",
                template, runSa);

        // teacher_review: 1 dòng mcq có edited_*, 1 dòng tf có teacher_review_statement.edited_statement_text, 1 dòng sa có reviewer_sa_answer/final_sa_answer
        int trMcq = id("INSERT INTO " + S + "teacher_review (question_id, question_type, reviewer_id, review_purpose,"
                        + " review_mode, reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option,"
                        + " edited, edited_question_text, edited_option_a, edited_option_b, edited_option_c, edited_option_d,"
                        + " edited_explanation, edit_distance)"
                        + " VALUES (?, 'mcq', ?, 'production', 'blind', 'A', TRUE, TRUE, 'easy', 'approved_with_edit', 'A',"
                        + " TRUE, 'Sửa $MN \\balance BC$', '$A \\balance B$', '$\\balanced$', '$C \\balance D$', '$D \\balance E$',"
                        + " 'Giải $MN \\balance BC$', 5)",
                gqMcq, reviewer);

        int trTf = id("INSERT INTO " + S + "teacher_review (question_id, question_type, reviewer_id, review_purpose,"
                        + " review_mode, format_ok, decision, edited, edit_distance)"
                        + " VALUES (?, 'tf', ?, 'production', 'blind', TRUE, 'approved_with_edit', TRUE, 3)",
                gqTf, reviewer);
        jdbc.update("INSERT INTO " + S + "teacher_review_statement (review_id, position, reviewer_label, difficulty_label,"
                        + " label_correct, final_label, edited_statement_text) VALUES"
                        + " (?, 'a', 'T', 'easy', TRUE, 'T', 'Ý sửa $DE \\balance BC$ và $\\balanced$.')",
                trTf);

        int trSa = id("INSERT INTO " + S + "teacher_review (question_id, question_type, reviewer_id, review_purpose,"
                        + " review_mode, reviewer_sa_kind, reviewer_sa_answer, final_sa_answer, answer_correct, format_ok,"
                        + " difficulty_label, decision, edited)"
                        + " VALUES (?, 'sa', ?, 'production', 'assisted', 'number', '$MN \\balance BC$', '$MN \\balance BC$',"
                        + " TRUE, TRUE, 'medium', 'approved_as_is', FALSE)",
                gqSa, reviewer);

        // bank_item (3 câu: mcq, tf đã gỡ mềm V30, sa) + bank_item_statement
        int biMcq = id("INSERT INTO " + S + "bank_item (source_question_id, review_id, question_type, question_text,"
                        + " option_a, option_b, option_c, option_d, correct_option, explanation, difficulty)"
                        + " VALUES (?, ?, 'mcq', 'Ngân hàng $MN \\balance BC$', '$MN \\balance BC$', '$MN \\balanced BC$',"
                        + " '$EF \\balance{GH}$', '$PQ \\balance$', 'A', 'Giải $MN \\balance BC$.', 'easy')",
                gqMcq, trMcq);

        int biTf = id("INSERT INTO " + S + "bank_item (source_question_id, review_id, question_type, question_text,"
                        + " explanation, removed_at, removed_by)"
                        + " VALUES (?, ?, 'tf', 'Đề dẫn $DE \\balance BC$.', 'Lời giải $DE \\balance BC$.', CURRENT_TIMESTAMP, ?)",
                gqTf, trTf, reviewer);
        jdbc.update("INSERT INTO " + S + "bank_item_statement (bank_item_id, position, statement_text, label, difficulty) VALUES"
                + " (?, 'a', '$DE \\balance BC$.', 'T', 'easy'),"
                + " (?, 'b', '$EF \\balance AB$.', 'F', 'easy'),"
                + " (?, 'c', '$\\balanced$.', 'T', 'medium'),"
                + " (?, 'd', '$AB = AC$.', 'F', 'hard')",
                biTf, biTf, biTf, biTf);

        int biSa = id("INSERT INTO " + S + "bank_item (source_question_id, review_id, question_type, question_text,"
                        + " sa_answer, explanation, difficulty)"
                        + " VALUES (?, ?, 'sa', 'Tính khi $MN \\balance BC$.', '$MN \\balance BC$', 'Do $MN \\balance BC$.', 'medium')",
                gqSa, trSa);

        // Chạy V35: số dòng đổi kỳ vọng: generated_question=3 (gqMcq, gqTf, gqSa; gqClean không đổi),
        // generated_question_statement=2 (a, b), bank_item=3, bank_item_statement=2 (a, b),
        // teacher_review=2 (trMcq, trSa), teacher_review_statement=1.
        assertEquals(1, flyway("35").migrate().migrationsExecuted);

        // 1. generated_question: đổi đúng \balance, giữ \balanced, giữ \\balance, giữ nguyên raw_llm_output và question_template
        assertEquals("Cho $\\triangle ABC$ có $MN \\parallel BC$ và $\\balanced$.",
                str("SELECT question_text FROM " + S + "generated_question WHERE id = ?", gqMcq));
        assertEquals("$MN \\parallel BC$",
                str("SELECT option_a FROM " + S + "generated_question WHERE id = ?", gqMcq));
        assertEquals("$MN \\balanced BC$",
                str("SELECT option_b FROM " + S + "generated_question WHERE id = ?", gqMcq));
        assertEquals("$EF \\parallel{GH}$, $IJ \\parallel_1 KL$, $PQ \\parallel$",
                str("SELECT option_c FROM " + S + "generated_question WHERE id = ?", gqMcq));
        assertEquals("$a \\\\balance$",
                str("SELECT option_d FROM " + S + "generated_question WHERE id = ?", gqMcq));
        assertEquals("Vì $MN \\parallel BC$ nên tỉ số bằng nhau.",
                str("SELECT explanation FROM " + S + "generated_question WHERE id = ?", gqMcq));
        assertEquals(rawMcqJson,
                str("SELECT raw_llm_output FROM " + S + "generated_question WHERE id = ?", gqMcq));
        assertEquals("Mẫu $MN \\balance BC$",
                str("SELECT source_question FROM " + S + "question_template WHERE id = ?", template));
        assertEquals("Cho $MN \\parallel BC$.",
                str("SELECT question_text FROM " + S + "generated_question WHERE id = ?", gqClean));

        // 2. generated_question (tf, sa) & generated_question_statement
        assertEquals("Hình vẽ có $DE \\parallel BC$.",
                str("SELECT question_text FROM " + S + "generated_question WHERE id = ?", gqTf));
        assertEquals("a) $DE \\parallel BC$.",
                str("SELECT explanation FROM " + S + "generated_question WHERE id = ?", gqTf));
        assertEquals("{\"tf\": \"\\balance\"}",
                str("SELECT raw_llm_output FROM " + S + "generated_question WHERE id = ?", gqTf));
        assertEquals("$DE \\parallel BC$.",
                str("SELECT statement_text FROM " + S + "generated_question_statement WHERE question_id = ? AND position = 'a'", gqTf));
        assertEquals("$EF \\parallel{AB}$ và $\\balanced$.",
                str("SELECT statement_text FROM " + S + "generated_question_statement WHERE question_id = ? AND position = 'b'", gqTf));
        assertEquals("$\\balanced$ giữ nguyên.",
                str("SELECT statement_text FROM " + S + "generated_question_statement WHERE question_id = ? AND position = 'c'", gqTf));
        assertEquals("$a \\\\balance$ giữ nguyên.",
                str("SELECT statement_text FROM " + S + "generated_question_statement WHERE question_id = ? AND position = 'd'", gqTf));

        assertEquals("Biết $MN \\parallel BC$, tính $x$.",
                str("SELECT question_text FROM " + S + "generated_question WHERE id = ?", gqSa));
        assertEquals("$MN \\parallel BC$",
                str("SELECT sa_answer FROM " + S + "generated_question WHERE id = ?", gqSa));
        assertEquals("Từ $MN \\parallel BC$ suy ra $x = 4$.",
                str("SELECT explanation FROM " + S + "generated_question WHERE id = ?", gqSa));
        assertEquals("{\"sa\": \"\\balance\"}",
                str("SELECT raw_llm_output FROM " + S + "generated_question WHERE id = ?", gqSa));

        // 3. teacher_review & teacher_review_statement
        assertEquals("Sửa $MN \\parallel BC$",
                str("SELECT edited_question_text FROM " + S + "teacher_review WHERE id = ?", trMcq));
        assertEquals("$A \\parallel B$",
                str("SELECT edited_option_a FROM " + S + "teacher_review WHERE id = ?", trMcq));
        assertEquals("$\\balanced$",
                str("SELECT edited_option_b FROM " + S + "teacher_review WHERE id = ?", trMcq));
        assertEquals("$C \\parallel D$",
                str("SELECT edited_option_c FROM " + S + "teacher_review WHERE id = ?", trMcq));
        assertEquals("$D \\parallel E$",
                str("SELECT edited_option_d FROM " + S + "teacher_review WHERE id = ?", trMcq));
        assertEquals("Giải $MN \\parallel BC$",
                str("SELECT edited_explanation FROM " + S + "teacher_review WHERE id = ?", trMcq));
        assertEquals("Ý sửa $DE \\parallel BC$ và $\\balanced$.",
                str("SELECT edited_statement_text FROM " + S + "teacher_review_statement WHERE review_id = ? AND position = 'a'", trTf));
        assertEquals("$MN \\parallel BC$",
                str("SELECT reviewer_sa_answer FROM " + S + "teacher_review WHERE id = ?", trSa));
        assertEquals("$MN \\parallel BC$",
                str("SELECT final_sa_answer FROM " + S + "teacher_review WHERE id = ?", trSa));

        // 4. bank_item & bank_item_statement
        assertEquals("Ngân hàng $MN \\parallel BC$",
                str("SELECT question_text FROM " + S + "bank_item WHERE id = ?", biMcq));
        assertEquals("$MN \\parallel BC$",
                str("SELECT option_a FROM " + S + "bank_item WHERE id = ?", biMcq));
        assertEquals("$MN \\balanced BC$",
                str("SELECT option_b FROM " + S + "bank_item WHERE id = ?", biMcq));
        assertEquals("$EF \\parallel{GH}$",
                str("SELECT option_c FROM " + S + "bank_item WHERE id = ?", biMcq));
        assertEquals("$PQ \\parallel$",
                str("SELECT option_d FROM " + S + "bank_item WHERE id = ?", biMcq));
        assertEquals("Giải $MN \\parallel BC$.",
                str("SELECT explanation FROM " + S + "bank_item WHERE id = ?", biMcq));

        assertEquals("Đề dẫn $DE \\parallel BC$.",
                str("SELECT question_text FROM " + S + "bank_item WHERE id = ?", biTf));
        assertEquals("Lời giải $DE \\parallel BC$.",
                str("SELECT explanation FROM " + S + "bank_item WHERE id = ?", biTf));
        assertEquals("$DE \\parallel BC$.",
                str("SELECT statement_text FROM " + S + "bank_item_statement WHERE bank_item_id = ? AND position = 'a'", biTf));
        assertEquals("$EF \\parallel AB$.",
                str("SELECT statement_text FROM " + S + "bank_item_statement WHERE bank_item_id = ? AND position = 'b'", biTf));
        assertEquals("$\\balanced$.",
                str("SELECT statement_text FROM " + S + "bank_item_statement WHERE bank_item_id = ? AND position = 'c'", biTf));

        assertEquals("Tính khi $MN \\parallel BC$.",
                str("SELECT question_text FROM " + S + "bank_item WHERE id = ?", biSa));
        assertEquals("$MN \\parallel BC$",
                str("SELECT sa_answer FROM " + S + "bank_item WHERE id = ?", biSa));
        assertEquals("Do $MN \\parallel BC$.",
                str("SELECT explanation FROM " + S + "bank_item WHERE id = ?", biSa));
    }

    private String str(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
