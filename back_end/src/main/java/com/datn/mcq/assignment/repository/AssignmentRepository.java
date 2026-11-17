package com.datn.mcq.assignment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy cập các bảng của V32 bằng JdbcTemplate (không entity JPA): exam_assignment, assignment_class, assignment_item,
 * assignment_attempt, attempt_answer. Chỉ module {@code assignment} gọi. Không đọc bảng của module khác.
 */
@Repository
public class AssignmentRepository {

    public record AssignmentRow(Integer id, Integer examId, Integer createdBy, String publicCode, String title,
                                int durationMinutes, LocalDateTime closesAt, boolean showScore, boolean showSolutions,
                                String status, LocalDateTime closedAt, String scoringJson, LocalDateTime createdAt) {
        public boolean open() {
            return OPEN.equals(status);
        }
    }

    public record ClassRow(String classLabel, int rosterSize) {}

    public record ItemRow(Integer slotId, Integer bankItemId, String questionType, String part, int numberInPart,
                          int position) {}

    public record AttemptRow(Integer id, Integer assignmentId, String classLabel, int seatNo, LocalDateTime startedAt,
                             LocalDateTime deadlineAt, LocalDateTime submittedAt, BigDecimal score,
                             BigDecimal maxScore) {}

    public record AnswerRow(Integer attemptId, Integer slotId, String mcqChoice, String tfChoices, String saText,
                            int correctCount, BigDecimal score) {}

    public static final String OPEN = "open";
    public static final String CLOSED = "closed";

    private static final String ASSIGNMENT_COLUMNS = "id, exam_id, created_by, public_code, title, duration_minutes,"
            + " closes_at, show_score, show_solutions, status, closed_at, scoring::text AS scoring, created_at";
    private static final String ATTEMPT_COLUMNS = "id, assignment_id, class_label, seat_no, started_at, deadline_at,"
            + " submitted_at, score, max_score";

    private static final RowMapper<AssignmentRow> ASSIGNMENT = (rs, i) -> new AssignmentRow(rs.getInt("id"),
            rs.getInt("exam_id"), rs.getInt("created_by"), rs.getString("public_code"), rs.getString("title"),
            rs.getInt("duration_minutes"), time(rs.getTimestamp("closes_at")), rs.getBoolean("show_score"),
            rs.getBoolean("show_solutions"), rs.getString("status"), time(rs.getTimestamp("closed_at")),
            rs.getString("scoring"), time(rs.getTimestamp("created_at")));

    private static final RowMapper<AttemptRow> ATTEMPT = (rs, i) -> new AttemptRow(rs.getInt("id"),
            rs.getInt("assignment_id"), rs.getString("class_label"), rs.getInt("seat_no"),
            time(rs.getTimestamp("started_at")), time(rs.getTimestamp("deadline_at")),
            time(rs.getTimestamp("submitted_at")), rs.getBigDecimal("score"), rs.getBigDecimal("max_score"));

    private static final RowMapper<AnswerRow> ANSWER = (rs, i) -> new AnswerRow(rs.getInt("attempt_id"),
            rs.getInt("slot_id"), rs.getString("mcq_choice"), rs.getString("tf_choices"), rs.getString("sa_text"),
            rs.getInt("correct_count"), rs.getBigDecimal("score"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public AssignmentRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    private static LocalDateTime time(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    // ---------- bài giao ----------

    public boolean codeExists(String publicCode) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM exam_assignment WHERE public_code = ?)", Boolean.class, publicCode));
    }

    public int insertAssignment(Integer examId, Integer createdBy, String publicCode, String title, int durationMinutes,
                                LocalDateTime closesAt, boolean showScore, boolean showSolutions, String scoringJson,
                                LocalDateTime now) {
        return jdbc.queryForObject("INSERT INTO exam_assignment (exam_id, created_by, public_code, title,"
                        + " duration_minutes, closes_at, show_score, show_solutions, scoring, consent_confirmed_at,"
                        + " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) RETURNING id", Integer.class,
                examId, createdBy, publicCode, title, durationMinutes, closesAt, showScore, showSolutions, scoringJson,
                now, now);
    }

    public void insertClasses(int assignmentId, List<ClassRow> classes) {
        for (int i = 0; i < classes.size(); i++) {
            jdbc.update("INSERT INTO assignment_class (assignment_id, class_label, roster_size, position)"
                    + " VALUES (?, ?, ?, ?)", assignmentId, classes.get(i).classLabel(), classes.get(i).rosterSize(),
                    i + 1);
        }
    }

    public void insertItems(int assignmentId, List<ItemRow> items) {
        jdbc.batchUpdate("INSERT INTO assignment_item (assignment_id, slot_id, bank_item_id, question_type, part,"
                        + " number_in_part, position) VALUES (?, ?, ?, ?, ?, ?, ?)",
                items.stream().map(it -> new Object[] {assignmentId, it.slotId(), it.bankItemId(), it.questionType(),
                        it.part(), it.numberInPart(), it.position()}).toList());
    }

    public Optional<AssignmentRow> findInExam(Integer assignmentId, Integer examId) {
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM exam_assignment WHERE id = ? AND exam_id = ?",
                ASSIGNMENT, assignmentId, examId).stream().findFirst();
    }

