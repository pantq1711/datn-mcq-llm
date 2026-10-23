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
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("docx không có word/document.xml");
    }
}
