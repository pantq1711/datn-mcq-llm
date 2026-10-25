package com.datn.mcq.qc.repository;

import com.datn.mcq.qc.entity.AnswerCheckStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnswerCheckStatementRepository
        extends JpaRepository<AnswerCheckStatement, AnswerCheckStatement.Key> {
    List<AnswerCheckStatement> findByQcResultIdOrderByPositionAsc(Integer qcResultId);

    /** Xóa nhãn Solver từng ý của một dòng answer_check (trong transaction của nơi gọi). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AnswerCheckStatement s WHERE s.qcResultId = :qcResultId")
    int deleteByQcResultId(@Param("qcResultId") Integer qcResultId);
}
