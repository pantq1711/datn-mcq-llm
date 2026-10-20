package com.datn.mcq.experiment.service;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.repository.ExperimentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class ExperimentService {

    private final ExperimentRunRepository experimentRunRepository;

    public ExperimentService(ExperimentRunRepository experimentRunRepository) {
        this.experimentRunRepository = experimentRunRepository;
    }

    @Transactional(readOnly = true)
    public ExperimentRun getById(Integer runId) {
        return experimentRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy experiment_run id=" + runId));
    }

    @Transactional
    public ExperimentRun createRun(
            Integer subjectId,
            Purpose purpose,
            Strategy strategy,
            String modelName,
            Double temperature,
            String promptVersion,
            String promptText,
            Integer numQuestionsRequested) {
        ExperimentRun run = new ExperimentRun();
        run.setSubjectId(subjectId);
        run.setPurpose(purpose != null ? purpose : Purpose.PRODUCTION);
        run.setStrategy(strategy != null ? strategy : Strategy.FEW_SHOT_STRUCTURED);
        run.setModelName(modelName != null && !modelName.isBlank() ? modelName : "gemini-3.5-flash-lite");
        run.setTemperature(temperature != null ? temperature : 0.7);
        run.setPromptVersion(promptVersion != null ? promptVersion : "v1.0-math-demo");
        run.setPromptText(promptText);
        run.setNumQuestionsRequested(numQuestionsRequested);
        run.setStatus("running");
        run.setStartedAt(LocalDateTime.now());
        return experimentRunRepository.save(run);
    }

    @Transactional
    public ExperimentRun getOrCreateDemoRun(Integer subjectId, String promptText) {
        return experimentRunRepository.findFirstBySubjectIdAndPromptVersionOrderByIdAsc(
                        subjectId, "math-demo-seed-v1")
                .orElseGet(() -> {
                    ExperimentRun run = createRun(
                            subjectId,
                            Purpose.PRODUCTION,
                            Strategy.FEW_SHOT_STRUCTURED,
                            "gemini-3.5-flash-lite",
                            0.7,
                            "math-demo-seed-v1",
                            promptText,
                            5
                    );
                    run.setStatus("completed");
                    run.setEndedAt(LocalDateTime.now());
                    return experimentRunRepository.save(run);
                });
    }
}

