package com.datn.mcq.exam.controller;

import com.datn.mcq.common.security.CurrentUser;
import com.datn.mcq.exam.dto.ExamBuildDtos.BuildExamRequest;
import com.datn.mcq.exam.dto.ExamBuildDtos.BuildResultDto;
import com.datn.mcq.exam.dto.ExamBuildDtos.ProgressDto;
import com.datn.mcq.exam.dto.ExamBuildDtos.SlotDto;
import com.datn.mcq.exam.dto.ExamEditDtos.EditQuestionRequest;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.QcTraceDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamPageDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.export.ExamExportService;
import com.datn.mcq.exam.export.ExamExportService.DocxFile;
import com.datn.mcq.exam.export.EquationFormat;
import com.datn.mcq.exam.export.ExamHeader;
import com.datn.mcq.exam.service.ExamActionService;
import com.datn.mcq.exam.service.ExamBuildService;
import com.datn.mcq.exam.service.ExamQcTraceService;
import com.datn.mcq.exam.service.ExamViewService;
import com.datn.mcq.matrix.dto.MatrixDtos.CreateLessonRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Đề tạo theo ma trận ở chế độ giáo viên (V7). Đây là API production, KHÔNG phải form chấm mù (thay đổi so với quyết
 * định 21/10 và V1, xem docs/ai-development-rules.md).
 *
 * <p>W1-1: chỉ giáo viên; người thao tác lấy từ token. Đề của người khác trả 404 như đề không tồn tại.
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    /** Header của xuất Word dạng MathType; SecurityConfig mở header này cho frontend đọc (CORS). */
    public static final String EQUATION_KEPT_HEADER = "X-Equation-Kept-Omml";
    /** Số công thức dài không ngắt được nên bị thu nhỏ cho vừa lề (xuất MathType); cũng mở qua CORS. */
    public static final String EQUATION_SCALED_HEADER = "X-Equation-Scaled";
    /** Số công thức pandoc không đọc được nên nằm ở dạng chữ trong file (xuất MathType, 11/11); cũng mở qua CORS. */
    public static final String EQUATION_FAILED_HEADER = "X-Equation-Failed";
    /** Câu chứa các công thức đó, "I.8,III.2" (phần.số câu); chỉ có khi xác định được; cũng mở qua CORS. */
    public static final String EQUATION_FAILED_AT_HEADER = "X-Equation-Failed-At";

    private final ExamBuildService examBuildService;
    private final ExamViewService examViewService;
    private final ExamActionService examActionService;
    private final ExamExportService examExportService;
    private final ExamQcTraceService examQcTraceService;

    public ExamController(ExamBuildService examBuildService, ExamViewService examViewService,
                          ExamActionService examActionService, ExamExportService examExportService,
                          ExamQcTraceService examQcTraceService) {
        this.examQcTraceService = examQcTraceService;
        this.examBuildService = examBuildService;
        this.examViewService = examViewService;
        this.examActionService = examActionService;
        this.examExportService = examExportService;
    }

    /**
     * Đề của tôi, mới nhất trước, kèm số chỗ theo trạng thái. Chỉ đọc (không đối soát job).
     *
     * @param page từ 0
     * @param size từ 1 đến 50
     * @param kind tùy chọn: "lesson" (sinh theo bài) hoặc "matrix" (từ ma trận); sai thì 400
     */
    @GetMapping
    public ExamPageDto list(CurrentUser user, @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) String kind) {
        return examViewService.list(user.id(), page, size, kind);
    }

    /**
     * W1-4: sinh theo bài (không cần ma trận): môn, khối, bài, số câu theo dạng và mức. Dùng lại luồng tạo đề theo ma
     * trận (ngân hàng trước, phần thiếu xếp hàng sinh câu). Tối đa 50 câu mỗi lần.
     */
    @PostMapping("/build-by-lesson")
    public BuildResultDto buildByLesson(CurrentUser user, @RequestBody CreateLessonRequest request) {
        return examBuildService.buildByLesson(request, user.id());
    }

    /**
     * Xuất đề ra Word (.docx, bản cơ bản): đề, rồi trang ĐÁP ÁN và LỜI GIẢI. Cần pandoc trên máy chủ: không có thì 503.
     * Tên file theo mã đề.
     *
     * <p>{@code equation}: {@code omml} (mặc định, Equation gốc của Word) hoặc {@code mathtype} (đối tượng MathType,
     * bấm đúp sửa bằng MathType); giá trị khác 400. Với {@code mathtype}, header {@value #EQUATION_KEPT_HEADER} cho biết số
     * công thức phải giữ dạng Equation (chưa hỗ trợ, hoặc cả file khi không đổi được); {@value #EQUATION_FAILED_HEADER} số
     * công thức pandoc không đọc được (nằm ở dạng chữ, các công thức khác vẫn là MathType) và
     * {@value #EQUATION_FAILED_AT_HEADER} câu chứa chúng.
     *
     * <p>{@code durationMinutes} (tùy chọn): thời gian làm bài, số nguyên 5–180, in ở đầu đề; không truyền thì in "……"; giá
     * trị khác 400.
     *
     * <p>{@code includeDistractorNotes} (tùy chọn, 13/11, PA1): {@code true} (mặc định) in dòng "Phân tích phương án nhiễu"
     * sau lời giải của câu mcq có lỗi sai kèm phương án (chỉ lỗi khớp hoặc chưa kiểm; chỉ ở phần lời giải chi tiết),
     * {@code false} thì bỏ; giá trị khác 400.
     */
    @GetMapping("/{examId}/export/docx")
    public ResponseEntity<byte[]> exportDocx(CurrentUser user, @PathVariable Integer examId,
                                             @RequestParam(required = false) String equation,
                                             @RequestParam(required = false) String durationMinutes,
                                             @RequestParam(required = false) String includeDistractorNotes) {
        EquationFormat format = EquationFormat.parse(equation);
        Integer minutes = ExamHeader.parseDuration(durationMinutes);
        boolean notes = parseIncludeDistractorNotes(includeDistractorNotes);
        DocxFile file = examExportService.docx(examId, user.id(), format, minutes, notes);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExamExportService.DOCX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString());
        if (file.equationsKeptOmml() != null) {
            response.header(EQUATION_KEPT_HEADER, Integer.toString(file.equationsKeptOmml()));
        }
        if (file.equationsScaled() != null) {
            response.header(EQUATION_SCALED_HEADER, Integer.toString(file.equationsScaled()));
        }
        if (file.equationsFailed() != null) {
            response.header(EQUATION_FAILED_HEADER, Integer.toString(file.equationsFailed()));
        }
        if (!file.equationsFailedAt().isEmpty()) {
            response.header(EQUATION_FAILED_AT_HEADER, String.join(",", file.equationsFailedAt()));
        }
        return response.body(file.content());
    }

    /** Xem đề (chế độ giáo viên): câu theo phần I-III kèm đáp án, lời giải và cờ "cần xem kỹ". */
    @GetMapping("/{examId}")
    public ExamViewDto view(CurrentUser user, @PathVariable Integer examId) {
        return examViewService.view(examId, user.id());
    }

    /** Tạo đề một lần bấm từ ma trận của mình: lấy câu ngân hàng khớp từng ô trước, phần thiếu xếp hàng sinh câu. */
    @PostMapping("/build")
    public BuildResultDto build(CurrentUser user, @RequestBody BuildExamRequest request) {
        return examBuildService.build(request, user.id());
    }

    /** Tiến độ theo ô. Mỗi lần gọi đối soát kết quả các job đã xong (thử lại khi cần). */
    @GetMapping("/{examId}/progress")
    public ProgressDto progress(CurrentUser user, @PathVariable Integer examId) {
        return examBuildService.progress(examId, user.id());
    }

    /** Giữ câu: câu hệ thống sinh được chép vào ngân hàng (teacher_review 'assisted'). Đã Giữ rồi thì 409. */
    @PostMapping("/{examId}/slots/{slotId}/keep")
    public ExamItemDto keep(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer slotId) {
        return examActionService.keep(examId, slotId, user.id());
    }

    /**
     * Body tùy chọn của Đổi câu.
     *
     * @param source      W1-4 (D5-B): "generate" (mặc định khi bỏ trống) sinh câu mới qua hàng đợi; "bank" lấy câu ngân
     *                    hàng khớp ô, hết câu thì 409 BANK_EXHAUSTED (không tự sinh); giá trị khác thì 400
     * @param instruction yêu cầu bổ sung của giáo viên cho lượt sinh lại này (tùy chọn; trim, tối đa 1000 ký tự từ PR B,
     *                    dài hơn thì 400; trống thì như không có); không dùng với source = bank
     * @param reason      B7 (V25): lý do Đổi do giáo viên chọn (tùy chọn): wrong_answer, ambiguous_options, off_matrix,
     *                    duplicate_pattern, latex_format, too_hard, too_easy, other; trống thì không ghi; giá trị khác 400
     * @param requestCodes (12/11, PR B) mã chip yêu cầu của lượt Đổi. Không gửi hoặc null: thừa hưởng yêu cầu của ô, không có
     *                    thì của đề. Có gửi (kể cả []) hoặc có instruction: lượt Đổi TỰ ĐẶT yêu cầu, bỏ hẳn yêu cầu của ô và
     *                    đề. Mã lạ, mã không hợp dạng câu của chỗ, hoặc mã khác rỗng với source = bank: 400 INVALID_REQUEST
     */
    public record ReplaceRequest(String source, String instruction, String reason, List<String> requestCodes) {
        /** Chữ ký trước PR B (không có mã yêu cầu). */
        public ReplaceRequest(String source, String instruction, String reason) {
            this(source, instruction, reason, null);
        }

        /** Chữ ký trước B7 (không có lý do). */
        public ReplaceRequest(String source, String instruction) {
            this(source, instruction, null);
        }
    }

    /**
     * Đổi câu khác: sinh lại đúng ô qua hàng đợi (có thể kèm yêu cầu của giáo viên) hoặc, với {@code source = "bank"},
     * lấy câu ngân hàng khớp ô. Chỗ đang sinh thì 409; ngân hàng hết câu thì 409 BANK_EXHAUSTED.
     */
    @PostMapping("/{examId}/slots/{slotId}/replace")
    public ExamItemDto replace(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer slotId,
                               @RequestBody(required = false) ReplaceRequest request) {
        return examActionService.replace(examId, slotId, user.id(), request != null ? request.source() : null,
                request != null ? request.instruction() : null, request != null ? request.reason() : null,
                request != null ? request.requestCodes() : null);
    }

    /**
     * W1-2: sửa câu của một chỗ chưa Giữ (đề, phương án/ý, đáp án, lời giải theo dạng câu). Tạo bản sửa mới, bản gốc
     * LLM giữ nguyên; QC không cần LLM chạy ngay, Solver chạy lại qua hàng đợi khi đề, phương án/ý hoặc đáp án đổi
     * (response có {@code qcState = "checking"}). 400 INVALID_REQUEST / FORMAT_INVALID; 409 SLOT_KEPT, SLOT_NOT_READY,
     * SLOT_FROM_BANK, VERSION_CONFLICT; đề của người khác 404.
     */
    @PutMapping("/{examId}/slots/{slotId}/question")
    public ExamItemDto editQuestion(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer slotId,
                                    @RequestBody(required = false) EditQuestionRequest request) {
        return examActionService.editQuestion(examId, slotId, user.id(), request);
    }

    /**
     * W1-3: vết QC của một chỗ: các lần sinh, phiên bản (LLM gốc / giáo viên sửa), từng bước QC, lý do cờ và lịch sử
     * thao tác. Chỉ đọc (không đối soát job). Đề của người khác hoặc chỗ không thuộc đề: 404.
     */
    @GetMapping("/{examId}/slots/{slotId}/qc-trace")
    public QcTraceDto qcTrace(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer slotId) {
        return examQcTraceService.trace(examId, slotId, user.id());
    }

    /** Các chỗ của một ô (tóm tắt). */
    @GetMapping("/{examId}/cells/{cellId}")
    public List<SlotDto> cellSlots(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer cellId) {
        return examBuildService.cellSlots(examId, cellId, user.id());
    }

    /** PA1: {@code includeDistractorNotes} trống là true; chỉ nhận true/false (không phân biệt hoa thường), khác thì 400. */
    static boolean parseIncludeDistractorNotes(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String v = value.strip();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("Tham số includeDistractorNotes chỉ nhận true hoặc false");
    }
}
