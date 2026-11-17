package com.datn.mcq.assignment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bảng item_stat và cột exam_assignment.stats_computed_at (V33) bằng JdbcTemplate. Chỉ module {@code assignment} gọi, trong
 * transaction của nơi gọi (đã khóa hàng bài giao khi ghi).
 */
@Repository
public class ItemStatRepository {

    /** Một dòng item_stat; {@code choiceCountsJson}, {@code flagsJson} là JSON dạng chữ. */
    public record StatRow(Integer slotId, Integer bankItemId, String questionType, int n, int groupSize, BigDecimal p,
                          BigDecimal d, String choiceCountsJson, String flagsJson, LocalDateTime computedAt) {}

    private final JdbcTemplate jdbc;

    public ItemStatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Thay toàn bộ thống kê của bài giao bằng {@code rows} (có thể rỗng khi n = 0) và ghi lúc tính. */
    public void replace(Integer assignmentId, List<StatRow> rows, LocalDateTime computedAt) {
        jdbc.update("DELETE FROM item_stat WHERE assignment_id = ?", assignmentId);
        jdbc.batchUpdate("INSERT INTO item_stat (assignment_id, slot_id, bank_item_id, question_type, n, group_size, p,"
                        + " d, choice_counts, flags, computed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                rows.stream().map(r -> new Object[] {assignmentId, r.slotId(), r.bankItemId(), r.questionType(), r.n(),
                        r.groupSize(), r.p(), r.d(), r.choiceCountsJson(), r.flagsJson(), computedAt}).toList());
        jdbc.update("UPDATE exam_assignment SET stats_computed_at = ? WHERE id = ?", computedAt, assignmentId);
    }

    /**
     * Vô hiệu thống kê (giáo viên xóa lượt sau khi đã tính): xóa các dòng và đặt {@code stats_computed_at = NULL}; lần đọc
     * sau tính lại. Không làm gì khi chưa tính.
     */
    public void invalidate(Integer assignmentId) {
        jdbc.update("DELETE FROM item_stat WHERE assignment_id = ?", assignmentId);
        jdbc.update("UPDATE exam_assignment SET stats_computed_at = NULL WHERE id = ? AND stats_computed_at IS NOT NULL",
                assignmentId);
    }

    public List<StatRow> list(Integer assignmentId) {
        return jdbc.query("SELECT slot_id, bank_item_id, question_type, n, group_size, p, d, choice_counts::text,"
                        + " flags::text, computed_at FROM item_stat WHERE assignment_id = ?",
                (rs, i) -> new StatRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getString(8), rs.getString(9),
                        time(rs.getTimestamp(10))), assignmentId);
    }

    private static LocalDateTime time(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
