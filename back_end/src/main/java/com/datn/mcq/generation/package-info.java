/**
 * Sinh câu hỏi: hàng đợi job (retry, rate-limit, cộng dồn token/độ trễ/chi phí), dựng prompt, parse JSON 7 trường.
 *
 * <p>Bảng sở hữu: generation_job, generated_question.
 * <br>Được phép phụ thuộc: llm, qc, common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.generation;
