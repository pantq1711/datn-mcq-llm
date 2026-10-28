package com.datn.mcq.material.extract;

import com.datn.mcq.common.exception.ApiErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.datn.mcq.material.extract.MaterialTestFiles.drawingImage;
import static com.datn.mcq.material.extract.MaterialTestFiles.minimalDocx;
import static com.datn.mcq.material.extract.MaterialTestFiles.oleEquation;
import static com.datn.mcq.material.extract.MaterialTestFiles.omml;
import static com.datn.mcq.material.extract.MaterialTestFiles.paragraph;
import static com.datn.mcq.material.extract.MaterialTestFiles.vmlImage;
import static com.datn.mcq.material.extract.MaterialTestFiles.zip;
import static com.datn.mcq.material.extract.MaterialTestFiles.zipWithZeros;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2-3 bước 2: chống file docx độc (zip bomb, quá nhiều mục, tên mục thoát thư mục, XML có DTD/XXE) và đếm công thức
 * MathType, Equation gốc, ảnh trong word/document.xml.
 */
class DocxSafetyTest {

    @TempDir
    Path tmp;

    private final MaterialProperties props = new MaterialProperties();

    private Path write(byte[] bytes) throws IOException {
        return Files.write(tmp.resolve("in-" + System.nanoTime() + ".docx"), bytes);
    }

    private static void assertInvalid(ApiErrorException ex) {
        assertEquals("INVALID_FILE", ex.getCode(), ex.getMessage());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, ex.getStatus(), ex.getMessage());
    }

    // ---------- ZipSafetyScanner ----------

    @Test
    void docxBinhThuongQuaVaTraDanhSachMuc() throws Exception {
        List<String> names = new ZipSafetyScanner(props).scan(write(minimalDocx(paragraph("Xin chao"))));
        assertTrue(names.contains("word/document.xml"));
        assertTrue(names.contains("[Content_Types].xml"));
    }

    @Test
    void mucGiaiNenQuaLonBiTuChoiKhongDocHet() throws Exception {
        props.setZipMaxEntryBytes(5L << 20);
        Path bomb = write(zipWithZeros("word/document.xml", 40L << 20));
        assertTrue(Files.size(bomb) < (1L << 20), "bomb nén rất nhỏ so với 40 MB khi giải nén");
        assertInvalid(assertThrows(ApiErrorException.class, () -> new ZipSafetyScanner(props).scan(bomb)));
    }

    @Test
    void tiLeNenQuaCaoBiTuChoi() throws Exception {
        props.setZipMaxEntryBytes(50L << 20);
        props.setZipMaxRatio(50);
        Path bomb = write(zipWithZeros("word/media/x.bin", 4L << 20));
        assertInvalid(assertThrows(ApiErrorException.class, () -> new ZipSafetyScanner(props).scan(bomb)));
    }

    @Test
    void tongDungLuongGiaiNenQuaLonBiTuChoi() throws Exception {
        props.setZipMaxTotalBytes(5L << 20);
        props.setZipMaxRatio(100_000);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.bin", new byte[3 << 20]);
        files.put("b.bin", new byte[3 << 20]);
        Path p = write(zip(files));
        assertInvalid(assertThrows(ApiErrorException.class, () -> new ZipSafetyScanner(props).scan(p)));
    }

    @Test
    void quaNhieuMucBiTuChoi() throws Exception {
        props.setZipMaxEntries(10);
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < 11; i++) {
            files.put("m" + i + ".xml", new byte[]{'x'});
        }
        Path p = write(zip(files));
        assertInvalid(assertThrows(ApiErrorException.class, () -> new ZipSafetyScanner(props).scan(p)));
    }

    @Test
    void tenMucThoatThuMucBiTuChoi() throws Exception {
        for (String name : List.of("../evil.xml", "word/../../evil.xml", "/etc/passwd", "C:\\evil.xml")) {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put(name, new byte[]{'x'});
            Path p = write(zip(files));
            assertInvalid(assertThrows(ApiErrorException.class, () -> new ZipSafetyScanner(props).scan(p)));
        }
    }

    @Test
    void zipHongBiTuChoi() throws Exception {
        Path p = write(new byte[]{'P', 'K', 3, 4, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        assertInvalid(assertThrows(ApiErrorException.class, () -> new ZipSafetyScanner(props).scan(p)));
    }

    // ---------- DocxFormulaCounter ----------

    @Test
    void demMathTypeEquationGocVaAnhNhungBoQuaDoiTuongKhac() throws Exception {
        String body = paragraph("Cau 1")
                + oleEquation("Equation.DSMT4") + oleEquation("Equation.DSMT4") + oleEquation("Equation.3")
                + oleEquation("Excel.Sheet.12")
                + omml("x+1") + "<w:p><m:oMathPara><m:oMath><m:r><m:t>y</m:t></m:r></m:oMath></m:oMathPara></w:p>"
                + drawingImage() + vmlImage();
        DocxFormulaCounter.FormulaStats s = new DocxFormulaCounter().count(write(minimalDocx(body)));
        assertEquals(3, s.mathType(), "Equation.DSMT4 x2 và Equation.3; Excel.Sheet.12 không tính");
        assertEquals(2, s.omml(), "một m:oMath riêng và một m:oMath trong m:oMathPara");
        assertEquals(2, s.images(), "ảnh DrawingML và VML ngoài đối tượng OLE; ảnh xem trước của OLE không tính");
    }

    @Test
    void khongCoCongThucThiTatCaBangKhong() throws Exception {
        DocxFormulaCounter.FormulaStats s = new DocxFormulaCounter().count(write(minimalDocx(paragraph("Chi co chu"))));
        assertEquals(0, s.mathType());
        assertEquals(0, s.omml());
        assertEquals(0, s.images());
    }

    @Test
    void xmlCoDtaVaThucTheNgoaiBiTuChoiKhongDocFileMay() throws Exception {
        Path secret = Files.writeString(tmp.resolve("secret.txt"), "BI-MAT-CUA-MAY-CHU");
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + "<w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>";
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("[Content_Types].xml", MaterialTestFiles.CONTENT_TYPES.getBytes());
        files.put("word/document.xml", xml.getBytes());
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> new DocxFormulaCounter().count(write(zip(files))));
        assertInvalid(ex);
        assertFalse(String.valueOf(ex.getMessage()).contains("BI-MAT"), "thông báo không lộ nội dung file máy chủ");
    }

    @Test
    void thieuWordDocumentXmlBi415KhongPhaiDocx() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("[Content_Types].xml", MaterialTestFiles.CONTENT_TYPES.getBytes());
        files.put("xl/workbook.xml", "<workbook/>".getBytes());
        ApiErrorException ex = assertThrows(ApiErrorException.class,
                () -> DocxTextExtractor.requireDocxEntries(List.of("[Content_Types].xml", "xl/workbook.xml")));
        assertEquals("UNSUPPORTED_FILE_TYPE", ex.getCode());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());
        DocxTextExtractor.requireDocxEntries(List.of("[Content_Types].xml", "word/document.xml", "word/styles.xml"));
    }
}
