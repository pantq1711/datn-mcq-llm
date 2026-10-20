package com.datn.mcq.experiment.repository;

import com.datn.mcq.experiment.entity.ExperimentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, Integer> {
    Optional<ExperimentRun> findFirstBySubjectIdAndPromptVersionOrderByIdAsc(
            Integer subjectId, String promptVersion);
}

