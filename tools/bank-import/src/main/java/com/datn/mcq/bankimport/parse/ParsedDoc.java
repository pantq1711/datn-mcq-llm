package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.model.Source;

import java.util.List;
import java.util.Map;

/**
 * Kết quả tách một file.
 *
 * @param teacher  tên giáo viên cắt từ tên file; CHỈ để tự kiểm tra rò rỉ, không được ghi ra output
 * @param problems các điểm không tách được (không chứa nội dung câu)
 * @param binaries media và embeddings theo đường dẫn trong zip (để chép ảnh)
 */
public record ParsedDoc(Source source, String teacher, List<Question> questions, List<Problem> problems,
                        Map<String, byte[]> binaries) {

    /** Một điểm bất thường ở mức câu hoặc mức tài liệu. */
    public record Problem(String where, String code) {}
}
