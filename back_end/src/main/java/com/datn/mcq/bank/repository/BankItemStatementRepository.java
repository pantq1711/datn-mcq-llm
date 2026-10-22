package com.datn.mcq.bank.repository;

import com.datn.mcq.bank.entity.BankItemStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BankItemStatementRepository extends JpaRepository<BankItemStatement, BankItemStatement.Key> {
    List<BankItemStatement> findByBankItemIdOrderByPositionAsc(Integer bankItemId);

    List<BankItemStatement> findByBankItemIdInOrderByBankItemIdAscPositionAsc(Collection<Integer> bankItemIds);
}
