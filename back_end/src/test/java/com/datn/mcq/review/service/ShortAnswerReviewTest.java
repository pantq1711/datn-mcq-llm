package com.datn.mcq.review.service;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chấm mù câu Trả lời ngắn (GĐ2, bản sửa 1 mục 8 và quyết định A, E): kiểm tra request theo dạng câu với thông báo
 * trung tính, answer_correct theo giá trị (LENIENT), đáp số cuối vào ngân hàng ở chế độ STRICT, rollback khi lỗi.
 * Dữ liệu ghi thẳng bằng SQL trên datn_mcq_test; gọi qua HTTP thật.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ShortAnswerReviewTest {

    private static final String LLM_ANSWER = "$-\\frac{3}{4}$";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private int template;
    private int teacher;
    private int saRun;
    private int saQuestion;
    private int mcqQuestion;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "ShortAnswerReviewTest.setUp");
        int subject = insert("INSERT INTO subject (name) VALUES ('Toán Chấm SA')");
        teacher = insert("INSERT INTO users (name, email, role) VALUES ('GV', 'gv.sareview@test', 'teacher')");
        template = insert("INSERT INTO question_template (subject_id, source_question, is_active, grade_level, topic)"
                + " VALUES (?, 'Mẫu', TRUE, 8, 'Phương trình')", subject);
        saRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose,"
                + " prompt_version, question_type) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production', 'sa-v0-draft',"
                + " 'sa')", subject);
        saQuestion = saQuestion(LLM_ANSWER, "qc_passed");
        int mcqRun = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'few-shot-structured', 'm', 0.7, 'p', 'production')", subject);
        mcqQuestion = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, created_at)"
                + " VALUES (?, ?, 'Tính $1+1$', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'qc_passed', now())",
                template, mcqRun);
    }

    private int saQuestion(String answer, String status) {
        return insert("INSERT INTO generated_question (template_id, run_id, question_type, question_text, sa_answer,"
                + " explanation, raw_llm_output, parse_success, status, llm_difficulty, created_at)"
                + " VALUES (?, ?, 'sa', 'Nghiệm của $4x + 3 = 0$ là bao nhiêu?', ?, 'LỜI GIẢI BÍ MẬT', '{}', TRUE, ?,"
                + " 'medium', now())", template, saRun, answer, status);
    }

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    private ResultActions submit(int questionId, String fields) throws Exception {
        String body = "{\"reviewerId\": " + teacher + ", \"formatOk\": true, " + fields + "}";
        return mockMvc.perform(post("/api/reviews/questions/" + questionId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertRejected(int questionId, String fields, String message) throws Exception {
        String body = submit(questionId, fields)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(message))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("frac{3}{4}"), "thông báo không được lộ đáp số LLM");
        assertFalse(body.contains("BÍ MẬT"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bank_item", Integer.class));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, questionId));
    }

    private static final String SA = "\"questionType\": \"sa\", \"difficultyLabel\": \"medium\", ";

    @Test
    void requestKhongHopLeTra400TrungTinh() throws Exception {
        assertRejected(saQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerAnswer\": \"A\","
                + " \"reviewerSaKind\": \"number\", \"reviewerSaAnswer\": \"1\"",
                "Dữ liệu đánh giá không khớp dạng câu hỏi.");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"finalOption\": \"A\","
                + " \"reviewerSaKind\": \"number\", \"reviewerSaAnswer\": \"1\", \"finalSaAnswer\": \"1\"",
                "Dữ liệu đánh giá không khớp dạng câu hỏi.");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"editedOptionA\": \"x\","
                + " \"reviewerSaKind\": \"number\", \"reviewerSaAnswer\": \"1\", \"finalSaAnswer\": \"1\"",
                "Dữ liệu đánh giá không khớp dạng câu hỏi.");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_as_is\"",
                "Phải chọn reviewerSaKind (number, ambiguous)");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\"",
                "Khi chọn 'number', phải nhập reviewerSaAnswer");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"x = 2\"", "Đáp số giáo viên nhập không đọc được thành một số.");
        assertRejected(saQuestion, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"ambiguous\","
                + " \"reviewerSaAnswer\": \"1\", \"reasonCodes\": [\"answer_ambiguous\"]",
                "Khi chọn 'ambiguous', reviewerSaAnswer phải để trống");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"ambiguous\"",
                "Khi reviewerSaKind là 'ambiguous', không được chọn 'approved_as_is'");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"-0,75\", \"finalSaAnswer\": \"-3/4\"",
                "Khi chọn approved_as_is, finalSaAnswer phải trùng reviewerSaAnswer");
        // Quyết định E: đáp số cuối dùng chế độ STRICT, nên "05" chỉ dùng được ở bước chấm mù. Quyết định 3 sau GĐ2:
        // thông báo nêu cách sửa, lấy từ chính chuỗi giáo viên nhập (không lộ đáp số LLM).
        assertRejected(saQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"05\"", "Đáp số có chữ số 0 thừa ở đầu, hãy nhập 5");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"finalSaAnswer\": \"-007,5\"",
                "Đáp số có chữ số 0 thừa ở đầu, hãy nhập -7,5");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\"", "Khi chọn approved_with_edit, bắt buộc nhập finalSaAnswer");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"finalSaAnswer\": \"-0\"", "Đáp số cuối không hợp lệ: số 0 mang dấu âm");
        assertRejected(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"finalSaAnswer\": \"\\\\frac{1}{2}\"",
                "Bản sửa còn lỗi định dạng công thức LaTeX: final_sa_answer: "
                        + "Có lệnh LaTeX nằm ngoài cặp $...$ (thiếu dấu $ bao quanh công thức)");
        assertRejected(saQuestion, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"finalSaAnswer\": \"1\", \"reasonCodes\": [\"content_unclear\"]",
                "Khi chọn 'rejected', finalSaAnswer phải để trống (null)");
        assertRejected(saQuestion, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\"", "Khi chọn 'rejected', phải có ít nhất 1 lý do trong reasonCodes");
        assertRejected(saQuestion, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"reasonCodes\": [\"options_indistinct\"]",
                "Mã lý do 'options_indistinct' không áp dụng cho dạng câu này.");
        assertRejected(saQuestion, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"reasonCodes\": [\"statement_undecidable\"]",
                "Mã lý do 'statement_undecidable' không áp dụng cho dạng câu này.");
        // Gửi form MCQ cho câu sa và ngược lại: không khớp dạng câu.
        assertRejected(saQuestion, "\"reviewerAnswer\": \"B\", \"difficultyLabel\": \"easy\","
                + " \"decision\": \"approved_as_is\"", "Dữ liệu đánh giá không khớp dạng câu hỏi.");
        assertRejected(mcqQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"2\"", "Dữ liệu đánh giá không khớp dạng câu hỏi.");
    }

    @Test
    void answerCorrectTheoGiaTri() throws Exception {
        submit(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"-0.75\", \"finalSaAnswer\": \"$-\\\\frac{3}{4}$\"")
                .andExpect(status().isOk());
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT answer_correct FROM teacher_review WHERE question_id = ?", Boolean.class, saQuestion));

        int wrong = saQuestion(LLM_ANSWER, "qc_passed");
        submit(wrong, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"0,75\", \"reasonCodes\": [\"content_inaccurate\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionStatus").value("rejected"));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "SELECT answer_correct FROM teacher_review WHERE question_id = ?", Boolean.class, wrong));

        int ambiguous = saQuestion(LLM_ANSWER, "qc_passed");
        submit(ambiguous, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"ambiguous\","
                + " \"reasonCodes\": [\"answer_ambiguous\"]")
                .andExpect(status().isOk());
        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", ambiguous);
        assertEquals(Boolean.FALSE, r.get("answer_correct"));
        assertEquals("ambiguous", r.get("reviewer_sa_kind"));
        assertNull(r.get("reviewer_sa_answer"));
        assertEquals("answer_ambiguous", jdbc.queryForObject(
                "SELECT reason_code FROM teacher_review_reason WHERE review_id = ?", String.class, r.get("id")));

        // Giáo viên nhập "05" (LENIENT) được nhận ở bước chấm mù; đáp số cuối viết chuẩn.
        int lenient = saQuestion("5", "qc_passed");
        submit(lenient, SA + "\"decision\": \"approved_with_edit\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \" 05 \", \"finalSaAnswer\": \"5\"")
                .andExpect(status().isOk());
        Map<String, Object> l = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", lenient);
        assertEquals(Boolean.TRUE, l.get("answer_correct"));
        assertEquals("05", l.get("reviewer_sa_answer"));
        assertEquals(Boolean.FALSE, l.get("edited"));
        assertEquals("5", jdbc.queryForObject("SELECT sa_answer FROM bank_item WHERE source_question_id = ?",
                String.class, lenient));
    }

    @Test
    void dapSoLlmKhongConPhanTichDuocThiAnswerCorrectNull() throws Exception {
        int legacy = saQuestion("x = 1", "qc_passed");
        submit(legacy, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"reasonCodes\": [\"content_unclear\"]")
                .andExpect(status().isOk());
        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", legacy);
        assertNull(r.get("answer_correct"));
        assertEquals("sa", r.get("question_type"));
    }

    @Test
    void duyetCoSuaVaoNganHang() throws Exception {
        String body = submit(saQuestion, SA + "\"decision\": \"approved_with_edit\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"-0,75\", \"finalSaAnswer\": \"-0,75\","
                + " \"editedQuestionText\": \"Nghiệm của phương trình $4x + 3 = 0$ bằng bao nhiêu?\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.finalSaAnswer").value("-0,75"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("answerCorrect"));
        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", saQuestion);
        assertEquals(Boolean.TRUE, r.get("edited"));
        assertTrue(((Number) r.get("edit_distance")).intValue() > 0);
        assertNull(r.get("edited_option_a"));
        Map<String, Object> bank = jdbc.queryForMap("SELECT * FROM bank_item WHERE source_question_id = ?", saQuestion);
        assertEquals("Nghiệm của phương trình $4x + 3 = 0$ bằng bao nhiêu?", bank.get("question_text"));
        assertEquals("-0,75", bank.get("sa_answer"));
        assertEquals("LỜI GIẢI BÍ MẬT", bank.get("explanation"));
        assertNull(bank.get("option_a"));
    }

    @Test
    void loiKhiGoKhoiNganHangThiKhongGhiDo() throws Exception {
        submit(saQuestion, SA + "\"decision\": \"approved_as_is\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"-0,75\"").andExpect(status().isOk());
        int bankItem = jdbc.queryForObject("SELECT id FROM bank_item WHERE source_question_id = ?", Integer.class,
                saQuestion);
        int exam = insert("INSERT INTO exam (exam_code, title, subject_id, created_by) VALUES ('SA-1', 'Đề', "
                + "(SELECT subject_id FROM experiment_run WHERE id = ?), ?)", saRun, teacher);
        jdbc.update("INSERT INTO exam_question (exam_id, bank_item_id, position) VALUES (?, ?, 1)", exam, bankItem);
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", saQuestion);

        submit(saQuestion, SA + "\"decision\": \"rejected\", \"reviewerSaKind\": \"number\","
                + " \"reviewerSaAnswer\": \"1\", \"reasonCodes\": [\"content_inaccurate\"]")
                .andExpect(status().isConflict());

        assertEquals(before, jdbc.queryForMap("SELECT * FROM teacher_review WHERE question_id = ?", saQuestion));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_review_reason", Integer.class));
        assertEquals("in_bank", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?",
                String.class, saQuestion));
        assertEquals("-0,75", jdbc.queryForObject("SELECT sa_answer FROM bank_item WHERE id = ?", String.class,
                bankItem));
    }
}
