package com.datn.mcq.generation.repository;

import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.generation.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Integer> {

    List<GenerationJob> findAllByOrderByIdDesc();

    /** V7: job mới nhất của một chỗ trong đề. */
    Optional<GenerationJob> findFirstByExamSlotIdOrderByIdDesc(Integer examSlotId);

    @Query("""
            SELECT j FROM GenerationJob j
            WHERE j.status = :status
              AND (j.nextRunAt IS NULL OR j.nextRunAt <= :now)
            ORDER BY j.id ASC
            """)
    List<GenerationJob> findDueJobs(@Param("status") JobStatus status, @Param("now") LocalDateTime now);

    default Optional<GenerationJob> findFirstDuePendingJob(LocalDateTime now) {
        List<GenerationJob> due = findDueJobs(JobStatus.PENDING, now);
        return due.isEmpty() ? Optional.empty() : Optional.of(due.get(0));
    }
}

