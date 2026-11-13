package com.datn.mcq.exam.export;

import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.config.ExamScoringProperties;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ItemStatementDto;
import com.datn.mcq.exam.dto.ExamViewDtos.PartDto;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.exam.service.ExamViewService;
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.service.MatrixService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * @param equationsFailed   khi xuất MathType: số công thức pandoc không đọc được, nằm ở dạng chữ trong file; null khi
     *                          xuất Equation
     * @param equationsFailedAt vị trí câu chứa các công thức đó, dạng "&lt;phần&gt;.&lt;số câu&gt;" ("I.8"), không lặp, theo
     *                          thứ tự trong đề; rỗng khi không có hoặc không xác định được
     */
    public record DocxFile(String fileName, byte[] content, Integer equationsKeptOmml, Integer equationsScaled,
                           Integer equationsFailed, List<String> equationsFailedAt) {
        public DocxFile {
            equationsFailedAt = equationsFailedAt == null ? List.of() : List.copyOf(equationsFailedAt);
        }
    }

    /**
     * Một công thức không chuyển được: câu thứ {@code number} của phần {@code part} (đánh số như trong file), trường
     * {@code field} (question_text, option_a, statement_b, explanation, sa_answer...), công thức thứ {@code ordinal} (từ 1)
     * của trường đó.
     */
    record FailedFormula(String part, int number, Integer questionId, Integer bankItemId, Integer slotId, String field,
                         int ordinal) {
        String position() {
            return part + "." + number;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ExamExportService.class);

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
        return docx(examId, actorId, format, durationMinutes, true);
    }

    /**
     * @param includeDistractorNotes (13/11, PA1) in "Phân tích phương án nhiễu" sau lời giải câu mcq (mặc định true); chỉ ở
     *                               phần lời giải chi tiết
     */
    public DocxFile docx(Integer examId, Integer actorId, EquationFormat format, Integer durationMinutes,
                         boolean includeDistractorNotes) {
        ExamViewDto view = viewService.view(examId, actorId);
        String markdown = ExamMarkdownBuilder.build(view, header(view, durationMinutes), scoring, includeDistractorNotes);
        PandocRunner.Docx docx = exportMetrics == null
                ? pandocRunner.markdownToDocx(markdown, format)
                : exportMetrics.measure(format == null ? "omml" : format.name().toLowerCase(java.util.Locale.ROOT),
                        () -> pandocRunner.markdownToDocx(markdown, format));
        List<String> failedAt = List.of();
        if (docx.equationsFailed() != null && docx.equationsFailed() > 0) {
            failedAt = logFailed(view, docx.equationsFailed(), docx.failedLatex());
        }
        return new DocxFile(fileName(view), docx.content(), docx.equationsKeptOmml(),
                docx.equationsScaled(), docx.equationsFailed(), failedAt);
    }

    /**
     * Ghi WARN mỗi công thức không chuyển được kèm đề, mã câu và vị trí (KHÔNG ghi nội dung câu); trả vị trí các câu chứa
     * chúng ({@link FailedFormula#position()}), không lặp.
     */
    private static List<String> logFailed(ExamViewDto view, int failed, List<String> failedLatex) {
        List<FailedFormula> located = locateFailed(view, failedLatex);
        for (FailedFormula f : located) {
            log.warn("Xuất MathType đề {}: công thức không chuyển được ở phần {} câu {} (questionId={}, bankItemId={}, "
                            + "slotId={}), trường {}, công thức thứ {} của trường", view.examId(), f.part(), f.number(),
                    f.questionId(), f.bankItemId(), f.slotId(), f.field(), f.ordinal());
        }
        if (located.size() < failed) {
            log.warn("Xuất MathType đề {}: {} công thức không chuyển được chưa xác định được câu chứa", view.examId(),
                    failed - located.size());
        }
        return located.stream().map(FailedFormula::position).distinct().toList();
    }

    /**
     * Câu chứa từng công thức trong {@code failedLatex} (LaTeX như pandoc đọc, khoảng trắng đã gộp): so với các công thức
     * $...$ của từng trường sau đúng bước chuẩn hóa của file xuất ({@link ExamMarkdownBuilder#withoutLiteralNewlines}). Mỗi
     * công thức hỏng ứng với một lần xuất hiện; không tìm thấy thì bỏ qua (nơi gọi ghi log số còn lại).
     */
    static List<FailedFormula> locateFailed(ExamViewDto view, List<String> failedLatex) {
        List<String> remaining = new ArrayList<>(failedLatex);
        List<FailedFormula> out = new ArrayList<>();
        ExamViewDto clean = ExamMarkdownBuilder.withoutLiteralNewlines(view);
        for (PartDto part : clean.parts()) {
            int number = 0;
            for (ExamItemDto item : part.items()) {
                number++;
                if (remaining.isEmpty() || item.questionText() == null) {
                    continue;
                }
                for (Map.Entry<String, String> field : fields(item).entrySet()) {
                    int ordinal = 0;
                    for (ExamMarkdownBuilder.Piece piece : ExamMarkdownBuilder.pieces(field.getValue())) {
                        if (!piece.math()) {
                            continue;
                        }
                        ordinal++;
                        if (remaining.remove(String.join(" ", piece.text().strip().split("\\s+")))) {
                            out.add(new FailedFormula(part.part(), number, item.questionId(), item.bankItemId(),
                                    item.slotId(), field.getKey(), ordinal));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Các trường có công thức của một câu, theo tên cột (bỏ trường null). */
    private static Map<String, String> fields(ExamItemDto item) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("question_text", item.questionText());
        out.put("option_a", item.optionA());
        out.put("option_b", item.optionB());
        out.put("option_c", item.optionC());
        out.put("option_d", item.optionD());
        if (item.statements() != null) {
            for (ItemStatementDto s : item.statements()) {
                out.put("statement_" + s.key().getDbValue(), s.text());
            }
        }
        out.put("sa_answer", item.saAnswer());
        out.put("explanation", item.explanation());
        out.values().removeIf(java.util.Objects::isNull);
        return out;
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
