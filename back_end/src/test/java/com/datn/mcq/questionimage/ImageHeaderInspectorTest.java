package com.datn.mcq.questionimage;

import com.datn.mcq.questionimage.service.ImageHeaderInfo;
import com.datn.mcq.questionimage.service.ImageHeaderInspector;
import com.datn.mcq.questionimage.service.ImageImportProperties;
import com.datn.mcq.questionimage.service.ImageValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kiểm khung EMF: ngoài rclBounds (pixel thiết bị) phải kiểm cả rclFrame (0,01 mm), vì LibreOffice dựng ảnh theo rclFrame.
 */
class ImageHeaderInspectorTest {

    private final ImageHeaderInspector inspector = new ImageHeaderInspector(new ImageImportProperties());

    /** EMR_HEADER 88 byte + EMR_EOF 20 byte, với rclBounds và rclFrame cho trước. */
    private static byte[] emf(int boundsRight, int boundsBottom, int frameRight, int frameBottom) {
        ByteBuffer buf = ByteBuffer.allocate(108).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1).putInt(88);
        buf.putInt(0).putInt(0).putInt(boundsRight).putInt(boundsBottom); // rclBounds
        buf.putInt(0).putInt(0).putInt(frameRight).putInt(frameBottom);   // rclFrame (0,01 mm)
        buf.putInt(0x464D4520).putInt(0x00010000).putInt(108).putInt(2);
        buf.putShort((short) 0).putShort((short) 0);
        buf.putInt(0).putInt(0).putInt(0);
        buf.putInt(100).putInt(100); // szlDevice
        buf.putInt(10).putInt(10);   // szlMillimeters
        buf.putInt(14).putInt(20).putInt(0).putInt(0).putInt(108);
        return buf.array();
    }

    @Test
    @DisplayName("EMF khung bình thường (rclFrame 10 cm × 5 cm) vẫn qua kiểm tra, không chặn nhầm")
    void inspect_EmfRclFrameBinhThuong_QuaKiemTra() {
        // 10.000 × 5.000 (0,01 mm) = 10 cm × 5 cm ≈ 378 × 189 px ở 96 DPI (làm tròn lên); rclBounds 301 × 151 px
        ImageHeaderInfo info = inspector.inspect(emf(300, 150, 10_000, 5_000));

        assertEquals("emf", info.format());
        assertEquals(378, info.width());
        assertEquals(189, info.height());
    }

    @Test
    @DisplayName("EMF rclBounds nhỏ nhưng rclFrame vượt tổng pixel cho phép thì bị chặn")
    void inspect_EmfRclFrameVuotTongPixel_BiChan() {
        // 100.000 × 100.000 (0,01 mm) = 1 m × 1 m ≈ 3780 × 3780 px: từng cạnh < 4000 nhưng 14,3 MP > 12 MP
        ImageValidationException ex = assertThrows(ImageValidationException.class,
                () -> inspector.inspect(emf(100, 100, 100_000, 100_000)));

        assertEquals("IMAGE_PIXEL_FLOOD", ex.getErrorCode());
    }
}
