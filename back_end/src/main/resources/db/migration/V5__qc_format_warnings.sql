-- V5__qc_format_warnings.sql
-- Lưu cảnh báo không chặn của bước format (quyết định Q8 ngày 22/10), ví dụ "label: 4 ý cùng nhãn T",
-- "answer: phân số chưa tối giản", "statement_a: đã bỏ tiền tố "a)" ở đầu ý". Trước V5 các cảnh báo này chỉ có trong
-- response của generate-sync; luồng job làm mất chúng. Không sửa V1-V4.
--
-- Giữ dữ liệu cũ: cột mới cho phép NULL, dòng cũ có warnings = NULL. Chỉ dòng check_type = 'format' được có giá trị,
-- và giá trị phải là mảng JSON (mảng chuỗi). Không có cảnh báo thì để NULL.

ALTER TABLE quality_check_result
    ADD COLUMN warnings JSONB,
    ADD CONSTRAINT chk_qcr_warnings_format CHECK (
        warnings IS NULL OR (check_type = 'format' AND jsonb_typeof(warnings) = 'array'));

COMMENT ON COLUMN quality_check_result.warnings IS
    'Cảnh báo không chặn của bước format (mảng chuỗi); chỉ dòng check_type = format; NULL khi không có.';
