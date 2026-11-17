package com.datn.mcq.questionimage.service;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Đọc header nhị phân để trích xuất kích thước và kiểm tra giới hạn an toàn trước khi nạp ảnh vào bộ nhớ.
 * Ngăn chặn tấn công cạn kiệt tài nguyên (Decompression Bomb / Pixel Flood / OOM) từ file giả mạo.
 */
@Component
public class ImageHeaderInspector {

    private static final byte[] PNG_MAGIC = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_87A = new byte[] {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF_89A = new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] BMP_MAGIC = new byte[] {0x42, 0x4D};
    private static final int EMF_SIGNATURE = 0x464D4520; // " EMF" dạng Little-Endian (20 45 4D 46)

    private final ImageImportProperties props;

    public ImageHeaderInspector(ImageImportProperties props) {
        this.props = props;
    }

    /**
     * Phân tích byte header của ảnh, xác thực magic byte và giới hạn kích thước trước khi giải mã.
     *
     * @param data Mảng byte thô của file ảnh
     * @return Thông tin kích thước và định dạng ảnh
     * @throws ImageValidationException Nếu ảnh không đúng định dạng, file hỏng hoặc vượt quá ngưỡng an toàn
     */
    public ImageHeaderInfo inspect(byte[] data) {
        if (data == null || data.length < 8) {
            throw new ImageValidationException("INVALID_IMAGE_FORMAT", "Dữ liệu ảnh rỗng hoặc quá ngắn");
        }

        if (data.length > props.getMaxSourceBytes()) {
            throw new ImageValidationException("IMAGE_TOO_LARGE",
                    "Kích thước file (" + data.length + " bytes) vượt quá giới hạn tối đa " + props.getMaxSourceBytes() + " bytes");
        }

        ImageHeaderInfo info;
        if (startsWith(data, PNG_MAGIC)) {
            info = inspectPng(data);
        } else if (startsWith(data, JPEG_MAGIC)) {
            info = inspectJpeg(data);
        } else if (startsWith(data, GIF_87A) || startsWith(data, GIF_89A)) {
            info = inspectGif(data);
        } else if (startsWith(data, BMP_MAGIC)) {
            info = inspectBmp(data);
        } else if (isEmf(data)) {
            info = inspectEmf(data);
        } else {
            throw new ImageValidationException("INVALID_IMAGE_FORMAT", "Định dạng ảnh không được hỗ trợ hoặc magic bytes không hợp lệ");
        }

        validateDimensions(info);
        return info;
    }

    /**
     * Kiểm tra header IHDR và giới hạn kích thước của ảnh PNG trước khi giải mã (dùng cho đầu ra LibreOffice trước autocrop).
     */
    public ImageHeaderInfo inspectPngHeader(byte[] data) {
        if (data == null || !startsWith(data, PNG_MAGIC)) {
            throw new ImageValidationException("INVALID_IMAGE_FORMAT", "Dữ liệu không phải ảnh PNG hợp lệ");
        }
        ImageHeaderInfo info = inspectPng(data);
        validateDimensions(info.width(), info.height());
        return info;
    }

