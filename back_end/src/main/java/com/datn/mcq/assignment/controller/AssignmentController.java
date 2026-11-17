package com.datn.mcq.assignment.controller;

import com.datn.mcq.assignment.dto.AssignmentDtos.AssignmentDto;
import com.datn.mcq.assignment.dto.AssignmentDtos.CreateAssignmentRequest;
import com.datn.mcq.assignment.dto.AssignmentDtos.DeleteAttemptsRequest;
import com.datn.mcq.assignment.dto.AssignmentDtos.DeleteAttemptsResult;
import com.datn.mcq.assignment.dto.ItemStatDtos.ItemStatsDto;
import com.datn.mcq.assignment.service.AssignmentService;
import com.datn.mcq.assignment.service.ItemStatService;
import com.datn.mcq.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bài giao của giáo viên (docs/design/lam-bai-truc-tuyen.md mục 3.1). Vai trò TEACHER (SecurityConfig, nhóm
 * {@code /api/exams/**}); chỉ chủ đề, đề của người khác trả 404.
 */
@RestController
@RequestMapping("/api/exams/{examId}/assignments")
public class AssignmentController {

    private final AssignmentService service;
    private final ItemStatService itemStatService;

    public AssignmentController(AssignmentService service, ItemStatService itemStatService) {
        this.service = service;
        this.itemStatService = itemStatService;
    }

    /** T1: giao bài. 400 INVALID_REQUEST; 404; 409 ASSIGN_NOT_ALL_KEPT / ASSIGN_SA_KEY_UNSUPPORTED (kèm danh sách câu). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentDto create(CurrentUser user, @PathVariable Integer examId,
                                @RequestBody(required = false) CreateAssignmentRequest request) {
        return service.create(examId, user.id(), request);
    }

    /** T2: các bài đã giao của đề, mới nhất trước; không có bảng điểm. */
    @GetMapping
    public List<AssignmentDto> list(CurrentUser user, @PathVariable Integer examId) {
        return service.list(examId, user.id());
    }

    /** T3: một bài giao kèm bảng điểm (theo lớp rồi số thứ tự; lượt trùng có {@code duplicate = true}). */
    @GetMapping("/{assignmentId}")
    public AssignmentDto get(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer assignmentId) {
        return service.get(examId, assignmentId, user.id());
    }

    /** T4: đóng tay; đã đóng thì trả nguyên trạng. */
    @PostMapping("/{assignmentId}/close")
    public AssignmentDto close(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer assignmentId) {
        return service.close(examId, assignmentId, user.id());
    }

    /** T5: xóa một lượt, ở mọi trạng thái của bài giao (kể cả đã đóng). */
    @DeleteMapping("/{assignmentId}/attempts/{attemptId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttempt(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer assignmentId,
                              @PathVariable Integer attemptId) {
        service.deleteAttempt(examId, assignmentId, attemptId, user.id());
    }

    /** Xóa nhiều lượt một lần, ở mọi trạng thái: {@code {"attemptIds": [..]}} → {@code {"deleted": n}}. */
    @PostMapping("/{assignmentId}/attempts/bulk-delete")
    public DeleteAttemptsResult deleteAttempts(CurrentUser user, @PathVariable Integer examId,
                                               @PathVariable Integer assignmentId,
                                               @RequestBody(required = false) DeleteAttemptsRequest request) {
        return service.deleteAttempts(examId, assignmentId, request == null ? null : request.attemptIds(), user.id());
    }

    /**
     * B1 (PR B): phân tích câu (p, D, lượt chọn, cờ hậu kiểm). 409 ASSIGNMENT_OPEN khi bài còn mở; 409 STATS_NOT_READY khi đã
     * đóng nhưng còn ân hạn nộp 2 phút ({@code errors[0]} là lúc có thống kê).
     */
    @GetMapping("/{assignmentId}/item-stats")
    public ItemStatsDto itemStats(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer assignmentId) {
        return itemStatService.get(examId, assignmentId, user.id());
    }

    /** B2 (PR B): tính lại phân tích câu; lỗi như B1. */
    @PostMapping("/{assignmentId}/item-stats/recompute")
    public ItemStatsDto recomputeItemStats(CurrentUser user, @PathVariable Integer examId,
                                           @PathVariable Integer assignmentId) {
        return itemStatService.recompute(examId, assignmentId, user.id());
    }

    /** T6: xóa bài giao cùng mọi lượt làm và câu trả lời. */
    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser user, @PathVariable Integer examId, @PathVariable Integer assignmentId) {
        service.delete(examId, assignmentId, user.id());
    }
}
