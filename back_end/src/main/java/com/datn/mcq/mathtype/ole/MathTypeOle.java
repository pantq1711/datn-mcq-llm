package com.datn.mcq.mathtype.ole;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.apache.poi.hpsf.ClassID;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * Đóng gói MTEF thành file OLE compound (word/embeddings/oleObjectN.bin) giống đối tượng MathType thật:
 * CLSID gốc {0002CE03-0000-0000-C000-000000000046} (Equation.DSMT4) và 4 stream \1CompObj, \1Ole, \3ObjInfo,
 * "Equation Native" (EQNOLEFILEHDR 28 byte + MTEF).
 */
public final class MathTypeOle {

    public static final String CLSID = "{0002CE03-0000-0000-C000-000000000046}";
    public static final String PROG_ID = "Equation.DSMT4";

    private MathTypeOle() {
    }

    public static byte[] build(byte[] mtef) {
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            DirectoryEntry root = fs.getRoot();
            root.setStorageClsid(new ClassID(CLSID));
            root.createDocument("\u0001CompObj", new ByteArrayInputStream(compObj()));
            // \1Ole: version 0x02000001, flags = 8 (như MathType 7), các trường còn lại 0
            root.createDocument("\u0001Ole", new ByteArrayInputStream(
                    new byte[]{1, 0, 0, 2, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
            root.createDocument("\u0003ObjInfo", new ByteArrayInputStream(new byte[]{0, 0, 3, 0, 4, 0}));
            root.createDocument("Equation Native", new ByteArrayInputStream(equationNative(mtef)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** EQNOLEFILEHDR: cbHdr=28, version=0x00020000, cf=0, cbObject=len(MTEF), 16 byte dự trữ. */
    static byte[] equationNative(byte[] mtef) {
        ByteBuffer b = ByteBuffer.allocate(28 + mtef.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 28).putInt(0x00020000).putShort((short) 0).putInt(mtef.length).put(new byte[16]);
        b.put(mtef);
        return b.array();
    }

    /** \1CompObj theo [MS-OLEDS] 2.3.8, cùng nội dung MathType ghi. */
    static byte[] compObj() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new byte[]{1, 0, (byte) 0xFE, (byte) 0xFF, 3, 0x0A, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF});
        byte[] clsid = new byte[16];
        new ClassID(CLSID).write(clsid, 0);
        o.writeBytes(clsid);
        lpstr(o, "MathType 6.0 Equation");
        lpstr(o, "MathType EF");
        lpstr(o, PROG_ID);
        o.writeBytes(new byte[]{(byte) 0xF4, 0x39, (byte) 0xB2, 0x71}); // UnicodeMarker
        o.writeBytes(new byte[12]); // 3 chuỗi Unicode rỗng
        return o.toByteArray();
    }

    private static void lpstr(ByteArrayOutputStream o, String s) {
        byte[] bytes = (s + "\0").getBytes(StandardCharsets.US_ASCII);
        o.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.length).array());
        o.writeBytes(bytes);
    }
}
