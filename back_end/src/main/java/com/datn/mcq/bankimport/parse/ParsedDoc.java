package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.model.Source;

import java.util.List;

/**
 * Kết quả tách một file.
 *
 * @param teacher  tên giáo viên cắt từ tên file; CHỈ để tự kiểm tra rò rỉ, không được ghi ra output hay DB
 * @param problems các điểm không tách được (không chứa nội dung câu)
 */
public record ParsedDoc(Source source, String teacher, List<Question> questions, List<Problem> problems) {

    /** Một điểm bất thường ở mức câu hoặc mức tài liệu. */
    public record Problem(String where, String code) {}

    /** Không in tên giáo viên hay nội dung câu. */
    @Override
    public String toString() {
        return "ParsedDoc[" + source.docId() + ", " + questions.size() + " câu]";
    }
}
