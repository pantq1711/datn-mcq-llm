package com.datn.mcq.exam.export;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.ExamStatus;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D4 (phòng thủ khi xuất Word): lệnh pandoc xuất chạy với {@code --sandbox} (pandoc không đọc file nào ngoài các file
 * trong dòng lệnh, nên đề chứa {@code ![](/etc/passwd)} hay chỉ thị include không lấy được file của máy chủ); văn bản ngoài
 * công thức không thể tạo code span hay raw block; công thức xuất ra giống hệt khi không có {@code --sandbox}.
 */
class PandocSandboxTest {

    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /** Pandoc giả: ghi các đối số nhận được vào {@code args.txt} rồi tạo exam.docx rỗng trong thư mục chạy. */
    private static String fakePandoc(Path dir) throws Exception {
        Path args = dir.resolve("args.txt");
        if (WINDOWS) {
            Path script = dir.resolve("fake-pandoc.cmd");
            Files.writeString(script, "@echo off\r\necho %* > \"" + args + "\"\r\ntype nul > exam.docx\r\n");
            return script.toString();
        }
        Path script = dir.resolve("fake-pandoc.sh");
        Files.writeString(script, "#!/bin/sh\necho \"$@\" > '" + args + "'\n: > exam.docx\n");
        assertTrue(script.toFile().setExecutable(true));
        return script.toString();
    }

