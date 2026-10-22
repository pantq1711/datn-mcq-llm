package com.datn.mcq.qc.repository;

import com.datn.mcq.qc.entity.AnswerCheckStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerCheckStatementRepository
        extends JpaRepository<AnswerCheckStatement, AnswerCheckStatement.Key> {
    List<AnswerCheckStatement> findByQcResultIdOrderByPositionAsc(Integer qcResultId);
}
