/**
 * Ngoại lệ dùng chung và bộ xử lý lỗi toàn cục (@RestControllerAdvice), trả lỗi theo một định dạng thống nhất.
 *
 * <p>Bảng sở hữu: -.
 * <br>Được phép phụ thuộc: không phụ thuộc module nào.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.common.exception;
