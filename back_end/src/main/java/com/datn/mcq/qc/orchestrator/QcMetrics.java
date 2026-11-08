package com.datn.mcq.qc.orchestrator;

import com.datn.mcq.qc.dto.AnswerCheckStatus;
import com.datn.mcq.qc.dto.DedupStatus;
import com.datn.mcq.qc.dto.GroundingResult;
import com.datn.mcq.qc.dto.GroundingVerdict;
import com.datn.mcq.qc.dto.QcPipelineReport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Giám sát (08/11): QC loại câu ở bước nào. {@code datn_qc_rejected_total{step}} nhận bốn bước cố định:
 * {@code format}, {@code dedup}, {@code solver} (Solver lệch nhãn), {@code solver_error} (Solver không kết luận được) và
 * {@code grounding} (không có căn cứ trong tài liệu). Chỉ ghi số đếm, không đổi kết quả QC.
 */
@Component
public class QcMetrics {

    private final MeterRegistry registry;

    public QcMetrics(MeterRegistry registry) {
        this.registry = registry;
        // Khởi tạo sẵn ở giá trị 0 để lần đầu tiên cũng tính là mức tăng (xem ExportMetrics).
        for (String step : new String[]{"format", "dedup", "solver", "solver_error", "grounding"}) {
            rejectedCounter(step);
        }
        for (String status : new String[]{"generated", "format_failed", "duplicate", "qc_passed"}) {
            pipelineCounter(status);
        }
        for (String verdict : new String[]{"supported", "not_supported", "unknown"}) {
            groundingCounter(verdict);
        }
    }

    private Counter pipelineCounter(String status) {
        return Counter.builder("datn.qc.pipeline")
                .description("Số lần chạy pipeline QC theo trạng thái cuối của câu")
                .tag("status", status).register(registry);
    }

    private Counter groundingCounter(String verdict) {
        return Counter.builder("datn.qc.grounding")
                .description("Kết luận của judge bám tài liệu")
                .tag("verdict", verdict).register(registry);
    }

    private Counter rejectedCounter(String step) {
        return Counter.builder("datn.qc.rejected")
                .description("Số câu bị loại hoặc bị cờ ở từng bước QC")
                .tag("step", step).register(registry);
    }

    /** Ghi kết quả một lần chạy {@code QcOrchestrator.evaluateAndSave}. */
    public void recordPipeline(QcPipelineReport report) {
        if (report == null) {
            return;
        }
        if (report.resultingStatus() != null) {
            pipelineCounter(report.resultingStatus().getDbValue()).increment();
        }
        if (!report.formatPassed()) {
            rejected("format");
        } else if (report.dedupStatus() == DedupStatus.DUPLICATE) {
            rejected("dedup");
        } else if (report.answerCheckStatus() == AnswerCheckStatus.INCONSISTENT) {
            rejected("solver");
        } else if (report.answerCheckStatus() == AnswerCheckStatus.ERROR) {
            rejected("solver_error");
        }
    }

    /** Ghi kết luận của judge "bám tài liệu"; null (không có kết quả) bị bỏ qua. */
    public void recordGrounding(GroundingResult result) {
        if (result == null || result.verdict() == null) {
            return;
        }
        groundingCounter(result.verdict().dbValue()).increment();
        if (result.verdict() == GroundingVerdict.NOT_SUPPORTED) {
            rejected("grounding");
        }
    }

    private void rejected(String step) {
        rejectedCounter(step).increment();
    }
}