    public Optional<AssignmentRow> findById(Integer assignmentId) {
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM exam_assignment WHERE id = ?", ASSIGNMENT,
                assignmentId).stream().findFirst();
    }

    /** Khóa hàng bài giao (FOR UPDATE) tới hết transaction: bắt đầu lượt (trần lượt), đóng, xóa lượt. */
    public Optional<AssignmentRow> lockById(Integer assignmentId) {
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM exam_assignment WHERE id = ? FOR UPDATE", ASSIGNMENT,
                assignmentId).stream().findFirst();
    }

    public Optional<AssignmentRow> findByCode(String publicCode) {
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM exam_assignment WHERE public_code = ?", ASSIGNMENT,
                publicCode).stream().findFirst();
    }

    public List<AssignmentRow> listByExam(Integer examId) {
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM exam_assignment WHERE exam_id = ?"
                + " ORDER BY created_at DESC, id DESC", ASSIGNMENT, examId);
    }

    public boolean existsForExam(Integer examId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM exam_assignment WHERE exam_id = ?)", Boolean.class, examId));
    }

    /** Đóng lười: hết hạn thì {@code closed_at = closes_at}. Trả số dòng đổi (0 khi đã đóng hoặc chưa tới hạn). */
    public int closeIfDue(Integer assignmentId, LocalDateTime now) {
        return jdbc.update("UPDATE exam_assignment SET status = 'closed', closed_at = closes_at"
                + " WHERE id = ? AND status = 'open' AND closes_at <= ?", assignmentId, now);
    }

    /** Đóng tay: {@code closed_at = now}, hạn đóng kéo về {@code now} nếu còn sau đó. 0 khi đã đóng. */
    public int closeNow(Integer assignmentId, LocalDateTime now) {
        return jdbc.update("UPDATE exam_assignment SET status = 'closed', closed_at = ?, closes_at = LEAST(closes_at, ?)"
                + " WHERE id = ? AND status = 'open'", now, now, assignmentId);
    }

    /** Xóa bài giao; lớp, thành phần đề, lượt làm và câu trả lời xóa theo (ON DELETE CASCADE). */
    public void delete(Integer assignmentId) {
        jdbc.update("DELETE FROM exam_assignment WHERE id = ?", assignmentId);
    }

    public List<ClassRow> classes(Integer assignmentId) {
        return jdbc.query("SELECT class_label, roster_size FROM assignment_class WHERE assignment_id = ?"
                + " ORDER BY position", (rs, i) -> new ClassRow(rs.getString(1), rs.getInt(2)), assignmentId);
    }

    public List<ItemRow> items(Integer assignmentId) {
        return jdbc.query("SELECT slot_id, bank_item_id, question_type, part, number_in_part, position"
                        + " FROM assignment_item WHERE assignment_id = ? ORDER BY position",
                (rs, i) -> new ItemRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        rs.getInt(6)), assignmentId);
    }

    // ---------- lượt làm ----------

    public int countAttempts(Integer assignmentId) {
        return jdbc.queryForObject("SELECT count(*) FROM assignment_attempt WHERE assignment_id = ?", Integer.class,
                assignmentId);
    }

    /** Số lượt khác (chưa tính lượt mới) đã dùng lớp + số thứ tự này trong bài giao. */
    public int countSameSeat(Integer assignmentId, String classLabel, int seatNo) {
        return jdbc.queryForObject("SELECT count(*) FROM assignment_attempt WHERE assignment_id = ? AND class_label = ?"
                + " AND seat_no = ?", Integer.class, assignmentId, classLabel, seatNo);
    }

    public int insertAttempt(Integer assignmentId, String classLabel, int seatNo, String tokenHash,
                             LocalDateTime startedAt, LocalDateTime deadlineAt) {
        return jdbc.queryForObject("INSERT INTO assignment_attempt (assignment_id, class_label, seat_no, token_hash,"
                        + " started_at, deadline_at) VALUES (?, ?, ?, ?, ?, ?) RETURNING id", Integer.class,
                assignmentId, classLabel, seatNo, tokenHash, startedAt, deadlineAt);
    }

    public Optional<AttemptRow> attemptByTokenHash(String tokenHash) {
        return jdbc.query("SELECT " + ATTEMPT_COLUMNS + " FROM assignment_attempt WHERE token_hash = ?", ATTEMPT,
                tokenHash).stream().findFirst();
    }

    public Optional<AttemptRow> attemptById(Integer attemptId) {
        return jdbc.query("SELECT " + ATTEMPT_COLUMNS + " FROM assignment_attempt WHERE id = ?", ATTEMPT, attemptId)
                .stream().findFirst();
    }

    /** Các lượt của bài giao, theo lớp (thứ tự khai lúc giao), số thứ tự, rồi lúc bắt đầu. */
    public List<AttemptRow> attempts(Integer assignmentId) {
        return jdbc.query("SELECT a.id, a.assignment_id, a.class_label, a.seat_no, a.started_at, a.deadline_at,"
                + " a.submitted_at, a.score, a.max_score FROM assignment_attempt a JOIN assignment_class c"
                + " ON c.assignment_id = a.assignment_id AND c.class_label = a.class_label WHERE a.assignment_id = ?"
                + " ORDER BY c.position, a.seat_no, a.started_at, a.id", ATTEMPT, assignmentId);
    }

    /** Nộp một lần: chỉ đổi khi chưa nộp. Trả số dòng đổi (0: đã nộp, kể cả do lần nộp đồng thời). */
    public int markSubmitted(Integer attemptId, LocalDateTime submittedAt, BigDecimal score, BigDecimal maxScore) {
        return jdbc.update("UPDATE assignment_attempt SET submitted_at = ?, score = ?, max_score = ?"
                + " WHERE id = ? AND submitted_at IS NULL", submittedAt, score, maxScore, attemptId);
    }

    public void insertAnswers(List<AnswerRow> answers) {
        jdbc.batchUpdate("INSERT INTO attempt_answer (attempt_id, slot_id, mcq_choice, tf_choices, sa_text,"
                        + " correct_count, score) VALUES (?, ?, ?, ?, ?, ?, ?)",
                answers.stream().map(a -> new Object[] {a.attemptId(), a.slotId(), a.mcqChoice(), a.tfChoices(),
                        a.saText(), a.correctCount(), a.score()}).toList());
    }

    public List<AnswerRow> answersOfAssignment(Integer assignmentId) {
        return jdbc.query("SELECT n.attempt_id, n.slot_id, n.mcq_choice, n.tf_choices, n.sa_text, n.correct_count,"
                + " n.score FROM attempt_answer n JOIN assignment_attempt a ON a.id = n.attempt_id"
                + " WHERE a.assignment_id = ?", ANSWER, assignmentId);
    }

    public List<AnswerRow> answersOfAttempt(Integer attemptId) {
        return jdbc.query("SELECT attempt_id, slot_id, mcq_choice, tf_choices, sa_text, correct_count, score"
                + " FROM attempt_answer WHERE attempt_id = ?", ANSWER, attemptId);
    }

    /** Xóa các lượt {@code attemptIds} THUỘC bài giao; id của bài giao khác bị bỏ qua. Trả số lượt đã xóa. */
    public int deleteAttempts(Integer assignmentId, Collection<Integer> attemptIds) {
        if (attemptIds.isEmpty()) {
            return 0;
        }
        return named.update("DELETE FROM assignment_attempt WHERE assignment_id = :assignment AND id IN (:ids)",
                new MapSqlParameterSource().addValue("assignment", assignmentId).addValue("ids", attemptIds));
    }
}
