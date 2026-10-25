/**
 * Giáo viên chấm/duyệt: giao câu (label_assignment), nộp review, lý do từ chối. Là nơi áp dụng các ràng buộc DB không tự chặn được và quy tắc chấm mù.
 *
 * <p>Bảng sở hữu: label_assignment, teacher_review, teacher_review_reason, teacher_review_statement.
 * <br>Được phép phụ thuộc: generation, qc, bank, user, common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.review;
