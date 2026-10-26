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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V20 (W2-3 bước 6): job {@code grounding_check} trong generation_job và bảng question_grounding (kết quả QC "bám tài
 * liệu"). Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V20UpgradeFromV19Test {

    private static final String SCHEMA = "v20_upgrade_test";
    private static final String S = SCHEMA + ".";
    private static final String REFS = "[{\"materialId\":1,\"sha256\":\"x\",\"from\":0,\"to\":8,\"rank\":0,\"score\":1.5,"
            + "\"selection\":\"whole\"}]";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private int run;
    private int template;
    private int question;

    @BeforeEach
    void before() {
        dropSchema("V20UpgradeFromV19Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V20UpgradeFromV19Test.after");
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

    private void migrateAndSeed() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V20UpgradeFromV19Test.migrate");
        assertEquals(19, flyway("19").migrate().migrationsExecuted);
        assertEquals(1, flyway("20").migrate().migrationsExecuted);
        int subject = id("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        template = id("INSERT INTO " + S + "question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 'Chủ đề')", subject);
        run = id("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        question = newQuestion();
    }

    private int newQuestion() {
        return id("INSERT INTO " + S + "generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, final_difficulty)"
                + " VALUES (?, ?, 'Đề', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'qc_passed', 'easy')", template, run);
    }

    private int groundingJob(Integer questionId, String context) {
        return id("INSERT INTO " + S + "generation_job (run_id, template_id, question_id, job_type, material_context,"
                + " material_refs) VALUES (?, ?, ?, 'grounding_check', ?, CAST(? AS jsonb))", run, template, questionId,
                context, context == null ? null : REFS);
    }

    private void rejects(String constraint, Runnable action) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, action::run,
                constraint);
        assertTrue(String.valueOf(ex.getMessage()).contains(constraint), "kỳ vọng " + constraint + ", gặp: "
                + ex.getMessage());
    }

    // ---------- generation_job ----------

    @Test
    void jobGroundingCheckPhaiCoCauHoiVaDoanTaiLieu() {
        migrateAndSeed();
        groundingJob(question, "[Tài liệu 1, đoạn 1]\nNội dung");
        rejects("chk_gj_question_job", () -> groundingJob(null, "ctx"));
        rejects("chk_gj_grounding_ctx", () -> groundingJob(newQuestion(), null));
    }

    @Test
    void loaiJobLaMotTrongBaVaAnswerCheckVanPhaiCoCauHoi() {
        migrateAndSeed();
        rejects("chk_gj_job_type", () -> jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id,"
                + " job_type) VALUES (?, ?, 'khac')", run, template));
        rejects("chk_gj_question_job", () -> jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id,"
                + " job_type) VALUES (?, ?, 'answer_check')", run, template));
        jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id, question_id, job_type) VALUES (?, ?, ?,"
                + " 'answer_check')", run, template, question);
        jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id) VALUES (?, ?)", run, template);
    }

    @Test
    void moiCauChiCoMotJobGroundingNhungVanCoJobAnswerCheck() {
        migrateAndSeed();
        groundingJob(question, "ctx");
        rejects("uq_gj_grounding_question", () -> groundingJob(question, "ctx khác"));
        jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id, question_id, job_type) VALUES (?, ?, ?,"
                + " 'answer_check')", run, template, question);
        groundingJob(newQuestion(), "ctx");
    }

    // ---------- question_grounding ----------

    private int grounding(Integer questionId, String verdict, String quote, Boolean found, String reason, String judge,
                          String refs) {
        return id("INSERT INTO " + S + "question_grounding (question_id, verdict, reason, evidence_quote,"
                + " evidence_found, judge_model, material_refs) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
                questionId, verdict, reason, quote, found, judge, refs);
    }

    @Test
    void ketQuaHopLeChoBaVerdictVaCoMacDinh() {
        migrateAndSeed();
        int a = grounding(question, "supported", "Bình phương của một tổng", true, "Khớp tài liệu.", "gemini", REFS);
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT generated_with_material FROM " + S
                + "question_grounding WHERE id = ?", Boolean.class, a), "mặc định: sinh có tài liệu");
        assertTrue(jdbc.queryForObject("SELECT checked_at IS NOT NULL FROM " + S + "question_grounding WHERE id = ?",
                Boolean.class, a));
        grounding(newQuestion(), "not_supported", null, null, "Mâu thuẫn.", "gemini", REFS);
        grounding(newQuestion(), "unknown", null, null, "Không đủ.", "gemini", REFS);
        grounding(newQuestion(), "not_supported", "đoạn mâu thuẫn", false, "x", "gemini", REFS);
    }

    @Test
    void supportedBatBuocCoTrichDanDaKiemChung() {
        migrateAndSeed();
        rejects("chk_qg_supported_evidence", () -> grounding(newQuestion(), "supported", null, null, "r", "m", REFS));
        rejects("chk_qg_supported_evidence", () -> grounding(newQuestion(), "supported", "trích", false, "r", "m", REFS));
        rejects("chk_qg_evidence", () -> grounding(newQuestion(), "unknown", null, true, "r", "m", REFS));
    }

    @Test
    void rangBuocConLaiCuaKetQua() {
        migrateAndSeed();
        rejects("chk_qg_verdict", () -> grounding(newQuestion(), "maybe", null, null, "r", "m", REFS));
        rejects("chk_qg_reason", () -> grounding(newQuestion(), "unknown", null, null, "ă".repeat(601), "m", REFS));
        rejects("chk_qg_quote", () -> grounding(newQuestion(), "not_supported", "ă".repeat(401), true, "r", "m", REFS));
        rejects("chk_qg_judge", () -> grounding(newQuestion(), "unknown", null, null, "r", "  ", REFS));
        rejects("chk_qg_refs", () -> grounding(newQuestion(), "unknown", null, null, "r", "m", "{\"a\":1}"));
        grounding(question, "unknown", null, null, "r", "m", REFS);
        rejects("uq_qg_question", () -> grounding(question, "unknown", null, null, "r", "m", REFS));
    }
}
