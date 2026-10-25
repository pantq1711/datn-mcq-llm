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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V18 (W2-3 bước 1): bảng lesson_material (tài liệu bài học đính kèm, chỉ lưu văn bản đã trích). Chạy trên schema tạm
 * {@value #SCHEMA}: nâng từ V17, kiểm các ràng buộc và xóa dây chuyền theo chủ sở hữu.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class V18UpgradeFromV17Test {

    private static final String SCHEMA = "v18_upgrade_test";
    private static final String S = SCHEMA + ".";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String TEXT = "Hằng đẳng thức đáng nhớ: $(a+b)^2 = a^2 + 2ab + b^2$.";
    private static final String SHA = "a".repeat(64);

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        dropSchema("V18UpgradeFromV17Test.before");
    }

    @AfterEach
    void after() {
        dropSchema("V18UpgradeFromV17Test.after");
    }

    private void dropSchema(String stage) {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, stage);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").target(target).load();
    }

    private int migrateTo18() {
        TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(dataSource, "V18UpgradeFromV17Test.migrate");
        assertEquals(17, flyway("17").migrate().migrationsExecuted);
        return flyway("18").migrate().migrationsExecuted;
    }

    private int newUser(String email) {
        return jdbc.queryForObject("INSERT INTO " + S + "users (name, email, role) VALUES ('GV', ?, 'teacher')"
                + " RETURNING id", Integer.class, email);
    }

    /** Một dòng hợp lệ; {@code overrides} thay giá trị theo tên cột. */
    private Map<String, Object> row(int owner, Map<String, Object> overrides) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("owner_id", owner);
        m.put("file_name", "bai3.docx");
        m.put("mime_type", DOCX);
        m.put("size_bytes", 2048L);
        m.put("sha256", SHA);
        m.put("extract_method", "pandoc");
        m.put("page_count", null);
        m.put("extracted_text", TEXT);
        m.put("char_count", TEXT.codePointCount(0, TEXT.length()));
        m.putAll(overrides);
        return m;
    }

    private int insert(Map<String, Object> row) {
        String cols = String.join(", ", row.keySet());
        String marks = String.join(", ", row.keySet().stream()
                .map(k -> k.equals("warnings") ? "CAST(? AS jsonb)" : "?").toList());
        return jdbc.queryForObject("INSERT INTO " + S + "lesson_material (" + cols + ") VALUES (" + marks
                + ") RETURNING id", Integer.class, row.values().toArray());
    }

    /** Dòng bị từ chối đúng bởi ràng buộc {@code constraint}. */
    private void rejects(String constraint, int owner, Map<String, Object> overrides) {
        rejectsAnyOf(List.of(constraint), owner, overrides);
    }

    /** Dòng bị từ chối bởi một trong các ràng buộc (PostgreSQL báo ràng buộc đứng trước theo thứ tự chữ cái). */
    private void rejectsAnyOf(List<String> constraints, int owner, Map<String, Object> overrides) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> insert(row(owner, overrides)), constraints.toString());
        assertTrue(constraints.stream().anyMatch(c -> String.valueOf(ex.getMessage()).contains(c)),
                "kỳ vọng " + constraints + ", gặp: " + ex.getMessage());
    }

    @Test
    void nangTuV17ThemDungMotMigrationVaChapNhanDongHopLe() {
        assertEquals(1, migrateTo18());
        int owner = newUser("gv1@test.vn");
        int id = insert(row(owner, Map.of("warnings", "[{\"code\":\"MATHTYPE_NOT_EXTRACTED\",\"count\":3}]")));
        assertEquals(TEXT, jdbc.queryForObject("SELECT extracted_text FROM " + S + "lesson_material WHERE id = ?",
                String.class, id));
        assertTrue(jdbc.queryForObject("SELECT created_at IS NOT NULL FROM " + S + "lesson_material WHERE id = ?",
                Boolean.class, id), "created_at có mặc định");
    }

    @Test
    void pdfCoSoTrangVaTxtLaPlain() {
        migrateTo18();
        int owner = newUser("gv2@test.vn");
        insert(row(owner, Map.of("mime_type", "application/pdf", "extract_method", "pdfbox", "page_count", 12,
                "sha256", "b".repeat(64))));
        insert(row(owner, Map.of("mime_type", "text/plain", "extract_method", "plain", "sha256", "c".repeat(64))));
    }

    @Test
    void rangBuocTuChoiDongSai() {
        migrateTo18();
        int owner = newUser("gv3@test.vn");
        rejects("chk_lm_name", owner, Map.of("file_name", "   "));
        // mime lạ cũng không khớp extract_method nên hai ràng buộc cùng bắt; chỉ cần dòng bị từ chối.
        rejectsAnyOf(List.of("chk_lm_mime", "chk_lm_method_mime"), owner, Map.of("mime_type", "application/msword"));
        rejects("chk_lm_size", owner, Map.of("size_bytes", 0L));
        rejects("chk_lm_size", owner, Map.of("size_bytes", 10_485_761L));
        rejects("chk_lm_sha", owner, Map.of("sha256", "XYZ"));
        rejects("chk_lm_sha", owner, Map.of("sha256", "A".repeat(64)));
        rejects("chk_lm_method", owner, Map.of("extract_method", "ocr"));
        rejects("chk_lm_method_mime", owner, Map.of("extract_method", "pdfbox"));
        rejects("chk_lm_pages", owner, Map.of("page_count", 3));
        rejects("chk_lm_pages", owner, Map.of("mime_type", "application/pdf", "extract_method", "pdfbox",
                "page_count", 0));
        rejects("chk_lm_chars", owner, Map.of("char_count", 5));
        rejects("chk_lm_chars", owner, Map.of("extracted_text", "", "char_count", 0));
        rejects("chk_lm_warnings", owner, Map.of("warnings", "{\"code\":\"X\"}"));
    }

    @Test
    void cungChuSoHuuKhongCoHaiBanCungSha256NhungKhacChuSoHuuThiDuoc() {
        migrateTo18();
        int a = newUser("a@test.vn");
        int b = newUser("b@test.vn");
        insert(row(a, Map.of()));
        rejects("uq_lm_owner_sha", a, Map.of());
        insert(row(b, Map.of()));
    }

    @Test
    void xoaChuSoHuuXoaTaiLieuCuaNguoiDo() {
        migrateTo18();
        int a = newUser("a2@test.vn");
        int b = newUser("b2@test.vn");
        insert(row(a, Map.of()));
        insert(row(b, Map.of()));
        jdbc.update("DELETE FROM " + S + "users WHERE id = ?", a);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + S + "lesson_material", Integer.class));
        assertEquals(b, jdbc.queryForObject("SELECT owner_id FROM " + S + "lesson_material", Integer.class));
    }
}
