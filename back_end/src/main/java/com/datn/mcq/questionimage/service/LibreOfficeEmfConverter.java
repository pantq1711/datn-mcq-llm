package com.datn.mcq.questionimage.service;

import com.datn.mcq.config.PandocLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Chuyển đổi hàng loạt ảnh EMF sang PNG bằng LibreOffice headless.
 * Điều phối tài nguyên qua PandocLimiter và tự động cắt lề trắng (autocrop).
 */
@Component
public class LibreOfficeEmfConverter implements EmfConverter {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeEmfConverter.class);
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private static final List<String> WINDOWS_SYSTEM_VARS = List.of("SystemRoot", "TEMP", "TMP");

    private final ImageImportProperties props;
    private final PandocLimiter limiter;
    private final ImageHeaderInspector inspector;

    @Autowired
    public LibreOfficeEmfConverter(ImageImportProperties props, PandocLimiter limiter, ImageHeaderInspector inspector) {
        this.props = props;
        this.limiter = limiter;
        this.inspector = inspector;
    }

    public LibreOfficeEmfConverter(ImageImportProperties props, PandocLimiter limiter) {
        this(props, limiter, new ImageHeaderInspector(props));
    }

    @Override
    public boolean isAvailable() {
        Path homeDir = null;
        try {
            homeDir = Files.createTempDirectory("datn_soffice_home_");
            Process process = newSofficeProcess(List.of("--version"), homeDir)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                destroyProcessTree(process);
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("LibreOffice không khả dụng tại '{}': {}", props.getLibreofficePath(), e.getMessage());
            return false;
        } finally {
            if (homeDir != null) {
                deleteDirectoryRecursively(homeDir);
            }
        }
    }

    @Override
    public Map<String, EmfConvertResult> convertBatch(Map<String, byte[]> emfFiles) {
        if (emfFiles == null || emfFiles.isEmpty()) {
            return Collections.emptyMap();
        }

        // Lấy permit từ PandocLimiter
        boolean acquired;
        try {
            acquired = limiter.tryAcquire(props.getTimeout());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failAll(emfFiles, "CONVERTER_BUSY", "Bị gián đoạn khi chờ tài nguyên chuyển đổi");
        }

        if (!acquired) {
            return failAll(emfFiles, "CONVERTER_BUSY", "Hệ thống đang bận xử lý tài liệu khác, vui lòng thử lại sau");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("datn_emf_");
            Path inDir = Files.createDirectories(tempDir.resolve("in"));
            Path outDir = Files.createDirectories(tempDir.resolve("out"));
            Path profileDir = Files.createDirectories(tempDir.resolve("profile"));
            Path homeDir = Files.createDirectories(tempDir.resolve("home"));

            // Kiểm tra header và khung EMF trước khi ghi file tạm và gọi LibreOffice
            Map<String, EmfConvertResult> results = new LinkedHashMap<>();
            Map<String, String> keyToFileId = new LinkedHashMap<>();
            int idx = 0;
            for (Map.Entry<String, byte[]> entry : emfFiles.entrySet()) {
                try {
                    ImageHeaderInfo info = inspector.inspect(entry.getValue());
                    if (!"emf".equals(info.format())) {
                        results.put(entry.getKey(), EmfConvertResult.failure(
                                "INVALID_IMAGE_FORMAT", "Tệp đầu vào không phải định dạng EMF hợp lệ"));
                        continue;
                    }
                } catch (ImageValidationException e) {
                    results.put(entry.getKey(), EmfConvertResult.failure(e.getErrorCode(), e.getMessage()));
                    continue;
                }
                String fileId = "img_" + (++idx);
                keyToFileId.put(entry.getKey(), fileId);
                Path emfPath = inDir.resolve(fileId + ".emf");
                Files.write(emfPath, entry.getValue());
            }

            if (keyToFileId.isEmpty()) {
                return results;
            }

            // Dựng lệnh soffice đổi cả lô trong 1 tiến trình (môi trường sạch, trần bộ nhớ nếu cấu hình)
            List<String> command = new ArrayList<>();
            command.add("--headless");
            command.add("--norestore");
            command.add("--convert-to");
            command.add("png");
            command.add("--outdir");
            command.add(outDir.toAbsolutePath().toString());
            command.add("-env:UserInstallation=file:///" + profileDir.toAbsolutePath().toString().replace('\\', '/'));

            for (String fileId : keyToFileId.values()) {
                command.add(inDir.resolve(fileId + ".emf").toAbsolutePath().toString());
            }

            Path logFile = tempDir.resolve("soffice.log");
            ProcessBuilder pb = newSofficeProcess(command, homeDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile());
            Process process = pb.start();

            boolean finished = process.waitFor(props.getTimeout().toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                destroyProcessTree(process);
                log.warn("LibreOffice chuyển đổi EMF bị quá hạn (timeout {}s)", props.getTimeout().toSeconds());
                for (String key : keyToFileId.keySet()) {
                    results.put(key, EmfConvertResult.failure("CONVERTER_TIMEOUT", "Quá thời gian chuyển đổi ảnh EMF"));
                }
                return results;
            }
            if (process.exitValue() != 0) {
                // Ví dụ soffice bị dừng vì vượt trần bộ nhớ (prlimit --as): ảnh thiếu PNG sẽ nhận CONVERTER_FAILED bên dưới
                log.warn("LibreOffice đổi EMF thoát với mã {}", process.exitValue());
            }

            // Đọc và xử lý kết quả từng ảnh
            for (Map.Entry<String, String> entry : keyToFileId.entrySet()) {
                String originalKey = entry.getKey();
                String fileId = entry.getValue();
                Path pngPath = outDir.resolve(fileId + ".png");

                if (!Files.exists(pngPath) || Files.size(pngPath) == 0) {
                    results.put(originalKey, EmfConvertResult.failure("CONVERTER_FAILED", "Không thể xuất ảnh PNG từ EMF"));
                    continue;
                }

                try {
                    byte[] rawPng = Files.readAllBytes(pngPath);
                    byte[] croppedPng = autocropWhitespace(rawPng);
                    results.put(originalKey, EmfConvertResult.ok(croppedPng));
                } catch (ImageValidationException e) {
                    log.warn("PNG sau khi đổi EMF cho '{}' không qua kiểm tra header: {} ({})",
                            originalKey, e.getErrorCode(), e.getMessage());
                    results.put(originalKey, EmfConvertResult.failure(e.getErrorCode(), e.getMessage()));
                } catch (Exception e) {
                    log.warn("Lỗi xử lý PNG sau khi đổi EMF cho '{}': {}", originalKey, e.getMessage());
                    results.put(originalKey, EmfConvertResult.failure("CONVERTER_POST_PROCESS_FAILED", e.getMessage()));
                }
            }
            return results;

        } catch (Exception e) {
            log.error("Lỗi khi chạy LibreOffice đổi EMF: {}", e.getMessage(), e);
            return failAll(emfFiles, "CONVERTER_ERROR", "Lỗi chuyển đổi EMF: " + e.getMessage());
        } finally {
            limiter.release();
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }
        }
    }

    /**
     * Tự động cắt lề trắng (autocrop whitespace) cho ảnh PNG. Kiểm tra header PNG trước khi giải mã để chặn ảnh quá lớn.
     */
    public byte[] autocropWhitespace(byte[] pngBytes) throws IOException {
        inspector.inspectPngHeader(pngBytes);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (image == null) {
            return pngBytes;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        int top = 0;
        int bottom = height - 1;
        int left = 0;
        int right = width - 1;

        // Quét top
        topLoop:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isWhiteOrTransparent(image.getRGB(x, y))) {
                    top = y;
                    break topLoop;
                }
            }
        }

        // Quét bottom
        bottomLoop:
        for (int y = height - 1; y >= top; y--) {
            for (int x = 0; x < width; x++) {
                if (!isWhiteOrTransparent(image.getRGB(x, y))) {
                    bottom = y;
                    break bottomLoop;
                }
            }
        }

        // Quét left
        leftLoop:
        for (int x = 0; x < width; x++) {
            for (int y = top; y <= bottom; y++) {
                if (!isWhiteOrTransparent(image.getRGB(x, y))) {
                    left = x;
                    break leftLoop;
                }
            }
        }

        // Quét right
        rightLoop:
        for (int x = width - 1; x >= left; x--) {
            for (int y = top; y <= bottom; y++) {
                if (!isWhiteOrTransparent(image.getRGB(x, y))) {
                    right = x;
                    break rightLoop;
                }
            }
        }

        // Nếu ảnh hoàn toàn trắng hoặc rỗng
        if (right < left || bottom < top) {
            return pngBytes;
        }

        // Thêm padding 2px nếu có chỗ
        int pad = 2;
        int cropX = Math.max(0, left - pad);
        int cropY = Math.max(0, top - pad);
        int cropW = Math.min(width - cropX, (right - left + 1) + 2 * pad);
        int cropH = Math.min(height - cropY, (bottom - top + 1) + 2 * pad);

        BufferedImage cropped = image.getSubimage(cropX, cropY, cropW, cropH);
        // Sao chép sang BufferedImage mới để giải phóng bộ nhớ của ảnh gốc
        BufferedImage copy = new BufferedImage(cropW, cropH,
                image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(cropped, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(copy, "png", baos);
        return baos.toByteArray();
    }

    private boolean isWhiteOrTransparent(int rgb) {
        int alpha = (rgb >> 24) & 0xFF;
        if (alpha == 0) {
            return true;
        }
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        // Pixel gần như trắng tinh
        return red >= 250 && green >= 250 && blue >= 250;
    }

    /**
     * Lệnh đầy đủ để chạy soffice. Khi cấu hình {@code soffice-max-address-space} thì bọc bằng {@code prlimit --as=<byte> --}:
     * prlimit exec thẳng soffice (giữ PID, nên diệt cây tiến trình khi timeout vẫn đúng) và các tiến trình con
     * (oosplash, soffice.bin) thừa kế trần. Thiếu prlimit thì tiến trình không khởi động được, ảnh không được đổi (fail closed).
     */
    public List<String> buildCommand(List<String> sofficeArgs) {
        List<String> command = new ArrayList<>();
        DataSize cap = props.getSofficeMaxAddressSpace();
        if (cap != null && cap.toBytes() > 0) {
            command.add(props.getPrlimitPath());
            command.add("--as=" + cap.toBytes());
            command.add("--");
        }
        command.add(props.getLibreofficePath());
        command.addAll(sofficeArgs);
        return command;
    }

    /**
     * Môi trường tối thiểu cho soffice: không thừa kế biến của JVM (JWT_SECRET, LLM_API_KEY, DB_PASSWORD...). Chỉ giữ PATH
     * (để script soffice tìm lệnh hệ thống), HOME là thư mục tạm riêng của lần chạy, LANG cố định. Trên Windows (máy dev)
     * giữ thêm SystemRoot, TEMP, TMP vì thiếu thì soffice.exe không chạy; các biến này không chứa bí mật.
     */
    public static Map<String, String> sofficeEnvironment(Map<String, String> parent, Path homeDir, boolean windows) {
        Map<String, String> env = new LinkedHashMap<>();
        String path = lookupIgnoreCase(parent, "PATH");
        if (path != null) {
            env.put("PATH", path);
        }
        env.put("HOME", homeDir.toAbsolutePath().toString());
        env.put("LANG", "C.UTF-8");
        if (windows) {
            for (String name : WINDOWS_SYSTEM_VARS) {
                String value = lookupIgnoreCase(parent, name);
                if (value != null) {
                    env.put(name, value);
                }
            }
        }
        return env;
    }

    /** Tạo ProcessBuilder cho soffice với lệnh từ {@link #buildCommand} và môi trường từ {@link #sofficeEnvironment}. */
    public ProcessBuilder newSofficeProcess(List<String> sofficeArgs, Path homeDir) {
        ProcessBuilder pb = new ProcessBuilder(buildCommand(sofficeArgs));
        Map<String, String> env = pb.environment();
        Map<String, String> clean = sofficeEnvironment(new LinkedHashMap<>(env), homeDir, IS_WINDOWS);
        env.clear();
        env.putAll(clean);
        return pb;
    }

    private static String lookupIgnoreCase(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private void destroyProcessTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
        }
        process.destroyForcibly();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, EmfConvertResult> failAll(Map<String, byte[]> files, String code, String message) {
        Map<String, EmfConvertResult> map = new LinkedHashMap<>();
        for (String key : files.keySet()) {
            map.put(key, EmfConvertResult.failure(code, message));
        }
        return map;
    }

    private void deleteDirectoryRecursively(Path path) {
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignored) {
        }
    }
}
