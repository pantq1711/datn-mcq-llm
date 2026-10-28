package com.datn.mcq.bank.repository;

import com.datn.mcq.bank.dto.BankSearchDtos.ExamRef;
import com.datn.mcq.bank.dto.BankSearchDtos.ReviewDto;
import com.datn.mcq.bank.dto.BankSearchDtos.TopicDto;
import com.datn.mcq.bank.dto.BankSearchDtos.UserRef;
import com.datn.mcq.bank.dto.BankSearchDtos.VersionDto;
import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn lọc, đếm và đọc nguồn gốc của ngân hàng câu (W1-3). Native SQL CHỈ ĐỌC, ghép từ các mảnh cố định (giá trị
 * người dùng luôn đi qua tham số có tên, không nối vào chuỗi SQL). Đọc chéo bảng generated_question, question_template,
 * subject, exam_matrix_cell, teacher_review, users, exam, exam_slot, exam_question, exam_slot_action, experiment_run
 * (ngoại lệ ghi ở docs/architecture/backend-structure.md, quyết định D2).
 */
@Repository
public class BankSearchRepository {

    /**
     * Bộ lọc đã kiểm tra hợp lệ; thuộc tính null là không lọc. {@code viewerId} là người xem: câu riêng tư
     * (bank_item.visible_to_user_id, V19) chỉ hiện với chính người đó.
     */
    public record Filter(Integer subjectId, Integer gradeLevel, QuestionType questionType, String topic,
                         String content, Difficulty difficulty, String levels, String origin, String q,
                         String usage, Integer viewerId) {}

    /** Mảnh WHERE: câu dùng chung, hoặc câu riêng tư của chính người xem. */
    private static final String VISIBLE = " AND (b.visible_to_user_id IS NULL OR b.visible_to_user_id = :viewerId)";

    /** Thứ tự sắp xếp; mỗi giá trị ứng với một mảnh ORDER BY cố định. */
    public enum Sort {
        NEWEST("b.id DESC"),
        OLDEST("b.id ASC"),
        LEAST_USED("coalesce(us.uses, 0) ASC, us.last_used ASC NULLS FIRST, b.id ASC"),
        RECENTLY_USED("us.last_used DESC NULLS LAST, b.id DESC");

        private final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    /** Một dòng ngân hàng cùng thông tin môn, chủ đề, nguồn và mức dùng. */
    public record Row(Integer id, Integer sourceQuestionId, Integer reviewId, QuestionType questionType,
                      String questionText, String optionA, String optionB, String optionC, String optionD,
                      Option correctOption, String saAnswer, String explanation, Difficulty difficulty,
                      LocalDateTime createdAt, Integer subjectId, String subjectName, Integer gradeLevel,
                      String topic, String content, String reviewMode, boolean edited, long usageCount,
                      LocalDateTime lastUsedAt) {}

    /** Đếm đề đang dùng câu: số đề khác nhau và ngày tạo đề mới nhất (đề của mọi giáo viên). */
    private static final String USAGE_SUBQUERY = """
            (SELECT u.bank_item_id, count(*) AS uses, max(e.created_at) AS last_used
             FROM (SELECT exam_id, bank_item_id FROM exam_slot WHERE bank_item_id IS NOT NULL
                   UNION
                   SELECT exam_id, bank_item_id FROM exam_question) u
             JOIN exam e ON e.id = u.exam_id
             %s
             GROUP BY u.bank_item_id) us""";

    private static final String SELECT_ROW = """
            SELECT b.id, b.source_question_id, b.review_id, b.question_type, b.question_text,
                   b.option_a, b.option_b, b.option_c, b.option_d, b.correct_option, b.sa_answer, b.explanation,
                   b.difficulty, b.created_at, t.subject_id, s.name AS subject_name, t.grade_level, t.topic,
                   mc.content, r.review_mode, (g.edited_from_question_id IS NOT NULL OR r.edited) AS edited,
                   coalesce(us.uses, 0) AS uses, us.last_used
            """;

