package com.datn.mcq.questionimage;

import com.datn.mcq.config.PandocLimiter;
import com.datn.mcq.questionimage.service.EmfConvertResult;
import com.datn.mcq.questionimage.service.ImageImportProperties;
import com.datn.mcq.questionimage.service.LibreOfficeEmfConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.util.unit.DataSize;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        buf.putInt(0x464D4520); // dSignature = " EMF" (MS-EMF chuẩn: 20 45 4D 46)
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

    @Test
    @DisplayName("autocropWhitespace từ chối PNG khai báo kích thước 20000x20000 trước khi giải mã")
    void autocropWhitespace_PngHeaderKhungLon_TuChoiTruocKhiGiaiMa() {
        byte[] fakeBombPng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D,
                0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x4E, 0x20, // width = 20000
                0x00, 0x00, 0x4E, 0x20, // height = 20000
                0x08, 0x02, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };

        com.datn.mcq.questionimage.service.ImageValidationException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.datn.mcq.questionimage.service.ImageValidationException.class,
                        () -> converter.autocropWhitespace(fakeBombPng));
        assertEquals("IMAGE_DIMENSION_TOO_LARGE", ex.getErrorCode());
    }

    @Test
    @DisplayName("convertBatch từ chối EMF khung quá lớn trước khi gọi LibreOffice")
    void convertBatch_EmfKhungLon_TuChoiTruocKhiGoiLibreOffice() {
        props.setLibreofficePath("non-existent-soffice-binary-xyz");
        LibreOfficeEmfConverter guardedConverter = new LibreOfficeEmfConverter(props, limiter);

        byte[] hugeEmf = createMinimalValidEmf();
        ByteBuffer.wrap(hugeEmf, 8, 16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0).putInt(0).putInt(20000).putInt(20000);

        Map<String, EmfConvertResult> results = guardedConverter.convertBatch(Map.of("huge1", hugeEmf));

        assertEquals(1, results.size());
        EmfConvertResult r = results.get("huge1");
        assertNotNull(r);
        assertFalse(r.success());
        assertEquals("IMAGE_DIMENSION_TOO_LARGE", r.errorCode());
    }

    @Test
    @DisplayName("convertBatch từ chối EMF có rclBounds nhỏ nhưng rclFrame cực lớn, trước khi gọi LibreOffice")
    void convertBatch_EmfRclFrameLon_TuChoiTruocKhiGoiLibreOffice() {
        props.setLibreofficePath("non-existent-soffice-binary-xyz");
        LibreOfficeEmfConverter guardedConverter = new LibreOfficeEmfConverter(props, limiter);

        byte[] emf = createMinimalValidEmf();
        // rclFrame 0..2.000.000 (đơn vị 0,01 mm = 20 m) ≈ 75.591 px ở 96 DPI; rclBounds vẫn 100×100
        ByteBuffer.wrap(emf, 24, 16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0).putInt(0).putInt(2_000_000).putInt(2_000_000);

        Map<String, EmfConvertResult> results = guardedConverter.convertBatch(Map.of("frame1", emf));

        EmfConvertResult r = results.get("frame1");
        assertNotNull(r);
        assertFalse(r.success());
        assertEquals("IMAGE_DIMENSION_TOO_LARGE", r.errorCode());
    }

    @Test
    @DisplayName("Môi trường của soffice chỉ còn PATH, HOME (thư mục tạm), LANG; không lộ biến bí mật")
    void sofficeEnvironment_ChiGiuPathHomeLang() {
        Map<String, String> parent = new HashMap<>();
        parent.put("PATH", "/usr/bin:/bin");
        parent.put("JWT_SECRET", "s1");
        parent.put("LLM_API_KEY", "s2");
        parent.put("DB_PASSWORD", "s3");
        parent.put("JAVA_TOOL_OPTIONS", "-Xmx1g");
        parent.put("HOME", "/home/app");
        parent.put("SystemRoot", "C:\\Windows");
        Path home = Path.of("tmp", "home");

        Map<String, String> env = LibreOfficeEmfConverter.sofficeEnvironment(parent, home, false);

        assertEquals(Set.of("PATH", "HOME", "LANG"), env.keySet());
        assertEquals("/usr/bin:/bin", env.get("PATH"));
        assertEquals(home.toAbsolutePath().toString(), env.get("HOME"));
        assertEquals("C.UTF-8", env.get("LANG"));
    }

    @Test
    @DisplayName("Trên Windows giữ thêm SystemRoot, TEMP, TMP (không bí mật) để soffice.exe chạy được")
    void sofficeEnvironment_Windows_GiuThemBienHeThong() {
        Map<String, String> parent = new HashMap<>();
        parent.put("Path", "C:\\Windows\\system32");
        parent.put("SystemRoot", "C:\\Windows");
        parent.put("TEMP", "C:\\Temp");
        parent.put("TMP", "C:\\Temp");
        parent.put("JWT_SECRET", "s1");

        Map<String, String> env = LibreOfficeEmfConverter.sofficeEnvironment(parent, Path.of("h"), true);

        assertEquals(Set.of("PATH", "HOME", "LANG", "SystemRoot", "TEMP", "TMP"), env.keySet());
        assertEquals("C:\\Windows\\system32", env.get("PATH"));
    }

    @Test
    @DisplayName("ProcessBuilder của soffice dùng đúng môi trường đã lọc")
    void newSofficeProcess_MoiTruongDaLoc() {
        Path home = Path.of("tmp", "home");
        ProcessBuilder pb = converter.newSofficeProcess(List.of("--version"), home);

        Map<String, String> expected = LibreOfficeEmfConverter.sofficeEnvironment(
                System.getenv(), home, System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
        assertEquals(new HashMap<>(expected), new HashMap<>(pb.environment()));
    }

    @Test
    @DisplayName("Có cấu hình trần bộ nhớ thì lệnh soffice được bọc bằng prlimit --as")
    void buildCommand_CoTranBoNho_BocBangPrlimit() {
        props.setSofficeMaxAddressSpace(DataSize.ofGigabytes(2));

        List<String> cmd = converter.buildCommand(List.of("--version"));

        assertEquals(List.of("prlimit", "--as=2147483648", "--", "soffice", "--version"), cmd);
    }

    @Test
    @DisplayName("Không cấu hình trần bộ nhớ thì giữ lệnh soffice như cũ")
    void buildCommand_KhongCauHinhTran_KhongBocPrlimit() {
        List<String> cmd = converter.buildCommand(List.of("--version"));

        assertEquals(List.of("soffice", "--version"), cmd);
    }

    @Test
    @DisplayName("Cấu hình trần mà không có prlimit: không chạy soffice không trần, trả lỗi (fail closed)")
    void convertBatch_ThieuPrlimit_FailClosed() {
        props.setSofficeMaxAddressSpace(DataSize.ofGigabytes(2));
        props.setPrlimitPath("non-existent-prlimit-binary-xyz");

        Map<String, EmfConvertResult> results = converter.convertBatch(Map.of("fig1", createMinimalValidEmf()));

        EmfConvertResult r = results.get("fig1");
        assertNotNull(r);
        assertFalse(r.success());
        assertFalse(converter.isAvailable());
    }
}
