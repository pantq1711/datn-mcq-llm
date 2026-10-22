package com.datn.mcq.bank.repository;

import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankItemRepository extends JpaRepository<BankItem, Integer> {
    List<BankItem> findAllByOrderByIdDesc();
    List<BankItem> findByQuestionTypeOrderByIdDesc(QuestionType questionType);
    Optional<BankItem> findBySourceQuestionId(Integer sourceQuestionId);
}

