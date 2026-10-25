package com.datn.mcq.experiment.repository;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.experiment.entity.ExperimentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, Integer> {
    @Query("SELECT r.id FROM ExperimentRun r WHERE r.subjectId = :subjectId AND r.purpose = :purpose ORDER BY r.id")
    List<Integer> findIdsBySubjectIdAndPurpose(
            @Param("subjectId") Integer subjectId,
            @Param("purpose") Purpose purpose);
}
