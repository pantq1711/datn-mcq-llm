package com.datn.mcq.qc.service;

import com.datn.mcq.enums.CheckType;
import com.datn.mcq.qc.checker.DedupChecker;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupScope;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.repository.QualityCheckResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bước dedup của pipeline QC: một transaction ngắn, không gọi mạng.
 * Đọc ứng viên theo phạm vi, so trùng bằng {@link DedupChecker}, ghi một dòng quality_check_result(check_type = dedup).
 *
 * <p>Thiếu nội dung hoặc thiếu phạm vi: trả ERROR, không ghi dòng dedup (quyết định 4).
 * Lỗi đọc hoặc ghi DB: ném ngoại lệ, transaction rollback; nơi gọi phải coi là ERROR và giữ generated.
 *
 * <p>Hạn chế đã biết: chưa khóa theo phạm vi, nên hai câu giống nhau xử lý đồng thời có thể cùng không trùng.
 */
@Service
public class DedupService {

    private final QualityCheckResultRepository repository;

    public DedupService(QualityCheckResultRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DedupOutcome checkAndRecord(DedupCandidate subject, DedupScope scope) {
        if (subject == null || subject.questionId() == null || !subject.hasContent()) {
            return DedupOutcome.error("Thiếu nội dung câu cần so trùng");
        }
        if (scope == null || !scope.isComplete()) {
            return DedupOutcome.error("Thiếu hoặc sai phạm vi so trùng: cần purpose, run của câu và danh sách run"
                    + " được so (experiment chỉ gồm chính run đó)");
        }

        List<DedupCandidate> candidates = new ArrayList<>();
        for (Object[] row : repository.findDedupCandidateRows(
                subject.questionId(), scope.runId(), scope.comparableRunIds())) {
            candidates.add(new DedupCandidate(
                    ((Number) row[0]).intValue(),
                    (String) row[1], (String) row[2], (String) row[3], (String) row[4], (String) row[5]));
        }

        DedupOutcome outcome = DedupChecker.check(subject, candidates, DedupChecker.PROVISIONAL_THRESHOLD);

        QualityCheckResult result = repository
                .findByQuestionIdAndCheckType(subject.questionId(), CheckType.DEDUP)
                .orElseGet(QualityCheckResult::new);
        result.setQuestionId(subject.questionId());
        result.setCheckType(CheckType.DEDUP);
        result.setPassed(outcome.status() == DedupStatus.NO_DUPLICATE);
        result.setSimilarityScore(outcome.similarity());
        result.setComparedToQuestionId(outcome.comparedToQuestionId());
        result.setThresholdUsed(outcome.thresholdUsed());
        result.setJudgeAnswer(null);
        result.setJudgeModel(null);
        result.setCheckedAt(LocalDateTime.now());
        repository.saveAndFlush(result);
        return outcome;
    }
}
