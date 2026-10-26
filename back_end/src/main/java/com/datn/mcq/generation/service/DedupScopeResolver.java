package com.datn.mcq.generation.service;

import com.datn.mcq.enums.Purpose;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.qc.dto.DedupScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Dựng phạm vi dedup cho câu thuộc một run (quyết định 9, 2026-10-21):
 * experiment chỉ so trong cùng run; production so trong các run production cùng môn;
 * không bao giờ so chéo purpose.
 *
 * <p>Không dựng được phạm vi (run không tồn tại, thiếu purpose, lỗi đọc DB) thì trả null:
 * pipeline coi là dedup ERROR, giữ generated và không gọi Solver.
 */
@Service
public class DedupScopeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DedupScopeResolver.class);

    private final ExperimentService experimentService;
    /** Lấy muộn (ObjectProvider) vì bean cài đặt nằm ở module exam, phụ thuộc ngược lại generation. */
    private final ObjectProvider<SlotDedupContext> slotContext;

    public DedupScopeResolver(ExperimentService experimentService, ObjectProvider<SlotDedupContext> slotContext) {
        this.experimentService = experimentService;
        this.slotContext = slotContext;
    }

    /**
     * W1-3: phạm vi dedup của câu sinh cho một chỗ trong đề: như {@link #resolve(Integer)} cộng các câu đang nằm trong
     * cùng đề. {@code examSlotId} null (job thường, sinh đồng bộ) thì như {@link #resolve(Integer)}.
     */
    public DedupScope resolveForSlot(Integer runId, Integer examSlotId) {
        DedupScope scope = resolve(runId);
        if (scope == null || examSlotId == null) {
            return scope;
        }
        SlotDedupContext context = slotContext.getIfAvailable();
        if (context == null) {
            return scope;
        }
        return scope.withExtraCandidates(context.candidatesForSlot(examSlotId))
                .withCreatorUserId(context.creatorUserIdForSlot(examSlotId));
    }

    public DedupScope resolve(Integer runId) {
        if (runId == null) {
            return null;
        }
        try {
            ExperimentRun run = experimentService.getById(runId);
            if (run.getPurpose() == Purpose.EXPERIMENT) {
                return DedupScope.forExperiment(run.getId());
            }
            if (run.getPurpose() == Purpose.PRODUCTION) {
                return DedupScope.forProduction(run.getId(),
                        experimentService.listRunIds(run.getSubjectId(), Purpose.PRODUCTION));
            }
            return null;
        } catch (RuntimeException ex) {
            LOG.warn("Không dựng được phạm vi dedup cho run id={}: {}", runId, ex.getMessage());
            return null;
        }
    }
}
