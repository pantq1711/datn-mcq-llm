package com.datn.mcq.bankimport;

import com.datn.mcq.bankimport.service.BankImportService;
import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.datn.mcq.bankimport.DocxBuilder.cy;
import static com.datn.mcq.bankimport.DocxBuilder.t;
import static com.datn.mcq.bankimport.DocxBuilder.y;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NV-51 (quyết định 16/11, mục 4): câu thiếu nhãn mức [NB]/[TH]/[VD] không bị chặn nữa. Khi file có câu thiếu nhãn thì tham
 * số {@code defaultLevel} lúc tải lên là BẮT BUỘC (thiếu: 400 "File có N câu chưa ghi mức, thầy/cô chọn mức mặc định"); câu
 * thiếu nhãn nhận mức này kèm cờ thông tin {@code level_default}. Không có mức "chưa rõ". Đáp án tô vàng giữ quy tắc, câu
 * thiếu đáp án báo rõ lý do. Kèm NV-21: người tải gỡ được câu nhập của mình khỏi ngân hàng.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class BankImportDefaultLevelTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BankImportService service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int other;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "BankImportDefaultLevelTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV Nhập", "muc.a@test.vn", "teacher", "matkhau-dung-1", true);
        other = AuthTestSupport.insertUser(jdbc, "GV Khác", "muc.b@test.vn", "teacher", "matkhau-dung-2", true);
        service.resetRateLimitForTests();
    }

    /** Ba câu trắc nghiệm: câu 1 có nhãn [NB]; câu 2, 3 không ghi mức; câu 3 không tô vàng đáp án. */
    private static byte[] twoWithoutLevel() {
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");
        d.numbered(cy("[NB]"), t(" Hình vuông có mấy cạnh bằng nhau?"));
        d.p("A. 2").p("B. 3").p(t("C. "), y("4")).p("D. 5");
        d.numbered(t("Câu 2. Tam giác đều có mấy góc bằng nhau?"));
        d.p("A. 1").p("B. 2").p(t("C. "), y("3")).p("D. 4");
        d.numbered(t("Câu 3. Hình chữ nhật có mấy trục đối xứng?"));
        d.p("A. 1").p("B. 2").p("C. 3").p("D. 4");
        return d.bytes();
    }

    private ResultActions upload(byte[] bytes, String... params) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/bank-imports")
                .file(new MockMultipartFile("file", FixtureDoc.FILE_NAME, "application/octet-stream", bytes));
        for (int i = 0; i + 1 < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(req.with(AuthTestSupport.teacher(teacher)));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode item(JsonNode preview, int index) {
        return preview.path("items").get(index);
    }

    private static List<String> flags(JsonNode item) {
        List<String> out = new ArrayList<>();
        item.path("flags").forEach(f -> out.add(f.asText()));
        return out;
    }

    @Test
    void thieuMucMacDinhKhiFileCoCauChuaGhiMucLa400() throws Exception {
        ResultActions r = upload(twoWithoutLevel()).andExpect(status().isBadRequest());
        JsonNode body = json(r);
        assertEquals("IMPORT_LEVEL_DEFAULT_REQUIRED", body.path("code").asText());
        assertTrue(body.path("error").asText().contains("File có 2 câu chưa ghi mức"), body.toString());
        assertTrue(body.path("error").asText().contains("chọn mức mặc định"), body.toString());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM import_batch", Integer.class), "không lưu lô");
    }

    @Test
    void mucMacDinhSaiLa400() throws Exception {
        JsonNode body = json(upload(twoWithoutLevel(), "defaultLevel", "chua_ro").andExpect(status().isBadRequest()));
        assertEquals("IMPORT_LEVEL_INVALID", body.path("code").asText());
    }

    @Test
    void cauThieuNhanNhanMucMacDinhKhongBiChanCauCoNhanGiuMucCuaMinh() throws Exception {
        JsonNode p = json(upload(twoWithoutLevel(), "defaultLevel", "medium").andExpect(status().isCreated()));
        assertEquals(3, p.path("items").size());
        JsonNode labelled = item(p, 0);
        assertEquals("easy", labelled.path("level").asText(), "câu có nhãn [NB] giữ mức của mình");
        assertFalse(flags(labelled).contains("level_default"));
        JsonNode defaulted = item(p, 1);
        assertEquals("medium", defaulted.path("level").asText());
        assertTrue(flags(defaulted).contains("level_default"), defaulted.toString());
        assertFalse(flags(defaulted).contains("level_missing"), defaulted.toString());
        assertFalse(defaulted.path("blocking").asBoolean(), "thiếu nhãn mức không còn chặn");

        // câu 3: thiếu nhãn (nhận mức mặc định) nhưng không tô vàng đáp án: vẫn chặn, lý do nói rõ
        JsonNode noAnswer = item(p, 2);
        assertTrue(noAnswer.path("blocking").asBoolean());
        assertTrue(flags(noAnswer).contains("answer_missing"));
        String reasons = noAnswer.path("blockReasons").toString();
        assertTrue(reasons.contains("tô vàng") || reasons.contains("tô màu vàng"), reasons);

        // xác nhận: câu thiếu nhãn vào ngân hàng với mức mặc định
        int id = p.path("id").asInt();
        mockMvc.perform(post("/api/bank-imports/" + id + "/confirm").with(AuthTestSupport.teacher(teacher))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        Map.of("keys", List.of(labelled.path("key").asText(), defaulted.path("key").asText())))))
                .andExpect(status().isOk());
        assertEquals(List.of("easy", "medium"), jdbc.queryForList("SELECT difficulty FROM bank_item ORDER BY id",
                String.class));
        assertEquals(List.of("easy", "medium"), jdbc.queryForList("SELECT difficulty_label FROM teacher_review"
                + " ORDER BY id", String.class));
    }

    @Test
    void cauThieuDapAnBiChanVaThongBaoXacNhanNoiRoLyDo() throws Exception {
        JsonNode p = json(upload(twoWithoutLevel(), "defaultLevel", "hard").andExpect(status().isCreated()));
        String blockedKey = item(p, 2).path("key").asText();
        JsonNode body = json(mockMvc.perform(post("/api/bank-imports/" + p.path("id").asInt() + "/confirm")
                .with(AuthTestSupport.teacher(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("keys", List.of(blockedKey)))))
                .andExpect(status().is(422)));
        assertEquals("IMPORT_ITEM_BLOCKED", body.path("code").asText());
        String error = body.path("error").asText();
        assertTrue(error.contains(blockedKey), error);
        assertTrue(error.contains("tô vàng") || error.contains("tô màu vàng"), error);
    }

    @Test
    void fileDuNhanMucKhongCanMucMacDinh() throws Exception {
        upload(FixtureDoc.simpleMcq(2)).andExpect(status().isCreated());
    }

    @Test
    void nguoiTaiGoDuocCauNhapCuaMinhNguoiKhacKhongThay() throws Exception {
        JsonNode p = json(upload(FixtureDoc.simpleMcq(1)).andExpect(status().isCreated()));
        JsonNode r = json(mockMvc.perform(post("/api/bank-imports/" + p.path("id").asInt() + "/confirm")
                .with(AuthTestSupport.teacher(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("keys", List.of(item(p, 0).path("key").asText())))))
                .andExpect(status().isOk()));
        int bankItem = r.path("bankItemIds").get(0).asInt();
        mockMvc.perform(delete("/api/bank-items/" + bankItem).with(AuthTestSupport.teacher(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/bank-items/" + bankItem).with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isNoContent());
        assertEquals(teacher, jdbc.queryForObject("SELECT removed_by FROM bank_item WHERE id = ?", Integer.class,
                bankItem));
    }
}
