package com.datn.mcq.bankimport.out;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tùy chọn (--convert-images): đổi WMF/EMF sang PNG bằng System.Drawing của Windows PowerShell. Chỉ chạy trên Windows;
 * thất bại thì bỏ qua từng tệp (vẫn còn bản gốc). Không phụ thuộc LibreOffice hay Inkscape.
 */
public final class ImageConverter {

    private static final String SCRIPT = String.join("\n",
            "Add-Type -AssemblyName System.Drawing",
            "Get-Content -LiteralPath $args[0] -Encoding UTF8 | ForEach-Object {",
            "  $p = $_.Split('|'); $in = $p[0]; $out = $p[1]",
            "  try {",
            "    $m = [System.Drawing.Image]::FromFile($in)",
            "    $w = [Math]::Max(1, [int]($m.Width * 3)); $h = [Math]::Max(1, [int]($m.Height * 3))",
            "    $b = New-Object System.Drawing.Bitmap($w, $h)",
            "    $g = [System.Drawing.Graphics]::FromImage($b)",
            "    $g.Clear([System.Drawing.Color]::White)",
            "    $g.DrawImage($m, 0, 0, $w, $h)",
            "    $b.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)",
            "    $g.Dispose(); $b.Dispose(); $m.Dispose()",
            "  } catch { }",
            "}");

    static boolean available() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    /** Đổi từng cặp (nguồn → PNG); trả về danh sách PNG đã tạo được. */
    public static List<Path> convert(List<Path[]> pairs, Path workDir) {
        List<Path> done = new ArrayList<>();
        if (pairs.isEmpty() || !available()) {
            return done;
        }
        try {
            Path script = Files.createTempFile(workDir, "convert", ".ps1");
            Path list = Files.createTempFile(workDir, "convert", ".txt");
            Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (Path[] p : pairs) {
                lines.add(p[0].toAbsolutePath() + "|" + p[1].toAbsolutePath());
            }
            Files.write(list, lines, StandardCharsets.UTF_8);
            Process proc = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                    script.toString(), list.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!proc.waitFor(300, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
            }
            Files.deleteIfExists(script);
            Files.deleteIfExists(list);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        for (Path[] p : pairs) {
            if (Files.isRegularFile(p[1])) {
                done.add(p[1]);
            }
        }
        return done;
    }

    private ImageConverter() {}
}
