-- V16__slot_action_replace_source.sql
-- W1-4 bước 2 (ngày 25/10): Đổi bằng câu ngân hàng (D5-B). Không sửa V1-V15.
--
-- * exam_slot_action.replace_source: nguồn của câu thay thế khi Đổi: 'bank' (lấy câu ngân hàng khớp ô) hoặc 'generate'
--   (sinh mới qua hàng đợi). Bắt buộc với dòng 'replace', NULL với keep/edit. Trước D5-B Đổi luôn sinh mới nên mọi dòng
--   replace cũ được điền 'generate'. Thống kê "Đổi sang ngân hàng / sinh mới" đọc cột này (không suy ngược từ trạng thái
--   chỗ, vì chỗ còn bị thao tác tiếp).

ALTER TABLE exam_slot_action ADD COLUMN replace_source VARCHAR(10);

UPDATE exam_slot_action SET replace_source = 'generate' WHERE action = 'replace';

ALTER TABLE exam_slot_action
    ADD CONSTRAINT chk_esa_replace_source CHECK (
        (action = 'replace' AND replace_source IN ('bank', 'generate'))
        OR (action <> 'replace' AND replace_source IS NULL));

COMMENT ON COLUMN exam_slot_action.replace_source IS
    'Chỉ dòng replace: bank = Đổi bằng câu ngân hàng, generate = sinh mới (W1-4).';
