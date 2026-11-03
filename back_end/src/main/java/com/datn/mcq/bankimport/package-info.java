/**
 * Nhập ngân hàng câu từ Word (V26, quyết định ngày 3/11/2026, mô hình B1): giáo viên tải bộ câu hỏi .docx (Phần I trắc
 * nghiệm, Phần II đúng/sai, Phần III trả lời ngắn, đáp án tô vàng, công thức MathType), xem trước kèm cờ, chọn câu và xác
 * nhận; câu đã xác nhận vào ngân hàng RIÊNG TƯ của chính người tải. Lõi phân tích ({@code docx}, {@code parse},
 * {@code model}) chuyển từ tools/bank-import; công thức MathType dùng chung {@code com.datn.mcq.mtef}.
 *
 * <p>Bảng sở hữu: import_batch. Ghi thêm (chỉ khi xác nhận, một transaction): question_template, experiment_run (qua
 * GenerationService), generated_question ({@code origin = 'import'}), generated_question_statement, teacher_review,
 * teacher_review_statement, bank_item (qua BankService). Đọc chéo bank_item để so trùng (ImportDedupRepository).
 * <br>Được phép phụ thuộc (quyết định 3/11): bank, catalog, generation, review, material.extract, mtef, qc (checker/dto
 * thuần, không gọi mạng), common, config.
 *
 * <p>Không bao giờ: gọi LlmClient, xếp job Solver, trừ hạn mức sinh câu (D1), lưu file gốc hay tên file gốc, ghi nội
 * dung câu vào log. Bản đầu không nhập câu có ảnh, câu có công thức chưa chuyển được, câu có bảng dữ liệu.
 */
package com.datn.mcq.bankimport;
