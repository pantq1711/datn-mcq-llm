-- V6__exam_matrix.sql
-- Ma trận đề kiểm tra (luồng "tạo đề theo ma trận", thiết kế phần C đã duyệt ngày 22/10). Không sửa V1-V5.
--
-- Mỗi ô (exam_matrix_cell) là một nhóm câu cùng phần, dạng câu, chủ đề, nội dung và mức độ. Mỗi ô gắn một
-- question_template tạo tự động (source_question ghi chủ đề, nội dung và yêu cầu cần đạt), để dùng lại pipeline sinh câu,
-- phạm vi dedup và danh sách "câu đã sinh" theo template.
--
-- Cấu trúc đề Toán THCS: phần I là trắc nghiệm 4 phương án (mcq), phần II là Đúng/Sai (tf), phần III là Trả lời ngắn
-- (sa). Câu tf ghi mức theo từng ý (statement_difficulties, 4 giá trị theo thứ tự a-d), không có mức cấp câu.
-- Số lệnh hỏi không lưu riêng: mcq/sa mỗi câu một lệnh hỏi, tf mỗi câu 4 lệnh hỏi theo 4 mức của ý.

CREATE TABLE exam_matrix (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    subject_id      INT NOT NULL,
    grade_level     INT,
    source          TEXT,
    notes           JSONB,
    sample_key      VARCHAR(50),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_em_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_em_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_em_grade CHECK (grade_level IS NULL OR grade_level BETWEEN 6 AND 9),
    CONSTRAINT chk_em_notes CHECK (notes IS NULL OR jsonb_typeof(notes) = 'array')
);

CREATE TABLE exam_matrix_cell (
    id                      SERIAL PRIMARY KEY,
    matrix_id               INT NOT NULL,
    position                INT NOT NULL,
    part                    VARCHAR(3) NOT NULL,
    question_type           VARCHAR(10) NOT NULL,
    topic                   VARCHAR(255) NOT NULL,
    content                 TEXT,
    learning_outcome_easy   TEXT,
    learning_outcome_medium TEXT,
    learning_outcome_hard   TEXT,
    difficulty              VARCHAR(10),
    statement_difficulties  VARCHAR(40),
    question_count          INT NOT NULL,
    template_id             INT NOT NULL,
    CONSTRAINT fk_emc_matrix FOREIGN KEY (matrix_id) REFERENCES exam_matrix(id) ON DELETE CASCADE,
    CONSTRAINT fk_emc_template FOREIGN KEY (template_id) REFERENCES question_template(id),
    CONSTRAINT uq_emc_position UNIQUE (matrix_id, position),
    CONSTRAINT chk_emc_part_type CHECK (
        (part = 'I' AND question_type = 'mcq')
        OR (part = 'II' AND question_type = 'tf')
        OR (part = 'III' AND question_type = 'sa')),
    CONSTRAINT chk_emc_topic CHECK (length(btrim(topic)) > 0),
    CONSTRAINT chk_emc_count CHECK (question_count BETWEEN 1 AND 50),
    CONSTRAINT chk_emc_levels CHECK (
        (question_type = 'tf' AND difficulty IS NULL
            AND statement_difficulties ~ '^(easy|medium|hard)(,(easy|medium|hard)){3}$')
        OR (question_type IN ('mcq', 'sa') AND difficulty IN ('easy', 'medium', 'hard')
            AND statement_difficulties IS NULL))
);

CREATE INDEX idx_emc_matrix ON exam_matrix_cell (matrix_id);
