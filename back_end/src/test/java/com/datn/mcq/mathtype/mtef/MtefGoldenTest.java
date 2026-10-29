package com.datn.mcq.mathtype.mtef;

import com.datn.mcq.mathtype.MtefTestSupport;
import com.datn.mcq.mathtype.MtefTestSupport.Para;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "Chuẩn vàng": MTEF do {@link OmmlToMtef} sinh từ OMML của pandoc phải cùng cấu trúc với MTEF do chính MathType 7.12
 * sinh (Toggle TeX) cho cùng biểu thức. Dữ liệu là các bộ mẫu của spike (spikes/mathtype-ole/samples, nhóm tự viết):
 * {@code *-pandoc.docx} (đầu vào), {@code *-mathtype.docx} (MathType sinh), {@code *.tex.txt} (nhãn từng đoạn).
 *
 * <p>So cây record (bỏ màu, record TeX gốc, header và các record định nghĩa), giống tools/compare_mtef.py. Ngoại lệ có
 * chủ đích, ghi trong spikes/mathtype-ole/BAO_CAO.md:
 * <ul>
 *   <li>Mục 9: "\," ghi U+EF02 (MathType tự nhập ghi U+EF07, bộ dịch Texvc không có bản dịch) → quy U+EF07 của MathType
 *       về U+EF02 trước khi so.</li>
 *   <li>Mục 3: một số câu MathType không nhập được TeX (không có đối tượng để so), hoặc nhập sai.</li>
 * </ul>
 */
class MtefGoldenTest {

    @Test
    void bieuThucMau10KhopMathType() {
        Outcome o = compare("exprs10");
        assertEquals(Set.of(), o.mismatch, o.report());
        assertEquals(Set.of(), o.keptOmml, o.report());
        assertEquals(Set.of(), o.mathTypeMissing, o.report());
        assertEquals(10, o.matched, o.report());
    }

    @Test
    void boThcs50KhopMathType() {
        Outcome o = compare("thcs50");
        assertEquals(Set.of(), o.mismatch, o.report());
        assertEquals(Set.of(), o.keptOmml, o.report());
        assertEquals(Set.of("[a;b]"), o.mathTypeMissing, "MathType Toggle TeX không tạo đối tượng cho [a;b]");
        assertEquals(49, o.matched, o.report());
    }

    @Test
    void boKyHieu68KhopMathTypeTruCacCaDaBiet() {
        Outcome o = compare("chars68");
        // MathType nhập TeX làm mất dấu tiếng Việt ("ậ" thành "?"); bộ sinh ghi đúng U+1EAD ở style Text.
        assertEquals(Set.of("\\text{V\u1EADy } x=2"), o.mismatch, o.report());
        // ⌊ ⌋ chưa có mã hóa đã kiểm chứng (MathType nhập ra U+FFFD) → giữ Equation của Word.
        assertEquals(Set.of("\\lfloor x\\rfloor"), o.keptOmml, o.report());
        assertEquals(Set.of("1,5", "2.5", "3!"), o.mathTypeMissing, "MathType Toggle TeX không tạo đối tượng");
        assertEquals(63, o.matched, o.report());
    }

    private static final class Outcome {
        int matched;
        final Set<String> mismatch = new TreeSet<>();
        final Set<String> keptOmml = new TreeSet<>();
        final Set<String> mathTypeMissing = new TreeSet<>();
        final List<String> details = new ArrayList<>();

        String report() {
            return String.join("\n", details);
        }
    }

    private static Outcome compare(String set) {
        List<String> labels = MtefTestSupport.latexList(set + ".tex.txt");
        List<Para> input = MtefTestSupport.paragraphs(MtefTestSupport.resource(set + "-pandoc.docx"));
        List<Para> expected = MtefTestSupport.paragraphs(MtefTestSupport.resource(set + "-mathtype.docx"));
        assertEquals(labels.size(), input.size(), set + ": số đoạn của docx pandoc");
        assertEquals(labels.size(), expected.size(), set + ": số đoạn của docx MathType");
        Outcome o = new Outcome();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            Para in = input.get(i);
            if (expected.get(i).mtef() == null) {
                o.mathTypeMissing.add(label);
                continue;
            }
            byte[] actual;
            try {
                actual = OmmlToMtef.convert(in.oMath(), !in.display());
            } catch (OmmlToMtef.Unsupported e) {
                o.keptOmml.add(label);
                o.details.add("#" + (i + 1) + " giữ OMML (" + e.getMessage() + "): " + label);
                continue;
            }
            List<String> exp = MtefTestSupport.dump(expected.get(i).mtef()).stream()
                    .map(l -> l.replace("U+EF07", "U+EF02")).toList();
            List<String> act = MtefTestSupport.dump(actual);
            if (exp.equals(act)) {
                o.matched++;
            } else {
                o.mismatch.add(label);
                o.details.add("#" + (i + 1) + " KHÁC: " + label + "\n  MathType: " + exp + "\n  bộ sinh:  " + act);
            }
        }
        return o;
    }
}
