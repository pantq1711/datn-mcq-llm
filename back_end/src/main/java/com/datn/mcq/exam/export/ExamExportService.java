package com.datn.mcq.exam.export;

import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.exam.service.ExamViewService;
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.service.MatrixService;
import org.springframework.stereotype.Service;

/**
 * Xuất đề ra Word (ngày 23/10, bản cơ bản): dữ liệu xem đề ở chế độ giáo viên (đã đối soát) → Markdown
 * ({@link ExamMarkdownBuilder}) → docx ({@link PandocRunner}). Không giữ transaction trong lúc chạy pandoc.
 *
 * <p>Từ 2/11 file có đầu đề ({@link ExamHeader}): tên kì, môn và khối lấy từ ma trận của đề; mã đề in ra là 101 (đề gốc),
 * mã hệ thống MT… chỉ còn ở tên file.
 *
 * <p>Công thức là Equation của Word hoặc đối tượng MathType ({@link EquationFormat}).
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
    private final ExamRepository examRepository;
    private final MatrixService matrixService;
    private final CatalogService catalogService;

    public ExamExportService(ExamViewService viewService, PandocRunner pandocRunner, ExamRepository examRepository,
                             MatrixService matrixService, CatalogService catalogService) {
        this.viewService = viewService;
        this.pandocRunner = pandocRunner;
        this.examRepository = examRepository;
        this.matrixService = matrixService;
        this.catalogService = catalogService;
    }

    /** Chỉ chủ đề xuất được (W1-1): đề của người khác 404 như khi xem đề. */
    public DocxFile docx(Integer examId, Integer actorId, EquationFormat format) {
        ExamViewDto view = viewService.view(examId, actorId);
        PandocRunner.Docx docx = pandocRunner.markdownToDocx(ExamMarkdownBuilder.build(view, header(view)), format);
        return new DocxFile(fileName(view), docx.content(), docx.equationsKeptOmml());
    }

    /**
     * Đầu đề của đề đã qua kiểm tra chủ sở hữu. Đề tạo từ ma trận dùng tên ma trận làm tên kì; đề sinh theo bài (ma trận ẩn)
     * hoặc đề cũ không có ma trận dùng tên đề. Ma trận chưa có thời gian làm bài nên dòng thời gian để trống.
     */
    ExamHeader header(ExamViewDto view) {
        Exam exam = examRepository.findById(view.examId()).orElse(null);
        if (exam == null || exam.getMatrixId() == null) {
            return ExamHeader.blank(view.title());
        }
        ExamMatrix matrix = matrixService.getMatrix(exam.getMatrixId());
        String subject = catalogService.getSubjectById(matrix.getSubjectId()).getName();
        String period = matrix.isLesson() ? view.title() : matrix.getName();
        return ExamHeader.of(period, subject, matrix.getGradeLevel(), null);
    }

    static String fileName(ExamViewDto view) {
        String code = view.examCode() == null || view.examCode().isBlank() ? "de-" + view.examId()
                : view.examCode().strip().replaceAll("[^A-Za-z0-9._-]", "_");
        return code + ".docx";
    }
}
