package com.datn.mcq.exam.controller;

import com.datn.mcq.exam.dto.ExamBuildDtos.BuildExamRequest;
import com.datn.mcq.exam.dto.ExamBuildDtos.BuildResultDto;
import com.datn.mcq.exam.dto.ExamBuildDtos.ProgressDto;
import com.datn.mcq.exam.dto.ExamBuildDtos.SlotDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamItemDto;
import com.datn.mcq.exam.dto.ExamViewDtos.ExamViewDto;
import com.datn.mcq.exam.export.ExamExportService;
import com.datn.mcq.exam.export.ExamExportService.DocxFile;
import com.datn.mcq.exam.service.ExamActionService;
import com.datn.mcq.exam.service.ExamBuildService;
import com.datn.mcq.exam.service.ExamViewService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Đề tạo theo ma trận ở chế độ giáo viên (V7). Đây là API production, KHÔNG phải form chấm mù (thay đổi so với quyết
 * định 21/10 và V1, xem docs/ai-development-rules.md).
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamBuildService examBuildService;
    private final ExamViewService examViewService;
    private final ExamActionService examActionService;
    private final ExamExportService examExportService;

    public ExamController(ExamBuildService examBuildService, ExamViewService examViewService,
                          ExamActionService examActionService, ExamExportService examExportService) {
        this.examBuildService = examBuildService;
        this.examViewService = examViewService;
        this.examActionService = examActionService;
        this.examExportService = examExportService;
    }

    /**
     * Xuất đề ra Word (.docx, bản cơ bản): đề, rồi trang ĐÁP ÁN và LỜI GIẢI; công thức là Equation gốc của Word. Cần
     * pandoc trên máy chủ: không có thì 503. Tên file theo mã đề.
     */
    @GetMapping("/{examId}/export/docx")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Integer examId) {
        DocxFile file = examExportService.docx(examId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExamExportService.DOCX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    /** Xem đề (chế độ giáo viên): câu theo phần I-III kèm đáp án, lời giải và cờ "cần xem kỹ". */
    @GetMapping("/{examId}")
    public ExamViewDto view(@PathVariable Integer examId) {
        return examViewService.view(examId);
    }

    /** Tạo đề một lần bấm: lấy câu ngân hàng khớp từng ô trước, phần thiếu xếp hàng sinh câu. */
    @PostMapping("/build")
    public BuildResultDto build(@RequestBody BuildExamRequest request) {
        return examBuildService.build(request);
    }

    /** Tiến độ theo ô. Mỗi lần gọi đối soát kết quả các job đã xong (thử lại khi cần). */
    @GetMapping("/{examId}/progress")
    public ProgressDto progress(@PathVariable Integer examId) {
        return examBuildService.progress(examId);
    }

    /** Body tùy chọn của thao tác trên câu; bỏ trống teacherId thì dùng giáo viên mặc định. */
    public record SlotActionRequest(Integer teacherId) {}

    /** Giữ câu: câu hệ thống sinh được chép vào ngân hàng (teacher_review 'assisted'). Đã Giữ rồi thì 409. */
    @PostMapping("/{examId}/slots/{slotId}/keep")
    public ExamItemDto keep(@PathVariable Integer examId, @PathVariable Integer slotId,
                            @RequestBody(required = false) SlotActionRequest request) {
        return examActionService.keep(examId, slotId, request != null ? request.teacherId() : null);
    }

    /**
     * Body tùy chọn của Đổi câu.
     *
     * @param instruction yêu cầu bổ sung của giáo viên cho lượt sinh lại này (tùy chọn; trim, tối đa 500 ký tự, dài hơn
     *                    thì 400; trống thì như không có)
     */
    public record ReplaceRequest(Integer teacherId, String instruction) {}

    /** Đổi câu khác: sinh lại đúng ô qua hàng đợi, có thể kèm yêu cầu của giáo viên. Chỗ đang sinh thì 409. */
    @PostMapping("/{examId}/slots/{slotId}/replace")
    public ExamItemDto replace(@PathVariable Integer examId, @PathVariable Integer slotId,
                               @RequestBody(required = false) ReplaceRequest request) {
        return examActionService.replace(examId, slotId, request != null ? request.teacherId() : null,
                request != null ? request.instruction() : null);
    }

    /** Các chỗ của một ô (tóm tắt). */
    @GetMapping("/{examId}/cells/{cellId}")
    public List<SlotDto> cellSlots(@PathVariable Integer examId, @PathVariable Integer cellId) {
        return examBuildService.cellSlots(examId, cellId);
    }
}
