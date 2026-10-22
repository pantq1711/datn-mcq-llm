package com.datn.mcq.bank.repository;

import com.datn.mcq.bank.entity.BankItemStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BankItemStatementRepository extends JpaRepository<BankItemStatement, BankItemStatement.Key> {
    List<BankItemStatement> findByBankItemIdOrderByPositionAsc(Integer bankItemId);

    List<BankItemStatement> findByBankItemIdInOrderByBankItemIdAscPositionAsc(Collection<Integer> bankItemIds);

    /** Xóa 4 ý của một câu tf trong ngân hàng (trong transaction của nơi gọi), dùng khi duyệt lại. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BankItemStatement s WHERE s.bankItemId = :bankItemId")
    int deleteByBankItemId(@Param("bankItemId") Integer bankItemId);
}
