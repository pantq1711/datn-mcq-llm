/**
 * Pipeline kiểm tra chất lượng: format (JSON + LaTeX) -> dedup (pgvector) -> answer_check và độ khó. Ghi kết quả từng bước.
 *
 * <p>Bảng sở hữu: quality_check_result, question_embedding.
 * <br>Được phép phụ thuộc: llm, common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.qc;
