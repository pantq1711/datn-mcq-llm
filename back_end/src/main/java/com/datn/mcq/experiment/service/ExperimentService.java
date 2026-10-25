package com.datn.mcq.experiment.service;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.Strategy;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.repository.ExperimentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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

    /** Id các run của một môn theo purpose, tăng dần. Dùng để dựng phạm vi dedup production. */
    @Transactional(readOnly = true)
    public List<Integer> listRunIds(Integer subjectId, Purpose purpose) {
        return experimentRunRepository.findIdsBySubjectIdAndPurpose(subjectId, purpose);
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
        return createRun(subjectId, purpose, strategy, modelName, temperature, promptVersion, promptText,
                numQuestionsRequested, QuestionType.MCQ);
    }

    /**
     * Tạo run cho một dạng câu (V4). Run tf/sa phải là production với strategy zero-shot
     * (CHECK chk_er_tf_sa_production); nơi gọi chịu trách nhiệm truyền đúng.
     */
    @Transactional
    public ExperimentRun createRun(
            Integer subjectId,
            Purpose purpose,
            Strategy strategy,
            String modelName,
            Double temperature,
            String promptVersion,
            String promptText,
            Integer numQuestionsRequested,
            QuestionType questionType) {
        ExperimentRun run = new ExperimentRun();
        run.setQuestionType(questionType != null ? questionType : QuestionType.MCQ);
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

    /** Như {@link #createRun}, kèm hyperparams (JSON) — dùng cho run sinh câu theo ô ma trận (V7). */
    @Transactional
    public ExperimentRun createRun(
            Integer subjectId,
            Purpose purpose,
            Strategy strategy,
            String modelName,
            Double temperature,
            String promptVersion,
            String promptText,
            Integer numQuestionsRequested,
            QuestionType questionType,
            String hyperparams) {
        ExperimentRun run = createRun(subjectId, purpose, strategy, modelName, temperature, promptVersion, promptText,
                numQuestionsRequested, questionType);
        run.setHyperparams(hyperparams);
        return experimentRunRepository.save(run);
    }
}

