package com.datn.mcq.qc.service;

import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionType;
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
        if (subject.questionType() == QuestionType.SA) {
            // V4: chỉ so với câu cùng dạng; câu sa so theo thân câu và khóa chuẩn của đáp số.
            for (Object[] row : repository.findShortAnswerDedupCandidateRows(
                    subject.questionId(), scope.runId(), scope.comparableRunIds(), scope.creatorUserId())) {
                candidates.add(DedupCandidate.shortAnswer(
                        ((Number) row[0]).intValue(), (String) row[1], (String) row[2]));
            }
        } else if (subject.questionType() == QuestionType.TF) {
            // GĐ3: đề dẫn và 4 ý (không gồm nhãn).
            for (Object[] row : repository.findTrueFalseDedupCandidateRows(
                    subject.questionId(), scope.runId(), scope.comparableRunIds(), scope.creatorUserId())) {
                candidates.add(DedupCandidate.trueFalse(((Number) row[0]).intValue(), (String) row[1],
                        java.util.Arrays.asList((String) row[2], (String) row[3], (String) row[4], (String) row[5])));
            }
        } else if (subject.questionType() == QuestionType.MCQ) {
            for (Object[] row : repository.findDedupCandidateRows(
                    subject.questionId(), scope.runId(), scope.comparableRunIds(), scope.creatorUserId())) {
                candidates.add(new DedupCandidate(
                        ((Number) row[0]).intValue(),
                        (String) row[1], (String) row[2], (String) row[3], (String) row[4], (String) row[5]));
            }
        } else {
            return DedupOutcome.error("Dedup chưa hỗ trợ dạng câu " + subject.questionType().getDbValue());
        }

        // W1-3: câu đang nằm trong cùng đề (nơi gọi cung cấp theo nội dung); chỉ so câu cùng dạng, không so với chính
        // nó. Có thể trùng id với một ứng viên đọc từ DB (câu ngân hàng đã sửa: cùng câu nguồn, hai văn bản): giữ cả
        // hai, kết quả lấy điểm cao nhất.
        for (DedupCandidate extra : scope.extraCandidates()) {
            if (extra.questionType() == subject.questionType() && !extra.questionId().equals(subject.questionId())) {
                candidates.add(extra);
            }
        }
        // W1-2: bản giáo viên sửa không so với các bản trước của chính nó (gồm bản gốc LLM).
        candidates.removeIf(c -> scope.excludedQuestionIds().contains(c.questionId()));
        DedupOutcome outcome = DedupChecker.check(subject, candidates, DedupChecker.PROVISIONAL_THRESHOLD);

        QualityCheckResult result = repository
                .findByQuestionIdAndCheckType(subject.questionId(), CheckType.DEDUP)
                .orElseGet(QualityCheckResult::new);
        result.setQuestionId(subject.questionId());
        result.setQuestionType(subject.questionType());
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
