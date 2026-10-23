package com.datn.mcq.exam.export;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Gọi pandoc để chuyển Markdown của đề sang docx (ngày 23/10, đợt 3, việc 3). */
class PandocRunnerTest {

    @Test
    void khongCoPandocTra503CoThongBao() {
        PandocRunner runner = new PandocRunner("pandoc-khong-co-tren-may-nay");
        DocumentExportException ex = assertThrows(DocumentExportException.class, () -> runner.markdownToDocx("# A"));
        assertEquals(503, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("pandoc") && ex.getMessage().contains("README"), ex.getMessage());
    }

    /** Chỉ chạy khi máy có pandoc; công thức $...$ thành Equation gốc của Word (m:oMath). */
    @Test
    void pandocThatSinhDocxCoEquationGocCuaWord() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long before;
        try (var s = Files.list(tmp)) {
            before = s.filter(p -> p.getFileName().toString().startsWith(PandocRunner.TEMP_PREFIX)).count();
        }
        byte[] docx = new PandocRunner(pandoc.get()).markdownToDocx("# Đề thử\n\n**Câu 1.** Tính $\\frac{1}{2} + x^2$.\n\n"
                + "A. $1$\n\nB. $2$\n\na\\) Ý một.\n");
        assertEquals('P', docx[0]);
        assertEquals('K', docx[1]);
        String xml = PandocTestSupport.documentXml(docx);
        assertTrue(xml.contains("m:oMath"), "công thức là Equation gốc của Word");
        assertTrue(xml.contains(">A.</w:t>") && xml.contains(">a) Ý một.</w:t>"),
                "nhãn A. và a) giữ nguyên chữ (pandoc tách dấu cách trước công thức thành run riêng)");
        assertFalse(xml.contains("w:numPr"), "không biến A./a) thành danh sách tự đánh số");
        try (var s = Files.list(tmp)) {
            assertEquals(before, s.filter(p -> p.getFileName().toString().startsWith(PandocRunner.TEMP_PREFIX)).count(),
                    "dọn thư mục tạm sau khi xong");
        }
    }
}
