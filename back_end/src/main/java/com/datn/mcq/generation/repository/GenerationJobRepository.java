package com.datn.mcq.generation.repository;

import com.datn.mcq.generation.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Integer> {

    List<GenerationJob> findAllByOrderByIdDesc();

    /** V7: job mới nhất của một chỗ trong đề. */
    Optional<GenerationJob> findFirstByExamSlotIdOrderByIdDesc(Integer examSlotId);

    /**
     * Đợt A (ngày 23/10): khóa một job pending đã đến hạn để nhận. SKIP LOCKED bỏ qua job đang bị transaction khác
     * khóa, nên hai worker (hai luồng hoặc hai instance) không nhận trùng. Phải gọi trong transaction đọc-ghi; khóa giữ
     * tới khi transaction đó commit (sau khi đổi job sang running).
     *
     * <p>{@code now} là giờ của JVM, không dùng now() của PostgreSQL: next_run_at được ghi bằng giờ JVM, còn đồng hồ
     * máy chủ DB (container) có thể lệch vài trăm mili giây, làm job vừa xếp hàng chưa "đến hạn".
     */
    @Query(value = """
            SELECT * FROM generation_job
            WHERE status = 'pending' AND (next_run_at IS NULL OR next_run_at <= :now)
            ORDER BY next_run_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<GenerationJob> lockNextDuePendingJob(@Param("now") LocalDateTime now);

    /** Như {@link #lockNextDuePendingJob} nhưng chỉ job sinh câu (process-next với runAnswerCheck = false, W1-2). */
    @Query(value = """
            SELECT * FROM generation_job
            WHERE status = 'pending' AND job_type = 'generate' AND (next_run_at IS NULL OR next_run_at <= :now)
            ORDER BY next_run_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<GenerationJob> lockNextDuePendingGenerateJob(@Param("now") LocalDateTime now);

    /** V13 (W1-2): job mới nhất của một câu theo loại (job Solver của bản giáo viên sửa). */
    Optional<GenerationJob> findFirstByQuestionIdAndJobTypeOrderByIdDesc(Integer questionId, String jobType);

    /**
     * V13 (W1-2): hủy job Solver còn chờ của một bản đã bị thay bằng bản sửa mới ({@code failed}, last_error
     * "superseded"). Job đang chạy không bị đụng; kết quả về muộn của nó bị bỏ khi ghi (bản đã bị thay).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE generation_job
            SET status = 'failed', last_error = 'superseded', updated_at = :now
            WHERE question_id = :questionId AND job_type = 'answer_check' AND status = 'pending'
            """, nativeQuery = true)
    int cancelPendingAnswerChecks(@Param("questionId") Integer questionId, @Param("now") LocalDateTime now);

    /**
     * Đợt A: trả job running nhận trước {@code cutoff} (V10 started_at; dòng trước V10 xét updated_at) về pending, chạy
     * ngay ({@code next_run_at = now}, giờ JVM), ghi last_error "stale: reset". Hoàn lại lượt attempt đã tăng lúc nhận
     * job, để lần reset không tính vào attempt.
     */
    @Modifying
    @Query(value = """
            UPDATE generation_job
            SET status = 'pending', next_run_at = :now, last_error = 'stale: reset',
                attempt = GREATEST(attempt - 1, 0), started_at = NULL, updated_at = :now
            WHERE status = 'running' AND COALESCE(started_at, updated_at) < :cutoff
            """, nativeQuery = true)
    int resetStaleRunning(@Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);
}
