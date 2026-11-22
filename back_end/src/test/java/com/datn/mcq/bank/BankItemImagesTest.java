package com.datn.mcq.bank;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.questionimage.RecordingStatementInspector;
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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G0 H3 (O1, sau E2E 22/11 B8): {@code GET /api/bank-items} và {@code GET /api/bank-items/{id}} trả {@code images} của câu
 * nhập có hình, mỗi ảnh là URL ký ngắn hạn cho đúng người xem (H3); không có hình thì không có khóa {@code images}. Câu riêng
 * của người khác không lộ ảnh. Truy vấn ảnh theo lô (không tăng theo số câu) và không nạp cột bytea {@code data}.
 */
@SpringBootTest(properties = {
        "app.question-images.url-signing-key=dGVzdC1vbmx5LXF1ZXN0aW9uLWltYWdlLXVybC1zaWduaW5nLWtleS1ub3QtcHJvZA==",
        "app.question-images.url-ttl=1h",
        RecordingStatementInspector.PROPERTY
})
@Import(TestSafetyConfiguration.class)
class BankItemImagesTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47};

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int subject;
    private int teacher1;
    private int teacher2;
    private int seq;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "BankItemImagesTest.setUp");
        subject = insert("INSERT INTO subject (name) VALUES ('Toán')");
        teacher1 = insert("INSERT INTO users (name, email, role) VALUES ('Cô An', 'an.img@test', 'teacher')");
        teacher2 = insert("INSERT INTO users (name, email, role) VALUES ('Thầy Bình', 'binh.img@test', 'teacher')");
        RecordingStatementInspector.clear();
    }

    // ---------- dựng dữ liệu ----------

    private Integer insert(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Integer.class, args);
    }

    /** Một cặp (bank_item, question_image) của GV1: câu nhập có hình; {@code visibleTo} null là câu dùng chung. */
    private int[] bankItem(Integer visibleTo, boolean withImage) {
        int n = ++seq;
        int tpl = insert("INSERT INTO question_template (subject_id, source_question, is_active, topic, grade_level)"
                + " VALUES (?, 'Mẫu', TRUE, 'Hình học', 8)", subject);
        int run = insert("INSERT INTO experiment_run (subject_id, strategy, model_name, temperature, prompt_text,"
                + " purpose) VALUES (?, 'zero-shot', 'm', 0.7, 'p', 'production')", subject);
        int q = insert("INSERT INTO generated_question (template_id, run_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, raw_llm_output, parse_success, status, final_difficulty)"
                + " VALUES (?, ?, 'Câu nguồn', '$1$', '$2$', '$3$', '$4$', 'B', '{}', TRUE, 'in_bank', 'easy')",
                tpl, run);
        int imageId = 0;
        String text = "Cho hình vẽ sau, tính góc $x$.";
        if (withImage) {
            int batch = insert("INSERT INTO import_batch (owner_id, display_name, file_sha256, subject_id, grade_level,"
                    + " topic, status, question_count, imported_count, created_at, expires_at, confirmed_at)"
                    + " VALUES (?, 'Lô có hình', ?, ?, 8, 'Hình học', 'confirmed', 1, 1, CURRENT_TIMESTAMP,"
                    + " CURRENT_TIMESTAMP + INTERVAL '24 hours', CURRENT_TIMESTAMP)",
                    teacher1, String.format("%064x", n), subject);
            imageId = insert("INSERT INTO question_image (owner_id, import_batch_id, item_key, generated_question_id,"
                    + " role, ordinal, mime, data, byte_size, width_px, height_px, display_width_mm, sha256,"
                    + " source_format) VALUES (?, ?, 'mcq-001', ?, 'stem', 1, 'image/png', E'\\\\x89504e47', 4, 120,"
                    + " 80, 50, ?, 'png')", teacher1, batch, q, String.format("%064x", 1000 + n));
            text = text + "\n⟦img:" + imageId + "⟧";
        }
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, 'easy', 'approved_as_is', 'B')", q, teacher1);
        int item = insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, difficulty, visible_to_user_id) VALUES (?, ?, ?, '$1$', '$2$',"
                + " '$3$', '$4$', 'B', 'easy', ?)", q, review, text, visibleTo);
        return new int[] {item, imageId};
    }

    private String body(int userId, String url) throws Exception {
        return mockMvc.perform(get(url).with(AuthTestSupport.teacher(userId))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode list(int userId) throws Exception {
        return objectMapper.readTree(body(userId, "/api/bank-items?size=50"));
    }

    private static JsonNode itemOf(JsonNode page, int id) {
        for (JsonNode i : page.get("items")) {
            if (i.get("id").asInt() == id) {
                return i;
            }
        }
        throw new AssertionError("Danh sách không có câu " + id);
    }

    /** URL ảnh của người xem: tải được không cần JWT (chữ ký là quyền) và trả đúng byte ảnh. */
    private void assertFetchable(String url, int viewer, long imageId) throws Exception {
        assertTrue(url.startsWith("/api/question-images/" + imageId + "?exp="), url);
        assertTrue(url.contains("&u=" + viewer + "&sig="), url);
        mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(PNG_BYTES));
    }

    // ---------- test ----------

    @Test
    void danhSachVaChiTietCoImagesKyChoNguoiXem() throws Exception {
        int[] fig = bankItem(null, true);

        JsonNode inList = itemOf(list(teacher1), fig[0]);
        assertEquals(1, inList.get("images").size());
        JsonNode img = inList.get("images").get(0);
        assertEquals(fig[1], img.get("id").asInt());
        assertEquals("stem", img.get("role").asText());
        assertEquals(1, img.get("ordinal").asInt());
        assertEquals("image/png", img.get("mime").asText());
        assertEquals(120, img.get("widthPx").asInt());
        assertEquals(80, img.get("heightPx").asInt());
        assertEquals(50, img.get("displayWidthMm").asInt());
        assertFetchable(img.get("url").asText(), teacher1, fig[1]);
        assertTrue(inList.get("questionText").asText().contains("⟦img:" + fig[1] + "⟧"),
                "token vẫn nằm trong đề: giao diện đặt ảnh theo token");

        JsonNode detail = objectMapper.readTree(body(teacher1, "/api/bank-items/" + fig[0]));
        assertEquals(1, detail.get("images").size());
        assertEquals(fig[1], detail.get("images").get(0).get("id").asInt());
        assertFetchable(detail.get("images").get(0).get("url").asText(), teacher1, fig[1]);
    }

    @Test
    void cauKhongCoHinhKhongCoKhoaImages() throws Exception {
        int[] plain = bankItem(null, false);

        assertFalse(itemOf(list(teacher1), plain[0]).has("images"), "câu không hình: JSON như cũ");
        assertFalse(objectMapper.readTree(body(teacher1, "/api/bank-items/" + plain[0])).has("images"));
    }

    @Test
    void giaoVienKhacXemCauChungThayUrlKyChoChinhHo() throws Exception {
        int[] fig = bankItem(null, true);

        JsonNode img = itemOf(list(teacher2), fig[0]).get("images").get(0);
        assertFetchable(img.get("url").asText(), teacher2, fig[1]);
        JsonNode detail = objectMapper.readTree(body(teacher2, "/api/bank-items/" + fig[0]));
        assertFetchable(detail.get("images").get(0).get("url").asText(), teacher2, fig[1]);
    }

    @Test
    void cauRiengCuaNguoiKhacKhongLoAnh() throws Exception {
        int[] priv = bankItem(teacher1, true);

        String otherList = body(teacher2, "/api/bank-items?size=50");
        assertEquals(0, objectMapper.readTree(otherList).get("items").size());
        assertFalse(otherList.contains("/api/question-images/"), "không có URL ảnh trong phản hồi của GV khác");
        mockMvc.perform(get("/api/bank-items/" + priv[0]).with(AuthTestSupport.teacher(teacher2)))
                .andExpect(status().isNotFound());

        // chính chủ vẫn thấy
        assertEquals(1, itemOf(list(teacher1), priv[0]).get("images").size());
    }

    @Test
    void truyVanAnhTheoLoKhongTangTheoSoCauVaKhongNapCotData() throws Exception {
        bankItem(null, true);
        RecordingStatementInspector.clear();
        list(teacher1);
        assertEquals(1, RecordingStatementInspector.imageQueries().size(), "1 câu có hình: 1 truy vấn ảnh");

        for (int i = 0; i < 9; i++) {
            bankItem(null, true);
        }
        RecordingStatementInspector.clear();
        assertEquals(10, list(teacher1).get("items").size());
        assertEquals(1, RecordingStatementInspector.imageQueries().size(), "10 câu có hình: vẫn 1 truy vấn ảnh");
        for (String sql : RecordingStatementInspector.imageQueries()) {
            assertFalse(RecordingStatementInspector.selectsDataColumn(sql), "không nạp cột data: " + sql);
        }

        RecordingStatementInspector.clear();
        body(teacher1, "/api/bank-items/" + bankItemIdOfFirst());
        assertFalse(RecordingStatementInspector.imageQueries().isEmpty(), "chi tiết cũng lấy ảnh");
        for (String sql : RecordingStatementInspector.imageQueries()) {
            assertFalse(RecordingStatementInspector.selectsDataColumn(sql), "không nạp cột data: " + sql);
        }
    }

    private int bankItemIdOfFirst() {
        return jdbc.queryForObject("SELECT min(id) FROM bank_item", Integer.class);
    }
}
