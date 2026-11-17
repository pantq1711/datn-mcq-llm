package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.repository.AssignmentRepository;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.exam.service.ExamDeletionGuard;
import org.springframework.stereotype.Component;

/**
 * Quyết định 16/11 (AN-04): đề còn bài giao (mở hay đã đóng) thì không xóa được, 409 {@value #EXAM_HAS_ASSIGNMENTS}. Nếu
 * cho xóa mềm đề, mọi API giáo viên của bài giao trả 404 theo đề, nên link học sinh còn sống và giáo viên mất đường xóa
 * dữ liệu học sinh. Giáo viên xóa các bài đã giao trước (T6), rồi mới xóa đề.
 */
@Component
class AssignmentExamDeletionGuard implements ExamDeletionGuard {

    static final String EXAM_HAS_ASSIGNMENTS = "EXAM_HAS_ASSIGNMENTS";

    private final AssignmentRepository repository;

    AssignmentExamDeletionGuard(AssignmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void checkDeletable(Integer examId) {
        if (repository.existsForExam(examId)) {
            throw ApiErrorException.conflict(EXAM_HAS_ASSIGNMENTS,
                    "Đề đã được giao cho học sinh. Xóa các bài đã giao của đề này trước rồi mới xóa đề.");
        }
    }
}
