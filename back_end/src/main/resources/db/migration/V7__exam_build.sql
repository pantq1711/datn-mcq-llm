-- V7__exam_build.sql
-- Tạo đề theo ma trận một lần bấm (thiết kế phần C mục 2-4, duyệt ngày 22/10). Không sửa V1-V6.
--
-- * exam: đề tạo từ ma trận ở chế độ giáo viên (mode = 'teacher', không chấm mù). Đề cũ là 'manual'.
-- * exam_slot: mỗi chỗ trong đề ứng với một câu của một ô ma trận. Câu lấy từ ngân hàng (bank_item_id) hoặc do hệ thống
--   sinh (generated_question_id). Khi giáo viên "Giữ" câu sinh ra, câu được chép vào ngân hàng nên có cả hai cột.
-- * generation_job.exam_slot_id: job sinh câu cho một chỗ (job thường để NULL).
-- * teacher_review.review_mode: 'blind' (chấm mù, mọi dữ liệu cũ) hoặc 'assisted' (giáo viên đã thấy đáp án LLM ở chế độ
--   giáo viên). Đánh giá 'assisted' bị loại khỏi các chỉ số chấm mù (answer_correct, duyệt không sửa).
-- * exam_slot_action: nhật ký thao tác Giữ / Sửa / Đổi trên từng chỗ, để thống kê riêng.

ALTER TABLE exam
    ADD COLUMN matrix_id INT,
    ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'manual',
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT fk_exam_matrix FOREIGN KEY (matrix_id) REFERENCES exam_matrix(id),
    ADD CONSTRAINT chk_exam_mode CHECK (mode IN ('manual', 'teacher')),
    ADD CONSTRAINT chk_exam_teacher_matrix CHECK (mode <> 'teacher' OR matrix_id IS NOT NULL);

CREATE TABLE exam_slot (
    id                      SERIAL PRIMARY KEY,
    exam_id                 INT NOT NULL,
    cell_id                 INT NOT NULL,
    slot_no                 INT NOT NULL,
    question_type           VARCHAR(10) NOT NULL,
    run_id                  INT,
    state                   VARCHAR(20) NOT NULL DEFAULT 'pending',
    bank_item_id            INT,
    generated_question_id   INT,
    -- câu lệch Solver giữ lại để dùng (kèm cờ) nếu các lần sinh sau không ra câu dùng được
    fallback_question_id    INT,
    attempts                INT NOT NULL DEFAULT 0,
    solver_retries          INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_es_exam FOREIGN KEY (exam_id) REFERENCES exam(id) ON DELETE CASCADE,
    CONSTRAINT fk_es_cell FOREIGN KEY (cell_id) REFERENCES exam_matrix_cell(id),
    CONSTRAINT fk_es_run FOREIGN KEY (run_id) REFERENCES experiment_run(id),
    CONSTRAINT fk_es_bank_item FOREIGN KEY (bank_item_id) REFERENCES bank_item(id),
    CONSTRAINT fk_es_question FOREIGN KEY (generated_question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_es_fallback FOREIGN KEY (fallback_question_id) REFERENCES generated_question(id),
    CONSTRAINT uq_es_slot UNIQUE (exam_id, cell_id, slot_no),
    CONSTRAINT chk_es_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    CONSTRAINT chk_es_state CHECK (state IN ('pending', 'generating', 'ready', 'failed', 'kept', 'edited')),
    CONSTRAINT chk_es_ready CHECK (state NOT IN ('ready', 'kept', 'edited')
        OR bank_item_id IS NOT NULL OR generated_question_id IS NOT NULL),
    CONSTRAINT chk_es_kept CHECK (state NOT IN ('kept', 'edited') OR bank_item_id IS NOT NULL),
    CONSTRAINT chk_es_counters CHECK (attempts >= 0 AND solver_retries >= 0)
);

CREATE INDEX idx_es_exam ON exam_slot (exam_id);

ALTER TABLE generation_job
    ADD COLUMN exam_slot_id INT,
    ADD CONSTRAINT fk_gj_exam_slot FOREIGN KEY (exam_slot_id) REFERENCES exam_slot(id) ON DELETE SET NULL;

CREATE INDEX idx_gj_exam_slot ON generation_job (exam_slot_id);

ALTER TABLE teacher_review
    ADD COLUMN review_mode VARCHAR(10) NOT NULL DEFAULT 'blind',
    ADD CONSTRAINT chk_tr_review_mode CHECK (review_mode IN ('blind', 'assisted'));

CREATE TABLE exam_slot_action (
    id                      SERIAL PRIMARY KEY,
    slot_id                 INT NOT NULL,
    action                  VARCHAR(10) NOT NULL,
    generated_question_id   INT,
    bank_item_id            INT,
    edit_distance           INT,
    actor_id                INT,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_esa_slot FOREIGN KEY (slot_id) REFERENCES exam_slot(id) ON DELETE CASCADE,
    CONSTRAINT fk_esa_question FOREIGN KEY (generated_question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_esa_bank_item FOREIGN KEY (bank_item_id) REFERENCES bank_item(id),
    CONSTRAINT fk_esa_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_esa_action CHECK (action IN ('keep', 'edit', 'replace')),
    CONSTRAINT chk_esa_edit CHECK (action = 'edit' OR edit_distance IS NULL)
);