    private static final String FROM_JOINS = """
            FROM bank_item b
            JOIN generated_question g ON g.id = b.source_question_id
            JOIN question_template t ON t.id = g.template_id
            JOIN subject s ON s.id = t.subject_id
            JOIN teacher_review r ON r.id = b.review_id
            LEFT JOIN LATERAL (SELECT c.content FROM exam_matrix_cell c WHERE c.template_id = t.id
                               ORDER BY c.id LIMIT 1) mc ON TRUE
            LEFT JOIN %s ON us.bank_item_id = b.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BankSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> search(Filter f, Sort sort, int offset, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = SELECT_ROW + FROM_JOINS.formatted(USAGE_SUBQUERY.formatted("")) + where(f, p)
                + " ORDER BY " + sort.orderBy + " LIMIT :limit OFFSET :offset";
        p.addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(sql, p, (rs, i) -> row(rs));
    }

    public long count(Filter f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT count(*) " + FROM_JOINS.formatted(USAGE_SUBQUERY.formatted("")) + where(f, p);
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    /** Một dòng theo id mà {@code viewerId} được thấy (câu riêng tư của người khác như không có); rỗng nếu không có. */
    public Optional<Row> findRow(Integer id, Integer viewerId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("viewerId", viewerId);
        String sql = SELECT_ROW + FROM_JOINS.formatted(USAGE_SUBQUERY.formatted("WHERE u.bank_item_id = :id"))
                + " WHERE b.id = :id" + VISIBLE;
        return jdbc.query(sql, p, (rs, i) -> row(rs)).stream().findFirst();
    }

    private static String where(Filter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE TRUE").append(VISIBLE);
        p.addValue("viewerId", f.viewerId());
        if (f.subjectId() != null) {
            w.append(" AND t.subject_id = :subjectId");
            p.addValue("subjectId", f.subjectId());
        }
        if (f.gradeLevel() != null) {
            w.append(" AND t.grade_level = :gradeLevel");
            p.addValue("gradeLevel", f.gradeLevel());
        }
        if (f.questionType() != null) {
            w.append(" AND b.question_type = :questionType");
            p.addValue("questionType", f.questionType().getDbValue());
        }
        if (f.topic() != null) {
            w.append(" AND lower(btrim(coalesce(t.topic, ''))) = lower(btrim(:topic))");
            p.addValue("topic", f.topic());
        }
        if (f.content() != null) {
            w.append(" AND lower(btrim(coalesce(mc.content, ''))) = lower(btrim(:content))");
            p.addValue("content", f.content());
        }
        if (f.difficulty() != null) {
            w.append(" AND ((b.question_type <> 'tf' AND b.difficulty = :difficulty)"
                    + " OR (b.question_type = 'tf' AND EXISTS (SELECT 1 FROM bank_item_statement bs"
                    + " WHERE bs.bank_item_id = b.id AND bs.difficulty = :difficulty)))");
            p.addValue("difficulty", f.difficulty().getDbValue());
        }
        if (f.levels() != null) {
            w.append(" AND b.question_type = 'tf' AND (SELECT string_agg(bs.difficulty, ',' ORDER BY bs.position)"
                    + " FROM bank_item_statement bs WHERE bs.bank_item_id = b.id) = :levels");
            p.addValue("levels", f.levels());
        }
        if ("import".equals(f.origin())) {
            // Chỗ để dành cho câu nhập từ Word: chưa có nguồn nào ngoài câu AI đã duyệt.
            w.append(" AND FALSE");
        }
        if (f.q() != null) {
            w.append(" AND (b.question_text ILIKE :q ESCAPE '!' OR b.option_a ILIKE :q ESCAPE '!'"
                    + " OR b.option_b ILIKE :q ESCAPE '!' OR b.option_c ILIKE :q ESCAPE '!'"
                    + " OR b.option_d ILIKE :q ESCAPE '!' OR b.sa_answer ILIKE :q ESCAPE '!'"
                    + " OR EXISTS (SELECT 1 FROM bank_item_statement bs WHERE bs.bank_item_id = b.id"
                    + " AND bs.statement_text ILIKE :q ESCAPE '!'))");
            p.addValue("q", "%" + escapeLike(f.q()) + "%");
        }
        if ("used".equals(f.usage())) {
            w.append(" AND coalesce(us.uses, 0) > 0");
        } else if ("unused".equals(f.usage())) {
            w.append(" AND coalesce(us.uses, 0) = 0");
        }
        return w.toString();
    }

    /** Thoát ký tự đại diện của LIKE (dấu ! là ký tự thoát) để %, _ trong ô tìm được hiểu là ký tự thường. */
    static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    // ---------- nguồn gốc, đề đã dùng, chủ đề ----------

    /**
     * Chuỗi phiên bản của một câu, từ câu gốc LLM đến {@code sourceQuestionId}, kèm người sửa (nếu có dòng 'edit' trong
     * nhật ký thao tác). Phần tử đầu là câu gốc LLM.
     */
    public List<VersionDto> versions(Integer sourceQuestionId) {
        String sql = """
                WITH RECURSIVE chain AS (
                    SELECT id, edited_from_question_id, created_at, 0 AS depth
                    FROM generated_question WHERE id = :src
                    UNION ALL
                    SELECT g.id, g.edited_from_question_id, g.created_at, c.depth + 1
                    FROM generated_question g JOIN chain c ON g.id = c.edited_from_question_id)
                SELECT c.id, c.edited_from_question_id, c.created_at, a.actor_id, u.name,
                       CAST(a.changed_fields AS text) AS changed_fields, a.edit_distance
                FROM chain c
                LEFT JOIN exam_slot_action a ON a.generated_question_id = c.id AND a.action = 'edit'
                LEFT JOIN users u ON u.id = a.actor_id
                ORDER BY c.depth DESC""";
        return jdbc.query(sql, new MapSqlParameterSource("src", sourceQuestionId), (rs, i) -> {
            boolean edit = rs.getObject("edited_from_question_id") != null;
            Integer actor = (Integer) rs.getObject("actor_id");
            return new VersionDto(rs.getInt("id"), edit ? "teacher_edit" : "llm", time(rs, "created_at"),
                    edit && actor != null ? new UserRef(actor, rs.getString("name")) : null,
                    edit && actor != null ? JsonStringArray.read(rs.getString("changed_fields")) : null,
                    edit && actor != null ? (Integer) rs.getObject("edit_distance") : null);
        });
    }

    /** Run sinh câu của câu gốc LLM: {run_id, prompt_version, model_name}; rỗng nếu không có. */
    public Optional<String[]> runOf(Integer questionId) {
        return jdbc.query("SELECT r.id, r.prompt_version, r.model_name FROM generated_question g"
                        + " JOIN experiment_run r ON r.id = g.run_id WHERE g.id = :id",
                new MapSqlParameterSource("id", questionId),
                (rs, i) -> new String[] {String.valueOf(rs.getInt("id")), rs.getString("prompt_version"),
                        rs.getString("model_name")}).stream().findFirst();
    }

    public Optional<ReviewDto> review(Integer reviewId) {
        return jdbc.query("SELECT r.id, r.review_mode, r.decision, r.reviewer_id, u.name, r.reviewed_at"
                        + " FROM teacher_review r LEFT JOIN users u ON u.id = r.reviewer_id WHERE r.id = :id",
                new MapSqlParameterSource("id", reviewId),
                (rs, i) -> new ReviewDto(rs.getInt("id"), rs.getString("review_mode"), rs.getString("decision"),
                        new UserRef((Integer) rs.getObject("reviewer_id"), rs.getString("name")),
                        time(rs, "reviewed_at"))).stream().findFirst();
    }

    /** Đề của {@code ownerId} đang dùng câu (mới nhất trước, tối đa {@code limit}). */
    public List<ExamRef> examsOf(Integer bankItemId, Integer ownerId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", bankItemId)
                .addValue("owner", ownerId).addValue("limit", limit);
        return jdbc.query("""
                SELECT e.id, e.exam_code, e.title, e.created_at FROM exam e
                WHERE e.created_by = :owner
                  AND (EXISTS (SELECT 1 FROM exam_slot es WHERE es.exam_id = e.id AND es.bank_item_id = :id)
                       OR EXISTS (SELECT 1 FROM exam_question q WHERE q.exam_id = e.id AND q.bank_item_id = :id))
                ORDER BY e.created_at DESC, e.id DESC LIMIT :limit""", p,
                (rs, i) -> new ExamRef(rs.getInt("id"), rs.getString("exam_code"), rs.getString("title"),
                        time(rs, "created_at")));
    }

    public List<TopicDto> topics(Integer subjectId, Integer viewerId) {
        MapSqlParameterSource p = new MapSqlParameterSource("viewerId", viewerId);
        String sql = """
                SELECT t.subject_id, s.name, t.grade_level, min(btrim(t.topic)) AS topic, count(*) AS cnt
                FROM bank_item b
                JOIN generated_question g ON g.id = b.source_question_id
                JOIN question_template t ON t.id = g.template_id
                JOIN subject s ON s.id = t.subject_id
                WHERE TRUE""" + VISIBLE + (subjectId == null ? "" : " AND t.subject_id = :subjectId") + """

