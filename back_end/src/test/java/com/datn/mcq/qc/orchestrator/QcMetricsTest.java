package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.dto.GroundingResult;
import com.datn.mcq.qc.dto.GroundingVerdict;
import com.datn.mcq.qc.dto.QcPipelineReport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Giám sát (08/11): QC loại câu ở bước nào. Không cần DB. */
class QcMetricsTest {

    private MeterRegistry registry;
    private QcMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new QcMetrics(registry);
    }

    private static QcPipelineReport report(QuestionStatus status, boolean formatPassed, DedupStatus dedup,
                                           AnswerCheckStatus answer) {
        return new QcPipelineReport(status, formatPassed, List.of(), null, null, null, null, answer, dedup, null,
                List.of(), null, null, null);
    }

    private double rejected(String step) {
        var c = registry.find("datn.qc.rejected").tag("step", step).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void chuoiCoSanOGiaTri0ChoMoiBuocVaTrangThai() {
        for (String step : new String[]{"format", "dedup", "solver", "solver_error", "grounding"}) {
            assertEquals(0, rejected(step), step);
            assertEquals(0, registry.get("datn.qc.rejected").tag("step", step).counter().count(), step);
        }
        for (String status : new String[]{"generated", "format_failed", "duplicate", "qc_passed"}) {
            assertEquals(0, registry.get("datn.qc.pipeline").tag("status", status).counter().count(), status);
        }
        for (String verdict : new String[]{"supported", "not_supported", "unknown"}) {
            assertEquals(0, registry.get("datn.qc.grounding").tag("verdict", verdict).counter().count(), verdict);
        }
    }

    @Test
    void formatLoiTinhVaoBuocFormat() {
        metrics.recordPipeline(report(QuestionStatus.FORMAT_FAILED, false, DedupStatus.NOT_RUN, AnswerCheckStatus.NOT_RUN));

        assertEquals(1, rejected("format"));
        assertEquals(0, rejected("dedup"));
        assertEquals(1, registry.get("datn.qc.pipeline").tag("status", "format_failed").counter().count());
    }

    @Test
    void trungTinhVaoBuocDedup() {
        metrics.recordPipeline(report(QuestionStatus.DUPLICATE, true, DedupStatus.DUPLICATE, AnswerCheckStatus.NOT_RUN));

        assertEquals(1, rejected("dedup"));
        assertEquals(0, rejected("format"));
    }

    @Test
    void solverLechVaSolverLoiTinhRieng() {
        metrics.recordPipeline(report(QuestionStatus.GENERATED, true, DedupStatus.NO_DUPLICATE,
                AnswerCheckStatus.INCONSISTENT));
        metrics.recordPipeline(report(QuestionStatus.GENERATED, true, DedupStatus.NO_DUPLICATE, AnswerCheckStatus.ERROR));

        assertEquals(1, rejected("solver"));
        assertEquals(1, rejected("solver_error"));
    }

    @Test
    void cauDatKhongTinhLoai() {
        metrics.recordPipeline(report(QuestionStatus.QC_PASSED, true, DedupStatus.NO_DUPLICATE,
                AnswerCheckStatus.CONSISTENT));

        assertEquals(0, rejected("format") + rejected("dedup") + rejected("solver") + rejected("solver_error"));
        assertEquals(1, registry.get("datn.qc.pipeline").tag("status", "qc_passed").counter().count());
    }

    @Test
    void groundingKhongCoCanCuTinhVaoBuocGrounding() {
        metrics.recordGrounding(new GroundingResult(GroundingVerdict.NOT_SUPPORTED, "r", null, null, "m"));
        metrics.recordGrounding(new GroundingResult(GroundingVerdict.SUPPORTED, "r", "q", true, "m"));
        metrics.recordGrounding(null);

        assertEquals(1, rejected("grounding"));
        assertEquals(1, registry.get("datn.qc.grounding").tag("verdict", "supported").counter().count());
        assertEquals(1, registry.get("datn.qc.grounding").tag("verdict", "not_supported").counter().count());
        assertNull(registry.find("datn.qc.grounding").tag("verdict", "null").counter());
    }
}
