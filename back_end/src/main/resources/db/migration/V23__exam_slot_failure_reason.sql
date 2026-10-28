-- V23 (W2-4b): Lý do chỗ trong đề bị hỏng (exam_slot.state = 'failed'), tách lỗi kỹ thuật của job sinh câu
-- ('llm_error') khỏi hết số lần thử vì không đạt định dạng / trùng lặp ('qc_exhausted').

ALTER TABLE exam_slot
    ADD COLUMN failure_reason VARCHAR(20);

UPDATE exam_slot
SET failure_reason = 'qc_exhausted'
WHERE state = 'failed'
  AND failure_reason IS NULL;

ALTER TABLE exam_slot
    ADD CONSTRAINT chk_es_failure_reason
        CHECK ((state = 'failed' AND failure_reason IN ('llm_error', 'qc_exhausted'))
            OR (state <> 'failed' AND failure_reason IS NULL));

