package com.datn.mcq.questionimage.service;

import com.datn.mcq.config.PandocLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Chuyển đổi hàng loạt ảnh EMF sang PNG bằng LibreOffice headless.
 * Điều phối tài nguyên qua PandocLimiter và tự động cắt lề trắng (autocrop).
 */
@Component
public class LibreOfficeEmfConverter implements EmfConverter {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeEmfConverter.class);

    private final ImageImportProperties props;
    private final PandocLimiter limiter;

    public LibreOfficeEmfConverter(ImageImportProperties props, PandocLimiter limiter) {
        this.props = props;
        this.limiter = limiter;
    }

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(props.getLibreofficePath(), "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("LibreOffice không khả dụng tại '{}': {}", props.getLibreofficePath(), e.getMessage());
            return false;
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

            // Ánh xạ key -> file tạm
            Map<String, String> keyToFileId = new LinkedHashMap<>();
            int idx = 0;
            for (Map.Entry<String, byte[]> entry : emfFiles.entrySet()) {
                String fileId = "img_" + (++idx);
                keyToFileId.put(entry.getKey(), fileId);
                Path emfPath = inDir.resolve(fileId + ".emf");
                Files.write(emfPath, entry.getValue());
            }

            // Dựng lệnh soffice đổi cả lô trong 1 tiến trình
            List<String> command = new ArrayList<>();
            command.add(props.getLibreofficePath());
            command.add("--headless");
            command.add("--convert-to");
            command.add("png");
            command.add("--outdir");
            command.add(outDir.toAbsolutePath().toString());
            command.add("-env:UserInstallation=file:///" + profileDir.toAbsolutePath().toString().replace('\\', '/'));

            for (String fileId : keyToFileId.values()) {
                command.add(inDir.resolve(fileId + ".emf").toAbsolutePath().toString());
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(props.getTimeout().toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("LibreOffice chuyển đổi EMF bị quá hạn (timeout {}s)", props.getTimeout().toSeconds());
                return failAll(emfFiles, "CONVERTER_TIMEOUT", "Quá thời gian chuyển đổi ảnh EMF");
            }

            // Đọc và xử lý kết quả từng ảnh
            Map<String, EmfConvertResult> results = new LinkedHashMap<>();
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
     * Tự động cắt lề trắng (autocrop whitespace) cho ảnh PNG.
     */
    public byte[] autocropWhitespace(byte[] pngBytes) throws IOException {
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
