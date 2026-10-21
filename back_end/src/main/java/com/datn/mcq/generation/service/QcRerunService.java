package com.datn.mcq.generation.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.springframework.stereotype.Service;

/**
 * V4 (quyết định 6 ngày 22/10): chạy lại QC cho một câu ở trạng thái generated.
 *
 * <p>Đi qua đúng pipeline format -> dedup -> answer_check (Solver chỉ chạy khi dedup kết luận không trùng),
 * trạng thái mới lấy từ báo cáo pipeline, không ép. Trước khi chạy, kết quả dedup và answer_check cũ bị xóa
 * để lần chạy lỗi không bị hiểu nhầm là thành công. Không có transaction bao ngoài: Solver được gọi
 * ngoài transaction DB (M-20).
 *
 * <p>CHƯA có endpoint HTTP: backend chưa có xác thực và phân quyền (Q-6), không được mở đường công khai
 * kích hoạt Solver tính phí cho câu tùy ý.
 */
@Service
public class QcRerunService {

    /** Kết quả cho người nghiên cứu (không phải payload chấm mù). */
    public record QcRerunResult(
            Integer questionId,
            QuestionStatus previousStatus,
            QuestionStatus resultingStatus,
            QcPipelineReport qcReport
    ) {}

    private final GenerationService generationService;
    private final CatalogService catalogService;
    private final QcOrchestrator qcOrchestrator;
    private final DedupScopeResolver dedupScopeResolver;

    public QcRerunService(
            GenerationService generationService,
            CatalogService catalogService,
            QcOrchestrator qcOrchestrator,
            DedupScopeResolver dedupScopeResolver) {
        this.generationService = generationService;
        this.catalogService = catalogService;
        this.qcOrchestrator = qcOrchestrator;
        this.dedupScopeResolver = dedupScopeResolver;
    }

    /**
     * @throws java.util.NoSuchElementException câu không tồn tại
     * @throws IllegalStateException            câu không ở trạng thái generated (duplicate là trạng thái cuối)
     */
    public QcRerunResult rerun(Integer questionId) {
        GeneratedQuestion gq = generationService.getQuestionById(questionId);
        QuestionStatus previous = gq.getStatus();
        if (previous != QuestionStatus.GENERATED) {
            throw new IllegalStateException("Chỉ chạy lại QC cho câu ở trạng thái generated (câu id="
                    + questionId + " đang ở " + (previous != null ? previous.getDbValue() : "null") + ").");
        }

        qcOrchestrator.clearDedupAndAnswerCheckResults(gq.getId());

        QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        QcPipelineReport report = qcOrchestrator.evaluateAndSave(new QuestionQcInput(
                gq.getId(),
                Boolean.TRUE.equals(gq.getParseSuccess()),
                gq.getQuestionText(),
                gq.getOptionA(),
                gq.getOptionB(),
                gq.getOptionC(),
                gq.getOptionD(),
                gq.getCorrectOption(),
                gq.getExplanation(),
                GenerationJobProcessor.isMathSubject(subject.getName()),
                true
        ), dedupScopeResolver.resolve(gq.getRunId()));

        GeneratedQuestion updated = generationService.updateStatusAndDifficulty(
                gq.getId(), report.resultingStatus(), null);
        return new QcRerunResult(gq.getId(), previous, updated.getStatus(), report);
    }
}
