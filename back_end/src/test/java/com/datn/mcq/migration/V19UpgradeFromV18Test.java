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
 * V19 (W2-3 bước 4): ngữ cảnh sinh câu của đề (exam_generation_context, exam_context_material), ảnh chụp đoạn tài liệu
 * trong generation_job (material_context, material_refs) và bank_item.visible_to_user_id (câu sinh từ tài liệu chỉ chủ
 * sở hữu thấy). Chạy trên schema tạm {@value #SCHEMA}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V19UpgradeFromV18Test {

    private static final String SCHEMA = "v19_upgrade_test";
    private static final String S = SCHEMA + ".";
    private static final String SHA = "a".repeat(64);

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private int user;
    private int subject;
    private int exam;
    private int cell;
    private int material;

    @BeforeEach
    void before() {
        dropSchema("V19UpgradeFromV18Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V19UpgradeFromV18Test.after");
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

    /** Nâng lên V19 và dựng dữ liệu nền: người dùng, môn, đề, ô ma trận, tài liệu. */
    private void migrateAndSeed() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V19UpgradeFromV18Test.migrate");
        assertEquals(18, flyway("18").migrate().migrationsExecuted);
        assertEquals(1, flyway("19").migrate().migrationsExecuted);
        user = id("INSERT INTO " + S + "users (name, email, role) VALUES ('GV', 'gv19@test.vn', 'teacher')");
        subject = id("INSERT INTO " + S + "subject (name) VALUES ('Toán')");
        exam = newExam("MT19A");
        int template = id("INSERT INTO " + S + "question_template (subject_id, source_question, is_active, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 'Chủ đề')", subject);
        int matrix = id("INSERT INTO " + S + "exam_matrix (name, subject_id, created_by) VALUES ('M', ?, ?)", subject,
                user);
        cell = id("INSERT INTO " + S + "exam_matrix_cell (matrix_id, position, part, question_type, topic, difficulty,"
                + " question_count, template_id) VALUES (?, 1, 'I', 'mcq', 'Chủ đề', 'easy', 1, ?)", matrix, template);
        material = id("INSERT INTO " + S + "lesson_material (owner_id, file_name, mime_type, size_bytes, sha256,"
                + " extract_method, extracted_text, char_count) VALUES (?, 'a.txt', 'text/plain', 10, ?, 'plain',"
                + " 'Xin chao', 8)", user, SHA);
    }

    private int newExam(String code) {
        return id("INSERT INTO " + S + "exam (exam_code, title, subject_id, created_by) VALUES (?, 'Đề', ?, ?)", code,
                subject, user);
    }

    private int context(int examId, Integer cellId, String instruction) {
        return id("INSERT INTO " + S + "exam_generation_context (exam_id, cell_id, extra_instruction) VALUES (?, ?, ?)",
                examId, cellId, instruction);
    }

    private void rejects(String constraint, Runnable action) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, action::run,
                constraint);
        assertTrue(String.valueOf(ex.getMessage()).contains(constraint), "kỳ vọng " + constraint + ", gặp: "
                + ex.getMessage());
    }

    @Test
    void phamViMoiDeMotDongChungVaMotDongChoMoiO() {
        migrateAndSeed();
        context(exam, null, "Dùng ngữ cảnh thực tế.");
        rejects("uq_egc_scope", () -> context(exam, null, "Trùng phạm vi cả đề"));
        context(exam, cell, null);
        rejects("uq_egc_scope", () -> context(exam, cell, "Trùng phạm vi ô"));
        context(newExam("MT19B"), null, null);
    }

    @Test
    void yeuCauThemTu1Den1000KyTuHoacNull() {
        migrateAndSeed();
        rejects("chk_egc_instruction", () -> context(newExam("MT19C"), null, ""));
        rejects("chk_egc_instruction", () -> context(newExam("MT19D"), null, "ă".repeat(1001)));
        context(newExam("MT19E"), null, "ă".repeat(1000));
        context(newExam("MT19F"), null, null);
    }

    @Test
    void taiLieuGanVaoNguCanhVaChanXoaTaiLieuDangDung() {
        migrateAndSeed();
        int ctx = context(exam, null, null);
        jdbc.update("INSERT INTO " + S + "exam_context_material (context_id, material_id) VALUES (?, ?)", ctx, material);
        rejects("exam_context_material_pkey", () -> jdbc.update("INSERT INTO " + S
                + "exam_context_material (context_id, material_id) VALUES (?, ?)", ctx, material));
        rejects("fk_ecm_material", () -> jdbc.update("DELETE FROM " + S + "lesson_material WHERE id = ?", material));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + S + "lesson_material", Integer.class));
    }

    @Test
    void xoaDeXoaNguCanhVaLienKetNhungKhongXoaTaiLieu() {
        migrateAndSeed();
        int ctx = context(exam, null, "Yêu cầu");
        jdbc.update("INSERT INTO " + S + "exam_context_material (context_id, material_id) VALUES (?, ?)", ctx, material);
        jdbc.update("DELETE FROM " + S + "exam WHERE id = ?", exam);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + S + "exam_generation_context", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + S + "exam_context_material", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + S + "lesson_material", Integer.class),
                "tài liệu là của giáo viên, không mất khi xóa đề");
    }

    @Test
    void anhChupDoanTaiLieuCuaJobPhaiDiCungCapVaLaMang() {
        migrateAndSeed();
        int run = id("INSERT INTO " + S + "experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int template = jdbc.queryForObject("SELECT id FROM " + S + "question_template LIMIT 1", Integer.class);
        String refs = "[{\"materialId\":1,\"from\":0,\"to\":8}]";
        jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id, material_context, material_refs)"
                + " VALUES (?, ?, 'Xin chao', CAST(? AS jsonb))", run, template, refs);
        jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id) VALUES (?, ?)", run, template);
        rejects("chk_gj_material_pair", () -> jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id,"
                + " material_context) VALUES (?, ?, 'Chỉ có đoạn, thiếu refs')", run, template));
        rejects("chk_gj_material_pair", () -> jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id,"
                + " material_refs) VALUES (?, ?, CAST(? AS jsonb))", run, template, refs));
        rejects("chk_gj_material_refs", () -> jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id,"
                + " material_context, material_refs) VALUES (?, ?, 'x', CAST('{\"a\":1}' AS jsonb))", run, template));
        rejects("chk_gj_material_ctx", () -> jdbc.update("INSERT INTO " + S + "generation_job (run_id, template_id,"
                + " material_context, material_refs) VALUES (?, ?, ?, CAST(? AS jsonb))", run, template,
                "x".repeat(12_001), refs));
    }

    @Test
    void bankItemCoCotHanCheNguoiXemVaKhoaNgoaiToiUsers() {
        migrateAndSeed();
        assertEquals("YES", jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE"
                + " table_schema = ? AND table_name = 'bank_item' AND column_name = 'visible_to_user_id'",
                String.class, SCHEMA));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid"
                + " JOIN pg_namespace n ON n.oid = t.relnamespace WHERE n.nspname = ? AND t.relname = 'bank_item'"
                + " AND c.contype = 'f' AND c.conname = 'fk_bi_visible_user'", Integer.class, SCHEMA));
    }
}
