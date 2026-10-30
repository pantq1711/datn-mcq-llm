package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.GenerationQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface GenerationQuotaRepository extends JpaRepository<GenerationQuota, Integer> {

    /** Bảo đảm giáo viên có một dòng (hai request đầu tiên song song: một cái chèn, cái kia chờ rồi không làm gì). */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO generation_quota (user_id, usage_date, requested) VALUES (:userId, :today, 0)"
            + " ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    int ensureRow(@Param("userId") Integer userId, @Param("today") LocalDate today);

    /**
     * Khóa dòng của giáo viên tới hết transaction (FOR NO KEY UPDATE: tuần tự hóa các lần kiểm trần của cùng giáo viên,
     * không chặn việc chèn đề/ma trận tham chiếu tới giáo viên) và trả số câu đã yêu cầu trong ngày {@code today}; dòng
     * của ngày khác tính là 0.
     */
    @Query(value = "SELECT CASE WHEN usage_date = :today THEN requested ELSE 0 END FROM generation_quota"
            + " WHERE user_id = :userId FOR NO KEY UPDATE", nativeQuery = true)
    Integer lockAndReadUsed(@Param("userId") Integer userId, @Param("today") LocalDate today);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE generation_quota SET usage_date = :today, requested = :requested WHERE user_id = :userId",
            nativeQuery = true)
    int setUsed(@Param("userId") Integer userId, @Param("today") LocalDate today, @Param("requested") int requested);

    /**
     * Số job sinh câu đang chờ hoặc đang chạy của các đề do giáo viên tạo (job gắn chỗ: exam_slot_id → exam_slot → exam).
     * Job answer_check và grounding_check không gắn chỗ nên không tính. Truy vấn native chỉ đọc trên generation_job
     * (ngoại lệ đọc chéo bảng, xem docs/architecture/backend-structure.md).
     */
    @Query(value = """
            SELECT count(*) FROM generation_job j
            JOIN exam_slot s ON s.id = j.exam_slot_id
            JOIN exam e ON e.id = s.exam_id
            WHERE e.created_by = :userId AND j.job_type = 'generate' AND j.status IN ('pending', 'running')
            """, nativeQuery = true)
    int countActiveGenerateJobs(@Param("userId") Integer userId);
}
