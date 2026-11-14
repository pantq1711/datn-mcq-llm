package com.datn.mcq.questionimage;

import com.datn.mcq.config.PandocLimiter;
import com.datn.mcq.questionimage.service.EmfConvertResult;
import com.datn.mcq.questionimage.service.ImageImportProperties;
import com.datn.mcq.questionimage.service.LibreOfficeEmfConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibreOfficeEmfConverterTest {

    private ImageImportProperties props;
    private PandocLimiter limiter;
    private LibreOfficeEmfConverter converter;

    @BeforeEach
    void setUp() {
        props = new ImageImportProperties();
        limiter = new PandocLimiter(2, Duration.ofSeconds(5), 256);
        converter = new LibreOfficeEmfConverter(props, limiter);
    }

    /**
     * Tạo byte EMF hợp lệ tối thiểu (EMR_HEADER 88 bytes + EMR_EOF 20 bytes).
     */
    private byte[] createMinimalValidEmf() {
        ByteBuffer buf = ByteBuffer.allocate(108).order(ByteOrder.LITTLE_ENDIAN);
        // EMR_HEADER (type=1, size=88)
        buf.putInt(1);
        buf.putInt(88);
        buf.putInt(0); buf.putInt(0); buf.putInt(100); buf.putInt(100); // rclBounds (0,0,100,100)
        buf.putInt(0); buf.putInt(0); buf.putInt(1000); buf.putInt(1000); // rclFrame
        buf.putInt(0x28464D45); // dSignature = " EMF" (0x28464D45)
        buf.putInt(0x00010000); // nVersion
        buf.putInt(108);        // nBytes
        buf.putInt(2);          // nRecords (Header + EOF)
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(100); buf.putInt(100); // szlDevice
        buf.putInt(10); buf.putInt(10);   // szlMillimeters
        // EMR_EOF (type=14, size=20)
        buf.putInt(14);
        buf.putInt(20);
        buf.putInt(0); // nPalEntries
        buf.putInt(0); // offPalEntries
        buf.putInt(108); // nSizeLast
        return buf.array();
    }

    @Test
    @DisplayName("convertBatch trả map rỗng khi đầu vào rỗng")
    void convertBatch_DauVaoRong_TraMapRong() {
        Map<String, EmfConvertResult> results = converter.convertBatch(Map.of());
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("convertBatch trả CONVERTER_BUSY khi PandocLimiter không thể lấy permit")
    void convertBatch_LimiterBusy_TraLoiBusy() throws Exception {
        PandocLimiter busyLimiter = mock(PandocLimiter.class);
        when(busyLimiter.tryAcquire(any(Duration.class))).thenReturn(false);

        LibreOfficeEmfConverter busyConverter = new LibreOfficeEmfConverter(props, busyLimiter);

        Map<String, EmfConvertResult> results = busyConverter.convertBatch(Map.of(
                "fig1", createMinimalValidEmf()
        ));

        assertEquals(1, results.size());
        EmfConvertResult r1 = results.get("fig1");
        assertNotNull(r1);
        assertFalse(r1.success());
        assertEquals("CONVERTER_BUSY", r1.errorCode());
    }

    @Test
    @DisplayName("convertBatch xử lý file EMF lỗi hoặc hỏng trả trạng thái thất bại cho từng file")
    void convertBatch_EmfHong_TraTrangThaiThatBai() {
        // Đường dẫn soffice không tồn tại hoặc giả lập lỗi
        props.setLibreofficePath("non-existent-soffice-binary-xyz");
        LibreOfficeEmfConverter failingConverter = new LibreOfficeEmfConverter(props, limiter);

        Map<String, EmfConvertResult> results = failingConverter.convertBatch(Map.of(
                "corrupt1", "Not an EMF".getBytes()
        ));

        assertEquals(1, results.size());
        EmfConvertResult r = results.get("corrupt1");
        assertNotNull(r);
        assertFalse(r.success());
        assertNotNull(r.errorCode());
    }

    @Test
    @DisplayName("autocropWhitespace cắt bỏ viền trắng xung quanh ảnh")
    void autocropWhitespace_CatVienTrang_ThanhCong() throws Exception {
        // Tạo ảnh 200x200 toàn màu trắng, có hình vuông đen 50x50 ở giữa (x=75..125, y=75..125)
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = bi.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(75, 75, 50, 50);
        g.dispose();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bi, "png", baos);
        byte[] originalPng = baos.toByteArray();

        byte[] croppedPng = converter.autocropWhitespace(originalPng);
        java.awt.image.BufferedImage croppedImg = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(croppedPng));

        assertNotNull(croppedImg);
        // Ban đầu 200x200, sau khi crop vùng 50x50 + padding 2px mỗi bên -> ~54x54
        assertTrue(croppedImg.getWidth() < 100, "Width sau crop (" + croppedImg.getWidth() + ") phải nhỏ hơn 100");
        assertTrue(croppedImg.getHeight() < 100, "Height sau crop (" + croppedImg.getHeight() + ") phải nhỏ hơn 100");
        assertTrue(croppedImg.getWidth() >= 50, "Width sau crop (" + croppedImg.getWidth() + ") phải >= 50");
    }
}
