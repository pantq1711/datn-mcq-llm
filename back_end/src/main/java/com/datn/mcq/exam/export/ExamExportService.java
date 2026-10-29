package com.datn.mcq.exam.export;

import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.service.ExamViewService;
import org.springframework.stereotype.Service;

/**
 * Xuất đề ra Word (ngày 23/10, bản cơ bản): dữ liệu xem đề ở chế độ giáo viên (đã đối soát) → Markdown
 * ({@link ExamMarkdownBuilder}) → docx ({@link PandocRunner}). Không giữ transaction trong lúc chạy pandoc.
 * Công thức là Equation của Word hoặc đối tượng MathType ({@link EquationFormat}).
 */
@Service
public class ExamExportService {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * @param fileName          tên file theo mã đề, ví dụ "MT2610231200001.docx"
     * @param equationsKeptOmml khi xuất MathType: số công thức giữ dạng Equation của Word; null khi xuất Equation
     */
    public record DocxFile(String fileName, byte[] content, Integer equationsKeptOmml) {}

    private final ExamViewService viewService;
    private final PandocRunner pandocRunner;

    public ExamExportService(ExamViewService viewService, PandocRunner pandocRunner) {
        this.viewService = viewService;
        this.pandocRunner = pandocRunner;
    }

    /** Chỉ chủ đề xuất được (W1-1): đề của người khác 404 như khi xem đề. */
    public DocxFile docx(Integer examId, Integer actorId, EquationFormat format) {
        ExamViewDto view = viewService.view(examId, actorId);
        PandocRunner.Docx docx = pandocRunner.markdownToDocx(ExamMarkdownBuilder.build(view), format);
        return new DocxFile(fileName(view), docx.content(), docx.equationsKeptOmml());
    }

    static String fileName(ExamViewDto view) {
        String code = view.examCode() == null || view.examCode().isBlank() ? "de-" + view.examId()
                : view.examCode().strip().replaceAll("[^A-Za-z0-9._-]", "_");
        return code + ".docx";
    }
}
