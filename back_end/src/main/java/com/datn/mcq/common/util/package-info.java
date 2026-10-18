/**
 * Tiện ích thuần (không gọi DB): chuẩn hóa văn bản, tính Levenshtein cho edit_distance, v.v.
 *
 * <p>Bảng sở hữu: -.
 * <br>Được phép phụ thuộc: không phụ thuộc module nào.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.common.util;
