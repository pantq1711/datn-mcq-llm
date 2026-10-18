/**
 * Lớp mỏng gọi dịch vụ ngoài: LlmClient (sinh câu hỏi, giải, chấm độ khó) và EmbeddingClient. Không chứa nghiệp vụ.
 *
 * <p>Bảng sở hữu: -.
 * <br>Được phép phụ thuộc: config.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.llm;
