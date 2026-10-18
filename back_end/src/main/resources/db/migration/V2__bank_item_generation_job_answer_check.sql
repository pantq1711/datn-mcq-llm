-- V2__bank_item_generation_job_answer_check.sql
-- Sửa các lỗ hổng phát hiện sau phản biện & cập nhật kết quả phỏng vấn giáo viên + Ma trận đề thật.
-- Nguyên tắc: Không sửa V1. Khi đổi enum phải cập nhật docs/contract/enums.md trong cùng commit.

-- 1. Phân biệt lần chạy thực nghiệm (experiment) và lần chạy dùng thật (production)
ALTER TABLE experiment_run
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'experiment',
    ADD CONSTRAINT chk_er_purpose CHECK (purpose IN ('experiment', 'production'));

-- 2. Bổ sung thông tin Khối, Chủ đề/Bài học, Yêu cầu cần đạt (từ Bản đặc tả) và Loại nguồn cho question_template
ALTER TABLE question_template
    ADD COLUMN grade_level INT,
    ADD COLUMN topic VARCHAR(255),
    ADD COLUMN learning_outcome TEXT,
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'text',
    ADD CONSTRAINT chk_qt_grade_level CHECK (grade_level IS NULL OR grade_level BETWEEN 6 AND 9),
    ADD CONSTRAINT chk_qt_source_type CHECK (source_type IN ('text', 'pdf', 'image'));

-- 3. Bổ sung cột lời giải thích (explanation) cho câu hỏi sinh ra và bản sửa của giáo viên
ALTER TABLE generated_question
    ADD COLUMN explanation TEXT;

ALTER TABLE teacher_review
    ADD COLUMN edited_explanation TEXT;

