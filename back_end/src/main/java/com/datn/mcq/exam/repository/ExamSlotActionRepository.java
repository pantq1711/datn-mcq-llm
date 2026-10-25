package com.datn.mcq.exam.repository;

import com.datn.mcq.exam.entity.ExamSlotAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSlotActionRepository extends JpaRepository<ExamSlotAction, Integer> {
    List<ExamSlotAction> findBySlotIdOrderByIdAsc(Integer slotId);
}
