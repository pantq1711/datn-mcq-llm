/**
 * Tài liệu bài học đính kèm (V18, W2-3): giáo viên tải docx, pdf, txt; server trích văn bản và chỉ lưu văn bản cùng
 * sha256, không lưu file gốc. Văn bản chỉ được đưa vào prompt dưới dạng DỮ LIỆU, qua các đoạn được chọn.
 *
 * <p>Bảng sở hữu: lesson_material.
 * <br>Được phép phụ thuộc: common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto. Module khác chỉ gọi qua service,
 * không gọi repository của module này.
 */
package com.datn.mcq.material;
