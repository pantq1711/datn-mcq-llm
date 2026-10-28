package com.datn.mcq.material;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.material.entity.LessonMaterial;
import com.datn.mcq.material.repository.LessonMaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2-3 bước 1: entity LessonMaterial khớp bảng lesson_material (Hibernate validate lúc khởi động) và các truy vấn theo
 * chủ sở hữu. Tài liệu của người khác không bao giờ trả về khi hỏi theo (id, chủ sở hữu).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class LessonMaterialRepositoryTest {

    private static final String TEXT = "Hằng đẳng thức đáng nhớ: $(a+b)^2 = a^2 + 2ab + b^2$.";

    @Autowired
    private LessonMaterialRepository repository;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private int teacherA;
    private int teacherB;

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbc, "LessonMaterialRepositoryTest.setUp");
        teacherA = jdbc.queryForObject("INSERT INTO users (name, email, role) VALUES ('A', 'a.lm@test.vn', 'teacher')"
                + " RETURNING id", Integer.class);
        teacherB = jdbc.queryForObject("INSERT INTO users (name, email, role) VALUES ('B', 'b.lm@test.vn', 'teacher')"
                + " RETURNING id", Integer.class);
    }

    private LessonMaterial material(int owner, char shaChar, String name) {
        LessonMaterial m = new LessonMaterial();
        m.setOwnerId(owner);
        m.setFileName(name);
        m.setMimeType(LessonMaterial.MIME_DOCX);
        m.setSizeBytes(2048L);
        m.setSha256(String.valueOf(shaChar).repeat(64));
        m.setExtractMethod(LessonMaterial.METHOD_PANDOC);
        m.setExtractedText(TEXT);
        m.setCharCount(TEXT.codePointCount(0, TEXT.length()));
        m.setWarnings("[{\"code\":\"MATHTYPE_NOT_EXTRACTED\",\"count\":2}]");
        m.setCreatedAt(LocalDateTime.now());
        return repository.saveAndFlush(m);
    }

    @Test
    void luuVaDocLaiDayDuCacTruongKeCaVanBanTiengViet() {
        LessonMaterial saved = material(teacherA, 'a', "bai3.docx");
        LessonMaterial loaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(TEXT, loaded.getExtractedText());
        assertEquals("bai3.docx", loaded.getFileName());
        assertEquals(LessonMaterial.MIME_DOCX, loaded.getMimeType());
        assertEquals(2048L, loaded.getSizeBytes());
        assertEquals("a".repeat(64), loaded.getSha256());
        assertEquals(TEXT.codePointCount(0, TEXT.length()), loaded.getCharCount());
        assertTrue(loaded.getWarnings().contains("MATHTYPE_NOT_EXTRACTED"));
    }

    @Test
    void hoiTheoIdVaChuSoHuuKhongTraTaiLieuCuaNguoiKhac() {
        LessonMaterial a = material(teacherA, 'a', "cua-a.docx");
        assertTrue(repository.findByIdAndOwnerId(a.getId(), teacherA).isPresent());
        assertTrue(repository.findByIdAndOwnerId(a.getId(), teacherB).isEmpty(), "của người khác: như không tồn tại");
    }

    @Test
    void timTheoSha256ChiTrongTaiLieuCuaChuSoHuu() {
        LessonMaterial a = material(teacherA, 'c', "x.docx");
        assertEquals(a.getId(), repository.findByOwnerIdAndSha256(teacherA, "c".repeat(64)).orElseThrow().getId());
        assertTrue(repository.findByOwnerIdAndSha256(teacherB, "c".repeat(64)).isEmpty());
    }

    @Test
    void danhSachVaDemChiCuaChuSoHuuMoiNhatTruoc() {
        LessonMaterial first = material(teacherA, 'a', "1.docx");
        LessonMaterial second = material(teacherA, 'b', "2.docx");
        material(teacherB, 'a', "cua-b.docx");
        List<LessonMaterial> page = repository.findByOwnerIdOrderByIdDesc(teacherA, PageRequest.of(0, 10));
        assertEquals(List.of(second.getId(), first.getId()), page.stream().map(LessonMaterial::getId).toList());
        assertEquals(2, repository.countByOwnerId(teacherA));
        assertEquals(1, repository.countByOwnerId(teacherB));
    }
}
