/**
 * Người dùng và vai trò (teacher, researcher). Chỗ cắm xác thực sau này; hiện chỉ cần biết 'người đang thao tác là ai'.
 *
 * <p>Bảng sở hữu: users.
 * <br>Được phép phụ thuộc: common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.user;
