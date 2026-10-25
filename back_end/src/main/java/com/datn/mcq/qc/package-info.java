/**
 * Pipeline kiểm tra chất lượng: format (JSON + LaTeX) -> dedup (chuẩn hóa + cosine 3-gram ký tự, không embedding)
 * -> answer_check. Solver chỉ chạy khi dedup kết luận không trùng. Ghi kết quả từng bước.
 *
 * <p>Bảng sở hữu: quality_check_result, answer_check_statement, question_embedding.
 * <br>Được phép phụ thuộc: llm, common.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.qc;
