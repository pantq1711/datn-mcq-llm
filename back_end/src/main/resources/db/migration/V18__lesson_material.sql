-- V18__lesson_material.sql
-- W2-3 bước 1 (ngày 25/10): tài liệu bài học giáo viên đính kèm để câu hỏi bám nội dung đã dạy. Không sửa V1-V17.
--
-- * lesson_material: một tài liệu (docx, pdf, txt) của một giáo viên. CHỈ lưu văn bản đã trích (extracted_text) cùng
--   sha256 của file gốc; KHÔNG lưu file gốc (quyết định C: bản quyền, dung lượng). Loại file (mime_type) do server nhận
--   dạng từ nội dung, không lấy từ Content-Type hay đuôi file client gửi; file_name chỉ để hiển thị.
-- * extract_method gắn với loại file: docx -> pandoc, pdf -> pdfbox, txt -> plain. page_count chỉ với pdf.
-- * warnings: mảng JSON các cảnh báo lúc trích (ví dụ công thức MathType không trích được), NULL khi không có.
-- * Giới hạn ở DB là trần an toàn (10 MiB, 1.000.000 ký tự); giới hạn nghiệp vụ chặt hơn nằm ở cấu hình ứng dụng
--   (app.materials.*). char_count = char_length(extracted_text) để số ký tự khai báo luôn đúng.
-- * Xóa giáo viên xóa luôn tài liệu của người đó (tài liệu là dữ liệu riêng tư). UNIQUE (owner_id, sha256): tải lại
--   cùng một file thì dùng lại bản cũ.

CREATE TABLE lesson_material (
    id              SERIAL PRIMARY KEY,
    owner_id        INT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    sha256          VARCHAR(64) NOT NULL,
    extract_method  VARCHAR(10) NOT NULL,
    page_count      INT,
    extracted_text  TEXT NOT NULL,
    char_count      INT NOT NULL,
    warnings        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lm_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_lm_owner_sha UNIQUE (owner_id, sha256),
    CONSTRAINT chk_lm_name CHECK (length(btrim(file_name)) > 0),
    CONSTRAINT chk_lm_mime CHECK (mime_type IN (
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/pdf',
        'text/plain')),
    CONSTRAINT chk_lm_size CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT chk_lm_sha CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_lm_method CHECK (extract_method IN ('pandoc', 'pdfbox', 'plain')),
    CONSTRAINT chk_lm_method_mime CHECK (
        (mime_type = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
            AND extract_method = 'pandoc')
        OR (mime_type = 'application/pdf' AND extract_method = 'pdfbox')
        OR (mime_type = 'text/plain' AND extract_method = 'plain')),
    CONSTRAINT chk_lm_pages CHECK (
        page_count IS NULL OR (mime_type = 'application/pdf' AND page_count > 0)),
    CONSTRAINT chk_lm_chars CHECK (
        char_count = char_length(extracted_text) AND char_count BETWEEN 1 AND 1000000),
    CONSTRAINT chk_lm_warnings CHECK (warnings IS NULL OR jsonb_typeof(warnings) = 'array')
);

CREATE INDEX idx_lm_owner ON lesson_material (owner_id, id DESC);

COMMENT ON TABLE lesson_material IS
    'W2-3: tài liệu bài học (docx/pdf/txt) của giáo viên; chỉ lưu văn bản đã trích và sha256, không lưu file gốc.';
COMMENT ON COLUMN lesson_material.mime_type IS
    'Loại file do server nhận dạng từ nội dung (magic bytes), không tin Content-Type hay đuôi file.';
COMMENT ON COLUMN lesson_material.warnings IS
    'Mảng JSON cảnh báo lúc trích văn bản (ví dụ công thức MathType bị bỏ); NULL khi không có.';