    /**
     * Kiểm tra chiều rộng, chiều cao và tổng số pixel với giới hạn cấu hình trước khi cấp phát bộ đệm ảnh.
     */
    public void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new ImageValidationException("CORRUPTED_IMAGE",
                    "Kích thước ảnh không hợp lệ: " + width + "x" + height);
        }

        if (width > props.getMaxDimension() || height > props.getMaxDimension()) {
            throw new ImageValidationException("IMAGE_DIMENSION_TOO_LARGE",
                    "Kích thước ảnh (" + width + "x" + height + ") vượt quá giới hạn tối đa " + props.getMaxDimension() + " px");
        }

        long totalPixels = (long) width * height;
        if (totalPixels > props.getMaxPixels()) {
            throw new ImageValidationException("IMAGE_PIXEL_FLOOD",
                    "Tổng số pixel (" + totalPixels + ") vượt quá ngưỡng tối đa cho phép " + props.getMaxPixels() + " pixels");
        }
    }

    private void validateDimensions(ImageHeaderInfo info) {
        validateDimensions(info.width(), info.height());
    }

    private ImageHeaderInfo inspectPng(byte[] data) {
        if (data.length < 24) {
            throw new ImageValidationException("CORRUPTED_IMAGE", "File PNG quá ngắn để đọc IHDR");
        }
        // IHDR chunk bắt đầu tại offset 12: byte 12..15 là "IHDR"
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            throw new ImageValidationException("CORRUPTED_IMAGE", "Chunk đầu tiên của PNG không phải IHDR");
        }

        ByteBuffer buf = ByteBuffer.wrap(data, 16, 8).order(ByteOrder.BIG_ENDIAN);
        int width = buf.getInt();
        int height = buf.getInt();
        return new ImageHeaderInfo("png", "image/png", width, height, data.length);
    }

    private ImageHeaderInfo inspectJpeg(byte[] data) {
        int offset = 2; // Bỏ qua 0xFF, 0xD8
        int length = data.length;

        while (offset < length - 1) {
            if ((data[offset] & 0xFF) != 0xFF) {
                offset++;
                continue;
            }

            // Bỏ qua các byte 0xFF liên tiếp
            while (offset < length && (data[offset] & 0xFF) == 0xFF) {
                offset++;
            }

            if (offset >= length) {
                break;
            }

            int marker = data[offset] & 0xFF;
            offset++;

            // EOI (End of Image) hoặc SOS (Start of Scan - bắt đầu compressed data)
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }

            // Standalone markers không có độ dài: RST0..RST7 (0xD0..0xD7), TEM (0x01)
            if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                continue;
            }

            if (offset + 1 >= length) {
                break;
            }

            int segmentLength = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            if (segmentLength < 2 || offset + segmentLength > length) {
                throw new ImageValidationException("CORRUPTED_IMAGE", "Segment JPEG có độ dài không hợp lệ");
            }

            // Các SOF markers chứa kích thước: SOF0..SOF3, SOF5..SOF7, SOF9..SOF11, SOF13..SOF15
            boolean isSof = (marker >= 0xC0 && marker <= 0xC3)
                    || (marker >= 0xC5 && marker <= 0xC7)
                    || (marker >= 0xC9 && marker <= 0xCB)
                    || (marker >= 0xCD && marker <= 0xCF);

            if (isSof) {
                if (segmentLength < 7) {
                    throw new ImageValidationException("CORRUPTED_IMAGE", "SOF segment JPEG quá ngắn");
                }
                // offset + 2: precision (1 byte)
                // offset + 3..4: height (2 bytes)
                // offset + 5..6: width (2 bytes)
                int height = ((data[offset + 3] & 0xFF) << 8) | (data[offset + 4] & 0xFF);
                int width = ((data[offset + 5] & 0xFF) << 8) | (data[offset + 6] & 0xFF);
                return new ImageHeaderInfo("jpeg", "image/jpeg", width, height, data.length);
            }

            offset += segmentLength;
        }

        throw new ImageValidationException("CORRUPTED_IMAGE", "Không tìm thấy SOF marker trong file JPEG");
    }

    private ImageHeaderInfo inspectGif(byte[] data) {
        if (data.length < 10) {
            throw new ImageValidationException("CORRUPTED_IMAGE", "File GIF quá ngắn");
        }
        ByteBuffer buf = ByteBuffer.wrap(data, 6, 4).order(ByteOrder.LITTLE_ENDIAN);
        int width = Short.toUnsignedInt(buf.getShort());
        int height = Short.toUnsignedInt(buf.getShort());
        return new ImageHeaderInfo("gif", "image/gif", width, height, data.length);
    }

    private ImageHeaderInfo inspectBmp(byte[] data) {
        if (data.length < 26) {
            throw new ImageValidationException("CORRUPTED_IMAGE", "File BMP quá ngắn");
        }
        ByteBuffer buf = ByteBuffer.wrap(data, 14, 12).order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = buf.getInt();

        int width;
        int height;
        if (headerSize == 12) {
            // BITMAPCOREHEADER
            width = Short.toUnsignedInt(buf.getShort());
            height = Short.toUnsignedInt(buf.getShort());
        } else {
            // BITMAPINFOHEADER hoặc mới hơn
            width = Math.abs(buf.getInt());
            height = Math.abs(buf.getInt());
        }
        return new ImageHeaderInfo("bmp", "image/bmp", width, height, data.length);
    }

    private boolean isEmf(byte[] data) {
        if (data.length < 44) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(data, 0, 44).order(ByteOrder.LITTLE_ENDIAN);
        int iType = buf.getInt(); // 0..3: EMR_HEADER phải = 1
        if (iType != 1) {
            return false;
        }
        int signature = ByteBuffer.wrap(data, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return signature == EMF_SIGNATURE;
    }

    private ImageHeaderInfo inspectEmf(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 8, 16).order(ByteOrder.LITTLE_ENDIAN);
        long left = buf.getInt();
        long top = buf.getInt();
        long right = buf.getInt();
        long bottom = buf.getInt();

        long rawWidth = Math.abs(right - left) + 1L;
        long rawHeight = Math.abs(bottom - top) + 1L;

        // Nếu rclBounds rỗng, thử đọc szlDevice tại offset 72..79 nếu dữ liệu đủ dài
        if ((rawWidth <= 1 || rawHeight <= 1) && data.length >= 80) {
            ByteBuffer devBuf = ByteBuffer.wrap(data, 72, 8).order(ByteOrder.LITTLE_ENDIAN);
            int devWidth = devBuf.getInt();
            int devHeight = devBuf.getInt();
            if (devWidth > 0 && devHeight > 0) {
                rawWidth = devWidth;
                rawHeight = devHeight;
            }
        }

        int width = rawWidth > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawWidth;
        int height = rawHeight > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawHeight;
        return new ImageHeaderInfo("emf", "image/x-emf", width, height, data.length);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
