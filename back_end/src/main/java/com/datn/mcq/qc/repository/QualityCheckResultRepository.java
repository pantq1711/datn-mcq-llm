package com.datn.mcq.qc.repository;

import com.datn.mcq.enums.CheckType;
import com.datn.mcq.qc.entity.QualityCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QualityCheckResultRepository extends JpaRepository<QualityCheckResult, Integer> {
    List<QualityCheckResult> findByQuestionIdOrderByIdAsc(Integer questionId);
    Optional<QualityCheckResult> findByQuestionIdAndCheckType(Integer questionId, CheckType checkType);
}

