package com.datn.mcq.bank.repository;

import com.datn.mcq.bank.entity.BankItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankItemRepository extends JpaRepository<BankItem, Integer> {
    List<BankItem> findAllByOrderByIdDesc();
    Optional<BankItem> findBySourceQuestionId(Integer sourceQuestionId);
}

