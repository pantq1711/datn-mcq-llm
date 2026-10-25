/**
 * Ma trận đề kiểm tra (V6): ma trận và các ô (phần, dạng câu, chủ đề, nội dung, yêu cầu cần đạt theo mức, mức độ, số câu).
 * Mỗi ô tạo một question_template để dùng lại pipeline sinh câu. Có mẫu "Giữa kì I – Toán 8" (docs/sample).
 *
 * <p>Bảng sở hữu: exam_matrix, exam_matrix_cell.
 * <br>Được phép phụ thuộc: catalog, common.
 *
 * <p>Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.matrix;