    @Test
    void lenhPandocXuatCoCoSandbox() throws Exception {
        Path dir = Files.createTempDirectory("pandoc-fake-");
        try {
            new PandocRunner(fakePandoc(dir)).markdownToDocx("# A");
            String args = Files.readString(dir.resolve("args.txt"), StandardCharsets.UTF_8);
            assertTrue(args.contains("--sandbox"), "lệnh pandoc xuất phải có --sandbox: " + args);
            assertTrue(args.contains("exam.md") && args.contains("--reference-doc=reference.docx"), args);
        } finally {
            try (var s = Files.walk(dir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ---------- pandoc thật ----------

    private static ExamItemDto mcq(int no, String text, String... options) {
        return new ExamItemDto(no, 1, no, "I", "Đa thức", QuestionType.MCQ, "ready", "generated", null, 100 + no, text,
                options[0], options[1], options[2], options[3], Option.B, null, null, "Lời giải $x^2 + \\frac{1}{2}$.",
                Difficulty.EASY, false, List.of());
    }

    private static ExamViewDto exam(List<ExamItemDto> items) {
        return new ExamViewDto(5, "MT2610301200001", "Đề thử", "teacher", ExamStatus.DRAFT, items.size(),
                LocalDateTime.of(2026, 10, 30, 12, 0), "ready", 1, List.of(new PartDto("I", items.size(), 1, items)));
    }

    /** Chạy pandoc theo lệnh CŨ (trước D4, không có --sandbox) trên cùng markdown. */
    private static byte[] legacyDocx(String pandoc, String markdown) throws Exception {
        Path dir = Files.createTempDirectory("pandoc-legacy-");
        try {
            Files.writeString(dir.resolve("exam.md"), markdown, StandardCharsets.UTF_8);
            try (var in = PandocRunner.class.getResourceAsStream(PandocRunner.REFERENCE_DOC_RESOURCE)) {
                Files.copy(in, dir.resolve("reference.docx"));
            }
            Process p = new ProcessBuilder(pandoc, "+RTS", "-M256m", "-RTS", "exam.md", "-f",
                    "markdown+tex_math_dollars", "-t", "docx", "--reference-doc=reference.docx", "-o", "exam.docx")
                    .directory(dir.toFile()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            assertTrue(p.waitFor(60, TimeUnit.SECONDS));
            assertEquals(0, p.exitValue());
            return Files.readAllBytes(dir.resolve("exam.docx"));
        } finally {
            try (var s = Files.walk(dir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(q -> q.toFile().delete());
            }
        }
    }

    @Test
    void deNhieuCongThucXuatGiongHetKhiCoSandbox() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        List<ExamItemDto> items = new ArrayList<>();
        String[] formulas = {"\\frac{a+b}{c-d}", "\\sqrt{x^2+y^2}", "\\sum_{i=1}^{n} i^2", "\\left(x-1\\right)^3",
                "\\begin{cases} x+y=1 \\\\ x-y=3 \\end{cases}", "80^\\circ", "\\overline{AB} \\parallel \\overline{CD}",
                "\\int_0^1 x\\,dx", "x \\leq y \\Rightarrow x^2 \\geq 0", "\\dfrac{1}{2} \\cdot \\vec{u}"};
        for (int i = 0; i < formulas.length; i++) {
            String f = formulas[i];
            items.add(mcq(i + 1, "Tính giá trị biểu thức $" + f + "$ khi $x = 2$ và giá $5\\$ (hết).",
                    "$" + f + "$", "$" + f + " + 1$", "$2" + f + "$", "$-" + f + "$"));
        }
        String markdown = ExamMarkdownBuilder.build(exam(items));

        byte[] sandboxed = new PandocRunner(pandoc.get()).markdownToDocx(markdown);
        byte[] legacy = legacyDocx(pandoc.get(), markdown);

        String newXml = PandocTestSupport.documentXml(sandboxed);
        assertTrue(newXml.contains("m:oMath"));
        assertEquals(PandocTestSupport.documentXml(legacy), newXml, "document.xml phải giống hệt bản không --sandbox");
        assertEquals(PandocTestSupport.entry(legacy, "word/styles.xml"), PandocTestSupport.entry(sandboxed,
                "word/styles.xml"));
    }

    private static ExamItemDto sa(int no, String text, String answer) {
        return new ExamItemDto(no, 3, no, "III", "Tứ giác", QuestionType.SA, "ready", "generated", null, 300 + no, text,
                null, null, null, null, null, answer, null, "Lời giải.", Difficulty.MEDIUM, false, List.of());
    }

    /**
     * F-05 (soát ngoài): đáp số sa đi vào Word qua inline() nên "$...$" có thể kéo dài qua nhiều dòng; dòng bắt đầu bằng
     * ```{=openxml} bên trong công thức khi đó thành khối raw openxml thật. Xuống dòng trong công thức phải thành khoảng
     * trắng; tiêu đề đề nhiều dòng cũng không được đóng sớm khối openxml của đầu đề. document.xml không có nút lạ.
     */
    @Test
    void tieuDeVaCongThucNhieuDongKhongSinhNutXmlLa() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        String hostileAnswer = "$5\n```{=openxml}\n<w:p><w:r><w:t>RAWMATH</w:t></w:r></w:p>\n```\n$";
        String hostileQuestion = "Tính ```{=openxml}<w:p><w:r><w:t>RAWTEXT</w:t></w:r></w:p>``` và <w:p>chen</w:p>";
        List<ExamItemDto> items = List.of(sa(1, hostileQuestion, hostileAnswer));
        ExamViewDto base = exam(items);
        ExamViewDto hostile = new ExamViewDto(base.examId(), base.examCode(),
                "Đề ```{=openxml}<w:p><w:r><w:t>RAWTITLE</w:t></w:r></w:p>```\n```\n\n# TIEUDEXXX\n",
                base.mode(), base.status(), base.matrixId(), base.createdAt(), base.buildStatus(),
                base.needsReviewCount(), base.parts());
        byte[] docx = new PandocRunner(pandoc.get()).markdownToDocx(ExamMarkdownBuilder.build(hostile));
        String xml = PandocTestSupport.documentXml(docx);

        for (String raw : List.of("<w:t>RAWMATH</w:t>", "<w:t>RAWTEXT</w:t>", "<w:t>RAWTITLE</w:t>")) {
            assertFalse(xml.contains(raw), "không chèn raw openxml: " + raw);
        }
        assertEquals(2, xml.split("w:val=\"Heading1\"", -1).length - 1,
                "chỉ có hai tiêu đề cấp 1 (ĐÁP ÁN, LỜI GIẢI): tiêu đề đề không tách được khối openxml của đầu đề");
        assertEquals(1, xml.split("<w:br w:type=\"page\"/>", -1).length - 1, "chỉ một ngắt trang chủ đích");
    }

    @Test
    void vanBanNgoaiCongThucKhongTaoCodeSpanHayRawBlock() throws Exception {
        Optional<String> pandoc = PandocTestSupport.find();
        assumeTrue(pandoc.isPresent(), "máy không có pandoc: bỏ qua");
        String hostile = "Chuỗi \\` và `{=openxml}` và ```{=openxml}<w:p><w:r><w:t>RAWCHEN</w:t></w:r></w:p>```";
        List<ExamItemDto> items = List.of(
                mcq(1, hostile + "\nDòng hai: `code` và ![ảnh](/etc/passwd) và $x^2$", "`{=openxml}`", "\\`", "\\\\`",
                        "\\"),
                mcq(2, "```{=openxml}\n<w:p><w:r><w:t>RAWCHEN2</w:t></w:r></w:p>\n```\nHết.", "A", "B", "C", "D"));
        byte[] docx = new PandocRunner(pandoc.get()).markdownToDocx(ExamMarkdownBuilder.build(exam(items)));
        String xml = PandocTestSupport.documentXml(docx);

        assertFalse(xml.contains("VerbatimChar") || xml.contains("SourceCode"), "không có code span / khối code");
        assertTrue(xml.contains("`{=openxml}`"), "chuỗi `{=openxml}` hiện nguyên chữ");
        assertTrue(xml.contains("\\`"), "chuỗi \\` hiện nguyên chữ");
        assertFalse(xml.contains("<w:t>RAWCHEN</w:t>") || xml.contains("<w:t>RAWCHEN2</w:t>"),
                "không chèn raw openxml");
        assertTrue(xml.contains("&lt;w:t&gt;RAWCHEN"), "mã giả hiện thành chữ, không thành thẻ");
        // Duy nhất ngắt trang do ExamMarkdownBuilder.PAGE_BREAK chèn có chủ đích.
        assertEquals(1, xml.split("<w:br w:type=\"page\"/>", -1).length - 1, "chỉ một ngắt trang chủ đích");
        assertFalse(xml.contains("<w:drawing"), "ảnh /etc/passwd không được nhúng");
    }
}