                GROUP BY t.subject_id, s.name, t.grade_level, lower(btrim(coalesce(t.topic, '')))
                ORDER BY s.name, t.grade_level NULLS LAST, 4""";
        if (subjectId != null) {
            p.addValue("subjectId", subjectId);
        }
        return jdbc.query(sql, p, (rs, i) -> new TopicDto(rs.getInt("subject_id"), rs.getString("name"),
                (Integer) rs.getObject("grade_level"), rs.getString("topic"), rs.getLong("cnt")));
    }

    // ---------- ánh xạ ----------

    private static Row row(ResultSet rs) throws SQLException {
        String difficulty = rs.getString("difficulty");
        String correct = rs.getString("correct_option");
        return new Row(rs.getInt("id"), rs.getInt("source_question_id"), rs.getInt("review_id"),
                DbEnum.fromDbValue(QuestionType.class, rs.getString("question_type")), rs.getString("question_text"),
                rs.getString("option_a"), rs.getString("option_b"), rs.getString("option_c"), rs.getString("option_d"),
                correct == null ? null : DbEnum.fromDbValue(Option.class, correct), rs.getString("sa_answer"),
                rs.getString("explanation"),
                difficulty == null ? null : DbEnum.fromDbValue(Difficulty.class, difficulty), time(rs, "created_at"),
                rs.getInt("subject_id"), rs.getString("subject_name"), (Integer) rs.getObject("grade_level"),
                rs.getString("topic"), rs.getString("content"), rs.getString("review_mode"), rs.getBoolean("edited"),
                rs.getLong("uses"), time(rs, "last_used"));
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }
}
