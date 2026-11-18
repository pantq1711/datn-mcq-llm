package com.datn.mcq.questionimage;

import com.datn.mcq.questionimage.service.ImageHeaderInfo;
import com.datn.mcq.questionimage.service.ImageHeaderInspector;
import com.datn.mcq.questionimage.service.ImageImportProperties;
import com.datn.mcq.questionimage.service.ImageNormalizer;
import com.datn.mcq.questionimage.service.ImageValidationException;
import com.datn.mcq.questionimage.service.NormalizedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageNormalizerTest {

    private ImageImportProperties props;
    private ImageHeaderInspector inspector;
    private ImageNormalizer normalizer;

    @BeforeEach
    void setUp() {
        props = new ImageImportProperties();
        inspector = new ImageHeaderInspector(props);
        normalizer = new ImageNormalizer(inspector, props);
    }

    private byte[] createTestImage(String format, int width, int height, Color color) throws IOException {
        int imageType = ("jpeg".equalsIgnoreCase(format) || "bmp".equalsIgnoreCase(format))
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage bi = new BufferedImage(width, height, imageType);
        Graphics2D g = bi.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawString("Test " + width + "x" + height, 5, Math.min(height - 5, 20));
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(bi, format, baos);
        assertTrue(written, "ImageIO phải hỗ trợ ghi format: " + format);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("PNG khai 20000x20000 trong IHDR bị từ chối trước khi giải mã (chống DoS / decompression bomb)")
    void pngKhai20000x20000_TuChoiTruocKhiGiaiMa() {
        // Tạo PNG giả mạo chỉ có 29 byte nhưng khai báo 20000x20000 trong IHDR
        byte[] fakeBombPng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG magic
                0x00, 0x00, 0x00, 0x0D,                                 // IHDR length 13
                0x49, 0x48, 0x44, 0x52,                                 // "IHDR"
                0x00, 0x00, 0x4E, 0x20,                                 // width = 20000
                0x00, 0x00, 0x4E, 0x20,                                 // height = 20000
                0x08, 0x02, 0x00, 0x00, 0x00,                           // 8-bit, truecolor
                0x00, 0x00, 0x00, 0x00                                  // CRC giả
        };

        ImageValidationException ex = assertThrows(ImageValidationException.class, () ->
                normalizer.normalize(fakeBombPng)
        );
        assertEquals("IMAGE_DIMENSION_TOO_LARGE", ex.getErrorCode());
    }

    @Test
    @DisplayName("Ảnh nguồn thô vượt quá 2 MB bị từ chối ngay lập tức")
    void anhVuot2MB_TuChoi() {
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0x89;
        oversized[1] = 0x50;
        oversized[2] = 0x4E;
        oversized[3] = 0x47;

        ImageValidationException ex = assertThrows(ImageValidationException.class, () ->
                normalizer.normalize(oversized)
        );
        assertEquals("IMAGE_TOO_LARGE", ex.getErrorCode());
    }

    @Test
    @DisplayName("Ảnh có tổng số pixel vượt quá 12 Megapixels bị từ chối (chống pixel flood)")
    void anhVuot12MP_TuChoi() {
        // Tạo header PNG 3500x3500 = 12.25 MP (> 12 MP)
        byte[] pixelFloodPng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D,
                0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x0D, (byte) 0xAC,                         // width = 3500
                0x00, 0x00, 0x0D, (byte) 0xAC,                         // height = 3500
                0x08, 0x02, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };

        ImageValidationException ex = assertThrows(ImageValidationException.class, () ->
                normalizer.normalize(pixelFloodPng)
        );
        assertEquals("IMAGE_PIXEL_FLOOD", ex.getErrorCode());
    }

    @Test
    @DisplayName("File giả đuôi hoặc byte magic không hợp lệ bị từ chối")
    void fileGiaDuoiPng_MagicKhongHopLe_TuChoi() {
        byte[] fakeData = "This is not an image file at all!".getBytes();

        ImageValidationException ex = assertThrows(ImageValidationException.class, () ->
                normalizer.normalize(fakeData)
        );
        assertEquals("INVALID_IMAGE_FORMAT", ex.getErrorCode());
    }

    @Test
    @DisplayName("Ảnh JPEG chuẩn hóa giữ nguyên định dạng JPEG và xóa sạch metadata/EXIF")
    void jpegGiuJpeg_XoaMetadata() throws Exception {
        byte[] rawJpeg = createTestImage("jpeg", 400, 300, Color.BLUE);

        NormalizedImage result = normalizer.normalize(rawJpeg);

        assertNotNull(result);
        assertEquals("image/jpeg", result.mime());
        assertEquals("jpeg", result.sourceFormat());
        assertEquals(400, result.width());
        assertEquals(300, result.height());
        assertTrue(result.byteSize() <= 512_000, "Byte size phải <= 500 KB");
        assertEquals(result.data().length, result.byteSize());

        // Kiểm tra magic JPEG của byte sau chuẩn hóa
        assertEquals((byte) 0xFF, result.data()[0]);
        assertEquals((byte) 0xD8, result.data()[1]);
        assertEquals((byte) 0xFF, result.data()[2]);
    }

    @Test
    @DisplayName("Ảnh BMP và GIF được chuyển đổi thành PNG hợp lệ")
    void bmpVaGif_ChuyenThanhPng() throws Exception {
        byte[] rawBmp = createTestImage("bmp", 200, 150, Color.GREEN);
        byte[] rawGif = createTestImage("gif", 180, 120, Color.YELLOW);

        NormalizedImage bmpResult = normalizer.normalize(rawBmp);
        NormalizedImage gifResult = normalizer.normalize(rawGif);

        assertEquals("image/png", bmpResult.mime());
        assertEquals("bmp", bmpResult.sourceFormat());
        assertEquals(200, bmpResult.width());
        assertEquals(150, bmpResult.height());

        assertEquals("image/png", gifResult.mime());
        assertEquals("gif", gifResult.sourceFormat());
        assertEquals(180, gifResult.width());
        assertEquals(120, gifResult.height());
    }

    @Test
    @DisplayName("Ảnh lớn hơn 1600px được thu nhỏ giữ nguyên tỉ lệ aspect ratio")
    void anhLonHon1600px_ThuNhoCanhDai() throws Exception {
        // Tạo ảnh PNG 2400x1200 (tỉ lệ 2:1, cạnh dài 2400 > 1600)
        byte[] largePng = createTestImage("png", 2400, 1200, Color.RED);

        NormalizedImage result = normalizer.normalize(largePng);

        assertEquals("image/png", result.mime());
        assertEquals(1600, result.width());
        assertEquals(800, result.height());
        assertTrue(result.byteSize() <= 512_000);
    }

    @Test
    @DisplayName("SHA-256 trả về đúng theo byte gốc của ảnh đầu vào")
    void sha256_GiuDungGiaTriGoc() throws Exception {
        byte[] rawPng = createTestImage("png", 100, 100, Color.MAGENTA);
        String expectedSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawPng));

        NormalizedImage result = normalizer.normalize(rawPng);

        assertEquals(expectedSha256, result.sha256());
    }

    @Test
    @DisplayName("GIF có Logical Screen nhỏ (10x10) nhưng khung ảnh lớn (5000x10) bị từ chối bởi ImageReader trước khi giải mã")
    void gifKhungLonHonLogicalScreen_TuChoiTruocKhiGiaiMa() {
        byte[] gifBigFrame = new byte[] {
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61,         // "GIF89a"
                0x0A, 0x00, 0x0A, 0x00,                     // Logical Screen: 10x10 (vượt qua kiểm tra header thô)
                (byte) 0x80, 0x00, 0x00,                    // Global Color Table Flag=1, 2 màu
                0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, // GCT (6 bytes)
                0x2C,                                       // Image Descriptor
                0x00, 0x00, 0x00, 0x00,                     // left=0, top=0
                (byte) 0x88, 0x13, 0x0A, 0x00,              // frame width=5000 (>4000), height=10
                0x00,                                       // packed=0
                0x02, 0x02, 0x4C, 0x01, 0x00,               // LZW tối thiểu + block terminator
                0x3B                                        // GIF trailer
        };

        ImageValidationException ex = assertThrows(ImageValidationException.class, () ->
                normalizer.normalize(gifBigFrame)
        );
        assertEquals("IMAGE_DIMENSION_TOO_LARGE", ex.getErrorCode());
    }

    @Test
    @DisplayName("EMF chuẩn MS-EMF (chữ ký 0x464D4520) đọc được khung hợp lệ và chặn khung vượt ngưỡng")
    void emfChuanMsEmf_KiemKhungHopLeVaTuChoiKhungLon() {
        byte[] validEmf = createEmfWithBounds(0, 0, 500, 400);
        ImageHeaderInfo info = inspector.inspect(validEmf);
        assertEquals("emf", info.format());
        assertEquals(501, info.width());
        assertEquals(401, info.height());

        byte[] hugeEmf = createEmfWithBounds(0, 0, 20000, 20000);
        ImageValidationException ex = assertThrows(ImageValidationException.class, () ->
                inspector.inspect(hugeEmf)
        );
        assertEquals("IMAGE_DIMENSION_TOO_LARGE", ex.getErrorCode());
    }

    private byte[] createEmfWithBounds(int left, int top, int right, int bottom) {
        ByteBuffer buf = ByteBuffer.allocate(88).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1);                          // EMR_HEADER
        buf.putInt(88);                         // nSize
        buf.putInt(left);
        buf.putInt(top);
        buf.putInt(right);
        buf.putInt(bottom);                     // rclBounds
        buf.putInt(0); buf.putInt(0); buf.putInt(1000); buf.putInt(1000); // rclFrame
        buf.putInt(0x464D4520);                 // " EMF" (MS-EMF chuẩn: 20 45 4D 46)
        return buf.array();
    }
}
