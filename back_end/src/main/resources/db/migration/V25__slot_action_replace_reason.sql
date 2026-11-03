-- V25__slot_action_replace_reason.sql
-- B7 tối thiểu (soát trước deploy, ngày 3/11): lý do giáo viên chọn khi Đổi câu. Không sửa V1-V24.
--
-- * exam_slot_action.replace_reason: lý do Đổi do giáo viên chọn (tùy chọn), chỉ dòng 'replace'; NULL khi giáo viên không
--   chọn lý do và với mọi dòng keep/edit và dòng replace trước V25. Giá trị: wrong_answer (đáp án sai), ambiguous_options
--   (phương án mơ hồ / nhiều đáp án), off_matrix (lệch ma trận: sai chủ đề hoặc mức), duplicate_pattern (trùng khuôn với
--   câu khác), latex_format (lỗi công thức / định dạng), too_hard (quá khó), too_easy (quá dễ), other (lý do khác).
--   API từ chối giá trị ngoài danh sách (400) trước khi ghi; CHECK dưới đây là chốt chặn cuối ở mức database.

ALTER TABLE exam_slot_action ADD COLUMN replace_reason VARCHAR(40);

ALTER TABLE exam_slot_action
    ADD CONSTRAINT chk_esa_replace_reason CHECK (
        replace_reason IS NULL
        OR (action = 'replace' AND replace_reason IN (
            'wrong_answer', 'ambiguous_options', 'off_matrix', 'duplicate_pattern', 'latex_format',
            'too_hard', 'too_easy', 'other')));

COMMENT ON COLUMN exam_slot_action.replace_reason IS
    'B7: lý do giáo viên chọn khi Đổi (chỉ dòng replace, tùy chọn): wrong_answer, ambiguous_options, off_matrix, duplicate_pattern, latex_format, too_hard, too_easy, other.';
