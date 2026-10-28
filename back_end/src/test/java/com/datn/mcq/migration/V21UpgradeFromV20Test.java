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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V21 (W2-3, đo chi phí): quality_check_result (dòng answer_check) và question_grounding thêm token_count và latency_ms của
 * lượt gọi LLM đã cho ra kết quả. Dòng cũ để NULL; số âm bị từ chối; dòng khác answer_check không mang số liệu này. Chạy trên
 * schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V21UpgradeFromV20Test {

    private static final String SCHEMA = "v21_upgrade_test";
    private static final String S = SCHEMA + ".";
    private static final String REFS = "[{\"materialId\":1,\"sha256\":\"x\",\"from\":0,\"to\":8,\"rank\":0,\"score\":1.5,"
            + "\"selection\":\"whole\"}]";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private int run;
    private int template;

    @BeforeEach
    void before() {
        dropSchema("V21UpgradeFromV20Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V21UpgradeFromV20Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private int id(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private int newQuestion() {
        return id("INSERT INTO " + S + "generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, final_difficulty)"
                + " VALUES (?, ?, 'Đề', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'qc_passed', 'easy')", template, run);
    }

    private int answerCheck(int questionId, String extraColumns, Object... extraArgs) {
        Object[] args = new Object[1 + extraArgs.length];
        args[0] = questionId;
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
        String values = extraArgs.length == 0 ? "" : ", " + "?, ".repeat(extraArgs.length).replaceAll(", $", "");
        return id("INSERT INTO " + S + "quality_check_result (question_id, check_type, passed, judge_answer, judge_model,"
                + " checked_at" + extraColumns + ") VALUES (?, 'answer_check', TRUE, 'B', 'solver', now()" + values + ")",
                args);
    }

    private int grounding(int questionId, String extraColumns, Object... extraArgs) {
        Object[] args = new Object[1 + extraArgs.length];
        args[0] = questionId;
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
        String values = extraArgs.length == 0 ? "" : ", " + "?, ".repeat(extraArgs.length).replaceAll(", $", "");
        return id("INSERT INTO " + S + "question_grounding (question_id, verdict, reason, judge_model, material_refs"
                + extraColumns + ") VALUES (?, 'unknown', 'r', 'gemini', CAST('" + REFS + "' AS jsonb)" + values + ")", args);
    }

    private void rejects(String constraint, Runnable action) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, action::run,
                constraint);
        assertTrue(String.valueOf(ex.getMessage()).contains(constraint), "kỳ vọng " + constraint + ", gặp: "
                + ex.getMessage());
    }

    private void migrateTo20AndSeed() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V21UpgradeFromV20Test.migrate");
        assertEquals(20, flyway("20").migrate().migrationsExecuted);
        int subject = id("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        template = id("INSERT INTO " + S + "question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 'Chủ đề')", subject);
        run = id("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
    }

    @Test
    void dongCuDuocGiuNguyenVaCoHaiCotMoiLaNull() {
        migrateTo20AndSeed();
        int q = newQuestion();
        int qcr = answerCheck(q, "");
        int qg = grounding(q, "");

        assertEquals(1, flyway("21").migrate().migrationsExecuted);

        Map<String, Object> a = jdbc.queryForMap("SELECT * FROM " + S + "quality_check_result WHERE id = ?", qcr);
        assertNull(a.get("token_count"));
        assertNull(a.get("latency_ms"));
        assertEquals("solver", a.get("judge_model"), "dòng cũ giữ nguyên");
        Map<String, Object> g = jdbc.queryForMap("SELECT * FROM " + S + "question_grounding WHERE id = ?", qg);
        assertNull(g.get("token_count"));
        assertNull(g.get("latency_ms"));
        assertEquals("unknown", g.get("verdict"));
    }

    @Test
    void ghiDuocSoLieuChoAnswerCheckVaGroundingKeCaSoKhong() {
        migrateTo20AndSeed();
        flyway("21").migrate();
        int a = answerCheck(newQuestion(), ", token_count, latency_ms", 1234, 850.5);
        assertEquals(1234, jdbc.queryForObject("SELECT token_count FROM " + S + "quality_check_result WHERE id = ?",
                Integer.class, a));
        assertEquals(850.5, jdbc.queryForObject("SELECT latency_ms FROM " + S + "quality_check_result WHERE id = ?",
                Double.class, a));
        answerCheck(newQuestion(), ", token_count, latency_ms", 0, 0.0);
        int g = grounding(newQuestion(), ", token_count, latency_ms", 987, 1200.25);
        assertEquals(987, jdbc.queryForObject("SELECT token_count FROM " + S + "question_grounding WHERE id = ?",
                Integer.class, g));
        assertEquals(1200.25, jdbc.queryForObject("SELECT latency_ms FROM " + S + "question_grounding WHERE id = ?",
                Double.class, g));
    }

    @Test
    void soAmBiTuChoi() {
        migrateTo20AndSeed();
        flyway("21").migrate();
        rejects("chk_qcr_usage", () -> answerCheck(newQuestion(), ", token_count", -1));
        rejects("chk_qcr_usage", () -> answerCheck(newQuestion(), ", latency_ms", -0.5));
        rejects("chk_qg_usage", () -> grounding(newQuestion(), ", token_count", -1));
        rejects("chk_qg_usage", () -> grounding(newQuestion(), ", latency_ms", -0.5));
    }

    @Test
    void chiDongAnswerCheckMangSoLieuLuotGoiLlm() {
        migrateTo20AndSeed();
        flyway("21").migrate();
        int q = newQuestion();
        jdbc.update("INSERT INTO " + S + "quality_check_result (question_id, check_type, passed, checked_at)"
                + " VALUES (?, 'format', TRUE, now())", q);
        rejects("chk_qcr_usage_answer_check", () -> jdbc.update("UPDATE " + S + "quality_check_result SET token_count = 5"
                + " WHERE question_id = ? AND check_type = 'format'", q));
        rejects("chk_qcr_usage_answer_check", () -> jdbc.update("UPDATE " + S + "quality_check_result SET latency_ms = 5"
                + " WHERE question_id = ? AND check_type = 'format'", q));
    }
}
