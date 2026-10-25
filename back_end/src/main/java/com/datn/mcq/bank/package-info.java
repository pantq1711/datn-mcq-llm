/**
 * Ngân hàng câu hỏi: bản chốt cuối cùng do giáo viên duyệt thật (production).
 *
 * <p>Bảng sở hữu: bank_item, bank_item_statement.
 * <br>Được phép phụ thuộc: common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.bank;
