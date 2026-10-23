package com.datn.mcq.goldset.repository;

import com.datn.mcq.goldset.entity.DedupGoldLabel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DedupGoldLabelRepository
        extends JpaRepository<DedupGoldLabel, DedupGoldLabel.DedupGoldLabelId> {
}

