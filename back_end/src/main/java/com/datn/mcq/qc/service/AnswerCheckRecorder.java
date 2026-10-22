package com.datn.mcq.qc.service;

import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.qc.entity.AnswerCheckStatement;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.AnswerCheckStatementRepository;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ghi kết quả Solver của câu Đúng/Sai (GĐ3): dòng answer_check và 4 dòng answer_check_statement trong CÙNG một
 * transaction. Tách thành bean riêng vì @Transactional gọi nội bộ trong QcOrchestrator không có hiệu lực (M-24).
 * Lỗi ở bất kỳ dòng nào thì cả dòng answer_check bị rollback, nên không có kết quả dở dang (nơi gọi coi là ERROR).
 * Không gọi mạng.
 */
@Service
public class AnswerCheckRecorder {

    private final QualityCheckResultRepository qualityCheckResultRepository;
    private final AnswerCheckStatementRepository answerCheckStatementRepository;

    public AnswerCheckRecorder(
            QualityCheckResultRepository qualityCheckResultRepository,
            AnswerCheckStatementRepository answerCheckStatementRepository) {
        this.qualityCheckResultRepository = qualityCheckResultRepository;
        this.answerCheckStatementRepository = answerCheckStatementRepository;
    }

    /**
     * @param judgeLabels nhãn Solver cho 4 ý theo thứ tự a-d
     */
    @Transactional
    public QualityCheckResult recordTrueFalse(Integer questionId, boolean passed, String judgeModel,
                                              List<TruthChoice> judgeLabels) {
        if (judgeLabels == null || judgeLabels.size() != StatementKey.values().length) {
            throw new IllegalArgumentException("Kết quả Solver câu đúng/sai phải có đủ 4 ý");
        }
        QualityCheckResult qcr = qualityCheckResultRepository
                .findByQuestionIdAndCheckType(questionId, CheckType.ANSWER_CHECK)
                .orElseGet(QualityCheckResult::new);
        qcr.setQuestionId(questionId);
        qcr.setQuestionType(QuestionType.TF);
        qcr.setCheckType(CheckType.ANSWER_CHECK);
        qcr.setPassed(passed);
        qcr.setJudgeAnswer(null);
        qcr.setJudgeModel(judgeModel);
        qcr.setJudgeSaKind(null);
        qcr.setJudgeSaAnswer(null);
        qcr.setCheckedAt(LocalDateTime.now());
        QualityCheckResult saved = qualityCheckResultRepository.saveAndFlush(qcr);

        answerCheckStatementRepository.deleteByQcResultId(saved.getId());
        StatementKey[] keys = StatementKey.values();
        for (int i = 0; i < keys.length; i++) {
            answerCheckStatementRepository.save(new AnswerCheckStatement(saved.getId(), keys[i], judgeLabels.get(i)));
        }
        answerCheckStatementRepository.flush();
        return saved;
    }
}
