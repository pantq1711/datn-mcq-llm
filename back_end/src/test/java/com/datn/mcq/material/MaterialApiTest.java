package com.datn.mcq.material;

import com.datn.mcq.common.exception.ApiExceptionHandler;
import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.material.entity.LessonMaterial;
import com.datn.mcq.material.extract.MaterialProperties;
import com.datn.mcq.material.service.MaterialService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.datn.mcq.material.extract.MaterialTestFiles.blankPdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.minimalDocx;
import static com.datn.mcq.material.extract.MaterialTestFiles.oleEquation;
import static com.datn.mcq.material.extract.MaterialTestFiles.omml;
import static com.datn.mcq.material.extract.MaterialTestFiles.paragraph;
import static com.datn.mcq.material.extract.MaterialTestFiles.pdf;
import static com.datn.mcq.material.extract.MaterialTestFiles.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-3 bước 3: API tài liệu bài học ({@code /api/materials}) qua HTTP thật (MockMvc + filter chain): tải lên (loại file
 * theo nội dung, tải lại cùng file, hạn mức), danh sách, chi tiết, xóa, và quyền sở hữu (của người khác là 404).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class MaterialApiTest {

    private static final String VN_TEXT =
            "Hằng đẳng thức đáng nhớ: bình phương của một tổng là $(a+b)^2 = a^2 + 2ab + b^2$.";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MaterialProperties props;
    @Autowired
    private MaterialService service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private int teacher;
    private int other;
    private long savedMaxFile;
    private int savedPerOwner;
    private int savedPerHour;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TestDataCleaner.cleanAll(dataSource, jdbc, "MaterialApiTest.setUp");
        teacher = AuthTestSupport.insertUser(jdbc, "GV A", "a.mat@test.vn", "teacher", "matkhau-dung-1", true);
        other = AuthTestSupport.insertUser(jdbc, "GV B", "b.mat@test.vn", "teacher", "matkhau-dung-2", true);
        savedMaxFile = props.getMaxFileBytes();
        savedPerOwner = props.getMaxPerOwner();
        savedPerHour = props.getMaxUploadsPerHour();
    }

    @AfterEach
    void restore() {
        props.setMaxFileBytes(savedMaxFile);
        props.setMaxPerOwner(savedPerOwner);
        props.setMaxUploadsPerHour(savedPerHour);
    }

    // ---------- tiện ích ----------

    private ResultActions upload(int owner, String name, String contentType, byte[] bytes) throws Exception {
        return upload(AuthTestSupport.teacher(owner), name, contentType, bytes);
    }

    private ResultActions upload(RequestPostProcessor who, String name, String contentType, byte[] bytes)
            throws Exception {
        return mockMvc.perform(multipart("/api/materials")
                .file(new MockMultipartFile("file", name, contentType, bytes)).with(who));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode uploadOk(int owner, String name, String contentType, byte[] bytes) throws Exception {
        return json(upload(owner, name, contentType, bytes).andExpect(status().isCreated()));
    }

    private void assertError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status));
        assertEquals(code, json(r).path("code").asText(), "code");
    }

    private int rows(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static byte[] vnText(String suffix) {
        return utf8(VN_TEXT + " " + suffix);
    }

    // ---------- tải lên ----------

    @Test
    void taiTxtTaoTaiLieu201KhongTraVanBanDayDu() throws Exception {
        JsonNode m = uploadOk(teacher, "C:\\Users\\gv\\bai 3.txt", "text/plain", utf8(VN_TEXT));
        assertTrue(m.path("id").asInt() > 0);
        assertEquals("bai 3.txt", m.path("fileName").asText(), "bỏ đường dẫn của client");
        assertEquals("txt", m.path("kind").asText());
        assertEquals("plain", m.path("extractMethod").asText());
        assertEquals(utf8(VN_TEXT).length, m.path("sizeBytes").asInt());
        assertEquals(VN_TEXT.codePointCount(0, VN_TEXT.length()), m.path("charCount").asInt());
        assertTrue(m.path("sha256").asText().matches("[0-9a-f]{64}"));
        assertTrue(m.path("warnings").isArray() && m.path("warnings").isEmpty());
        assertFalse(m.has("extractedText"), "không trả văn bản đầy đủ");
        assertFalse(m.has("textPreview"), "xem trước chỉ có ở chi tiết");
        assertFalse(m.has("ownerId"), "không lộ chủ sở hữu");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM lesson_material WHERE id = ?", m.path("id").asInt());
        assertEquals(teacher, row.get("owner_id"));
        assertEquals(LessonMaterial.MIME_TXT, row.get("mime_type"));
        assertEquals(VN_TEXT, row.get("extracted_text"));
    }

    @Test
    void loaiFileTheoNoiDungKhongTheoDuoiHayContentTypeCuaClient() throws Exception {
        // PDF thật nhưng đặt tên .txt và khai báo text/plain.
        JsonNode asPdf = uploadOk(teacher, "x.txt", "text/plain", pdf("Hang dang thuc dang nho so mot"));
        assertEquals("pdf", asPdf.path("kind").asText());
        assertEquals("pdfbox", asPdf.path("extractMethod").asText());
        assertEquals(1, asPdf.path("pageCount").asInt());
        // Văn bản thật nhưng đặt tên .docx và khai báo docx.
        JsonNode asTxt = uploadOk(teacher, "y.docx", LessonMaterial.MIME_DOCX, vnText("a"));
        assertEquals("txt", asTxt.path("kind").asText());
        assertFalse(asTxt.has("pageCount"));
        if (PandocTestSupport.find().isPresent()) {
            JsonNode asDocx = uploadOk(teacher, "z.pdf", "application/pdf",
                    minimalDocx(paragraph("Noi dung bai hoc so ba")));
            assertEquals("docx", asDocx.path("kind").asText());
            assertEquals("pandoc", asDocx.path("extractMethod").asText());
        }
    }

    @Test
    void taiLaiCungFileLaDungLaiBanCu200ConGiaoVienKhacThiCoBanRieng() throws Exception {
        byte[] file = vnText("lan 1");
        JsonNode first = uploadOk(teacher, "a.txt", "text/plain", file);
        JsonNode again = json(upload(teacher, "doi-ten.txt", "text/plain", file).andExpect(status().isOk()));
        assertEquals(first.path("id").asInt(), again.path("id").asInt());
        assertEquals("a.txt", again.path("fileName").asText(), "giữ tên cũ");
        assertEquals(1, rows("SELECT count(*) FROM lesson_material WHERE owner_id = ?", teacher));

        JsonNode theirs = uploadOk(other, "a.txt", "text/plain", file);
        assertNotEquals(first.path("id").asInt(), theirs.path("id").asInt());
        assertEquals(2, rows("SELECT count(*) FROM lesson_material"));
    }

    @Test
    void haiLanTaiDongThoiCungFileChiCoMotDongVaCungId() throws Exception {
        byte[] file = vnText("dong thoi");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return service.upload(teacher, "d.txt", file).material().id();
                }));
            }
            go.countDown();
            assertEquals(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, rows("SELECT count(*) FROM lesson_material WHERE owner_id = ?", teacher));
    }

    @Test
    void loiTrichVanBanKhongLuuGiVaTraMaLoi() throws Exception {
        assertError(upload(teacher, "a.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3}), 415, "UNSUPPORTED_FILE_TYPE");
        assertError(upload(teacher, "scan.pdf", "application/pdf", blankPdf(2)), 422, "NO_EXTRACTABLE_TEXT");
        assertError(upload(teacher, "rong.txt", "text/plain", new byte[0]), 400, "EMPTY_FILE");
        assertError(upload(teacher, "latin.txt", "text/plain", new byte[]{'a', (byte) 0xE0, 'b', ' ', 'c'}), 415,
                "UNSUPPORTED_FILE_TYPE");
        assertEquals(0, rows("SELECT count(*) FROM lesson_material"), "lỗi thì không lưu dòng nào");
    }

    @Test
    void thieuPhanFileHoacKhongPhaiMultipartThiLoi4xxCoMa() throws Exception {
        assertError(mockMvc.perform(multipart("/api/materials").with(AuthTestSupport.teacher(teacher))), 400,
                "MISSING_FILE");
        mockMvc.perform(post("/api/materials").contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(AuthTestSupport.teacher(teacher))).andExpect(status().is4xxClientError());
    }

    @Test
    void fileQuaLonBi413() throws Exception {
        props.setMaxFileBytes(100);
        assertError(upload(teacher, "lon.txt", "text/plain", utf8("x".repeat(101))), 413, "FILE_TOO_LARGE");
        upload(teacher, "vua.txt", "text/plain", utf8("x".repeat(100))).andExpect(status().isCreated());
    }

    @Test
    void trenMucToiDaMoiGiaoVienBi409NhungFileDaCoVanDungLaiDuoc() throws Exception {
        props.setMaxPerOwner(2);
        byte[] first = vnText("1");
        uploadOk(teacher, "1.txt", "text/plain", first);
        uploadOk(teacher, "2.txt", "text/plain", vnText("2"));
        assertError(upload(teacher, "3.txt", "text/plain", vnText("3")), 409, "MATERIAL_QUOTA_EXCEEDED");
        upload(teacher, "1b.txt", "text/plain", first).andExpect(status().isOk());
        uploadOk(other, "3.txt", "text/plain", vnText("3"));
    }

    @Test
    void tanSuatTaiLenQuaCaoBi429() throws Exception {
        props.setMaxUploadsPerHour(3);
        for (int i = 0; i < 3; i++) {
            uploadOk(teacher, i + ".txt", "text/plain", vnText("r" + i));
        }
        assertError(upload(teacher, "4.txt", "text/plain", vnText("r4")), 429, "TOO_MANY_UPLOADS");
        uploadOk(other, "ok.txt", "text/plain", vnText("r5"));
    }

    @Test
    void canhBaoMathTypeDuocTraVaLuuThanhMangJson() throws Exception {
        assumeTrue(PandocTestSupport.find().isPresent(), "máy không có pandoc: bỏ qua");
        byte[] docx = minimalDocx(paragraph("Cau 1. Tinh gia tri") + oleEquation("Equation.DSMT4")
                + oleEquation("Equation.3") + omml("x+1"));
        JsonNode m = uploadOk(teacher, "mathtype.docx", LessonMaterial.MIME_DOCX, docx);
        JsonNode dropped = null;
        boolean high = false;
        for (JsonNode w : m.path("warnings")) {
            if (w.path("code").asText().equals("MATHTYPE_FORMULAS_DROPPED")) {
                dropped = w;
            }
            high |= w.path("code").asText().equals("FORMULA_LOSS_HIGH");
        }
        assertEquals(2, dropped.path("count").asInt());
        assertEquals(3, dropped.path("total").asInt());
        assertTrue(dropped.path("message").asText().contains("2/3"));
        assertTrue(high, "2/3 vượt ngưỡng cảnh báo mất nhiều");
        String stored = jdbc.queryForObject("SELECT warnings::text FROM lesson_material WHERE id = ?", String.class,
                m.path("id").asInt());
        assertTrue(stored.contains("MATHTYPE_FORMULAS_DROPPED"), stored);
    }

    // ---------- danh sách, chi tiết, xóa ----------

    @Test
    void danhSachChiCuaToiMoiNhatTruocCoPhanTrangVaKhongCoVanBan() throws Exception {
        int a = uploadOk(teacher, "a.txt", "text/plain", vnText("a")).path("id").asInt();
        int b = uploadOk(teacher, "b.txt", "text/plain", vnText("b")).path("id").asInt();
        int c = uploadOk(teacher, "c.txt", "text/plain", vnText("c")).path("id").asInt();
        uploadOk(other, "cua-nguoi-khac.txt", "text/plain", vnText("x"));

        JsonNode page0 = json(mockMvc.perform(get("/api/materials?page=0&size=2").with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk()));
        assertEquals(List.of(c, b), ids(page0.path("items")));
        assertEquals(3, page0.path("totalItems").asInt());
        assertEquals(2, page0.path("totalPages").asInt());
        assertEquals(0, page0.path("page").asInt());
        assertEquals(2, page0.path("size").asInt());
        assertFalse(page0.path("items").get(0).has("extractedText"));
        assertFalse(page0.path("items").get(0).has("textPreview"));

        JsonNode page1 = json(mockMvc.perform(get("/api/materials?page=1&size=2").with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk()));
        assertEquals(List.of(a), ids(page1.path("items")));
    }

    private static List<Integer> ids(JsonNode items) {
        List<Integer> out = new ArrayList<>();
        items.forEach(i -> out.add(i.path("id").asInt()));
        return out;
    }

    @Test
    void danhSachThamSoSaiBi400() throws Exception {
        for (String q : List.of("page=-1", "size=0", "size=51", "page=abc")) {
            mockMvc.perform(get("/api/materials?" + q).with(AuthTestSupport.teacher(teacher)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void chiTietCoXemTruocToiDa500KyTuVaCuaNguoiKhacLa404() throws Exception {
        String longText = "Dòng chữ tiếng Việt có dấu. ".repeat(60);
        JsonNode m = uploadOk(teacher, "dai.txt", "text/plain", utf8(longText));
        int id = m.path("id").asInt();
        JsonNode detail = json(mockMvc.perform(get("/api/materials/" + id).with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isOk()));
        String preview = detail.path("textPreview").asText();
        assertEquals(500, preview.codePointCount(0, preview.length()));
        assertTrue(longText.strip().startsWith(preview));
        assertEquals(m.path("charCount").asInt(), detail.path("charCount").asInt());

        mockMvc.perform(get("/api/materials/" + id).with(AuthTestSupport.teacher(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/materials/999999").with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isNotFound());
    }

    @Test
    void xoaTaiLieuCuaMinhLa204RoiLa404CuaNguoiKhacLa404VaKhongMat() throws Exception {
        int id = uploadOk(teacher, "x.txt", "text/plain", vnText("xoa")).path("id").asInt();
        mockMvc.perform(delete("/api/materials/" + id).with(AuthTestSupport.teacher(other)))
                .andExpect(status().isNotFound());
        assertEquals(1, rows("SELECT count(*) FROM lesson_material WHERE id = ?", id), "người khác không xóa được");
        mockMvc.perform(delete("/api/materials/" + id).with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isNoContent());
        assertEquals(0, rows("SELECT count(*) FROM lesson_material WHERE id = ?", id));
        mockMvc.perform(delete("/api/materials/" + id).with(AuthTestSupport.teacher(teacher)))
                .andExpect(status().isNotFound());
    }

    // ---------- quyền ----------

    @Test
    void chuaDangNhapLa401VaAdminLa403ChoMoiDuong() throws Exception {
        int adminId = AuthTestSupport.insertUser(jdbc, "Admin", "admin.mat@test.vn", "admin", "matkhau-admin-12", true);
        int id = uploadOk(teacher, "q.txt", "text/plain", vnText("q")).path("id").asInt();
        for (RequestPostProcessor who : new RequestPostProcessor[]{null, AuthTestSupport.admin(adminId)}) {
            int expected = who == null ? 401 : 403;
            for (var req : List.of(get("/api/materials"), get("/api/materials/" + id), delete("/api/materials/" + id),
                    multipart("/api/materials").file(new MockMultipartFile("file", "a.txt", "text/plain", utf8("a"))))) {
                mockMvc.perform(who == null ? req : req.with(who)).andExpect(status().is(expected));
            }
        }
        assertEquals(1, rows("SELECT count(*) FROM lesson_material WHERE id = ?", id));
    }

    // ---------- đơn vị ----------

    @Test
    void tenFileDuocLamSachDeChiDeHienThi() {
        assertEquals("bai3.docx", MaterialService.sanitizeFileName("C:\\Users\\gv\\bai3.docx", LessonMaterial.MIME_DOCX));
        assertEquals("bai3.docx", MaterialService.sanitizeFileName("../../etc/bai3.docx", LessonMaterial.MIME_DOCX));
        assertEquals("a b .txt", MaterialService.sanitizeFileName("a\u0000 \u202E b\t.txt", LessonMaterial.MIME_TXT));
        assertEquals("tai-lieu.pdf", MaterialService.sanitizeFileName("  ", LessonMaterial.MIME_PDF));
        assertEquals("tai-lieu.txt", MaterialService.sanitizeFileName(null, LessonMaterial.MIME_TXT));
        assertEquals("tai-lieu.docx", MaterialService.sanitizeFileName("/", LessonMaterial.MIME_DOCX));
        String longName = "ă".repeat(300) + ".pdf";
        String cut = MaterialService.sanitizeFileName(longName, LessonMaterial.MIME_PDF);
        assertEquals(255, cut.codePointCount(0, cut.length()));
        assertTrue(cut.endsWith(".pdf"), "giữ đuôi khi cắt tên");
    }

    @Test
    void vuotGioiHanMultipartCuaMayChuTraBan413CoMa() {
        ResponseEntity<Map<String, Object>> r = new ApiExceptionHandler()
                .handleMaxUploadSize(new MaxUploadSizeExceededException(10L << 20));
        assertEquals(413, r.getStatusCode().value());
        assertEquals("FILE_TOO_LARGE", r.getBody().get("code"));
        assertTrue(String.valueOf(r.getBody().get("error")).contains("tối đa 10 MB"), String.valueOf(r.getBody().get("error")));
    }
}
