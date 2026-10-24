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
 */
public record DedupScope(Purpose purpose, Integer runId, List<Integer> comparableRunIds,
                         Set<Integer> excludedQuestionIds) {

    public DedupScope {
        comparableRunIds = comparableRunIds == null ? null : List.copyOf(comparableRunIds);
        excludedQuestionIds = excludedQuestionIds == null ? Set.of() : Set.copyOf(excludedQuestionIds);
    }

    public DedupScope(Purpose purpose, Integer runId, List<Integer> comparableRunIds) {
        this(purpose, runId, comparableRunIds, null);
    }

    /** Bản sao loại trừ thêm các câu {@code ids} khỏi ứng viên. */
    public DedupScope excluding(Collection<Integer> ids) {
        Set<Integer> all = new HashSet<>(excludedQuestionIds);
        all.addAll(ids);
        return new DedupScope(purpose, runId, comparableRunIds, all);
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
