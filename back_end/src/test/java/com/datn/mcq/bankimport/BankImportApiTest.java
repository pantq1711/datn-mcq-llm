package com.datn.mcq.bankimport;

import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.bankimport.service.BankImportProperties;
import com.datn.mcq.bankimport.service.BankImportService;
import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.llm.client.GeminiDailyUsageLookup;
import com.datn.mcq.llm.client.GeminiRateLimiter;
import com.datn.mcq.llm.client.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API nhập ngân hàng câu từ Word ({@code /api/bank-imports}) qua HTTP thật (MockMvc + filter chain): bản xem trước, xác
 * nhận vào ngân hàng RIÊNG TƯ của người tải, hủy, quyền sở hữu (lô người khác là 404), giới hạn đầu vào, rollback, không
 * gọi LlmClient và không làm tăng bộ đếm Gemini. Fixture tự dựng bằng {@link DocxBuilder}.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class BankImportApiTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BankImportProperties props;
    @Autowired
    private BankImportService service;
    @Autowired
    private com.datn.mcq.bankimport.service.ImportBatchWriter batchWriter;
    @Autowired
    private ExamSlotRepository examSlotRepository;
    @Autowired
    private GeminiDailyUsageLookup usageLookup;
    @MockitoSpyBean
    private LlmClient llmClient;
    @MockitoSpyBean
    private BankService bankService;

    /** Đoạn ASCII có trong nội dung câu 1 của FixtureDoc.full() (log console có thể làm hỏng chữ tiếng Việt). */
    private static final String CONTENT_ASCII = "\\frac{3}{4}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int other;
    private BankImportProperties saved;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "BankImportApiTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV Nhập", "nhap.a@test.vn", "teacher", "matkhau-dung-1", true);
        other = AuthTestSupport.insertUser(jdbc, "GV Khác", "nhap.b@test.vn", "teacher", "matkhau-dung-2", true);
        saved = props.copy();
        service.resetRateLimitForTests();
    }

    @AfterEach
    void restore() {
        props.restore(saved);
        reset(bankService);
    }

    // ---------- tiện ích ----------

    private ResultActions upload(int owner, String name, byte[] bytes, String... params) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/bank-imports")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes));
        for (int i = 0; i + 1 < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(req.with(AuthTestSupport.teacher(owner)));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode previewOk(int owner, byte[] bytes) throws Exception {
        return json(upload(owner, FixtureDoc.FILE_NAME, bytes).andExpect(status().isCreated()));
    }

    private void assertError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status));
        assertEquals(code, json(r).path("code").asText(), "code");
    }

    private ResultActions confirm(int owner, int batchId, List<String> keys) throws Exception {
        return mockMvc.perform(post("/api/bank-imports/" + batchId + "/confirm").with(AuthTestSupport.teacher(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("keys", keys))));
    }

    private static List<String> keys(JsonNode preview, boolean blocking) {
        List<String> out = new ArrayList<>();
        for (JsonNode i : preview.path("items")) {
            if (i.path("blocking").asBoolean() == blocking) {
                out.add(i.path("key").asText());
            }
        }
        return out;
    }

    /** Lùi thời điểm tạo và hạn của lô về quá khứ (giữ created_at &lt; expires_at theo chk_ib_expiry). */
    private void expire(String where, Object arg) {
        jdbc.update("UPDATE import_batch SET created_at = now() - interval '2 days',"
                + " expires_at = now() - interval '1 minute' WHERE " + where, arg);
    }

    private int rows(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---------- xem trước ----------

    @Test
    void taiFileTraBanXemTruocKhongLuuCauNao() throws Exception {
        JsonNode p = previewOk(teacher, FixtureDoc.full());
        assertTrue(p.path("id").asInt() > 0);
        assertEquals("preview", p.path("status").asText());
        assertEquals(FixtureDoc.DISPLAY_NAME, p.path("displayName").asText());
        assertEquals(8, p.path("gradeLevel").asInt());
        assertEquals(FixtureDoc.TOPIC, p.path("topic").asText());
        assertEquals(22, p.path("questionCount").asInt());
        assertEquals(18, p.path("items").size());
        assertEquals(3, p.path("skipped").path("has_image").asInt());
        assertEquals(1, p.path("skipped").path("formula_unsupported").asInt());
        assertEquals(4, p.path("skippedItems").size());
        assertTrue(p.path("blockedCount").asInt() >= 5, p.toString());
        assertEquals(18 - p.path("blockedCount").asInt(), p.path("importableCount").asInt());
        assertNotNull(p.path("expiresAt").asText(null));
        assertFalse(p.has("ownerId"), "không lộ chủ sở hữu");
        for (String bad : FixtureDoc.FORBIDDEN) {
            assertFalse(p.toString().contains(bad), "lọt: " + bad);
        }
        assertEquals(0, rows("SELECT count(*) FROM generated_question"), "xem trước chưa lưu câu");
        assertEquals(0, rows("SELECT count(*) FROM bank_item"));
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM import_batch WHERE id = ?", p.path("id").asInt());
        assertEquals(teacher, row.get("owner_id"));
        assertEquals(FixtureDoc.DISPLAY_NAME, row.get("display_name"));
        assertTrue(String.valueOf(row.get("file_sha256")).matches("[0-9a-f]{64}"));
        for (Object v : row.values()) {
            for (String bad : FixtureDoc.FORBIDDEN) {
                assertFalse(String.valueOf(v).contains(bad), "DB lọt: " + bad);
            }
        }
        LocalDateTime created = ((java.sql.Timestamp) row.get("created_at")).toLocalDateTime();
        LocalDateTime expires = ((java.sql.Timestamp) row.get("expires_at")).toLocalDateTime();
        assertEquals(Duration.ofHours(24), Duration.between(created, expires));

        JsonNode again = json(mockMvc.perform(get("/api/bank-imports/" + p.path("id").asInt())
                .with(AuthTestSupport.teacher(teacher))).andExpect(status().isOk()));
        assertEquals(p.path("items"), again.path("items"));
    }

    @Test
    void loCuaNguoiKhacLa404() throws Exception {
        int id = previewOk(teacher, FixtureDoc.full()).path("id").asInt();
        assertError(mockMvc.perform(get("/api/bank-imports/" + id).with(AuthTestSupport.teacher(other))), 404,
                "IMPORT_NOT_FOUND");
        assertError(confirm(other, id, List.of("mcq-001")), 404, "IMPORT_NOT_FOUND");
        assertError(mockMvc.perform(delete("/api/bank-imports/" + id).with(AuthTestSupport.teacher(other))), 404,
                "IMPORT_NOT_FOUND");
        assertError(mockMvc.perform(get("/api/bank-imports/999999").with(AuthTestSupport.teacher(teacher))), 404,
                "IMPORT_NOT_FOUND");
        assertEquals("preview", jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class, id));
        mockMvc.perform(get("/api/bank-imports/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void tenFileLaPhaiTruyenKhoiVaChuDe() throws Exception {
        assertError(upload(teacher, "bo cau hoi.docx", FixtureDoc.simpleMcq(2)), 422, "IMPORT_LESSON_UNKNOWN");
        JsonNode p = json(upload(teacher, "bo cau hoi - GV Ai Do.docx", FixtureDoc.simpleMcq(2), "gradeLevel", "7",
                "topic", "Phép cộng").andExpect(status().isCreated()));
        assertEquals(7, p.path("gradeLevel").asInt());
        assertEquals("Phép cộng", p.path("topic").asText());
        assertFalse(p.path("displayName").asText().contains("Ai Do"), p.path("displayName").asText());
    }

    @Test
    void khoiNgoai6Den9LaLoi() throws Exception {
        assertError(upload(teacher, "KHỐI 10 - CHƯƠNG 1 - BÀI 1 - MỆNH ĐỀ.docx", FixtureDoc.simpleMcq(2)), 422,
                "IMPORT_GRADE_INVALID");
    }

    // ---------- giới hạn đầu vào ----------

    @Test
    void fileQuaLon413() throws Exception {
        props.setMaxFileBytes(1000);
        assertError(upload(teacher, FixtureDoc.FILE_NAME, FixtureDoc.full()), 413, "FILE_TOO_LARGE");
    }

    @Test
    void khongPhaiDocx415() throws Exception {
        assertError(upload(teacher, "a.docx", "%PDF-1.4 giả".getBytes(StandardCharsets.US_ASCII)), 415,
                "UNSUPPORTED_FILE_TYPE");
        assertError(upload(teacher, "a.docx", "chỉ là chữ".getBytes(StandardCharsets.UTF_8)), 415,
                "UNSUPPORTED_FILE_TYPE");
        assertError(upload(teacher, "a.docx", new byte[0]), 400, "EMPTY_FILE");
    }

    @Test
    void zipBomBiTuChoi422() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] zeros = new byte[1 << 20];
            for (int i = 0; i < 4; i++) {
                zip.write(zeros);
            }
            zip.closeEntry();
        }
        assertError(upload(teacher, FixtureDoc.FILE_NAME, out.toByteArray()), 422, "INVALID_FILE");
    }

    @Test
    void doctypeBiTuChoi422() throws Exception {
        byte[] docx = new DocxBuilder().documentPrefix("<!DOCTYPE w [<!ENTITY x \"y\">]>")
                .p("PHẦN I. CÂU TRẮC NGHIỆM").bytes();
        assertError(upload(teacher, FixtureDoc.FILE_NAME, docx), 422, "INVALID_FILE");
    }

    @Test
    void khongCoCauNaoVaQuaNhieuCau() throws Exception {
        assertError(upload(teacher, FixtureDoc.FILE_NAME, new DocxBuilder().p("Chỉ có chữ").bytes()), 422,
                "IMPORT_NO_QUESTIONS");
        props.setMaxQuestions(5);
        assertError(upload(teacher, FixtureDoc.FILE_NAME, FixtureDoc.simpleMcq(6)), 422, "IMPORT_TOO_MANY_QUESTIONS");
        upload(teacher, FixtureDoc.FILE_NAME, FixtureDoc.simpleMcq(5)).andExpect(status().isCreated());
    }

    @Test
    void toiDaBaLoChoXacNhan() throws Exception {
        for (int i = 0; i < 3; i++) {
            previewOk(teacher, FixtureDoc.simpleMcq(2));
        }
        assertError(upload(teacher, FixtureDoc.FILE_NAME, FixtureDoc.simpleMcq(2)), 409, "IMPORT_PENDING_LIMIT");
        previewOk(other, FixtureDoc.simpleMcq(2));
        expire("owner_id = ?", teacher);
        previewOk(teacher, FixtureDoc.simpleMcq(2));
    }

    @Test
    void quaSoLanTaiMoiGio429() throws Exception {
        props.setMaxUploadsPerHour(2);
        upload(teacher, "x.docx", new byte[0]);
        previewOk(teacher, FixtureDoc.simpleMcq(1));
        assertError(upload(teacher, FixtureDoc.FILE_NAME, FixtureDoc.simpleMcq(1)), 429, "TOO_MANY_UPLOADS");
        previewOk(other, FixtureDoc.simpleMcq(1));
    }

    // ---------- xác nhận ----------

    @Test
    void xacNhanLuuVaoNganHangRiengTuCuaNguoiTai() throws Exception {
        JsonNode p = previewOk(teacher, FixtureDoc.full());
        int id = p.path("id").asInt();
        List<String> ok = keys(p, false);
        JsonNode r = json(confirm(teacher, id, ok).andExpect(status().isOk()));
        assertEquals(ok.size(), r.path("imported").asInt());
        assertEquals(ok.size(), r.path("bankItemIds").size());

        assertEquals(ok.size(), rows("SELECT count(*) FROM generated_question WHERE origin = 'import' AND import_batch_id = ?",
                id));
        assertEquals(0, rows("SELECT count(*) FROM generated_question WHERE origin = 'llm'"));
        assertEquals(ok.size(), rows("SELECT count(*) FROM generated_question WHERE status = 'in_bank'"
                + " AND llm_difficulty IS NULL AND raw_llm_output = ''"));
        assertEquals(0, rows("SELECT count(*) FROM generated_question WHERE question_type <> 'tf'"
                + " AND final_difficulty IS NULL"));
        assertEquals(ok.size(), rows("SELECT count(*) FROM bank_item WHERE visible_to_user_id = ?", teacher));
        assertEquals(0, rows("SELECT count(*) FROM bank_item WHERE visible_to_user_id IS DISTINCT FROM ?", teacher));
        assertEquals(ok.size(), rows("SELECT count(*) FROM teacher_review WHERE reviewer_id = ? AND review_mode = 'assisted'"
                + " AND decision = 'approved_as_is' AND review_purpose = 'production'", teacher));
        assertEquals(0, rows("SELECT count(*) FROM generation_job"), "không xếp job Solver");
        assertEquals(0, rows("SELECT count(*) FROM quality_check_result"));
        // tf: 4 ý nhận chung mức của câu (tf-001 là [TH])
        List<String> levels = jdbc.queryForList("SELECT s.difficulty FROM bank_item_statement s JOIN bank_item b"
                + " ON b.id = s.bank_item_id JOIN generated_question g ON g.id = b.source_question_id"
                + " WHERE b.question_text LIKE 'Đề dẫn giả một%' ORDER BY s.position", String.class);
        assertEquals(List.of("medium", "medium", "medium", "medium"), levels);
        // câu không lời giải: explanation NULL
        assertNull(jdbc.queryForObject("SELECT explanation FROM bank_item WHERE question_text LIKE ?", String.class,
                FixtureDoc.Q1_MARKER + "%"));
        assertEquals(1, rows("SELECT count(*) FROM question_template WHERE grade_level = 8 AND topic = ?",
                FixtureDoc.TOPIC));

        Map<String, Object> batch = jdbc.queryForMap("SELECT * FROM import_batch WHERE id = ?", id);
        assertEquals("confirmed", batch.get("status"));
        assertNull(batch.get("preview"), "xác nhận xong thì bỏ bản xem trước");
        assertEquals(ok.size(), batch.get("imported_count"));
        assertNotNull(batch.get("confirmed_at"));
        verifyNoInteractions(llmClient);

        assertError(confirm(teacher, id, ok), 409, "IMPORT_NOT_PREVIEW");
        assertError(mockMvc.perform(delete("/api/bank-imports/" + id).with(AuthTestSupport.teacher(teacher))), 409,
                "IMPORT_NOT_PREVIEW");
        JsonNode done = json(mockMvc.perform(get("/api/bank-imports/" + id).with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk()));
        assertEquals("confirmed", done.path("status").asText());
        assertEquals(0, done.path("items").size());
    }

    @Test
    void nganHangChiNguoiTaiThayVaLocOriginImport() throws Exception {
        JsonNode p = previewOk(teacher, FixtureDoc.full());
        List<String> ok = keys(p, false);
        confirm(teacher, p.path("id").asInt(), ok).andExpect(status().isOk());

        JsonNode mine = json(mockMvc.perform(get("/api/bank-items").param("origin", "import").param("size", "50")
                .with(AuthTestSupport.teacher(teacher))).andExpect(status().isOk()));
        assertEquals(ok.size(), mine.path("totalItems").asInt());
        assertEquals("import", mine.path("items").get(0).path("origin").path("type").asText());
        JsonNode ai = json(mockMvc.perform(get("/api/bank-items").param("origin", "ai")
                .with(AuthTestSupport.teacher(teacher))).andExpect(status().isOk()));
        assertEquals(0, ai.path("totalItems").asInt(), "câu nhập không phải câu AI");
        JsonNode theirs = json(mockMvc.perform(get("/api/bank-items").with(AuthTestSupport.teacher(other)))
                .andExpect(status().isOk()));
        assertEquals(0, theirs.path("totalItems").asInt(), "câu riêng tư không hiện với người khác");
        int anyId = mine.path("items").get(0).path("id").asInt();
        mockMvc.perform(get("/api/bank-items/" + anyId).with(AuthTestSupport.teacher(other)))
                .andExpect(status().isNotFound());

        Integer subject = jdbc.queryForObject("SELECT subject_id FROM import_batch WHERE id = ?", Integer.class,
                p.path("id").asInt());
        assertFalse(examSlotRepository.findBankCandidates(-1, teacher, subject, "sa", FixtureDoc.TOPIC, "", "easy", "",
                true, 10).isEmpty(), "chính người tải dùng lại được khi tạo đề");
        assertTrue(examSlotRepository.findBankCandidates(-1, other, subject, "sa", FixtureDoc.TOPIC, "", "easy", "",
                true, 10).isEmpty(), "không là ứng viên của người khác");
    }

    @Test
    void cauCoCoChanKhongChonDuoc() throws Exception {
        JsonNode p = previewOk(teacher, FixtureDoc.full());
        int id = p.path("id").asInt();
        List<String> blocked = keys(p, true);
        assertFalse(blocked.isEmpty());
        List<String> mixed = new ArrayList<>(keys(p, false));
        mixed.add(blocked.get(0));
        ResultActions r = confirm(teacher, id, mixed);
        assertError(r, 422, "IMPORT_ITEM_BLOCKED");
        assertTrue(json(r).path("error").asText().contains(blocked.get(0)), json(r).toString());
        assertError(confirm(teacher, id, List.of("mcq-999")), 400, "IMPORT_KEYS_INVALID");
        assertError(confirm(teacher, id, List.of()), 400, "IMPORT_KEYS_INVALID");
        List<String> twice = List.of(keys(p, false).get(0), keys(p, false).get(0));
        assertError(confirm(teacher, id, twice), 400, "IMPORT_KEYS_INVALID");
        assertEquals(0, rows("SELECT count(*) FROM bank_item"));
        assertEquals("preview", jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class, id));
    }

    @Test
    void loiGiuaChungThiRollbackToanBo() throws Exception {
        JsonNode p = previewOk(teacher, FixtureDoc.full());
        int id = p.path("id").asInt();
        doThrow(new RuntimeException("lỗi giả khi ghi ngân hàng"))
                .when(bankService).createOrUpdateShortAnswerFromReview(any(), any(), any(), any(), any(), any());
        confirm(teacher, id, keys(p, false)).andExpect(status().is5xxServerError());
        assertEquals(0, rows("SELECT count(*) FROM generated_question"));
        assertEquals(0, rows("SELECT count(*) FROM teacher_review"));
        assertEquals(0, rows("SELECT count(*) FROM bank_item"));
        assertEquals(0, rows("SELECT count(*) FROM experiment_run"));
        assertEquals("preview", jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class, id));
        reset(bankService);
        confirm(teacher, id, keys(p, false)).andExpect(status().isOk());
    }

    @Test
    void huyLoXemTruoc() throws Exception {
        int id = previewOk(teacher, FixtureDoc.full()).path("id").asInt();
        mockMvc.perform(delete("/api/bank-imports/" + id).with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isNoContent());
        assertError(mockMvc.perform(get("/api/bank-imports/" + id).with(AuthTestSupport.teacher(teacher))), 404,
                "IMPORT_NOT_FOUND");
        assertEquals(0, rows("SELECT count(*) FROM import_batch"));
    }

    @Test
    void banXemTruocHetHan() throws Exception {
        int id = previewOk(teacher, FixtureDoc.full()).path("id").asInt();
        expire("id = ?", id);
        assertError(mockMvc.perform(get("/api/bank-imports/" + id).with(AuthTestSupport.teacher(teacher))), 410,
                "IMPORT_EXPIRED");
        assertError(confirm(teacher, id, List.of("mcq-001")), 410, "IMPORT_EXPIRED");
        int id2 = previewOk(teacher, FixtureDoc.simpleMcq(1)).path("id").asInt();
        expire("id = ?", id2);
        service.purgeExpired();
        assertEquals(0, rows("SELECT count(*) FROM import_batch WHERE preview IS NOT NULL"), "nội dung bị xóa khi hết hạn");
        assertEquals(2, rows("SELECT count(*) FROM import_batch WHERE status = 'expired'"));
    }

    @Test
    void trungVoiNganHangNguoiTaiThayLaCoKhongChan() throws Exception {
        JsonNode first = previewOk(teacher, FixtureDoc.duplicatedInFile());
        confirm(teacher, first.path("id").asInt(), List.of(first.path("items").get(0).path("key").asText()))
                .andExpect(status().isOk());
        JsonNode again = previewOk(teacher, FixtureDoc.duplicatedInFile());
        assertTrue(again.path("items").get(0).path("flags").toString().contains("duplicate_in_bank"), again.toString());
        assertFalse(again.path("items").get(0).path("blocking").asBoolean());
        JsonNode theirs = previewOk(other, FixtureDoc.duplicatedInFile());
        assertFalse(theirs.path("items").get(0).path("flags").toString().contains("duplicate_in_bank"),
                "không so với câu riêng tư của người khác");
    }

    // ---------- không gọi Gemini ----------

    @Test
    void nhapNamMuoiCauRoiKhoiDongLaiBoDemGeminiKhongDoi() throws Exception {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        int before = usageLookup.countCallsSince(since);
        JsonNode p = previewOk(teacher, FixtureDoc.simpleMcq(50));
        assertEquals(50, p.path("items").size());
        confirm(teacher, p.path("id").asInt(), keys(p, false)).andExpect(status().isOk());
        assertEquals(50, rows("SELECT count(*) FROM bank_item"));
        assertEquals(before, usageLookup.countCallsSince(since), "câu nhập không phải lượt gọi Gemini");
        // khởi động lại: bộ điều tiết mới khởi tạo bộ đếm từ DB
        GeminiRateLimiter restarted = new GeminiRateLimiter(0, before + 1, 0, Clock.systemDefaultZone(), d -> { },
                usageLookup);
        assertTrue(restarted.hasDailyQuota(), "còn hạn mức sau khi nhập 50 câu");
        verifyNoInteractions(llmClient);
    }

    // ---------- lỗi ràng buộc DB: không log nội dung ----------

    @Test
    void viPhamCheckKhiXacNhanTra422VaLogChiCoTenRangBuoc(CapturedOutput output) throws Exception {
        JsonNode p = previewOk(teacher, FixtureDoc.full());
        int id = p.path("id").asInt();
        assertEquals("mcq-001", p.path("items").get(0).path("key").asText());
        // giả lập dữ liệu hỏng lọt qua bản xem trước: phương án A của câu 1 chỉ là khoảng trắng → chk_bi_type_content
        jdbc.update("UPDATE import_batch SET preview = jsonb_set(preview, '{items,0,options,0,text}', '\" \"'::jsonb)"
                + " WHERE id = ?", id);
        ResultActions r = confirm(teacher, id, keys(p, false));
        assertError(r, 422, "IMPORT_DATA_INVALID");
        assertTrue(json(r).path("error").asText().contains("chk_bi_type_content"), json(r).toString());
        assertFalse(json(r).toString().contains(FixtureDoc.Q1_MARKER));
        // log console có thể đổi bảng mã chữ tiếng Việt: chỉ so chuỗi ASCII (đề câu 1 có công thức \frac{3}{4})
        String log = output.getAll();
        assertTrue(log.contains("chk_bi_type_content") && log.contains("23514") && log.contains(" " + id),
                "log có tên ràng buộc, SQLState và mã lô");
        assertFalse(log.contains(CONTENT_ASCII), "log không chứa nội dung câu");
        assertFalse(log.contains("Failing row"), "log không chứa thông báo gốc của PostgreSQL");
        assertFalse(log.contains("violates check constraint"), "log không chứa thông báo gốc của PostgreSQL");
        assertEquals(0, rows("SELECT count(*) FROM generated_question"), "rollback toàn bộ");
        assertEquals(0, rows("SELECT count(*) FROM bank_item"));
        assertEquals("preview", jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class, id));
    }

    @Test
    void viPhamCheckKhiLuuLoTra422VaLogKhongCoNoiDung(CapturedOutput output) throws Exception {
        props.setPreviewTtl(Duration.ZERO);           // expires_at = created_at → chk_ib_expiry
        assertError(upload(teacher, FixtureDoc.FILE_NAME, FixtureDoc.full()), 422, "IMPORT_DATA_INVALID");
        String log = output.getAll();
        assertTrue(log.contains("chk_ib_expiry"), "log có tên ràng buộc");
        assertFalse(log.contains(CONTENT_ASCII), "log không chứa nội dung câu");
        assertFalse(log.contains("mcq-001"), "log không chứa bản xem trước");
        assertFalse(log.contains("Failing row"));
        assertEquals(0, rows("SELECT count(*) FROM import_batch"));
    }

    // ---------- trần lô chờ khi đồng thời ----------

    @Test
    void luuLoDongThoiKhongVuotTranLoCho() throws Exception {
        int subject = jdbc.queryForObject("INSERT INTO subject (name) VALUES ('Toán') RETURNING id", Integer.class);
        int threads = 6;
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int n = i;
                results.add(pool.submit(() -> {
                    com.datn.mcq.bankimport.entity.ImportBatch b = new com.datn.mcq.bankimport.entity.ImportBatch();
                    LocalDateTime now = LocalDateTime.now();
                    b.setOwnerId(teacher);
                    b.setDisplayName("Lô đồng thời " + n);
                    b.setFileSha256("a".repeat(63) + n);
                    b.setSubjectId(subject);
                    b.setGradeLevel(8);
                    b.setTopic("Chủ đề");
                    b.setQuestionCount(1);
                    b.setPreview("{\"items\": [], \"skipped\": []}");
                    b.setCreatedAt(now);
                    b.setExpiresAt(now.plusHours(1));
                    barrier.await();
                    try {
                        batchWriter.savePreview(b);
                        return "ok";
                    } catch (com.datn.mcq.common.exception.ApiErrorException e) {
                        return e.getCode();
                    }
                }));
            }
            List<String> codes = new ArrayList<>();
            for (java.util.concurrent.Future<String> f : results) {
                codes.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(3, codes.stream().filter("ok"::equals).count(), codes.toString());
            assertEquals(3, codes.stream().filter("IMPORT_PENDING_LIMIT"::equals).count(), codes.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(3, rows("SELECT count(*) FROM import_batch WHERE owner_id = ? AND status = 'preview'", teacher));
    }

    @Test
    void logKhongChuaNoiDungCauHayTenGiaoVien(CapturedOutput output) throws Exception {
        JsonNode p = previewOk(teacher, FixtureDoc.full());
        confirm(teacher, p.path("id").asInt(), keys(p, false)).andExpect(status().isOk());
        assertFalse(output.getAll().contains(FixtureDoc.Q1_MARKER), "nội dung câu không vào log");
        assertFalse(output.getAll().contains(CONTENT_ASCII), "nội dung câu không vào log (so chuỗi ASCII)");
        assertFalse(output.getAll().contains("NGUYỄN VĂN GIẢ"));
        assertFalse(output.getAll().contains(FixtureDoc.FILE_NAME));
    }
}
