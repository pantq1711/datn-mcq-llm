
CREATE TABLE subject (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(255),
    email   VARCHAR(255),
    role    VARCHAR(50)
);

CREATE TABLE question_template (
    id                  SERIAL PRIMARY KEY,
    subject_id          INT NOT NULL,
    source_question     TEXT NOT NULL,
    version             INT,
    is_active           BOOLEAN,
    CONSTRAINT fk_qt_subject FOREIGN KEY (subject_id) REFERENCES subject(id)
);

CREATE TABLE experiment_run (
    id                          SERIAL PRIMARY KEY,
    subject_id                  INT NOT NULL,
    strategy                    VARCHAR(50) NOT NULL,
    model_name                  VARCHAR(100) NOT NULL,
    temperature                 FLOAT,
    prompt_version              VARCHAR(50),
    prompt_text                 TEXT NOT NULL,
    exemplar_ids                TEXT,
    hyperparams                 TEXT,
    num_questions_requested     INT,
    status                      VARCHAR(50),
    started_at                  TIMESTAMP,
    ended_at                    TIMESTAMP,
    CONSTRAINT fk_er_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_er_strategy CHECK (strategy IN ('zero-shot', 'few-shot', 'few-shot-structured'))
);

-- Vòng đời status (ĐỀ XUẤT, hai bạn xác nhận):
--   generated     : vừa sinh và parse xong, chưa qua QC
--   format_failed : không đạt kiểm tra định dạng
--   duplicate     : bị bước dedup loại
--   qc_passed     : qua QC tự động, chờ giáo viên duyệt
--   rejected      : giáo viên từ chối (luồng duyệt thật)
--   in_bank       : đã duyệt, vào ngân hàng câu hỏi
-- Quy tắc: chỉ review review_purpose = 'production' mới đổi status.
-- Gán nhãn thực nghiệm (review_purpose = 'experiment') KHÔNG đổi status.
-- Khi review 'production' duyệt (as_is hoặc with_edit) và status -> 'in_bank', service ghi:
--   final_option -> correct_option; bản sửa (nếu có) -> question_text/option_*;
--   difficulty_label -> final_difficulty.
-- Bản gốc LLM vẫn nằm trong raw_llm_output. llm_difficulty không bị ghi đè.
CREATE TABLE generated_question (
    id                          SERIAL PRIMARY KEY,
    template_id                 INT NOT NULL,
    run_id                      INT NOT NULL,
    question_text               TEXT,
    option_a                    TEXT,
    option_b                    TEXT,
    option_c                    TEXT,
    option_d                    TEXT,
    correct_option              CHAR(1),
    raw_llm_output              TEXT NOT NULL,
    -- Do bước đánh giá độ khó riêng điền, KHÔNG nằm trong JSON sinh câu hỏi.
    -- NULL nếu câu dừng sớm (fail format hoặc trùng) và chưa chạy đánh giá độ khó.
    llm_difficulty              VARCHAR(10),
    difficulty_judge_model      VARCHAR(100),
    difficulty_prompt_version   VARCHAR(50),
    -- Độ khó cuối cùng khi câu vào ngân hàng: service copy từ difficulty_label của review 'production'.
    final_difficulty            VARCHAR(10),
    parse_success               BOOLEAN,
    token_count                 INT,
    latency_ms                  FLOAT,
    cost_usd                    FLOAT,
    status                      VARCHAR(50) NOT NULL DEFAULT 'generated',
    created_at                  TIMESTAMP,
    CONSTRAINT fk_gq_template FOREIGN KEY (template_id) REFERENCES question_template(id),
    CONSTRAINT fk_gq_run FOREIGN KEY (run_id) REFERENCES experiment_run(id),
    CONSTRAINT chk_gq_correct_option CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    CONSTRAINT chk_gq_difficulty CHECK (llm_difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_gq_final_difficulty CHECK (final_difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_gq_status CHECK (status IN
        ('generated', 'format_failed', 'duplicate', 'qc_passed', 'rejected', 'in_bank')),
    -- câu trong ngân hàng phải có đáp án và độ khó cuối cùng
    CONSTRAINT chk_gq_in_bank_complete CHECK (
        status <> 'in_bank' OR (final_difficulty IS NOT NULL AND correct_option IS NOT NULL))
);

CREATE TABLE question_embedding (
    question_id         INT PRIMARY KEY,
    embedding_model     VARCHAR(100),
    -- BYTEA chỉ để import ERD. Khi viết migration thật:
    --   CREATE EXTENSION IF NOT EXISTS vector;
    --   embedding vector(N)  -- N phải khớp số chiều của embedding_model
    embedding           BYTEA,
    CONSTRAINT fk_qe_question FOREIGN KEY (question_id) REFERENCES generated_question(id)
);

-- Chỉ ghi kết quả của 2 bước QC tự động. Độ khó ghi ở generated_question.llm_difficulty.
CREATE TABLE quality_check_result (
    id                          SERIAL PRIMARY KEY,
    question_id                 INT NOT NULL,
    check_type                  VARCHAR(50) NOT NULL,
    passed                      BOOLEAN NOT NULL,
    similarity_score            FLOAT,
    compared_to_question_id     INT,
    threshold_used              FLOAT,
    checked_at                  TIMESTAMP,
    CONSTRAINT fk_qcr_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_qcr_compared FOREIGN KEY (compared_to_question_id) REFERENCES generated_question(id),
    CONSTRAINT chk_qcr_check_type CHECK (check_type IN ('format', 'dedup')),
    -- chỉ bước dedup mới có câu để so sánh
    CONSTRAINT chk_qcr_compared CHECK (check_type = 'dedup' OR compared_to_question_id IS NULL)
);

-- Giao câu cho người chấm trong thực nghiệm.
-- Có dòng assignment nhưng chưa có teacher_review tương ứng = chưa chấm.
-- Chỉ rút mẫu từ câu có parse_success = TRUE (câu hỏng định dạng không hiển thị được).
CREATE TABLE label_assignment (
    question_id         INT NOT NULL,
    reviewer_id         INT NOT NULL,
    is_shared           BOOLEAN NOT NULL,
    assigned_at         TIMESTAMP,
    PRIMARY KEY (question_id, reviewer_id),
    CONSTRAINT fk_la_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_la_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

-- Một dòng = một lần nộp đánh giá đầy đủ (không lưu bản nháp ở bảng này).
-- QUY TẮC CHUNG (thực nghiệm và duyệt thật):
--   * API không trả correct_option (đáp án LLM) về form ở bất kỳ bước nào, kể cả trong JSON ẩn.
--   * Không báo giáo viên biết đáp án họ chọn trùng hay lệch đáp án LLM, trước hay sau khi nộp.
--   * Server lưu đúng những gì giáo viên nộp, tự tính answer_correct.
--   * Riêng review_purpose = 'experiment': form còn giấu độ khó LLM, kết quả QC và chiến lược.
-- HAI CHỈ SỐ TÁCH RIÊNG khi phân tích RQ1:
--   * Tỷ lệ duyệt không sửa = tỷ lệ decision = 'approved_as_is' (đúng theo đề cương)
--   * Độ chính xác đáp án LLM = tỷ lệ answer_correct = TRUE
-- reviewer_answer: A-D là đáp án giáo viên chọn; 'multiple' = hơn 1 đáp án đúng; 'none' = không có đáp án đúng.
--   Nhãn này là đánh giá cho BẢN GỐC của câu, điền trước khi sửa.
-- answer_correct: hệ thống tự tính (reviewer_answer trùng correct_option của LLM thì TRUE).
-- final_option: đáp án đúng cuối cùng khi duyệt. Bắt buộc khi approved_*, phải NULL khi rejected.
--   Với approved_as_is bằng reviewer_answer. Với approved_with_edit giáo viên chọn (nhất là khi
--   reviewer_answer là 'multiple' hoặc 'none').
-- edit_distance: hệ thống tự tính từ văn bản gốc và văn bản đã sửa (tổng Levenshtein trên 5 trường,
--   đã trim khoảng trắng và chuẩn hóa xuống dòng). Đổi đáp án đúng không tính vào edit_distance.
-- Lý do từ chối lưu ở teacher_review_reason. Service kiểm tra: rejected thì có ít nhất 1 lý do.
CREATE TABLE teacher_review (
    id                      SERIAL PRIMARY KEY,
    question_id             INT NOT NULL,
    reviewer_id             INT NOT NULL,
    review_purpose          VARCHAR(20) NOT NULL,
    reviewer_answer         VARCHAR(10) NOT NULL,
    answer_correct          BOOLEAN NOT NULL,
    format_ok               BOOLEAN NOT NULL,
    difficulty_label        VARCHAR(10) NOT NULL,
    decision                VARCHAR(20) NOT NULL,
    final_option            CHAR(1),
    edited                  BOOLEAN NOT NULL DEFAULT FALSE,
    edited_question_text    TEXT,
    edited_option_a         TEXT,
    edited_option_b         TEXT,
    edited_option_c         TEXT,
    edited_option_d         TEXT,
    edit_distance           INT,
    reviewed_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tr_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_tr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CONSTRAINT uq_tr_question_reviewer UNIQUE (question_id, reviewer_id),
    CONSTRAINT chk_tr_review_purpose CHECK (review_purpose IN ('experiment', 'production')),
    CONSTRAINT chk_tr_reviewer_answer CHECK (reviewer_answer IN ('A', 'B', 'C', 'D', 'multiple', 'none')),
    CONSTRAINT chk_tr_difficulty CHECK (difficulty_label IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_tr_decision CHECK (decision IN ('approved_as_is', 'approved_with_edit', 'rejected')),
    CONSTRAINT chk_tr_final_option CHECK (final_option IN ('A', 'B', 'C', 'D')),
    -- duyệt thì có đáp án đúng cuối cùng; từ chối thì không
    CONSTRAINT chk_tr_final_consistency CHECK (
        (decision = 'rejected' AND final_option IS NULL)
        OR (decision <> 'rejected' AND final_option IS NOT NULL)),
    -- dùng nguyên trạng: không có bản sửa
    CONSTRAINT chk_tr_as_is_not_edited CHECK (decision <> 'approved_as_is' OR edited = FALSE),
    -- từ chối thì không lưu bản sửa
    CONSTRAINT chk_tr_rejected_not_edited CHECK (decision <> 'rejected' OR edited = FALSE),
    -- chỉ dựa vào lựa chọn của chính giáo viên (không so với đáp án LLM nên không làm lộ đáp án):
    -- phải chọn A-D và đáp án cuối = đáp án đã chọn
    CONSTRAINT chk_tr_as_is_own_answer CHECK (
        decision <> 'approved_as_is'
        OR (reviewer_answer IN ('A', 'B', 'C', 'D') AND final_option = reviewer_answer)),
    CONSTRAINT chk_tr_edit_distance CHECK (
        (edited = FALSE AND edit_distance IS NULL)
        OR (edited = TRUE AND edit_distance IS NOT NULL AND edit_distance >= 0))
);

CREATE TABLE teacher_review_reason (
    review_id           INT NOT NULL,
    reason_code         VARCHAR(50) NOT NULL,
    detail              TEXT,
    PRIMARY KEY (review_id, reason_code),
    CONSTRAINT fk_trr_review FOREIGN KEY (review_id) REFERENCES teacher_review(id),
    CONSTRAINT chk_trr_reason_code CHECK (reason_code IN (
        'content_inaccurate', 'content_off_curriculum', 'content_insufficient_info', 'content_unclear',
        'options_out_of_scope', 'options_indistinct',
        'presentation_question_hard_to_read', 'presentation_options_hard_to_read',
        'presentation_missing_question', 'presentation_grammar', 'presentation_formula_symbol_error',
        'answer_multiple_correct', 'answer_none_correct',
        'other')),
    CONSTRAINT chk_trr_other_detail CHECK (
        reason_code <> 'other' OR (detail IS NOT NULL AND length(trim(detail)) > 0))
);

-- Gold set dedup: mỗi dòng là một cặp câu.
-- Quy ước: luôn lưu question_id_1 < question_id_2 để UNIQUE chặn được cặp đảo ngược (a,b) và (b,a).
-- Phải sắp thứ tự id ở service trước khi insert.
-- predicted_similarity chỉ điền SAU khi gán nhãn xong.
-- sampling_stratum: cách chọn cặp (không dựa vào similarity của pipeline):
--   same_run / cross_run / cross_template (ứng viên tìm bằng mô hình embedding khác pipeline) / random
-- final_is_duplicate: nhãn cuối cùng sau khi các người gán nhãn đồng ý hoặc có người phân xử.
CREATE TABLE dedup_gold_pair (
    id                      SERIAL PRIMARY KEY,
    question_id_1           INT NOT NULL,
    question_id_2           INT NOT NULL,
    sampling_stratum        VARCHAR(20) NOT NULL,
    final_is_duplicate      BOOLEAN,
    adjudicator_id          INT,
    split                   VARCHAR(10),
    predicted_similarity    FLOAT,
    CONSTRAINT fk_dgp_q1 FOREIGN KEY (question_id_1) REFERENCES generated_question(id),
    CONSTRAINT fk_dgp_q2 FOREIGN KEY (question_id_2) REFERENCES generated_question(id),
    CONSTRAINT fk_dgp_adjudicator FOREIGN KEY (adjudicator_id) REFERENCES users(id),
    CONSTRAINT chk_dgp_order CHECK (question_id_1 < question_id_2),
    CONSTRAINT chk_dgp_stratum CHECK (sampling_stratum IN
        ('same_run', 'cross_run', 'cross_template', 'random')),
    CONSTRAINT chk_dgp_split CHECK (split IN ('dev', 'test')),
    CONSTRAINT uq_dgp_pair UNIQUE (question_id_1, question_id_2)
);

-- Nhãn của từng người gán cho từng cặp (dùng để tính κ giữa những người gán nhãn).
CREATE TABLE dedup_gold_label (
    pair_id             INT NOT NULL,
    annotator_id        INT NOT NULL,
    is_duplicate        BOOLEAN NOT NULL,
    labeled_at          TIMESTAMP,
    PRIMARY KEY (pair_id, annotator_id),
    CONSTRAINT fk_dgl_pair FOREIGN KEY (pair_id) REFERENCES dedup_gold_pair(id),
    CONSTRAINT fk_dgl_annotator FOREIGN KEY (annotator_id) REFERENCES users(id)
);

-- exam_code: mã đề do hệ thống sinh. status: 'draft' = vừa sinh, chờ giáo viên kiểm tra;
-- 'confirmed' = giáo viên xác nhận sử dụng. "Sinh lại" thì service thay danh sách câu và giữ draft.
CREATE TABLE exam (
    id              SERIAL PRIMARY KEY,
    exam_code       VARCHAR(20) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    subject_id      INT NOT NULL,
    created_by      INT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'draft',
    CONSTRAINT fk_exam_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_exam_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uq_exam_code UNIQUE (exam_code),
    CONSTRAINT chk_exam_status CHECK (status IN ('draft', 'confirmed'))
);

-- Khóa ngoại trỏ thẳng generated_question nên DB không chặn câu chưa in_bank.
-- Service PHẢI kiểm tra generated_question.status = 'in_bank' trước khi thêm câu vào đề.
CREATE TABLE exam_question (
    exam_id             INT NOT NULL,
    question_id         INT NOT NULL,
    position            INT,
    -- thứ tự phương án hiển thị: 4 ký tự A-D không lặp. 'ABCD' = giữ nguyên thứ tự gốc.
    option_order        CHAR(4) NOT NULL DEFAULT 'ABCD',
    PRIMARY KEY (exam_id, question_id),
    CONSTRAINT fk_eq_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
    CONSTRAINT fk_eq_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT chk_eq_option_order CHECK (option_order ~ '^[ABCD]{4}$' AND option_order !~ '(.).*\1')
);
