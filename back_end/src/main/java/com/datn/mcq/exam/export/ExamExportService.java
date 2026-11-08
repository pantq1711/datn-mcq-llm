package com.datn.mcq.exam.export;

import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.config.ExamScoringProperties;
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
 * mã hệ thống MT… chỉ còn ở tên file. Từ 4/11 file theo khuôn đề kiểm tra THCS (A4, bảng đáp án, điểm từ
 * {@link ExamScoringProperties}); xem {@link ExamMarkdownBuilder}.
 *
 * <p>Công thức là Equation của Word hoặc đối tượng MathType ({@link EquationFormat}).
 */
@Service
public class ExamExportService {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * @param fileName          tên file theo mã đề, ví dụ "MT2610231200001.docx"
     * @param equationsKeptOmml khi xuất MathType: số công thức giữ dạng Equation của Word (chuyển MTEF thất bại thật);
     *                          null khi xuất Equation
     * @param equationsScaled   khi xuất MathType: số công thức dài không ngắt được nên bị thu nhỏ cho vừa lề; null khi
     *                          xuất Equation
     */
    public record DocxFile(String fileName, byte[] content, Integer equationsKeptOmml, Integer equationsScaled) {}

    private final ExamViewService viewService;
    private final PandocRunner pandocRunner;
    private final ExamRepository examRepository;
    private final MatrixService matrixService;
    private final CatalogService catalogService;
    private final ExamScoringProperties scoring;
    private ExportMetrics exportMetrics;

    public ExamExportService(ExamViewService viewService, PandocRunner pandocRunner, ExamRepository examRepository,
                             MatrixService matrixService, CatalogService catalogService,
                             ExamScoringProperties scoring) {
        this.viewService = viewService;
        this.pandocRunner = pandocRunner;
        this.examRepository = examRepository;
        this.matrixService = matrixService;
        this.catalogService = catalogService;
        this.scoring = scoring;
    }

    /** Giám sát (08/11): nạp bằng setter để chữ ký constructor không đổi; có thể null. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExportMetrics(ExportMetrics exportMetrics) {
        this.exportMetrics = exportMetrics;
    }

    /** Chỉ chủ đề xuất được (W1-1): đề của người khác 404 như khi xem đề. Thời gian làm bài để trống. */
    public DocxFile docx(Integer examId, Integer actorId, EquationFormat format) {
        return docx(examId, actorId, format, null);
    }

    /**
     * @param durationMinutes thời gian làm bài (phút, 5–180) in ở đầu đề; null thì in "……" cho giáo viên điền tay. Nơi gọi
     *                        kiểm khoảng hợp lệ bằng {@link ExamHeader#parseDuration}.
     */
    public DocxFile docx(Integer examId, Integer actorId, EquationFormat format, Integer durationMinutes) {
        ExamViewDto view = viewService.view(examId, actorId);
        String markdown = ExamMarkdownBuilder.build(view, header(view, durationMinutes), scoring);
        PandocRunner.Docx docx = exportMetrics == null
                ? pandocRunner.markdownToDocx(markdown, format)
                : exportMetrics.measure(format == null ? "omml" : format.name().toLowerCase(java.util.Locale.ROOT),
                        () -> pandocRunner.markdownToDocx(markdown, format));
        return new DocxFile(fileName(view), docx.content(), docx.equationsKeptOmml(),
                docx.equationsScaled());
    }

    /**
     * Đầu đề của đề đã qua kiểm tra chủ sở hữu. Đề tạo từ ma trận dùng tên ma trận làm tên kì; đề sinh theo bài (ma trận ẩn)
     * hoặc đề cũ không có ma trận dùng tên đề. Ma trận chưa có thời gian làm bài nên thời gian lấy từ tham số của API.
     */
    ExamHeader header(ExamViewDto view, Integer durationMinutes) {
        Exam exam = examRepository.findById(view.examId()).orElse(null);
        if (exam == null || exam.getMatrixId() == null) {
            return ExamHeader.blank(view.title(), durationMinutes);
        }
        ExamMatrix matrix = matrixService.getMatrix(exam.getMatrixId());
        String subject = catalogService.getSubjectById(matrix.getSubjectId()).getName();
        String period = matrix.isLesson() ? view.title() : matrix.getName();
        return ExamHeader.of(period, subject, matrix.getGradeLevel(), durationMinutes);
    }

    static String fileName(ExamViewDto view) {
        String code = view.examCode() == null || view.examCode().isBlank() ? "de-" + view.examId()
                : view.examCode().strip().replaceAll("[^A-Za-z0-9._-]", "_");
        return code + ".docx";
    }
}
