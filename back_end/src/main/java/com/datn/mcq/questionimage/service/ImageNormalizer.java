package com.datn.mcq.questionimage.service;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * Chuẩn hóa ảnh: kiểm tra kích thước header, giải mã, thu nhỏ nếu vượt 1600px,
 * chuyển đổi định dạng an toàn (giữ JPEG, đổi GIF/BMP sang PNG), xóa bỏ hoàn toàn
 * metadata/EXIF rác và nén đảm bảo kích thước lưu trữ &lt;= 500 KB.
 */
@Component
public class ImageNormalizer {

    private final ImageHeaderInspector inspector;
    private final ImageImportProperties props;

    public ImageNormalizer(ImageHeaderInspector inspector, ImageImportProperties props) {
        this.inspector = inspector;
        this.props = props;
    }

    /**
     * Chuẩn hóa mảng byte ảnh nguồn thô.
     *
     * @param rawBytes Dữ liệu byte ảnh thô từ file docx
     * @return Dữ liệu ảnh đã được chuẩn hóa
     * @throws ImageValidationException Nếu ảnh không hợp lệ hoặc vượt ngưỡng cho phép
     */
    public NormalizedImage normalize(byte[] rawBytes) {
        // 1. Kiểm tra header và kích thước trước khi giải mã vào bộ nhớ
        ImageHeaderInfo headerInfo = inspector.inspect(rawBytes);

        // 2. Tính SHA-256 của byte gốc ban đầu
        String sha256 = calculateSha256(rawBytes);

        // 3. Giải mã ảnh qua ImageIO
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(rawBytes));
        } catch (IOException e) {
            throw new ImageValidationException("CORRUPTED_IMAGE", "Lỗi đọc dữ liệu ảnh: " + e.getMessage(), e);
        }

        if (image == null) {
            throw new ImageValidationException("CORRUPTED_IMAGE", "ImageIO không thể giải mã dữ liệu ảnh");
        }

        // 4. Xác định định dạng đích
        boolean isJpeg = "jpeg".equalsIgnoreCase(headerInfo.format());
        String targetMime = isJpeg ? "image/jpeg" : "image/png";
        String targetFormat = isJpeg ? "jpeg" : "png";

        // 5. Chuẩn hóa ColorSpace và thu nhỏ kích thước nếu vượt quá 1600px
        int maxDim = props.getMaxOutputDimension();
        int origW = image.getWidth();
        int origH = image.getHeight();

        int targetW = origW;
        int targetH = origH;
        if (Math.max(origW, origH) > maxDim) {
            double scale = (double) maxDim / Math.max(origW, origH);
            targetW = Math.max(1, (int) Math.round(origW * scale));
            targetH = Math.max(1, (int) Math.round(origH * scale));
        }

        int imageType = isJpeg
                ? BufferedImage.TYPE_INT_RGB
                : (image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);

        BufferedImage processed = new BufferedImage(targetW, targetH, imageType);
        Graphics2D g2 = processed.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isJpeg) {
                // Với JPEG không có alpha channel, phủ nền trắng trước khi vẽ
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, targetW, targetH);
            }

            g2.drawImage(image, 0, 0, targetW, targetH, null);
        } finally {
            g2.dispose();
        }

        // 6. Mã hóa lại và kiểm tra dung lượng byte xuất (<= 500 KB)
        byte[] outputBytes = encode(processed, targetFormat, isJpeg);
        if (outputBytes.length > props.getMaxOutputBytes()) {
            throw new ImageValidationException("IMAGE_BYTE_LIMIT",
                    "Dung lượng ảnh sau chuẩn hóa (" + outputBytes.length + " bytes) vượt quá giới hạn " + props.getMaxOutputBytes() + " bytes");
        }

        return new NormalizedImage(
                outputBytes,
                targetMime,
                headerInfo.format(),
                targetW,
                targetH,
                outputBytes.length,
                sha256
        );
    }

    private byte[] encode(BufferedImage image, String format, boolean isJpeg) {
        if (!isJpeg) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "png", baos);
            } catch (IOException e) {
                throw new ImageValidationException("CORRUPTED_IMAGE", "Lỗi ghi ảnh PNG: " + e.getMessage(), e);
            }
            return baos.toByteArray();
        }

        // Với JPEG: dùng ImageWriter với compression quality giảm dần nếu cần
        float[] qualityTries = new float[] {0.85f, 0.75f, 0.65f};
        byte[] bestResult = null;

        for (float quality : qualityTries) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new ImageValidationException("CORRUPTED_IMAGE", "Không tìm thấy JPEG ImageWriter");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);

                writer.write(null, new IIOImage(image, null, null), param);
                writer.dispose();
                bestResult = baos.toByteArray();
                if (bestResult.length <= props.getMaxOutputBytes()) {
                    return bestResult;
                }
            } catch (IOException e) {
                writer.dispose();
                throw new ImageValidationException("CORRUPTED_IMAGE", "Lỗi nén ảnh JPEG: " + e.getMessage(), e);
            }
        }

        return bestResult != null ? bestResult : new byte[0];
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng", e);
        }
    }
}
