/**
 * Tạo đề: đề gốc, mã đề, đề dự phòng, thứ tự câu và thứ tự phương án. Chỉ lấy câu từ bank_item.
 *
 * <p>Bảng sở hữu: exam, exam_question.
 * <br>Được phép phụ thuộc: bank, catalog, user.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.exam;
