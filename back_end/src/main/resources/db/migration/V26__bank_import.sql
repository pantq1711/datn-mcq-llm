-- V26__bank_import.sql
-- Nhập ngân hàng câu từ Word (ngày 3/11, mô hình B1): giáo viên tải bộ câu hỏi .docx, xem trước, xác nhận; câu đã xác nhận
-- vào ngân hàng RIÊNG TƯ của chính người tải theo chuỗi sẵn có question_template → experiment_run → generated_question →
-- teacher_review → bank_item. Không sửa V1-V25.
--
-- * import_batch: một lần tải lên. owner_id là người tải. display_name là tên đã làm sạch ("KHỐI 8 - CHƯƠNG 3 - BÀI 1 - tên
--   bài"); KHÔNG lưu tên file gốc và phần "GV …". file_sha256 nhận ra file. subject_id, grade_level, topic là môn, khối, chủ đề
--   của các câu khi xác nhận. status: preview (chờ xác nhận) → confirmed (đã xác nhận) hoặc expired (quá hạn 24 giờ,
--   expires_at); lô preview bị hủy thì xóa hẳn dòng. preview (JSONB) là bản xem trước: các câu đã phân tích kèm cờ và các
--   câu bị bỏ (chỉ vị trí và lý do); bị xóa (NULL) khi xác nhận hoặc hết hạn để không giữ lại nội dung bộ đề.
--   question_count là số câu đọc được trong file (kể cả câu bị bỏ); skipped_counts đếm câu bị bỏ theo lý do; imported_count
--   là số câu đã vào ngân hàng (chỉ khi confirmed).
-- * generated_question.origin: 'llm' (mọi dòng cũ: đầu ra LLM và bản giáo viên sửa) hoặc 'import' (câu nhập từ Word).
--   Câu nhập luôn có import_batch_id và ngược lại. MỌI thống kê đầu ra LLM và bộ đếm lượt gọi Gemini phải lọc
--   origin = 'llm' (cùng với edited_from_question_id IS NULL).

CREATE TABLE import_batch (
    id              SERIAL PRIMARY KEY,
    owner_id        INT NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    file_sha256     VARCHAR(64) NOT NULL,
    subject_id      INT NOT NULL,
    grade_level     INT NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'preview',
    question_count  INT NOT NULL,
    skipped_counts  JSONB NOT NULL DEFAULT '{}'::jsonb,
    imported_count  INT,
    preview         JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL,
    confirmed_at    TIMESTAMP,
    CONSTRAINT fk_ib_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_ib_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_ib_status CHECK (status IN ('preview', 'confirmed', 'expired')),
    CONSTRAINT chk_ib_grade CHECK (grade_level BETWEEN 6 AND 9),
    CONSTRAINT chk_ib_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_ib_topic CHECK (length(btrim(topic)) > 0),
    CONSTRAINT chk_ib_sha CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_ib_counts CHECK (question_count >= 0 AND (imported_count IS NULL OR imported_count >= 0)),
    CONSTRAINT chk_ib_skipped CHECK (jsonb_typeof(skipped_counts) = 'object'),
    CONSTRAINT chk_ib_preview CHECK ((status = 'preview') = (preview IS NOT NULL)),
    CONSTRAINT chk_ib_confirmed CHECK ((status = 'confirmed') = (confirmed_at IS NOT NULL AND imported_count IS NOT NULL)),
    CONSTRAINT chk_ib_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_ib_owner_status ON import_batch (owner_id, status);

ALTER TABLE generated_question
    ADD COLUMN origin VARCHAR(10) NOT NULL DEFAULT 'llm',
    ADD COLUMN import_batch_id INT,
    ADD CONSTRAINT chk_gq_origin CHECK (origin IN ('llm', 'import')),
    ADD CONSTRAINT fk_gq_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batch(id),
    ADD CONSTRAINT chk_gq_import_batch CHECK ((origin = 'import') = (import_batch_id IS NOT NULL)),
    -- câu nhập không phải đầu ra LLM: không có bản gốc LLM để sửa từ đó, không có mức LLM
    ADD CONSTRAINT chk_gq_import_not_llm CHECK (
        origin <> 'import' OR (edited_from_question_id IS NULL AND llm_difficulty IS NULL));

CREATE INDEX idx_gq_import_batch ON generated_question (import_batch_id) WHERE import_batch_id IS NOT NULL;

COMMENT ON TABLE import_batch IS
    'Nhập từ Word (3/11): một lần tải bộ câu hỏi .docx; preview là bản xem trước (NULL sau khi xác nhận hoặc hết hạn).';
COMMENT ON COLUMN import_batch.display_name IS
    'Tên đã làm sạch từ tên file (khối, chương, bài, tên bài); không lưu tên file gốc và phần "GV …".';
COMMENT ON COLUMN generated_question.origin IS
    'llm = đầu ra LLM hoặc bản giáo viên sửa; import = câu nhập từ Word. Thống kê đầu ra LLM lọc origin = ''llm''.';
