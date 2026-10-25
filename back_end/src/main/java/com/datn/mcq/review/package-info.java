/**
 * Giữ câu ở chế độ giáo viên: ghi teacher_review (review_mode = 'assisted') và chép câu vào ngân hàng trong cùng
 * transaction (AssistedKeepService). Luồng chấm mù và form chấm đã gỡ ngày 25/10/2026 (docs/ai-development-rules.md, v2).
 *
 * <p>Bảng sở hữu: teacher_review, teacher_review_statement. Bảng label_assignment và teacher_review_reason còn trong
 * DB (dữ liệu cũ, không có code ghi); entity LabelAssignment còn lại để chờ quyết định gỡ sau.
 * <br>Được phép phụ thuộc: generation, bank, catalog, experiment, user, common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.review;
