-- V14__qc_trace_solver_notes.sql
-- W1-3 (ngày 24/10): vết QC theo ô đề. Không sửa V1-V13.
--
-- * quality_check_result.solver_reasoning: lời giải thích của Solver (chỉ dòng answer_check), để vết QC cho giáo viên
--   thấy vì sao Solver lệch. Cắt ở 4000 ký tự khi ghi (CHECK chặn quá dài). NULL với dòng cũ và dòng không phải
--   answer_check. API xem đề không trả cột này.
-- * generated_question.solver_error: lý do Solver LỖI với câu sinh từ job (mạng, hết thời gian chờ, phản hồi hỏng...),
--   để phân biệt "chưa chạy" (không có dòng answer_check, solver_error NULL), "lỗi" (không có dòng answer_check,
--   solver_error có giá trị) và "lệch" (có dòng answer_check, passed = FALSE). Lỗi không tạo dòng answer_check vì
--   chk_qcr_judge_only_answer_check bắt buộc kết quả Solver hợp lệ. Cắt ở 1000 ký tự. Với bản giáo viên sửa, lý do
--   thất bại nằm ở generation_job.last_error của job answer_check (V13).

ALTER TABLE quality_check_result
    ADD COLUMN solver_reasoning TEXT,
    ADD CONSTRAINT chk_qcr_solver_reasoning CHECK (
        solver_reasoning IS NULL OR (check_type = 'answer_check' AND char_length(solver_reasoning) <= 4000));

COMMENT ON COLUMN quality_check_result.solver_reasoning IS
    'W1-3: lời giải thích của Solver (chỉ dòng answer_check, tối đa 4000 ký tự); NULL với dòng cũ.';

ALTER TABLE generated_question
    ADD COLUMN solver_error TEXT,
    ADD CONSTRAINT chk_gq_solver_error CHECK (solver_error IS NULL OR char_length(solver_error) <= 1000);

COMMENT ON COLUMN generated_question.solver_error IS
    'W1-3: lý do Solver lỗi với câu sinh từ job (không có dòng answer_check); NULL khi không lỗi hoặc chưa rõ.';
