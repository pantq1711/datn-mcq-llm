-- V13__question_edit_version.sql
-- W1-2 (ngày 24/10): giáo viên sửa câu trong ô đề chưa Giữ, chạy lại QC phù hợp. Không sửa V1-V12.
--
-- * generated_question.edited_from_question_id: mỗi lần sửa tạo MỘT DÒNG MỚI (bản sửa), trỏ về bản ngay trước nó; bản
--   gốc LLM và kết quả QC của nó giữ nguyên. NULL = đầu ra LLM; khác NULL = bản giáo viên sửa (raw_llm_output = '').
--   Nội dung một bản không đổi sau khi tạo, nên kết quả QC (quality_check_result theo question_id) luôn gắn đúng bản.
--   Khóa ngoại kép theo (id, question_type): bản sửa cùng dạng câu với bản trước. UNIQUE: mỗi bản có tối đa một bản sửa
--   tiếp (chuỗi thẳng, chặn hai lần sửa đồng thời từ cùng một bản ở mức DB).
--   Mọi truy vấn thống kê về đầu ra LLM phải lọc edited_from_question_id IS NULL.
-- * generation_job.job_type: 'generate' (job sinh câu, mọi dòng cũ) hoặc 'answer_check' (chạy lại Solver cho một bản
--   sửa). Job answer_check bắt buộc có question_id và không gắn chỗ (exam_slot_id NULL), để đối soát chỗ không nhầm là
--   job sinh câu.
-- * exam_slot_action.changed_fields: các trường đã đổi của một lần sửa (mảng JSON tên trường, ví dụ ["question_text",
--   "correct_option"]); bắt buộc với dòng 'edit', NULL với dòng khác.

ALTER TABLE generated_question
    ADD COLUMN edited_from_question_id INT,
    ADD CONSTRAINT fk_gq_edited_from FOREIGN KEY (edited_from_question_id, question_type)
        REFERENCES generated_question (id, question_type),
    ADD CONSTRAINT uq_gq_edited_from UNIQUE (edited_from_question_id),
    ADD CONSTRAINT chk_gq_edited_from_not_self CHECK (edited_from_question_id IS NULL OR edited_from_question_id <> id);

COMMENT ON COLUMN generated_question.edited_from_question_id IS
    'W1-2: bản giáo viên sửa trỏ bản ngay trước; NULL = đầu ra LLM. Thống kê đầu ra LLM lọc IS NULL.';

ALTER TABLE generation_job
    ADD COLUMN job_type VARCHAR(20) NOT NULL DEFAULT 'generate',
    ADD CONSTRAINT chk_gj_job_type CHECK (job_type IN ('generate', 'answer_check')),
    ADD CONSTRAINT chk_gj_answer_check CHECK (
        job_type <> 'answer_check' OR (question_id IS NOT NULL AND exam_slot_id IS NULL));

CREATE INDEX idx_gj_question_type ON generation_job (question_id, job_type);

COMMENT ON COLUMN generation_job.job_type IS
    'W1-2: generate (sinh câu) hoặc answer_check (chạy lại Solver cho bản giáo viên sửa, có question_id, không gắn chỗ).';

ALTER TABLE exam_slot_action
    ADD COLUMN changed_fields JSONB,
    ADD CONSTRAINT chk_esa_changed_fields CHECK (
        (action = 'edit' AND changed_fields IS NOT NULL AND jsonb_typeof(changed_fields) = 'array')
        OR (action <> 'edit' AND changed_fields IS NULL));

COMMENT ON COLUMN exam_slot_action.changed_fields IS
    'W1-2: tên các trường đã đổi khi Sửa (mảng JSON); chỉ dòng edit.';
