package com.datn.mcq.migration;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ràng buộc của V4 trên database test (thiết kế đã duyệt 22/10, phương án b + khóa ngoại kép).
 * Ghi thẳng bằng SQL để kiểm chính DB, không qua service.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V4SchemaConstraintTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private int template;
    private int teacher;
    private int teacher2;
    private int mcqRun;
    private int saRun;
    private int tfRun;

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbc, "V4SchemaConstraintTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán V4')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV1', 'gv1@test', 'teacher')");
        teacher2 = insert("INSERT INTO users (name, email, role) VALUES ('GV2', 'gv2@test', 'teacher')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active) VALUES (?, 'Mẫu', TRUE)",
                subject);
        mcqRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose)"
                + " VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production')", subject);
        saRun = typedRun(subject, "sa");
        tfRun = typedRun(subject, "tf");
    }

    private int typedRun(int subject, String type) {
        return insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', ?, ?)",
                subject, type + "-v0-draft", type);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private void assertViolation(String constraint, String sql, Object... args) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(sql, args), "Phải vi phạm " + constraint);
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        assertTrue(message.contains(constraint), "Mong đợi " + constraint + ", nhận: " + message);
    }

    private int mcqQuestion(String status) {
        return insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c,"
                + " option_d, correct_option, raw_llm_output, status, final_difficulty)"
                + " VALUES (?, ?, 'Q', 'a', 'b', 'c', 'd', 'A', '{}', ?, 'easy')", template, mcqRun, status);
    }

    private int saQuestion(String answer) {
        return insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " raw_llm_output) VALUES (?, ?, 'sa', 'Tính', ?, '{}')", template, saRun, answer);
    }

    private int tfQuestion() {
        return insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, raw_llm_output)"
                + " VALUES (?, ?, 'tf', 'Cho biểu thức P', '{}')", template, tfRun);
    }

    private int tfReview(int question, int reviewer) {
        return insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, format_ok,"
                + " decision) VALUES (?, ?, 'production', 'tf', TRUE, 'approved_as_is')", question, reviewer);
    }

    // ---------- experiment_run ----------

    @Test
    void runTfSaChiChoProductionVaZeroShot() {
        int subject = jdbc.queryForObject("SELECT subject_id FROM experiment_run WHERE id = ?", Integer.class, mcqRun);
        assertViolation("chk_er_tf_sa_production",
                "INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                        + " question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'experiment', 'tf')", subject);
        assertViolation("chk_er_tf_sa_production",
                "INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                        + " question_type) VALUES (?, 'few-shot', 'm', 0.7, 'p', 'production', 'sa')", subject);
        assertViolation("chk_er_question_type",
                "INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                        + " question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'essay')", subject);
        // mcq không đổi: experiment với strategy bất kỳ vẫn được
        insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text)"
                + " VALUES (?, 'few-shot', 'm', 0.7, 'p')", subject);
    }

    // ---------- generated_question ----------

    @Test
    void cauMcqCuMacDinhLaMcq() {
        int q = mcqQuestion("generated");
        assertEquals("mcq", jdbc.queryForObject("SELECT question_type FROM generated_question WHERE id = ?",
                String.class, q));
    }

    @Test
    void cotRiengTungDangCau() {
        assertViolation("chk_gq_type_columns",
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, option_a,"
                        + " raw_llm_output) VALUES (?, ?, 'sa', 'Q', 'a', '{}')", template, saRun);
        assertViolation("chk_gq_type_columns",
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, correct_option,"
                        + " raw_llm_output) VALUES (?, ?, 'tf', 'Q', 'A', '{}')", template, tfRun);
        assertViolation("chk_gq_type_columns",
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, llm_difficulty,"
                        + " raw_llm_output) VALUES (?, ?, 'tf', 'Q', 'easy', '{}')", template, tfRun);
        assertViolation("chk_gq_type_columns",
                "INSERT INTO generated_question (template_id, run_id, question_text, sa_answer, raw_llm_output)"
                        + " VALUES (?, ?, 'Q', '5', '{}')", template, mcqRun);
    }

    @Test
    void dapSoSaLuuTextKhongRegex() {
        for (String answer : new String[] {"$-\\frac{3}{4}$", "0.5", "2\\sqrt{3}", "05", "1.500", "√12"}) {
            int q = saQuestion(answer);
            assertEquals(answer, jdbc.queryForObject("SELECT sa_answer FROM generated_question WHERE id = ?",
                    String.class, q));
        }
    }

    @Test
    void dangCauPhaiKhopRun() {
        assertViolation("fk_gq_run_type",
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, raw_llm_output)"
                        + " VALUES (?, ?, 'tf', 'Q', '{}')", template, mcqRun);
        assertViolation("fk_gq_run_type",
                "INSERT INTO generated_question (template_id, run_id, question_text, raw_llm_output)"
                        + " VALUES (?, ?, 'Q', '{}')", template, saRun);
    }

    @Test
    void inBankTheoDangCau() {
        assertViolation("chk_gq_in_bank_complete",
                "INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c,"
                        + " correct_option, raw_llm_output, status, final_difficulty)"
                        + " VALUES (?, ?, 'Q', 'a', 'b', 'c', 'A', '{}', 'in_bank', 'easy')", template, mcqRun);
        assertViolation("chk_gq_in_bank_complete",
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                        + " raw_llm_output, status) VALUES (?, ?, 'sa', 'Q', '5', '{}', 'in_bank')", template, saRun);
        insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " raw_llm_output, status, final_difficulty) VALUES (?, ?, 'sa', 'Q', '5', '{}', 'in_bank', 'hard')",
                template, saRun);
        insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, raw_llm_output,"
                + " status) VALUES (?, ?, 'tf', 'Q', '{}', 'in_bank')", template, tfRun);
    }

    // ---------- generated_question_statement ----------

    @Test
    void yTfChiGanVaoCauTf() {
        int mcq = mcqQuestion("generated");
        assertViolation("fk_gqs_question",
                "INSERT INTO generated_question_statement (question_id, position, statement_text, llm_label)"
                        + " VALUES (?, 'a', 'x', 'T')", mcq);
        int tf = tfQuestion();
        jdbc.update("INSERT INTO generated_question_statement (question_id, position, statement_text, llm_label,"
                + " llm_difficulty) VALUES (?, 'a', 'x', 'T', 'easy')", tf);
        assertViolation("chk_gqs_position",
                "INSERT INTO generated_question_statement (question_id, position) VALUES (?, 'e')", tf);
        assertViolation("chk_gqs_llm_label",
                "INSERT INTO generated_question_statement (question_id, position, llm_label) VALUES (?, 'b', 'X')", tf);
        assertViolation("chk_gqs_type",
                "INSERT INTO generated_question_statement (question_id, question_type, position) VALUES (?, 'mcq', 'c')",
                mcq);
        assertViolation("generated_question_statement_pkey",
                "INSERT INTO generated_question_statement (question_id, position) VALUES (?, 'a')", tf);
    }

    @Test
    void khongDoiDuocDangCauKhiDaCoDongCon() {
        int tf = tfQuestion();
        jdbc.update("INSERT INTO generated_question_statement (question_id, position) VALUES (?, 'a')", tf);
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE generated_question SET question_type = 'sa', run_id = ? WHERE id = ?",
                        saRun, tf));
        assertTrue(String.valueOf(ex.getMostSpecificCause().getMessage()).contains("fk_gqs_question"),
                ex.getMostSpecificCause().getMessage());
    }

    // ---------- generation_job ----------

    @Test
    void jobTheoDangCau() {
        assertViolation("fk_gj_run_type",
                "INSERT INTO generation_job (run_id, template_id, question_type, target_statement_difficulties)"
                        + " VALUES (?, ?, 'tf', 'easy,easy,medium,hard')", saRun, template);
        assertViolation("chk_gj_target_statements",
                "INSERT INTO generation_job (run_id, template_id, question_type, target_statement_difficulties)"
                        + " VALUES (?, ?, 'tf', 'easy,easy,medium')", tfRun, template);
        assertViolation("chk_gj_target_statements",
                "INSERT INTO generation_job (run_id, template_id, question_type, target_difficulty,"
                        + " target_statement_difficulties) VALUES (?, ?, 'tf', 'easy', 'easy,easy,medium,hard')",
                tfRun, template);
        assertViolation("chk_gj_target_statements",
                "INSERT INTO generation_job (run_id, template_id, target_statement_difficulties)"
                        + " VALUES (?, ?, 'easy,easy,medium,hard')", mcqRun, template);
        insert("INSERT INTO generation_job (run_id, template_id, question_type, target_statement_difficulties)"
                + " VALUES (?, ?, 'tf', 'easy,easy,medium,hard')", tfRun, template);
        insert("INSERT INTO generation_job (run_id, template_id, target_difficulty) VALUES (?, ?, 'easy')",
                mcqRun, template);
    }

    // ---------- teacher_review ----------

    @Test
    void reviewMcqVanBatBuocCotCu() {
        int q = mcqQuestion("qc_passed");
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, answer_correct, format_ok,"
                        + " difficulty_label, decision, final_option) VALUES (?, ?, 'production', TRUE, TRUE, 'easy',"
                        + " 'approved_with_edit', 'A')", q, teacher);
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer, format_ok,"
                        + " difficulty_label, decision, final_option) VALUES (?, ?, 'production', 'A', TRUE, 'easy',"
                        + " 'approved_with_edit', 'A')", q, teacher);
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer, answer_correct,"
                        + " format_ok, decision, final_option) VALUES (?, ?, 'production', 'A', TRUE, TRUE,"
                        + " 'approved_with_edit', 'A')", q, teacher);
        assertViolation("chk_tr_final_consistency",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer, answer_correct,"
                        + " format_ok, difficulty_label, decision) VALUES (?, ?, 'production', 'A', TRUE, TRUE, 'easy',"
                        + " 'approved_with_edit')", q, teacher);
        assertViolation("chk_tr_as_is_own_answer",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer, answer_correct,"
                        + " format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production', 'A', TRUE,"
                        + " TRUE, 'easy', 'approved_as_is', 'B')", q, teacher);
        int ok = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'A', TRUE, TRUE, 'easy', 'approved_as_is', 'A')", q, teacher);
        assertEquals("mcq", jdbc.queryForObject("SELECT question_type FROM teacher_review WHERE id = ?",
                String.class, ok));
    }

    @Test
    void reviewSa() {
        int q = saQuestion("5");
        // answer_correct NULL được phép với sa (đáp số LLM không còn phân tích được)
        insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, format_ok,"
                + " difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer, final_sa_answer)"
                + " VALUES (?, ?, 'production', 'sa', TRUE, 'easy', 'approved_as_is', 'number', '05', '05')",
                q, teacher);
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, answer_correct,"
                        + " format_ok, difficulty_label, decision, reviewer_sa_kind) VALUES (?, ?, 'production', 'sa',"
                        + " TRUE, TRUE, 'easy', 'rejected', 'ambiguous')", q, teacher2);
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, answer_correct,"
                        + " format_ok, difficulty_label, decision, reviewer_sa_kind) VALUES (?, ?, 'production', 'sa',"
                        + " FALSE, TRUE, 'easy', 'rejected', 'number')", q, teacher2);
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, reviewer_answer,"
                        + " answer_correct, format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer)"
                        + " VALUES (?, ?, 'production', 'sa', 'A', TRUE, TRUE, 'easy', 'rejected', 'number', '5')",
                q, teacher2);
        assertViolation("chk_tr_final_consistency",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, answer_correct,"
                        + " format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer, final_sa_answer)"
                        + " VALUES (?, ?, 'production', 'sa', TRUE, TRUE, 'easy', 'rejected', 'number', '5', '5')",
                q, teacher2);
        assertViolation("chk_tr_as_is_own_answer",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, answer_correct,"
                        + " format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer, final_sa_answer)"
                        + " VALUES (?, ?, 'production', 'sa', TRUE, TRUE, 'easy', 'approved_as_is', 'number', '5', '6')",
                q, teacher2);
        assertViolation("fk_tr_question_type",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, answer_correct,"
                        + " format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer)"
                        + " VALUES (?, ?, 'production', 'sa', TRUE, TRUE, 'easy', 'rejected', 'number', '5')",
                mcqQuestion("qc_passed"), teacher2);
    }

    @Test
    void reviewTfVaNhanTungY() {
        int q = tfQuestion();
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, answer_correct,"
                        + " format_ok, decision) VALUES (?, ?, 'production', 'tf', TRUE, TRUE, 'rejected')", q, teacher);
        assertViolation("chk_tr_type_columns",
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type, difficulty_label,"
                        + " format_ok, decision) VALUES (?, ?, 'production', 'tf', 'easy', TRUE, 'rejected')", q, teacher);
        int review = tfReview(q, teacher);
        jdbc.update("INSERT INTO teacher_review_statement (review_id, position, reviewer_label, difficulty_label,"
                + " label_correct, final_label) VALUES (?, 'a', 'T', 'easy', TRUE, 'T')", review);
        assertViolation("chk_trs_undecidable_not_correct",
                "INSERT INTO teacher_review_statement (review_id, position, reviewer_label, difficulty_label,"
                        + " label_correct) VALUES (?, 'b', 'undecidable', 'easy', TRUE)", review);
        assertViolation("chk_trs_final_label",
                "INSERT INTO teacher_review_statement (review_id, position, reviewer_label, difficulty_label,"
                        + " label_correct, final_label) VALUES (?, 'c', 'T', 'easy', TRUE, 'U')", review);
        int mcqReview = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'A', TRUE, TRUE, 'easy', 'approved_as_is', 'A')", mcqQuestion("qc_passed"), teacher);
        assertViolation("fk_trs_review",
                "INSERT INTO teacher_review_statement (review_id, position, reviewer_label, difficulty_label,"
                        + " label_correct) VALUES (?, 'a', 'T', 'easy', TRUE)", mcqReview);
    }

    @Test
    void maLyDoMoi() {
        int review = tfReview(tfQuestion(), teacher);
        jdbc.update("INSERT INTO teacher_review_reason (review_id, reason_code) VALUES (?, 'statement_undecidable')",
                review);
        jdbc.update("INSERT INTO teacher_review_reason (review_id, reason_code) VALUES (?, 'answer_ambiguous')", review);
        jdbc.update("INSERT INTO teacher_review_reason (review_id, reason_code) VALUES (?, 'answer_none_correct')",
                review);
        assertViolation("chk_trr_reason_code",
                "INSERT INTO teacher_review_reason (review_id, reason_code) VALUES (?, 'rounding_instruction_missing')",
                review);
    }

    // ---------- quality_check_result, answer_check_statement ----------

    @Test
    void ketQuaSolverTheoDangCau() {
        int mcq = mcqQuestion("generated");
        assertViolation("chk_qcr_judge_only_answer_check",
                "INSERT INTO quality_check_result (question_id, check_type, passed, judge_model)"
                        + " VALUES (?, 'answer_check', FALSE, 'm')", mcq);
        assertViolation("chk_qcr_judge_only_answer_check",
                "INSERT INTO quality_check_result (question_id, check_type, passed, judge_answer, judge_model,"
                        + " judge_sa_kind) VALUES (?, 'answer_check', FALSE, 'A', 'm', 'number')", mcq);
        int sa = saQuestion("5");
        assertViolation("fk_qcr_question_type",
                "INSERT INTO quality_check_result (question_id, check_type, passed, judge_answer, judge_model)"
                        + " VALUES (?, 'answer_check', FALSE, 'A', 'm')", sa);
        assertViolation("chk_qcr_judge_only_answer_check",
                "INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_model,"
                        + " judge_sa_kind) VALUES (?, 'sa', 'answer_check', FALSE, 'm', 'unparseable')", sa);
        assertViolation("chk_qcr_judge_only_answer_check",
                "INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_model,"
                        + " judge_sa_kind, judge_sa_answer) VALUES (?, 'sa', 'answer_check', FALSE, 'm', 'ambiguous', '5')",
                sa);
        assertViolation("chk_qcr_judge_only_answer_check",
                "INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_sa_kind)"
                        + " VALUES (?, 'sa', 'format', TRUE, 'number')", sa);
        insert("INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_model,"
                + " judge_sa_kind, judge_sa_answer) VALUES (?, 'sa', 'answer_check', TRUE, 'm', 'number', '0.5')", sa);
        insert("INSERT INTO quality_check_result (question_id, question_type, check_type, passed)"
                + " VALUES (?, 'sa', 'format', TRUE)", sa);
    }

    @Test
    void nhanSolverTungYXoaDayChuyen() {
        int tf = tfQuestion();
        assertViolation("chk_qcr_judge_only_answer_check",
                "INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_answer,"
                        + " judge_model) VALUES (?, 'tf', 'answer_check', FALSE, 'A', 'm')", tf);
        int qcr = insert("INSERT INTO quality_check_result (question_id, question_type, check_type, passed, judge_model)"
                + " VALUES (?, 'tf', 'answer_check', FALSE, 'm')", tf);
        for (String pos : new String[] {"a", "b", "c", "d"}) {
            jdbc.update("INSERT INTO answer_check_statement (qc_result_id, position, judge_label) VALUES (?, ?, ?)",
                    qcr, pos, pos.equals("d") ? "undecidable" : "T");
        }
        assertViolation("chk_acs_label",
                "UPDATE answer_check_statement SET judge_label = 'yes' WHERE qc_result_id = ? AND position = 'a'", qcr);
        int mcqQcr = insert("INSERT INTO quality_check_result (question_id, check_type, passed)"
                + " VALUES (?, 'format', TRUE)", mcqQuestion("generated"));
        assertViolation("fk_acs_result",
                "INSERT INTO answer_check_statement (qc_result_id, position, judge_label) VALUES (?, 'a', 'T')", mcqQcr);

        jdbc.update("DELETE FROM quality_check_result WHERE id = ?", qcr);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM answer_check_statement WHERE qc_result_id = ?",
                Integer.class, qcr));
    }

    // ---------- bank_item, bank_item_statement ----------

    @Test
    void nganHangTheoDangCau() {
        int mcq = mcqQuestion("in_bank");
        int mcqReview = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'A', TRUE, TRUE, 'easy', 'approved_as_is', 'A')", mcq, teacher);
        assertViolation("chk_bi_type_content",
                "INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c,"
                        + " option_d, correct_option, difficulty, sa_answer) VALUES (?, ?, 'Q', 'a', 'b', 'c', 'd', 'A',"
                        + " 'easy', '5')", mcq, mcqReview);
        assertViolation("chk_bi_type_content",
                "INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c,"
                        + " option_d, correct_option, difficulty) VALUES (?, ?, 'Q', 'a', ' ', 'c', 'd', 'A', 'easy')",
                mcq, mcqReview);
        int mcqItem = insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, difficulty) VALUES (?, ?, 'Q', 'a', 'b', 'c', 'd', 'A', 'easy')",
                mcq, mcqReview);
        assertEquals("mcq", jdbc.queryForObject("SELECT question_type FROM bank_item WHERE id = ?", String.class, mcqItem));

        int sa = saQuestion("$\\frac{1}{2}$");
        int saReview = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, question_type,"
                + " answer_correct, format_ok, difficulty_label, decision, reviewer_sa_kind, reviewer_sa_answer,"
                + " final_sa_answer) VALUES (?, ?, 'production', 'sa', TRUE, TRUE, 'medium', 'approved_as_is', 'number',"
                + " '0,5', '0,5')", sa, teacher);
        assertViolation("chk_bi_type_content",
                "INSERT INTO bank_item (source_question_id, review_id, question_type, question_text, sa_answer)"
                        + " VALUES (?, ?, 'sa', 'Q', '0,5')", sa, saReview);
        assertViolation("chk_bi_type_content",
                "INSERT INTO bank_item (source_question_id, review_id, question_type, question_text, sa_answer,"
                        + " difficulty) VALUES (?, ?, 'sa', 'Q', '  ', 'medium')", sa, saReview);
        // Review mcq chưa dùng cho bank_item nào, để hai ca khóa ngoại dưới không vấp uq_bi_review.
        int spareMcqReview = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose,"
                + " reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option)"
                + " VALUES (?, ?, 'production', 'A', TRUE, TRUE, 'easy', 'approved_as_is', 'A')",
                mcqQuestion("in_bank"), teacher);
        // Dòng đúng hình dạng mcq nhưng câu gốc là sa: chỉ khóa ngoại kép chặn được.
        assertViolation("fk_bi_source_type",
                "INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c,"
                        + " option_d, correct_option, difficulty) VALUES (?, ?, 'Q', 'a', 'b', 'c', 'd', 'A', 'easy')",
                sa, spareMcqReview);
        insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text, sa_answer,"
                + " difficulty) VALUES (?, ?, 'sa', 'Q', '0,5', 'medium')", sa, saReview);

        int tf = tfQuestion();
        int tfRev = tfReview(tf, teacher);
        assertViolation("chk_bi_type_content",
                "INSERT INTO bank_item (source_question_id, review_id, question_type, question_text, difficulty)"
                        + " VALUES (?, ?, 'tf', 'Q', 'easy')", tf, tfRev);
        assertViolation("fk_bi_review_type",
                "INSERT INTO bank_item (source_question_id, review_id, question_type, question_text)"
                        + " VALUES (?, ?, 'tf', 'Q')", tf, spareMcqReview);
        int tfItem = insert("INSERT INTO bank_item (source_question_id, review_id, question_type, question_text)"
                + " VALUES (?, ?, 'tf', 'Q')", tf, tfRev);
        jdbc.update("INSERT INTO bank_item_statement (bank_item_id, position, statement_text, label, difficulty)"
                + " VALUES (?, 'a', 'x', 'T', 'easy')", tfItem);
        assertViolation("chk_bis_not_blank",
                "INSERT INTO bank_item_statement (bank_item_id, position, statement_text, label, difficulty)"
                        + " VALUES (?, 'b', '  ', 'F', 'easy')", tfItem);
        assertViolation("fk_bis_item",
                "INSERT INTO bank_item_statement (bank_item_id, position, statement_text, label, difficulty)"
                        + " VALUES (?, 'a', 'x', 'T', 'easy')", mcqItem);

        jdbc.update("DELETE FROM bank_item WHERE id = ?", tfItem);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item_statement WHERE bank_item_id = ?",
                Integer.class, tfItem));
        assertNull(jdbc.queryForObject("SELECT sa_answer FROM bank_item WHERE id = ?", String.class, mcqItem));
    }
}
