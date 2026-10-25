-- V8__qc_format_errors_job_prompt_note.sql
-- Ngày 23/10, sau lần chạy thật exam 1. Không sửa V1-V7.
--
-- 1. quality_check_result.errors: lý do bước format không qua (mảng chuỗi), để truy được vì sao câu format_failed mà
--    không phải đọc raw_llm_output. Chỉ dòng check_type = 'format' có passed = FALSE; NULL khi passed. Lỗi giải mã JSON
--    (trước FormatChecker) cũng nằm ở đây, dạng "Không giải mã được JSON hợp lệ từ phản hồi LLM: <chi tiết>".
-- 2. generation_job.prompt_note: ghi chú nơi tạo job muốn thêm vào prompt (job sinh câu theo ô ma trận: "Đây là câu thứ
--    k/n của ô này..." và lý do lần sinh trước bị loại). NULL với job thường.

ALTER TABLE quality_check_result
    ADD COLUMN errors JSONB,
    ADD CONSTRAINT chk_qcr_errors_format CHECK (
        errors IS NULL OR (check_type = 'format' AND passed = FALSE AND jsonb_typeof(errors) = 'array'));

COMMENT ON COLUMN quality_check_result.errors IS
    'Lý do bước format không qua (mảng chuỗi, gồm lỗi giải mã JSON); chỉ dòng format có passed = FALSE.';

ALTER TABLE generation_job
    ADD COLUMN prompt_note TEXT;

COMMENT ON COLUMN generation_job.prompt_note IS
    'Ghi chú thêm vào prompt của job (ô ma trận: câu thứ k/n, lý do lần sinh trước bị loại); NULL với job thường.';
