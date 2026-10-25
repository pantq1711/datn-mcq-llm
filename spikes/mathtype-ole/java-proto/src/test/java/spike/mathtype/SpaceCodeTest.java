package spike.mathtype;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ký tự khoảng cách trong MTEF do bộ sinh ghi phải nằm trong danh sách đã đối chiếu với các bộ dịch TeX của MathType
 * ({@link OmmlToMtef#VERIFIED_SPACE_CODES}). Lỗi gốc: "\," được ghi thành U+EF07 (như chính MathType 7.12 nhập TeX),
 * nhưng bộ dịch Texvc của MathType không có bản dịch cho U+EF07 ("MathType Translation Error: No translation available
 * for Inner space (0xEF07)"), gặp ở 3 đối tượng của KIEM_TRA_mo_rong.docx (a\,b và hai tích phân x\,dx).
 */
class SpaceCodeTest {

    private static final int THIN = 0xEF02;
    private static final int THICK = 0xEF04;
    private static final int EM = 0xEF05;
    private static final int INNER = 0xEF07;

    /** typeface Space (24) được ghi cộng 128 trong CHAR record (MTEF v5). */
    private static final int SPACE_TYPEFACE_BYTE = MtefWriter.FN_SPACE + 128;

    @Test
    void danhSachDaDoiChieuCoDinhVaKhongCoInnerSpace() {
        // Đổi danh sách này phải đối chiếu lại với Translators của MathType (Texvc và LaTeX/TeX) và ghi vào BAO_CAO.md.
        assertEquals(Set.of(THIN, THICK, EM), OmmlToMtef.VERIFIED_SPACE_CODES);
        assertFalse(OmmlToMtef.VERIFIED_SPACE_CODES.contains(INNER));
    }

    @Test
    void moiKyTuDauVaoChiSinhMaKhoangCachDaDoiChieu() {
        int mapped = 0;
        for (int cp = 0; cp <= 0xFFFF; cp++) {
            int code = OmmlToMtef.spaceCode(cp);
            if (code >= 0) {
                mapped++;
                assertTrue(OmmlToMtef.VERIFIED_SPACE_CODES.contains(code),
                        String.format("U+%04X sinh mã khoảng cách chưa đối chiếu U+%04X", cp, code));
            }
        }
        assertTrue(mapped > 0);
    }

    @Test
    void lenhKhoangCachCuaPandocRaDungMaTrongMtef() throws Exception {
        // Mã pandoc ghi vào OMML (đọc từ out/KIEM_TRA_mo_rong.pandoc.docx): \, = U+2009, \; = U+2004, "\ " = U+2005,
        // \quad = U+2001.
        assertEquals(List.of(THIN), spaceCodesInMtef("a b"), "\\,");
        assertEquals(List.of(THICK), spaceCodesInMtef("a b"), "\\;");
        assertEquals(List.of(THICK), spaceCodesInMtef("a b"), "\\ (dấu cách)");
        assertEquals(List.of(EM), spaceCodesInMtef("a b"), "\\quad");
        assertEquals(List.of(THIN), spaceCodesInMtef("x dx"), "x\\,dx (tích phân ở KIEM_TRA_mo_rong)");
    }

    @Test
    void moiKyTuKhoangTrangUnicodeChiRaMaDaDoiChieuHoacGiuEquation() throws Exception {
        int converted = 0;
        for (int cp = 0; cp <= 0xFFFF; cp++) {
            if (Character.getType(cp) != Character.SPACE_SEPARATOR) {
                continue;
            }
            List<Integer> codes;
            try {
                codes = spaceCodesInMtef("a" + Character.toString(cp) + "a");
            } catch (OmmlToMtef.Unsupported e) {
                continue; // công thức giữ dạng Equation của Word, không sinh MTEF
            }
            converted++;
            for (int code : codes) {
                assertTrue(OmmlToMtef.VERIFIED_SPACE_CODES.contains(code),
                        String.format("U+%04X sinh mã khoảng cách chưa đối chiếu U+%04X", cp, code));
            }
        }
        assertTrue(converted > 0);
    }

    /** Đổi một m:r chứa {@code text} sang MTEF rồi lấy mã của mọi CHAR record ở typeface Space, theo thứ tự. */
    private static List<Integer> spaceCodesInMtef(String text) throws Exception {
        String xml = "<m:oMath xmlns:m=\"" + OmmlToMtef.M + "\"><m:r><m:t xml:space=\"preserve\">"
                + text.replace("&", "&amp;").replace("<", "&lt;") + "</m:t></m:r></m:oMath>";
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Element oMath = f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
        byte[] mtef = OmmlToMtef.convert(oMath, true);
        List<Integer> codes = new ArrayList<>();
        // CHAR record: tag 2, options (0..7), typeface + 128, MTCode 16 bit little-endian.
        for (int i = 0; i + 4 < mtef.length; i++) {
            if (mtef[i] == 2 && (mtef[i + 1] & 0xF8) == 0 && (mtef[i + 2] & 0xFF) == SPACE_TYPEFACE_BYTE) {
                int code = (mtef[i + 3] & 0xFF) | ((mtef[i + 4] & 0xFF) << 8);
                if ((code & 0xFF00) == 0xEF00) {
                    codes.add(code);
                    i += 4;
                }
            }
        }
        return codes;
    }
}
