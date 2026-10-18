/**
 * Lần chạy sinh câu hỏi (experiment_run): chiến lược, model, prompt, tham số; tạo run và xếp job vào hàng đợi.
 *
 * <p>Bảng sở hữu: experiment_run.
 * <br>Được phép phụ thuộc: catalog, generation.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.experiment;
