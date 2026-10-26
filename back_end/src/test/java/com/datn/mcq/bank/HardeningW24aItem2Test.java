package com.datn.mcq.bank;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.service.DedupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-4a Mục 2: Kiểm thử quyền riêng tư ngân hàng và phạm vi ứng viên kiểm trùng dedup.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class HardeningW24aItem2Test {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DedupService dedupService;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private int teacher1Id;
    private int teacher2Id;
    private int adminId;
    private int subjectId;
    private int templateId;
    private int runId;
    private int editedBankItemId;
    private int privateBankItemAId;
    private int questionLlmId;
    private int questionEditId;

    private static final String STEM = "Cho tam giác ABC vuông tại A. Tính độ dài cạnh BC biết AB = 3 và AC = 4.";

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbc, "HardeningW24aItem2Test");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        teacher1Id = insert("INSERT INTO users (email, name, role) VALUES ('t1@test.com', 'Cô An', 'teacher')");
        teacher2Id = insert("INSERT INTO users (email, name, role) VALUES ('t2@test.com', 'Thầy Bình', 'teacher')");
        adminId = insert("INSERT INTO users (email, name, role) VALUES ('admin@test.com', 'Quản trị viên', 'admin')");

        subjectId = insert("INSERT INTO subject (name) VALUES ('Toán')");
        templateId = insert("INSERT INTO question_template (subject_id, source_question, is_active, topic, grade_level) VALUES (?, 'Mẫu', TRUE, 'Hình học', 8)", subjectId);

        runId = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose, prompt_version) VALUES (?, 'zero-shot', 'gemini-test', 0.2, 'prompt', 'production', 'matrix-mcq-v1-draft')", subjectId);

        // Câu LLM gốc
        questionLlmId = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty) VALUES (?, ?, 'Tính cạnh BC', '3', '4', '5', '6', 'C', '{}', true, 'qc_passed', 'easy')", templateId, runId);

        // Câu do Cô An (teacher1) sửa
        questionEditId = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty, final_difficulty, edited_from_question_id) VALUES (?, ?, 'Tính cạnh huyền BC', '3', '4', '5', '6', 'C', '', true, 'in_bank', 'easy', 'easy', ?)", templateId, runId, questionLlmId);

        // Review duyệt câu của Cô An
        int reviewId = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, review_mode, reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production', 'assisted', 'C', TRUE, TRUE, 'easy', 'approved_as_is', 'C')", questionEditId, teacher1Id);

        // Đưa câu vào ngân hàng dùng chung (visible_to_user_id = NULL)
        editedBankItemId = insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c, option_d, correct_option, difficulty, visible_to_user_id) VALUES (?, ?, 'Tính cạnh huyền BC', '3', '4', '5', '6', 'C', 'easy', NULL)", questionEditId, reviewId);

        // Ghi nhật ký sửa của Cô An
        int matrixId = insert("INSERT INTO exam_matrix (name, subject_id, created_by) VALUES ('Ma trận 1', ?, ?)", subjectId, teacher1Id);
        int cellId = insert("INSERT INTO exam_matrix_cell (matrix_id, position, part, question_type, topic, difficulty, question_count, template_id) VALUES (?, 1, 'I', 'mcq', 'Hình học', 'easy', 1, ?)", matrixId, templateId);
        int examId = insert("INSERT INTO exam (exam_code, title, subject_id, created_by, mode, matrix_id) VALUES ('EX01', 'Đề 1', ?, ?, 'teacher', ?)", subjectId, teacher1Id, matrixId);
        int slotId = insert("INSERT INTO exam_slot (exam_id, cell_id, slot_no, question_type, state, bank_item_id, generated_question_id) VALUES (?, ?, 1, 'mcq', 'edited', ?, ?)", examId, cellId, editedBankItemId, questionEditId);
        jdbc.update("INSERT INTO exam_slot_action (slot_id, action, generated_question_id, edit_distance, actor_id, changed_fields) VALUES (?, 'edit', ?, 3, ?, '[\"question_text\"]'::jsonb)",
                slotId, questionEditId, teacher1Id);

        // Tạo thêm 1 câu riêng tư của Cô An (visible_to_user_id = teacher1Id)
        int qPrivate = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty, final_difficulty) VALUES (?, ?, ?, 'A', 'B', 'C', 'D', 'A', '{}', true, 'in_bank', 'easy', 'easy')", templateId, runId, STEM);
        int reviewPrivate = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, review_mode, reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production', 'assisted', 'A', TRUE, TRUE, 'easy', 'approved_as_is', 'A')", qPrivate, teacher1Id);
        privateBankItemAId = insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c, option_d, correct_option, difficulty, visible_to_user_id) VALUES (?, ?, ?, 'A', 'B', 'C', 'D', 'A', 'easy', ?)", qPrivate, reviewPrivate, STEM, teacher1Id);
    }

    private int insert(String sql, Object... args) {
        org.springframework.jdbc.support.GeneratedKeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, kh);
        return ((Number) kh.getKeys().get("id")).intValue();
    }

    // ==========================================
    // 1. Quyền riêng tư ngân hàng (3 vai)
    // ==========================================

    @Test
    void privacy_chinhChuXem_thayTenThatVaId() throws Exception {
        String res = mockMvc.perform(get("/api/bank-items/" + editedBankItemId)
                        .with(AuthTestSupport.teacher(teacher1Id)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        JsonNode json = mapper.readTree(res);
        JsonNode versionEdit = json.get("provenance").get("versions").get(1);
        assertEquals(teacher1Id, versionEdit.get("editedBy").get("id").asInt());
        assertEquals("Cô An", versionEdit.get("editedBy").get("name").asText());

        JsonNode reviewer = json.get("provenance").get("review").get("reviewer");
        assertEquals(teacher1Id, reviewer.get("id").asInt());
        assertEquals("Cô An", reviewer.get("name").asText());
    }

    @Test
    void privacy_giaoVienKhacXem_thayNhanTrungTinhVaAnId() throws Exception {
        String res = mockMvc.perform(get("/api/bank-items/" + editedBankItemId)
                        .with(AuthTestSupport.teacher(teacher2Id)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        JsonNode json = mapper.readTree(res);
        JsonNode versionEdit = json.get("provenance").get("versions").get(1);
        assertTrue(versionEdit.get("editedBy").get("id").isNull());
        assertEquals("Giáo viên khác", versionEdit.get("editedBy").get("name").asText());

        JsonNode reviewer = json.get("provenance").get("review").get("reviewer");
        assertTrue(reviewer.get("id").isNull());
        assertEquals("Giáo viên khác", reviewer.get("name").asText());
    }

    @Test
    void privacy_adminXem_thayTenThatVaId() throws Exception {
        String res = mockMvc.perform(get("/api/bank-items/" + editedBankItemId)
                        .with(AuthTestSupport.admin(adminId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        JsonNode json = mapper.readTree(res);
        JsonNode versionEdit = json.get("provenance").get("versions").get(1);
        assertEquals(teacher1Id, versionEdit.get("editedBy").get("id").asInt());
        assertEquals("Cô An", versionEdit.get("editedBy").get("name").asText());

        JsonNode reviewer = json.get("provenance").get("review").get("reviewer");
        assertEquals(teacher1Id, reviewer.get("id").asInt());
        assertEquals("Cô An", reviewer.get("name").asText());
    }

    // ==========================================
    // 2. Phạm vi kiểm trùng dedup
    // ==========================================

    @Test
    void dedup_trungTrongCungDeVanBiBat() {
        // Tạo câu cùng đề trong DB để thỏa mãn foreign key fk_qcr_compared
        int siblingQuestionId = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty) "
                + "VALUES (?, ?, ?, 'A', 'B', 'C', 'D', 'A', '{}', true, 'generated', 'easy')", templateId, runId, STEM);

        // Giả lập câu fresh đang kiểm tra
        int freshId = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty) "
                + "VALUES (?, ?, ?, 'A', 'B', 'C', 'D', 'A', '{}', true, 'generated', 'easy')", templateId, runId, STEM);

        DedupCandidate subject = new DedupCandidate(freshId, STEM, "A", "B", "C", "D");

        // Ứng viên cùng đề đưa qua extraCandidates
        DedupCandidate sibling = new DedupCandidate(siblingQuestionId, STEM, "A", "B", "C", "D");
        DedupScope scope = DedupScope.forProduction(runId, List.of(runId))
                .withExtraCandidates(List.of(sibling))
                .withCreatorUserId(teacher2Id);

        DedupOutcome outcome = dedupService.checkAndRecord(subject, scope);
        assertEquals(DedupStatus.DUPLICATE, outcome.status());
        assertEquals(siblingQuestionId, outcome.comparedToQuestionId());
    }

    @Test
    void dedup_giaoVienBKhongBiChanBoiCauRiengTuCuaGiaoVienA() {
        // Giáo viên B sinh câu có nội dung giống hệt câu riêng tư của Cô An (STEM)
        int freshId = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty) "
                + "VALUES (?, ?, ?, 'A', 'B', 'C', 'D', 'A', '{}', true, 'generated', 'easy')", templateId, runId, STEM);

        DedupCandidate subject = new DedupCandidate(freshId, STEM, "A", "B", "C", "D");

        // Scope với creatorUserId = teacher2Id (Thầy Bình)
        DedupScope scope = DedupScope.forProduction(runId, List.of(runId))
                .withCreatorUserId(teacher2Id);

        DedupOutcome outcome = dedupService.checkAndRecord(subject, scope);
        // Thầy Bình KHÔNG bị chặn bởi câu riêng tư của Cô An
        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
    }

    @Test
    void dedup_giaoVienABiChanBoiChinhCauRiengTuCuaMinh() {
        // Chính Cô An sinh câu trùng với câu riêng tư cũ của chính mình
        int freshId = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, parse_success, status, llm_difficulty) "
                + "VALUES (?, ?, ?, 'A', 'B', 'C', 'D', 'A', '{}', true, 'generated', 'easy')", templateId, runId, STEM);

        DedupCandidate subject = new DedupCandidate(freshId, STEM, "A", "B", "C", "D");

        // Scope với creatorUserId = teacher1Id (Cô An)
        DedupScope scope = DedupScope.forProduction(runId, List.of(runId))
                .withCreatorUserId(teacher1Id);

        DedupOutcome outcome = dedupService.checkAndRecord(subject, scope);
        // Cô An BỊ chặn bởi câu riêng tư của chính mình
        assertEquals(DedupStatus.DUPLICATE, outcome.status());
    }
}
