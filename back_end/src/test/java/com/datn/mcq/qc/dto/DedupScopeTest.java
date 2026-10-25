package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.Purpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phạm vi so trùng (quyết định 9, 2026-10-21):
 * experiment chỉ so trong cùng run; production so trong các run production cùng môn;
 * phạm vi sai hình dạng thì không đầy đủ (dedup trả ERROR, không gọi Solver).
 */
class DedupScopeTest {

    @Test
    void experimentScopeContainsOnlyItsOwnRun() {
        DedupScope scope = DedupScope.forExperiment(5);
        assertEquals(Purpose.EXPERIMENT, scope.purpose());
        assertEquals(List.of(5), scope.comparableRunIds());
        assertTrue(scope.isComplete());
    }

    @Test
    void experimentScopeSpanningSeveralRunsIsRejected() {
        assertFalse(new DedupScope(Purpose.EXPERIMENT, 5, List.of(5, 6)).isComplete());
        assertFalse(new DedupScope(Purpose.EXPERIMENT, 5, List.of(6)).isComplete());
    }

    @Test
    void productionScopeMustContainItsOwnRun() {
        assertTrue(DedupScope.forProduction(5, List.of(3, 5)).isComplete());
        assertFalse(DedupScope.forProduction(5, List.of(3)).isComplete());
        assertFalse(DedupScope.forProduction(5, null).isComplete());
        assertFalse(DedupScope.forProduction(null, List.of(3)).isComplete());
    }

    @Test
    void scopeWithoutPurposeIsIncomplete() {
        assertFalse(new DedupScope(null, 5, List.of(5)).isComplete());
        assertFalse(DedupScope.forExperiment(null).isComplete());
    }
}
