/**
 * Học sinh làm đề trực tuyến qua link (docs/design/lam-bai-truc-tuyen.md, V32): giáo viên giao một đề đã chốt cho các lớp
 * kèm sĩ số; học sinh không đăng nhập, chọn lớp và nhập họ tên (chế độ mặc định từ quyết định 25/11, V36) hoặc số thứ tự
 * (bài giao cũ, hoặc giáo viên chọn), làm bài, nộp một lần; điểm, đáp án và lời giải chỉ trả cho học sinh sau khi bài đóng
 * (quyết định 16/11).
 *
 * <p>Bảng sở hữu: exam_assignment, assignment_class, assignment_item, assignment_attempt, attempt_answer.
 * <br>Được phép phụ thuộc: exam (chỉ {@code ExamAssignmentSource}, {@code ExamDeletionGuard}), bank (đọc câu; hình của
 * câu cũng qua {@code BankService}, D3 ngày 23/11: không phụ thuộc thẳng module questionimage), config (bảng điểm, hạn
 * mức). {@code exam} không biết {@code assignment}.
 *
 * <p>Cấu trúc: controller, dto, repository (JdbcTemplate, không entity JPA), service. API công khai nằm dưới
 * {@code /api/public/**}; API giáo viên dưới {@code /api/exams/{examId}/assignments}.
 */
package com.datn.mcq.assignment;
