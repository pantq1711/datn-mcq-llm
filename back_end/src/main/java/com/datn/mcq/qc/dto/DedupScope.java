package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.Purpose;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phạm vi so trùng (quyết định 9, 2026-10-21). Không bao giờ so chéo experiment và production.
 * <ul>
 *   <li>experiment: chỉ trong cùng run ({@code comparableRunIds} = [runId]), để kết quả không phụ thuộc
 *       thứ tự chạy các chiến lược;</li>
 *   <li>production: các run production cùng môn ({@code comparableRunIds}, phải chứa runId).</li>
 * </ul>
 * Trong các run đó, ứng viên là câu qc_passed, in_bank, và câu cùng run đã có dòng dedup passed
 * (xem QualityCheckResultRepository.findDedupCandidateRows). Phạm vi sai hình dạng thì không đầy đủ:
 * dedup trả ERROR, không ghi dòng dedup và không gọi Solver.
 *
 * @param purpose             purpose của run chứa câu đang kiểm tra
 * @param runId               run của câu đang kiểm tra
 * @param comparableRunIds    các run được phép so, do nơi gọi tính theo purpose
 * @param excludedQuestionIds (W1-2) câu không được dùng làm ứng viên, ngoài chính câu đang kiểm tra: các bản trước của
 *                            một bản giáo viên sửa (gồm bản gốc LLM), để câu không bị coi là trùng với chính nó
 * @param extraCandidates     (W1-3) ứng viên thêm do nơi gọi cung cấp theo nội dung, ngoài các câu đọc từ DB theo run:
 *                            các câu đang nằm trong CÙNG đề (câu ở ô khác chưa qua QC, văn bản câu ngân hàng đã sửa).
 *                            {@code questionId} của mỗi ứng viên là một generated_question có thật (câu nguồn của câu
 *                            ngân hàng), vì kết quả dedup trỏ tới nó (khóa ngoại compared_to_question_id). Chỉ ứng viên
 *                            cùng dạng câu với câu đang kiểm tra được so.
 */
public record DedupScope(Purpose purpose, Integer runId, List<Integer> comparableRunIds,
                         Set<Integer> excludedQuestionIds, List<DedupCandidate> extraCandidates) {

    public DedupScope {
        comparableRunIds = comparableRunIds == null ? null : List.copyOf(comparableRunIds);
        excludedQuestionIds = excludedQuestionIds == null ? Set.of() : Set.copyOf(excludedQuestionIds);
        extraCandidates = extraCandidates == null ? List.of() : List.copyOf(extraCandidates);
    }

    public DedupScope(Purpose purpose, Integer runId, List<Integer> comparableRunIds,
                      Set<Integer> excludedQuestionIds) {
        this(purpose, runId, comparableRunIds, excludedQuestionIds, null);
    }

    public DedupScope(Purpose purpose, Integer runId, List<Integer> comparableRunIds) {
        this(purpose, runId, comparableRunIds, null, null);
    }

    /** Bản sao loại trừ thêm các câu {@code ids} khỏi ứng viên. */
    public DedupScope excluding(Collection<Integer> ids) {
        Set<Integer> all = new HashSet<>(excludedQuestionIds);
        all.addAll(ids);
        return new DedupScope(purpose, runId, comparableRunIds, all, extraCandidates);
    }

    /** Bản sao có thêm các ứng viên so trùng theo nội dung (W1-3: câu trong cùng đề). */
    public DedupScope withExtraCandidates(Collection<DedupCandidate> candidates) {
        List<DedupCandidate> all = new java.util.ArrayList<>(extraCandidates);
        all.addAll(candidates);
        return new DedupScope(purpose, runId, comparableRunIds, excludedQuestionIds, all);
    }

    public static DedupScope forExperiment(Integer runId) {
        return new DedupScope(Purpose.EXPERIMENT, runId, runId == null ? null : List.of(runId));
    }

    public static DedupScope forProduction(Integer runId, List<Integer> productionRunIdsOfSubject) {
        return new DedupScope(Purpose.PRODUCTION, runId, productionRunIdsOfSubject);
    }

    /** Đủ phạm vi khi có purpose, run, danh sách run chứa run đó; experiment chỉ được có đúng run của nó. */
    public boolean isComplete() {
        if (purpose == null || runId == null || comparableRunIds == null || !comparableRunIds.contains(runId)) {
            return false;
        }
        return purpose != Purpose.EXPERIMENT || comparableRunIds.size() == 1;
    }
}