-- 4. Hàng đợi job sinh câu hỏi, lưu cả lần gọi thất bại và cộng dồn token, độ trễ, chi phí
-- Worker lấy job bằng SELECT ... FOR UPDATE SKIP LOCKED theo (status, next_run_at).
CREATE TABLE generation_job (
    id                  SERIAL PRIMARY KEY,
    run_id              INT NOT NULL,
    template_id         INT NOT NULL,
    question_id         INT,
    target_difficulty   VARCHAR(10),
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    attempt             INT NOT NULL DEFAULT 0,
    next_run_at         TIMESTAMP,
    last_error          TEXT,
    token_count         INT,
    latency_ms          FLOAT,
    cost_usd            FLOAT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gj_run FOREIGN KEY (run_id) REFERENCES experiment_run(id),
    CONSTRAINT fk_gj_template FOREIGN KEY (template_id) REFERENCES question_template(id),
    CONSTRAINT fk_gj_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT chk_gj_status CHECK (status IN ('pending', 'running', 'done', 'failed')),
    CONSTRAINT chk_gj_target_difficulty CHECK (
        target_difficulty IS NULL OR target_difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_gj_attempt CHECK (attempt >= 0),
    -- job xong thì phải có câu hỏi sinh ra
    CONSTRAINT chk_gj_done_has_question CHECK (status <> 'done' OR question_id IS NOT NULL)
);
CREATE INDEX idx_gj_poll ON generation_job (status, next_run_at);

-- 5. Bước kiểm tra đáp án bằng model giải độc lập (solver check - RQ2b)
ALTER TABLE quality_check_result
    DROP CONSTRAINT chk_qcr_check_type,
    ADD CONSTRAINT chk_qcr_check_type CHECK (check_type IN ('format', 'dedup', 'answer_check')),
    ADD COLUMN judge_answer VARCHAR(10),
    ADD COLUMN judge_model VARCHAR(100),
    ADD CONSTRAINT chk_qcr_judge_answer CHECK (judge_answer IN ('A', 'B', 'C', 'D', 'multiple', 'none')),
    -- Ràng buộc 2 chiều: answer_check bắt buộc có judge_answer & judge_model; các bước khác phải NULL
    ADD CONSTRAINT chk_qcr_judge_only_answer_check CHECK (
        (check_type = 'answer_check' AND judge_answer IS NOT NULL AND judge_model IS NOT NULL)
        OR (check_type <> 'answer_check' AND judge_answer IS NULL AND judge_model IS NULL));

-- 6. Cho phép lưu nhiều model embedding trên cùng 1 câu hỏi (phục vụ chọn cặp cross_template cho Gold Set)
UPDATE question_embedding SET embedding_model = 'default' WHERE embedding_model IS NULL;
ALTER TABLE question_embedding
    DROP CONSTRAINT question_embedding_pkey,
    ALTER COLUMN embedding_model SET NOT NULL,
    ADD PRIMARY KEY (question_id, embedding_model);

-- 7. Ngân hàng câu hỏi chuẩn (bank_item): lưu bản cuối cùng do giáo viên chốt (kèm explanation)
CREATE TABLE bank_item (
    id                  SERIAL PRIMARY KEY,
    source_question_id  INT NOT NULL,
    review_id           INT NOT NULL,
    question_text       TEXT NOT NULL,
    option_a            TEXT NOT NULL,
    option_b            TEXT NOT NULL,
    option_c            TEXT NOT NULL,
    option_d            TEXT NOT NULL,
    correct_option      CHAR(1) NOT NULL,
    explanation         TEXT,
    difficulty          VARCHAR(10) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bi_source FOREIGN KEY (source_question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_bi_review FOREIGN KEY (review_id) REFERENCES teacher_review(id),
    CONSTRAINT uq_bi_source UNIQUE (source_question_id),
    CONSTRAINT uq_bi_review UNIQUE (review_id),
    CONSTRAINT chk_bi_correct_option CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    CONSTRAINT chk_bi_difficulty CHECK (difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_bi_not_blank CHECK (
        length(btrim(question_text)) > 0
        AND length(btrim(option_a)) > 0 AND length(btrim(option_b)) > 0
        AND length(btrim(option_c)) > 0 AND length(btrim(option_d)) > 0)
);

-- Hỗ trợ Nhóm mã đề (1 đề gốc sinh nhiều mã đề chẵn/lẻ + đề dự phòng)
ALTER TABLE exam
    ADD COLUMN parent_exam_id INT,
    ADD COLUMN is_backup BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_exam_parent FOREIGN KEY (parent_exam_id) REFERENCES exam(id),
    ADD CONSTRAINT chk_exam_not_self_parent CHECK (parent_exam_id IS NULL OR parent_exam_id <> id);

-- Chuyển khóa ngoại của exam_question từ generated_question sang bank_item
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM exam_question) THEN
        RAISE EXCEPTION 'exam_question co du lieu, can viet buoc chuyen du lieu sang bank_item truoc khi chay V2';
    END IF;
END $$;

ALTER TABLE exam_question DROP CONSTRAINT fk_eq_question;
ALTER TABLE exam_question RENAME COLUMN question_id TO bank_item_id;
ALTER TABLE exam_question
    ADD CONSTRAINT fk_eq_bank_item FOREIGN KEY (bank_item_id) REFERENCES bank_item(id);

-- 8. Đổi cột cấu hình sang JSONB và thêm CHECK cho users.role
ALTER TABLE experiment_run
    ALTER COLUMN exemplar_ids TYPE JSONB
        USING CASE WHEN exemplar_ids IS NULL OR btrim(exemplar_ids) = '' THEN NULL ELSE exemplar_ids::jsonb END,
    ALTER COLUMN hyperparams TYPE JSONB
        USING CASE WHEN hyperparams IS NULL OR btrim(hyperparams) = '' THEN NULL ELSE hyperparams::jsonb END;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('teacher', 'researcher'));

-- 9. [TÙY CHỌN KHI CHỐT MODEL EMBEDDING]: Đổi BYTEA sang vector(N) của pgvector (ví dụ N = 768 cho Gemini)
-- CREATE EXTENSION IF NOT EXISTS vector;
-- ALTER TABLE question_embedding
--     ALTER COLUMN embedding TYPE vector(768) USING NULL;