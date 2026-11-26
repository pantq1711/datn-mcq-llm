package com.datn.mcq.bankimport.repository;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.questionimage.service.QuestionImageService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Ứng viên so trùng cho bản xem trước nhập từ Word: câu ngân hàng cùng môn mà người tải được thấy (câu dùng chung và câu
 * riêng tư của chính họ; câu riêng tư của người khác KHÔNG được so, để không lộ gián tiếp). Native SQL CHỈ ĐỌC qua
 * bank_item, bank_item_statement, generated_question, question_template (ngoại lệ đọc chéo bảng như BankSearchRepository).
 * Nội dung bản ngân hàng (có thể đã sửa lúc Giữ) là thứ được so, mang id câu nguồn.
 *
 * <p>Khóa hình H7 của câu ngân hàng lấy từ {@link QuestionImageService#imageKeysBySourceQuestion} (một truy vấn theo lô,
 * định nghĩa dùng chung với dedup và cờ giống câu trong đề, X1 24/11); trước đó là một truy vấn con trong SQL này.
 */
@Repository
public class ImportDedupRepository {

    private static final String SQL = """
            SELECT b.source_question_id, b.question_type, b.question_text, b.option_a, b.option_b, b.option_c,
                   b.option_d, b.sa_answer,
                   ARRAY(SELECT s.statement_text FROM bank_item_statement s WHERE s.bank_item_id = b.id
                         ORDER BY s.position) AS statements
            FROM bank_item b
            JOIN generated_question g ON g.id = b.source_question_id
            JOIN question_template t ON t.id = g.template_id
            WHERE t.subject_id = :subjectId
              AND (b.visible_to_user_id IS NULL OR b.visible_to_user_id = :viewerId)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionImageService questionImageService;

    public ImportDedupRepository(NamedParameterJdbcTemplate jdbc, QuestionImageService questionImageService) {
        this.jdbc = jdbc;
        this.questionImageService = questionImageService;
    }

    /** Một dòng ngân hàng trước khi gắn khóa hình. */
    private record Row(int sourceQuestionId, String type, String text, String a, String b, String c, String d,
                       String saAnswer, List<String> statements) {}

    public List<DedupCandidate> bankCandidates(Integer subjectId, Integer viewerId) {
        MapSqlParameterSource p = new MapSqlParameterSource("subjectId", subjectId).addValue("viewerId", viewerId);
        List<Row> rows = jdbc.query(SQL, p, (rs, i) -> {
            Array arr = rs.getArray("statements");
            List<String> statements = arr == null ? List.of() : Arrays.asList((String[]) arr.getArray());
            return new Row(rs.getInt("source_question_id"), rs.getString("question_type"), rs.getString("question_text"),
                    rs.getString("option_a"), rs.getString("option_b"), rs.getString("option_c"),
                    rs.getString("option_d"), rs.getString("sa_answer"), statements);
        });
        Map<Integer, String> keys = questionImageService.imageKeysBySourceQuestion(
                rows.stream().map(Row::sourceQuestionId).toList());
        return rows.stream().map(r -> {
            String imageKey = keys.getOrDefault(r.sourceQuestionId(), "");
            if (QuestionType.SA.getDbValue().equals(r.type())) {
                return DedupCandidate.shortAnswer(r.sourceQuestionId(), r.text(), r.saAnswer(), imageKey);
            }
            if (QuestionType.TF.getDbValue().equals(r.type())) {
                return DedupCandidate.trueFalse(r.sourceQuestionId(), r.text(), r.statements(), imageKey);
            }
            return new DedupCandidate(r.sourceQuestionId(), r.text(), r.a(), r.b(), r.c(), r.d(), imageKey);
        }).toList();
    }
}
