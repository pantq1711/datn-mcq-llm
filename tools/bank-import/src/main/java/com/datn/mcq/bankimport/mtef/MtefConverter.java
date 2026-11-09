package com.datn.mcq.bankimport.mtef;

import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Điểm vào: đối tượng OLE hoặc luồng MTEF → {@link FormulaResult}. Không bao giờ ném ngoại lệ vì dữ liệu xấu. */
public final class MtefConverter {

    private static final String EQUATION_STREAM = "Equation Native";

    /** Đối tượng OLE (compound file) của Equation.DSMT*: đọc luồng "Equation Native" rồi dịch MTEF bên trong. */
    public static FormulaResult fromOle(byte[] ole) {
        byte[] stream;
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(ole))) {
            if (!fs.getRoot().hasEntry(EQUATION_STREAM)) {
                return FormulaResult.unsupported("khong_co_luong_" + EQUATION_STREAM.replace(' ', '_') + " (Equation Native)");
            }
            try (DocumentInputStream in = fs.createDocumentInputStream(EQUATION_STREAM)) {
                stream = in.readAllBytes();
            }
        } catch (IOException | RuntimeException e) {
            return FormulaResult.unsupported("khong_doc_duoc_compound_file");
        }
        if (stream.length < 28) {
            return FormulaResult.unsupported("Equation Native qua ngan");
        }
        ByteBuffer b = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN);
        int cbHeader = b.getShort(0) & 0xFFFF;
        long cbObject = b.getInt(8) & 0xFFFFFFFFL;
        if (cbHeader < 28 || cbHeader > stream.length) {
            return FormulaResult.unsupported("header Equation Native khong hop le");
        }
        int end = (int) Math.min(stream.length, cbHeader + cbObject);
        return fromMtef(Arrays.copyOfRange(stream, cbHeader, end));
    }

    /** Luồng MTEF v5 trần. */
    public static FormulaResult fromMtef(byte[] mtef) {
        try {
            return new MtefToLatex().translate(MtefReader.parse(mtef));
        } catch (MtefReader.MtefException e) {
            return FormulaResult.unsupported(MtefReader.EMPTY.equals(e.getMessage()) ? MtefReader.EMPTY
                    : "mtef_loi_cau_truc: " + e.getMessage());
        } catch (RuntimeException e) {
            return FormulaResult.unsupported("mtef_loi: " + e.getClass().getSimpleName());
        }
    }

    private MtefConverter() {}
}
