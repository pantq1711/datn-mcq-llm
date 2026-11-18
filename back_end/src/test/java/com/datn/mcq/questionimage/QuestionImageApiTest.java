package com.datn.mcq.questionimage;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.questionimage.model.QuestionImage;
import com.datn.mcq.questionimage.repository.QuestionImageRepository;
import com.datn.mcq.questionimage.service.QuestionImageSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.question-images.url-signing-key=dGVzdC1vbmx5LXF1ZXN0aW9uLWltYWdlLXVybC1zaWduaW5nLWtleS1ub3QtcHJvZA==",
        "app.question-images.url-ttl=1h"
})
@Import(TestSafetyConfiguration.class)
class QuestionImageApiTest {

    private static final byte[] FAKE_PNG_DATA = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03};

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private QuestionImageRepository imageRepository;
    @Autowired
    private QuestionImageSigner signer;

    private MockMvc mockMvc;
    private int teacherId;
    private int otherTeacherId;
    private int batchId;
    private int subjectId;
    private QuestionImage testImage;
    private String testSha256;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "QuestionImageApiTest.setUp");

        teacherId = AuthTestSupport.insertUser(jdbc, "GV Chủ", "gv.chu@test.vn", "teacher", "matkhau-1", true);
        otherTeacherId = AuthTestSupport.insertUser(jdbc, "GV Khác", "gv.khac@test.vn", "teacher", "matkhau-2", true);

        subjectId = jdbc.queryForObject(
                "INSERT INTO subject (name) VALUES ('Toán học') RETURNING id",
                Integer.class
        );

        batchId = jdbc.queryForObject(
                "INSERT INTO import_batch (owner_id, display_name, file_sha256, subject_id, grade_level, topic, status, question_count, preview, created_at, expires_at) " +
                "VALUES (?, 'Bộ đề Toán 8', 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90', ?, 8, 'Hàm số', 'preview', 1, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '24 hours') RETURNING id",
                Integer.class,
                teacherId,
                subjectId
        );

        testSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(FAKE_PNG_DATA));

        testImage = new QuestionImage(
                teacherId,
                batchId,
                "mcq-001",
                null,
                "stem",
                (short) 1,
                "image/png",
                FAKE_PNG_DATA,
                FAKE_PNG_DATA.length,
                300,
                200,
                (short) 50,
                testSha256,
                "png"
        );
        testImage = imageRepository.save(testImage);
    }

    @Test
    @DisplayName("Tải ảnh hợp lệ: không cần JWT, trả 200, đúng Content-Type, Cache-Control, ETag và security headers")
    void taiAnhHopLe_KhongKemJwt_Tra200VaHeaderDayDu() throws Exception {
        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        String sig = signer.sign(testImage.getId(), exp, teacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(teacherId))
                        .param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(FAKE_PNG_DATA))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + testSha256 + "\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    @DisplayName("If-None-Match khớp ETag: trả 304 Not Modified và thân rỗng")
    void ifNoneMatchKhop_Tra304NotModified() throws Exception {
        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        String sig = signer.sign(testImage.getId(), exp, teacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(teacherId))
                        .param("sig", sig)
                        .header(HttpHeaders.IF_NONE_MATCH, "\"" + testSha256 + "\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + testSha256 + "\""));
    }

    @Test
    @DisplayName("Sai chữ ký hoặc sửa đổi tham số: trả 404 IMAGE_NOT_FOUND")
    void saiChuKy_Tra404ImageNotFound() throws Exception {
        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        String sig = signer.sign(testImage.getId(), exp, teacherId);

        // Đổi chữ ký giả
        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(teacherId))
                        .param("sig", "0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));

        // Giữ nguyên sig nhưng đổi userId u
        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(otherTeacherId))
                        .param("sig", sig))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("URL hết hạn: trả 404 IMAGE_NOT_FOUND")
    void urlHetHan_Tra404ImageNotFound() throws Exception {
        Instant now = Instant.now();
        long expiredTimestamp = now.getEpochSecond() - 3600; // đã hết hạn 1 tiếng trước
        String sig = signer.sign(testImage.getId(), expiredTimestamp, teacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(expiredTimestamp))
                        .param("u", String.valueOf(teacherId))
                        .param("sig", sig))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Tài khoản người dùng bị khóa: trả 404 IMAGE_NOT_FOUND")
    void userBiKhoa_Tra404ImageNotFound() throws Exception {
        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        String sig = signer.sign(testImage.getId(), exp, teacherId);

        // Khóa tài khoản
        jdbc.update("UPDATE users SET enabled = false WHERE id = ?", teacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(teacherId))
                        .param("sig", sig))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Ảnh preview của người khác: giáo viên khác ký URL vẫn bị trả 404")
    void nguoiDungKhacKhongCoQuyen_Tra404() throws Exception {
        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        // Giáo viên khác tự ký hoặc được đưa link gắn u=otherTeacherId
        String sig = signer.sign(testImage.getId(), exp, otherTeacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(otherTeacherId))
                        .param("sig", sig))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Lô preview đã hết hạn: trả 404 IMAGE_NOT_FOUND kể cả chính chủ")
    void loHetHan_Tra404ImageNotFound() throws Exception {
        // Đặt lô preview hết hạn (lùi cả created_at để thỏa chk_ib_expiry; đặt preview = NULL để thỏa chk_ib_preview)
        jdbc.update("UPDATE import_batch SET created_at = CURRENT_TIMESTAMP - INTERVAL '25 hours', expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour', status = 'expired', preview = NULL WHERE id = ?", batchId);

        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        String sig = signer.sign(testImage.getId(), exp, teacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(teacherId))
                        .param("sig", sig))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Ảnh câu ngân hàng công khai: giáo viên khác xem được (200)")
    void anhThuocNganHangCongKhai_NguoiKhacXemDuoc_Tra200() throws Exception {
        // Giả lập câu đã xác nhận vào generated_question và có bank_item công khai
        int templateId = jdbc.queryForObject(
                "INSERT INTO question_template (subject_id, grade_level, topic, source_question) " +
                "VALUES (?, 8, 'Hàm số', 'Đề mẫu') RETURNING id",
                Integer.class, subjectId
        );
        int runId = jdbc.queryForObject(
                "INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose) " +
                "VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production') RETURNING id",
                Integer.class, subjectId
        );
        int gqId = jdbc.queryForObject(
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, origin, import_batch_id) " +
                "VALUES (?, ?, 'mcq', 'Câu 1 ⟦img:" + testImage.getId() + "⟧', 'A', 'B', 'C', 'D', 'A', '{}', 'import', ?) RETURNING id",
                Integer.class, templateId, runId, batchId
        );
        // Gắn generated_question_id cho testImage
        testImage.setGeneratedQuestionId(gqId);
        imageRepository.save(testImage);

        // Tạo teacher_review và bank_item công khai (visible_to_user_id IS NULL)
        int reviewId = jdbc.queryForObject(
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option) " +
                "VALUES (?, ?, 'production', 'A', true, true, 'easy', 'approved_as_is', 'A') RETURNING id",
                Integer.class, gqId, teacherId
        );
        jdbc.update(
                "INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c, option_d, correct_option, difficulty, visible_to_user_id) " +
                "VALUES (?, ?, 'Câu 1', 'A', 'B', 'C', 'D', 'A', 'easy', NULL)",
                gqId, reviewId
        );

        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        String sig = signer.sign(testImage.getId(), exp, otherTeacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(otherTeacherId))
                        .param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FAKE_PNG_DATA));
    }

    @Test
    @DisplayName("Ảnh câu ngân hàng riêng tư của người khác: giáo viên khác không xem được (404)")
    void anhThuocNganHangRiengTu_NguoiKhacKhongXemDuoc_Tra404() throws Exception {
        int templateId = jdbc.queryForObject(
                "INSERT INTO question_template (subject_id, grade_level, topic, source_question) " +
                "VALUES (?, 8, 'Hàm số', 'Đề mẫu 2') RETURNING id",
                Integer.class, subjectId
        );
        int runId = jdbc.queryForObject(
                "INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose) " +
                "VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production') RETURNING id",
                Integer.class, subjectId
        );
        int gqId = jdbc.queryForObject(
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, origin, import_batch_id) " +
                "VALUES (?, ?, 'mcq', 'Câu riêng tư ⟦img:" + testImage.getId() + "⟧', 'A', 'B', 'C', 'D', 'A', '{}', 'import', ?) RETURNING id",
                Integer.class, templateId, runId, batchId
        );
        testImage.setGeneratedQuestionId(gqId);
        imageRepository.save(testImage);

        // Tạo teacher_review và bank_item RIÊNG TƯ của teacherId (visible_to_user_id = teacherId)
        int reviewId = jdbc.queryForObject(
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option) " +
                "VALUES (?, ?, 'production', 'A', true, true, 'easy', 'approved_as_is', 'A') RETURNING id",
                Integer.class, gqId, teacherId
        );
        jdbc.update(
                "INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c, option_d, correct_option, difficulty, visible_to_user_id) " +
                "VALUES (?, ?, 'Câu riêng tư', 'A', 'B', 'C', 'D', 'A', 'easy', ?)",
                gqId, reviewId, teacherId
        );

        Instant now = Instant.now();
        long exp = signer.calculateExp(now);
        String sig = signer.sign(testImage.getId(), exp, otherTeacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(otherTeacherId))
                        .param("sig", sig))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    /** Gắn testImage vào một câu nhập đã xác nhận, có bank_item công khai đã bị gỡ (ẩn mềm, V30). */
    private void ganAnhVaoCauNganHangCongKhaiDaGo() {
        int templateId = jdbc.queryForObject(
                "INSERT INTO question_template (subject_id, grade_level, topic, source_question) " +
                "VALUES (?, 8, 'Hàm số', 'Đề mẫu đã gỡ') RETURNING id",
                Integer.class, subjectId
        );
        int runId = jdbc.queryForObject(
                "INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text, purpose) " +
                "VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production') RETURNING id",
                Integer.class, subjectId
        );
        int gqId = jdbc.queryForObject(
                "INSERT INTO generated_question (template_id, run_id, question_type, question_text, option_a, option_b, option_c, option_d, correct_option, raw_llm_output, origin, import_batch_id) " +
                "VALUES (?, ?, 'mcq', 'Câu đã gỡ ⟦img:" + testImage.getId() + "⟧', 'A', 'B', 'C', 'D', 'A', '{}', 'import', ?) RETURNING id",
                Integer.class, templateId, runId, batchId
        );
        testImage.setGeneratedQuestionId(gqId);
        imageRepository.save(testImage);

        int reviewId = jdbc.queryForObject(
                "INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer, answer_correct, format_ok, difficulty_label, decision, final_option) " +
                "VALUES (?, ?, 'production', 'A', true, true, 'easy', 'approved_as_is', 'A') RETURNING id",
                Integer.class, gqId, teacherId
        );
        jdbc.update(
                "INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b, option_c, option_d, correct_option, difficulty, visible_to_user_id, removed_at, removed_by) " +
                "VALUES (?, ?, 'Câu đã gỡ', 'A', 'B', 'C', 'D', 'A', 'easy', NULL, CURRENT_TIMESTAMP, ?)",
                gqId, reviewId, teacherId
        );
    }

    @Test
    @DisplayName("Ảnh câu ngân hàng công khai đã gỡ: giáo viên khác không xem được (404)")
    void anhCauNganHangDaGo_NguoiKhacKhongXemDuoc_Tra404() throws Exception {
        ganAnhVaoCauNganHangCongKhaiDaGo();

        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, otherTeacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(otherTeacherId))
                        .param("sig", sig))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Ảnh câu ngân hàng đã gỡ: người tải vẫn xem được ảnh của mình (200)")
    void anhCauNganHangDaGo_NguoiTaiVanXemDuoc_Tra200() throws Exception {
        ganAnhVaoCauNganHangCongKhaiDaGo();

        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, teacherId);

        mockMvc.perform(get("/api/question-images/{id}", testImage.getId())
                        .param("exp", String.valueOf(exp))
                        .param("u", String.valueOf(teacherId))
                        .param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FAKE_PNG_DATA));
    }

    // ===== Thiếu / sai kiểu tham số: hợp đồng yêu cầu luôn 404 IMAGE_NOT_FOUND (không phân biệt) =====

    private void expectImageNotFound(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("T1: thiếu sig trả 404 IMAGE_NOT_FOUND")
    void thieuSig_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("exp", String.valueOf(exp))
                .param("u", String.valueOf(teacherId)));
    }

    @Test
    @DisplayName("T2: thiếu exp trả 404 IMAGE_NOT_FOUND")
    void thieuExp_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, teacherId);
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("u", String.valueOf(teacherId))
                .param("sig", sig));
    }

    @Test
    @DisplayName("T3: thiếu u trả 404 IMAGE_NOT_FOUND")
    void thieuU_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, teacherId);
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("exp", String.valueOf(exp))
                .param("sig", sig));
    }

    @Test
    @DisplayName("T4: thiếu cả ba tham số trả 404 IMAGE_NOT_FOUND")
    void thieuCaBaThamSo_Tra404() throws Exception {
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId()));
    }

    @Test
    @DisplayName("T5: exp không phải số trả 404 IMAGE_NOT_FOUND")
    void expKhongPhaiSo_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, teacherId);
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("exp", "abc")
                .param("u", String.valueOf(teacherId))
                .param("sig", sig));
    }

    @Test
    @DisplayName("T6: u không phải số trả 404 IMAGE_NOT_FOUND")
    void uKhongPhaiSo_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, teacherId);
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("exp", String.valueOf(exp))
                .param("u", "x")
                .param("sig", sig));
    }

    @Test
    @DisplayName("T7: exp tràn long trả 404 IMAGE_NOT_FOUND")
    void expTranSo_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, teacherId);
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("exp", "99999999999999999999")
                .param("u", String.valueOf(teacherId))
                .param("sig", sig));
    }

    @Test
    @DisplayName("T8: id không phải số trả 404 IMAGE_NOT_FOUND")
    void idKhongPhaiSo_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(testImage.getId(), exp, teacherId);
        expectImageNotFound(get("/api/question-images/{id}", "abc")
                .param("exp", String.valueOf(exp))
                .param("u", String.valueOf(teacherId))
                .param("sig", sig));
    }

    @Test
    @DisplayName("T9: sig rỗng hoặc sai độ dài trả 404 IMAGE_NOT_FOUND")
    void sigRongHoacSaiDoDai_Tra404() throws Exception {
        long exp = signer.calculateExp(Instant.now());
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("exp", String.valueOf(exp))
                .param("u", String.valueOf(teacherId))
                .param("sig", ""));
        expectImageNotFound(get("/api/question-images/{id}", testImage.getId())
                .param("exp", String.valueOf(exp))
                .param("u", String.valueOf(teacherId))
                .param("sig", "abc"));
    }

    @Test
    @DisplayName("T10: id không tồn tại với chữ ký đúng trả 404 IMAGE_NOT_FOUND")
    void idKhongTonTai_ChuKyDung_Tra404() throws Exception {
        long missingId = testImage.getId() + 1_000_000L;
        long exp = signer.calculateExp(Instant.now());
        String sig = signer.sign(missingId, exp, teacherId);
        expectImageNotFound(get("/api/question-images/{id}", missingId)
                .param("exp", String.valueOf(exp))
                .param("u", String.valueOf(teacherId))
                .param("sig", sig));
    }
}
