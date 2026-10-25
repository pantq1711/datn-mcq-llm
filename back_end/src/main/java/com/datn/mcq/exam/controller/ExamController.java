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
import com.datn.mcq.exam.service.ExamActionService;
import com.datn.mcq.exam.service.ExamBuildService;
import com.datn.mcq.exam.service.ExamQcTraceService;
import com.datn.mcq.exam.service.ExamViewService;
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
     */
    @GetMapping
    public ExamPageDto list(CurrentUser user, @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size) {
        return examViewService.list(user.id(), page, size);
    }

    /**
     * Xuất đề ra Word (.docx, bản cơ bản): đề, rồi trang ĐÁP ÁN và LỜI GIẢI; công thức là Equation gốc của Word. Cần
     * pandoc trên máy chủ: không có thì 503. Tên file theo mã đề.
     */
    @GetMapping("/{examId}/export/docx")
    public ResponseEntity<byte[]> exportDocx(CurrentUser user, @PathVariable Integer examId) {
        DocxFile file = examExportService.docx(examId, user.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExamExportService.DOCX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
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
     * @param instruction yêu cầu bổ sung của giáo viên cho lượt sinh lại này (tùy chọn; trim, tối đa 500 ký tự, dài hơn
     *                    thì 400; trống thì như không có)
     */
    public record ReplaceRequest(String instruction) {}

    /** Đổi câu khác: sinh lại đúng ô qua hàng đợi, có thể kèm yêu cầu của giáo viên. Chỗ đang sinh thì 409. */
    @PostMapping("/{examId}/slots/{slotId}/replace")
    public ExamItemDto replace(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer slotId,
                               @RequestBody(required = false) ReplaceRequest request) {
        return examActionService.replace(examId, slotId, user.id(), request != null ? request.instruction() : null);
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
}
