package com.datn.mcq.mathtype.ole;

import org.apache.poi.hpsf.ClassID;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** oleObjectN.bin phải có đúng cấu trúc đối tượng MathType thật (spike BAO_CAO mục 1), nếu không Word không nhận. */
class MathTypeOleTest {

    private static final byte[] MTEF = {5, 1, 0, 6, 9, 'D', 'S', 'M', 'T', '6', 0, 1, 10, 1, 0, 0, 0};

    @Test
    void coClsidMathTypeVaDuBonStream() throws Exception {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(MathTypeOle.build(MTEF)))) {
            DirectoryEntry root = fs.getRoot();
            assertEquals(new ClassID("{0002CE03-0000-0000-C000-000000000046}"), root.getStorageClsid());
            Set<String> names = new TreeSet<>(root.getEntryNames());
            assertEquals(Set.of("\u0001CompObj", "\u0001Ole", "\u0003ObjInfo", "Equation Native"), names);

            byte[] compObj = read(fs, "\u0001CompObj");
            String ascii = new String(compObj, StandardCharsets.ISO_8859_1);
            assertTrue(ascii.contains("Equation.DSMT4\0"), "ProgID Equation.DSMT4 trong \\1CompObj");
            assertTrue(ascii.contains("MathType EF\0"), "định dạng clipboard MathType EF");
            assertEquals(20, read(fs, "\u0001Ole").length);
            assertEquals(6, read(fs, "\u0003ObjInfo").length);
        }
    }

    @Test
    void equationNativeCoHeader28ByteRoiDenMtef() throws Exception {
        byte[] data;
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(MathTypeOle.build(MTEF)))) {
            data = read(fs, "Equation Native");
        }
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(28, b.getShort(0), "cbHdr");
        assertEquals(0x00020000, b.getInt(2), "version");
        assertEquals(MTEF.length, b.getInt(8), "cbObject = độ dài MTEF");
        assertArrayEquals(MTEF, Arrays.copyOfRange(data, 28, data.length));
    }

    @Test
    void progIdLaEquationDsmt4() {
        assertEquals("Equation.DSMT4", MathTypeOle.PROG_ID);
    }

    private static byte[] read(POIFSFileSystem fs, String name) throws Exception {
        try (DocumentInputStream in = fs.createDocumentInputStream(name)) {
            return in.readAllBytes();
        }
    }
}
