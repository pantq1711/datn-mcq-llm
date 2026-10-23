/**
 * Gold set cho dedup (RQ2a): cặp câu, nhãn của từng người, tách dev/test, tính ngưỡng và độ đồng thuận.
 *
 * <p>Bảng sở hữu: dedup_gold_pair, dedup_gold_label.
 * <br>Được phép phụ thuộc: qc, user.
 *
 * <p>Cấu trúc bên trong khi thêm code: entity, repository, service, controller, dto.
 * Controller chỉ gọi service của chính module; module khác chỉ gọi qua service, không gọi repository của nhau.
 */
package com.datn.mcq.goldset;
