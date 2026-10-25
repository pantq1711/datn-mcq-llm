/**
 * Tạo đề: đề gốc, mã đề, đề dự phòng, thứ tự câu và thứ tự phương án. Đề cuối chỉ lấy câu từ bank_item.
 *
 * <p>V7: tạo đề theo ma trận ở chế độ giáo viên (không chấm mù, quyết định 22/10): mỗi chỗ (exam_slot) lấy câu ngân
 * hàng khớp ô trước, phần thiếu sinh qua hàng đợi; xem đề có đáp án, lời giải, cờ "cần xem kỹ"; Giữ / Đổi câu khác ghi
 * exam_slot_action.
 *
 * <p>Bảng sở hữu: exam, exam_question, exam_slot, exam_slot_action.
 * <br>Được phép phụ thuộc: matrix, generation, qc, review, bank, catalog, user.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.exam;
