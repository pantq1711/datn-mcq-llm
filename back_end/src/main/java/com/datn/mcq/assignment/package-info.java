/**
 * Học sinh làm đề trực tuyến qua link (docs/design/lam-bai-truc-tuyen.md, V32): giáo viên giao một đề đã chốt cho các lớp
 * kèm sĩ số; học sinh không đăng nhập, chọn lớp và nhập số thứ tự, làm bài, nộp một lần; điểm, đáp án và lời giải chỉ trả
 * cho học sinh sau khi bài đóng (quyết định 16/11).
 *
 * <p>Bảng sở hữu: exam_assignment, assignment_class, assignment_item, assignment_attempt, attempt_answer.
 * <br>Được phép phụ thuộc: exam (chỉ {@code ExamAssignmentSource}, {@code ExamDeletionGuard}), bank (đọc câu), config
 * (bảng điểm). {@code exam} không biết {@code assignment}.
 *
 * <p>Cấu trúc: controller, dto, repository (JdbcTemplate, không entity JPA), service. API công khai nằm dưới
 * {@code /api/public/**}; API giáo viên dưới {@code /api/exams/{examId}/assignments}.
 */
package com.datn.mcq.assignment;
