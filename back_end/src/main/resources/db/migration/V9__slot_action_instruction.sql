-- V9__slot_action_instruction.sql
-- Ngày 23/10 (đợt 3). Không sửa V1-V8.
--
-- exam_slot_action.instruction: yêu cầu bổ sung của giáo viên khi Đổi câu (đã trim, 1-500 ký tự), chỉ cho dòng
-- 'replace'. Yêu cầu chỉ dùng cho lượt sinh lại đó (ghép vào generation_job.prompt_note của job sinh lại), không thành
-- ghi chú cố định của ô. NULL khi không có yêu cầu và với mọi dòng cũ.

ALTER TABLE exam_slot_action
    ADD COLUMN instruction TEXT,
    ADD CONSTRAINT chk_esa_instruction CHECK (
        instruction IS NULL OR (action = 'replace' AND char_length(instruction) BETWEEN 1 AND 500));

COMMENT ON COLUMN exam_slot_action.instruction IS
    'Yêu cầu bổ sung của giáo viên khi Đổi câu (1-500 ký tự, chỉ action = replace); NULL khi không có.';
