package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.mtef.FormulaResult;
import com.datn.mcq.bankimport.mtef.MtefConverter;

import java.util.HashMap;
import java.util.Map;

/** Chuyển công thức theo đường dẫn embedding, có nhớ kết quả; hạ xuống UNSUPPORTED nếu LaTeX không parse được. */
final class FormulaService {

    private final Map<String, byte[]> binaries;
    final Map<String, FormulaResult> cache = new HashMap<>();
    private final boolean validate;

    FormulaService(Map<String, byte[]> binaries, boolean validate) {
        this.binaries = binaries;
        this.validate = validate;
    }

    FormulaResult convert(String embeddingRef) {
        return cache.computeIfAbsent(embeddingRef, this::doConvert);
    }

    private FormulaResult doConvert(String ref) {
        byte[] ole = binaries.get(ref);
        if (ole == null) {
            return FormulaResult.unsupported("thieu_tep_embedding");
        }
        FormulaResult r = MtefConverter.fromOle(ole);
        if (validate && r.hasLatex() && !LatexCheck.parses(r.latex())) {
            return FormulaResult.unsupported("latex_khong_parse_duoc_bang_JLaTeXMath");
        }
        return r;
    }
}
