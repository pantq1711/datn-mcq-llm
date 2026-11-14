-- V30__question_image.sql
-- Bảng question_image lưu trữ ảnh câu hỏi (thiết kế G0, PR #61, duyệt 11/11/2026).
-- Ảnh được lưu dạng BYTEA STORAGE EXTERNAL trong database, phục vụ xem trước và lưu trữ chính thức.
-- Không sửa V1-V29.

CREATE TABLE question_image (
    id                      BIGSERIAL PRIMARY KEY,
    owner_id                INT NOT NULL,
    import_batch_id         INT NOT NULL,
    item_key                VARCHAR(20) NOT NULL,
    generated_question_id   INT,
    role                    VARCHAR(12) NOT NULL,
    ordinal                 SMALLINT NOT NULL,
    mime                    VARCHAR(10) NOT NULL,
    data                    BYTEA NOT NULL,
    byte_size               INT NOT NULL,
    width_px                INT NOT NULL,
    height_px               INT NOT NULL,
    display_width_mm        SMALLINT NOT NULL,
    sha256                  CHAR(64) NOT NULL,
    source_format           VARCHAR(5) NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qi_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_qi_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_qi_generated_question FOREIGN KEY (generated_question_id) REFERENCES generated_question(id) ON DELETE CASCADE,
    CONSTRAINT uq_qi_batch_item_ordinal UNIQUE (import_batch_id, item_key, ordinal),
    CONSTRAINT chk_qi_role CHECK (role IN ('stem', 'option_a', 'option_b', 'option_c', 'option_d',
                                           'statement_a', 'statement_b', 'statement_c', 'statement_d', 'solution')),
    CONSTRAINT chk_qi_mime CHECK (mime IN ('image/png', 'image/jpeg')),
    CONSTRAINT chk_qi_source_format CHECK (source_format IN ('png', 'jpeg', 'gif', 'bmp', 'emf')),
    CONSTRAINT chk_qi_ordinal CHECK (ordinal >= 1),
    CONSTRAINT chk_qi_byte_size CHECK (byte_size > 0 AND byte_size <= 512000),
    CONSTRAINT chk_qi_dimensions CHECK (width_px > 0 AND height_px > 0 AND GREATEST(width_px, height_px) <= 1600),
    CONSTRAINT chk_qi_display_width CHECK (display_width_mm BETWEEN 5 AND 120),
    CONSTRAINT chk_qi_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

ALTER TABLE question_image ALTER COLUMN data SET STORAGE EXTERNAL;

CREATE INDEX idx_qi_generated_question ON question_image (generated_question_id) WHERE generated_question_id IS NOT NULL;
CREATE INDEX idx_qi_batch_item ON question_image (import_batch_id, item_key);

COMMENT ON TABLE question_image IS
    'Lưu trữ ảnh của câu hỏi nhập từ Word (chuẩn hóa PNG/JPEG, byte gốc sha256)';
COMMENT ON COLUMN question_image.display_width_mm IS
    'Bề rộng hiển thị trong Word gốc (5..120 mm), dùng để căn chỉnh và xuất Word';
COMMENT ON COLUMN question_image.sha256 IS
    'SHA-256 của byte gốc trong docx, dùng cho dedup và ETag';
