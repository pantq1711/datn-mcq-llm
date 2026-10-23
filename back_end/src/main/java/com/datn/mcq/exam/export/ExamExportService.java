package com.datn.mcq.exam.export;

import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.service.ExamViewService;
import org.springframework.stereotype.Service;

/**
 * Xuất đề ra Word (ngày 23/10, bản cơ bản): dữ liệu xem đề ở chế độ giáo viên (đã đối soát) → Markdown
 * ({@link ExamMarkdownBuilder}) → docx ({@link PandocRunner}). Không giữ transaction trong lúc chạy pandoc.
 */
@Service
public class ExamExportService {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** @param fileName tên file theo mã đề, ví dụ "MT2610231200001.docx" */
    public record DocxFile(String fileName, byte[] content) {}

    private final ExamViewService viewService;
    private final PandocRunner pandocRunner;

    public ExamExportService(ExamViewService viewService, PandocRunner pandocRunner) {
        this.viewService = viewService;
        this.pandocRunner = pandocRunner;
    }

    public DocxFile docx(Integer examId) {
        ExamViewDto view = viewService.view(examId);
        byte[] content = pandocRunner.markdownToDocx(ExamMarkdownBuilder.build(view));
        return new DocxFile(fileName(view), content);
    }

    static String fileName(ExamViewDto view) {
        String code = view.examCode() == null || view.examCode().isBlank() ? "de-" + view.examId()
                : view.examCode().strip().replaceAll("[^A-Za-z0-9._-]", "_");
        return code + ".docx";
    }
}
