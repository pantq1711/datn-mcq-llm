package com.datn.mcq.generation.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.CheckType;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.worker.GenerationJobProcessor;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.entity.QualityCheckResult;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Quy tắc QC-RERUN (quyết định 6 ngày 22/10): chạy lại QC cho một câu ở trạng thái generated.
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
     * @throws IllegalStateException            câu không ở trạng thái generated (duplicate là trạng thái cuối),
     *                                          hoặc đã có dòng answer_check (Solver đã cho kết luận hợp lệ)
     */
    public QcRerunResult rerun(Integer questionId) {
        GeneratedQuestion gq = generationService.getQuestionById(questionId);
        QuestionStatus previous = gq.getStatus();
        if (previous != QuestionStatus.GENERATED) {
            throw new IllegalStateException("Chỉ chạy lại QC cho câu ở trạng thái generated (câu id="
                    + questionId + " đang ở " + (previous != null ? previous.getDbValue() : "null") + ").");
        }
        // Chỉ chạy lại sau lỗi hạ tầng (quyết định 22/10): answer_check ERROR/NOT_RUN hoặc dedup ERROR.
        // Cả ba trường hợp đó đều KHÔNG để lại dòng answer_check (lỗi không ghi dòng; dedup chưa kết luận thì
        // không gọi Solver). Có dòng answer_check nghĩa là Solver đã cho kết luận hợp lệ -> từ chối (409),
        // để tránh chạy lại Solver đến khi "qua".
        Optional<QualityCheckResult> answerCheck = qcOrchestrator.getResultsForQuestion(gq.getId()).stream()
                .filter(r -> r.getCheckType() == CheckType.ANSWER_CHECK)
                .findFirst();
        if (answerCheck.isPresent()) {
            throw new IllegalStateException(Boolean.TRUE.equals(answerCheck.get().getPassed())
                    ? "Không chạy lại QC: answer_check của câu id=" + questionId
                            + " đã có kết quả hợp lệ, lần QC trước không dừng vì lỗi hạ tầng."
                    : "Không chạy lại QC: Solver đã cho kết luận hợp lệ là lệch đáp án (answer_check INCONSISTENT)"
                            + " với câu id=" + questionId + ".");
        }

        qcOrchestrator.clearDedupAndAnswerCheckResults(gq.getId());

        QuestionTemplate tpl = catalogService.getTemplateById(gq.getTemplateId());
        Subject subject = catalogService.getSubjectById(tpl.getSubjectId());
        boolean validateLatex = GenerationJobProcessor.isMathSubject(subject.getName());
        QuestionQcInput input = StoredQcInput.of(gq,
                gq.getQuestionType() == QuestionType.TF ? generationService.getStatements(gq.getId()) : List.of(),
                validateLatex, true);
        QcPipelineReport report = qcOrchestrator.evaluateAndSave(input, dedupScopeResolver.resolve(gq.getRunId()));

        GeneratedQuestion updated = generationService.updateStatusAndDifficulty(
                gq.getId(), report.resultingStatus(), null);
        return new QcRerunResult(gq.getId(), previous, updated.getStatus(), report);
    }
}
