package com.datn.mcq.exam.export;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Tìm pandoc cho test gọi pandoc thật: PANDOC_PATH, rồi "pandoc" trên PATH, rồi bản cài mặc định trên Windows. */
public final class PandocTestSupport {

    private PandocTestSupport() {}

    public static Optional<String> find() {
        String env = System.getenv("PANDOC_PATH");
        if (env != null && !env.isBlank() && runs(env)) {
            return Optional.of(env);
        }
        if (runs("pandoc")) {
            return Optional.of("pandoc");
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            Path exe = Path.of(local, "Pandoc", "pandoc.exe");
            if (Files.isRegularFile(exe) && runs(exe.toString())) {
                return Optional.of(exe.toString());
            }
        }
        return Optional.empty();
    }

    private static boolean runs(String path) {
        try {
            Process p = new ProcessBuilder(path, "--version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Nội dung word/document.xml trong file docx; ném lỗi nếu không phải zip hợp lệ hoặc thiếu mục này. */
    public static String documentXml(byte[] docx) throws IOException {
        return entry(docx, "word/document.xml");
    }

    /** Nội dung một mục (ví dụ word/settings.xml) trong file docx; ném lỗi nếu thiếu. */
    public static String entry(byte[] docx, String name) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals(name)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("docx không có " + name);
    }

    /** Khai báo chế độ tương thích Word 2013+ (không mở ở Compatibility Mode). */
    public static boolean hasCompatibilityMode15(String settingsXml) {
        return settingsXml.matches("(?s).*<w:compat>\\s*<w:compatSetting w:name=\"compatibilityMode\""
                + " w:uri=\"http://schemas.microsoft.com/office/word\" w:val=\"15\"\\s*/>\\s*</w:compat>.*");
    }

    /** Kiểu Normal dùng Times New Roman cỡ 13 (sz = 26 nửa điểm). */
    public static boolean normalIsTimesNewRoman13(String stylesXml) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?s)<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">(.*?)</w:style>").matcher(stylesXml);
        if (!m.find()) {
            return false;
        }
        String normal = m.group(1);
        return normal.contains("w:ascii=\"Times New Roman\"") && normal.contains("w:hAnsi=\"Times New Roman\"")
                && normal.contains("<w:sz w:val=\"26\"");
    }
}
