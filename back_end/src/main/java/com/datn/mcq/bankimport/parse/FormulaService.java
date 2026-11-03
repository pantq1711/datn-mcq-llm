package com.datn.mcq.bankimport.parse;

import com.datn.mcq.mtef.FormulaResult;
import com.datn.mcq.mtef.MtefConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển công thức theo đường dẫn embedding, có nhớ kết quả. Dùng chung bộ đọc MTEF {@code com.datn.mcq.mtef} với trích
 * tài liệu bài học (P6). Không kiểm LaTeX ở đây: bước ánh xạ kiểm bằng {@code LatexValidationRules}.
 */
final class FormulaService {

    private final Map<String, byte[]> embeddings;
    private final Map<String, FormulaResult> cache = new HashMap<>();

    FormulaService(Map<String, byte[]> embeddings) {
        this.embeddings = embeddings;
    }

    FormulaResult convert(String embeddingRef) {
        return cache.computeIfAbsent(embeddingRef, this::doConvert);
    }

    private FormulaResult doConvert(String ref) {
        byte[] ole = embeddings.get(ref);
        if (ole == null) {
            return FormulaResult.unsupported("thieu_tep_embedding");
        }
        try {
            return MtefConverter.fromOle(ole);
        } catch (RuntimeException | StackOverflowError e) {
            return FormulaResult.unsupported("mtef_loi: " + e.getClass().getSimpleName());
        }
    }
}
