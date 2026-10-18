CREATE TABLE subject (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(255),
    email   VARCHAR(255),
    role    VARCHAR(50),
    CONSTRAINT chk_users_role CHECK (role IN ('teacher', 'researcher'))
);

CREATE TABLE question_template (
    id                  SERIAL PRIMARY KEY,
    subject_id          INT NOT NULL,
    grade_level         INT,
    topic               VARCHAR(255),
    learning_outcome    TEXT,
    source_type         VARCHAR(20) NOT NULL DEFAULT 'text',
    source_question     TEXT NOT NULL,
    version             INT,
    is_active           BOOLEAN,
    CONSTRAINT fk_qt_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_qt_grade_level CHECK (grade_level IS NULL OR grade_level BETWEEN 6 AND 9),
    CONSTRAINT chk_qt_source_type CHECK (source_type IN ('text', 'pdf', 'image'))
);

CREATE TABLE experiment_run (
    id                          SERIAL PRIMARY KEY,
    subject_id                  INT NOT NULL,
    purpose                     VARCHAR(20) NOT NULL DEFAULT 'experiment',
    strategy                    VARCHAR(50) NOT NULL,
    model_name                  VARCHAR(100) NOT NULL,
    temperature                 FLOAT NOT NULL,
    prompt_version              VARCHAR(50),
    prompt_text                 TEXT NOT NULL,
    exemplar_ids                JSONB,
    hyperparams                 JSONB,
    num_questions_requested     INT,
    status                      VARCHAR(50),
    started_at                  TIMESTAMP,
    ended_at                    TIMESTAMP,
    CONSTRAINT fk_er_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_er_purpose CHECK (purpose IN ('experiment', 'production')),
    CONSTRAINT chk_er_strategy CHECK (strategy IN ('zero-shot', 'few-shot', 'few-shot-structured'))
);

CREATE TABLE generated_question (
    id                          SERIAL PRIMARY KEY,
    template_id                 INT NOT NULL,
    run_id                      INT NOT NULL,
    question_text               TEXT,
    option_a                    TEXT,
    option_b                    TEXT,
    option_c                    TEXT,
    option_d                    TEXT,
    correct_option              VARCHAR(1),
    explanation                 TEXT,
    raw_llm_output              TEXT NOT NULL,
    llm_difficulty              VARCHAR(10),
    difficulty_judge_model      VARCHAR(100),
    difficulty_prompt_version   VARCHAR(50),
    final_difficulty            VARCHAR(10),
    parse_success               BOOLEAN,
    token_count                 INT,
    latency_ms                  FLOAT,
    cost_usd                    FLOAT,
    attempt_count               INT NOT NULL DEFAULT 1,
    status                      VARCHAR(50) NOT NULL DEFAULT 'generated',
    created_at                  TIMESTAMP,
    CONSTRAINT fk_gq_template FOREIGN KEY (template_id) REFERENCES question_template(id),
    CONSTRAINT fk_gq_run FOREIGN KEY (run_id) REFERENCES experiment_run(id),
    CONSTRAINT chk_gq_correct_option CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    CONSTRAINT chk_gq_difficulty CHECK (llm_difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_gq_final_difficulty CHECK (final_difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_gq_attempt_count CHECK (attempt_count >= 1),
    CONSTRAINT chk_gq_status CHECK (status IN
        ('generated', 'format_failed', 'duplicate', 'qc_passed', 'rejected', 'in_bank')),
    CONSTRAINT chk_gq_in_bank_complete CHECK (
        status <> 'in_bank' OR (
            question_text IS NOT NULL
            AND option_a IS NOT NULL AND option_b IS NOT NULL
            AND option_c IS NOT NULL AND option_d IS NOT NULL
            AND correct_option IS NOT NULL
            AND final_difficulty IS NOT NULL))
);

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
    CONSTRAINT chk_gj_done_has_question CHECK (status <> 'done' OR question_id IS NOT NULL)
);
CREATE INDEX idx_gj_poll ON generation_job (status, next_run_at);

CREATE TABLE question_embedding (
    question_id         INT NOT NULL,
    embedding_model     VARCHAR(100) NOT NULL,
    embedding           BYTEA,
    PRIMARY KEY (question_id, embedding_model),
    CONSTRAINT fk_qe_question FOREIGN KEY (question_id) REFERENCES generated_question(id)
);

CREATE TABLE quality_check_result (
    id                          SERIAL PRIMARY KEY,
    question_id                 INT NOT NULL,
    check_type                  VARCHAR(50) NOT NULL,
    passed                      BOOLEAN NOT NULL,
    similarity_score            FLOAT,
    compared_to_question_id     INT,
    threshold_used              FLOAT,
    judge_answer                VARCHAR(10),
    judge_model                 VARCHAR(100),
    checked_at                  TIMESTAMP,
    CONSTRAINT fk_qcr_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_qcr_compared FOREIGN KEY (compared_to_question_id) REFERENCES generated_question(id),
    CONSTRAINT uq_qcr_question_check UNIQUE (question_id, check_type),
    CONSTRAINT chk_qcr_check_type CHECK (check_type IN ('format', 'dedup', 'answer_check')),
    CONSTRAINT chk_qcr_compared CHECK (check_type = 'dedup' OR compared_to_question_id IS NULL),
    CONSTRAINT chk_qcr_judge_answer CHECK (judge_answer IN ('A', 'B', 'C', 'D', 'multiple', 'none')),
    CONSTRAINT chk_qcr_judge_only_answer_check CHECK (
        (check_type = 'answer_check' AND judge_answer IS NOT NULL AND judge_model IS NOT NULL)
        OR (check_type <> 'answer_check' AND judge_answer IS NULL AND judge_model IS NULL))
);

CREATE TABLE label_assignment (
    question_id         INT NOT NULL,
    reviewer_id         INT NOT NULL,
    assigned_at         TIMESTAMP,
    PRIMARY KEY (question_id, reviewer_id),
    CONSTRAINT fk_la_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_la_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

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
    final_option            VARCHAR(1),
    edited                  BOOLEAN NOT NULL DEFAULT FALSE,
    edited_question_text    TEXT,
    edited_option_a         TEXT,
    edited_option_b         TEXT,
    edited_option_c         TEXT,
    edited_option_d         TEXT,
    edited_explanation      TEXT,
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
    CONSTRAINT chk_tr_final_consistency CHECK (
        (decision = 'rejected' AND final_option IS NULL)
        OR (decision <> 'rejected' AND final_option IS NOT NULL)),
    CONSTRAINT chk_tr_as_is_not_edited CHECK (decision <> 'approved_as_is' OR edited = FALSE),
    CONSTRAINT chk_tr_rejected_not_edited CHECK (decision <> 'rejected' OR edited = FALSE),
    CONSTRAINT chk_tr_as_is_own_answer CHECK (
        decision <> 'approved_as_is'
        OR (reviewer_answer IN ('A', 'B', 'C', 'D') AND final_option = reviewer_answer)),
    CONSTRAINT chk_tr_edit_distance CHECK (
        (edited = FALSE AND edit_distance IS NULL)
        OR (edited = TRUE AND edit_distance IS NOT NULL AND edit_distance > 0))
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

CREATE TABLE bank_item (
    id                  SERIAL PRIMARY KEY,
    source_question_id  INT NOT NULL,
    review_id           INT NOT NULL,
    question_text       TEXT NOT NULL,
    option_a            TEXT NOT NULL,
    option_b            TEXT NOT NULL,
    option_c            TEXT NOT NULL,
    option_d            TEXT NOT NULL,
    correct_option      VARCHAR(1) NOT NULL,
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

CREATE TABLE dedup_gold_label (
    pair_id             INT NOT NULL,
    annotator_id        INT NOT NULL,
    is_duplicate        BOOLEAN NOT NULL,
    labeled_at          TIMESTAMP,
    PRIMARY KEY (pair_id, annotator_id),
    CONSTRAINT fk_dgl_pair FOREIGN KEY (pair_id) REFERENCES dedup_gold_pair(id),
    CONSTRAINT fk_dgl_annotator FOREIGN KEY (annotator_id) REFERENCES users(id)
);

CREATE TABLE exam (
    id              SERIAL PRIMARY KEY,
    parent_exam_id  INT,
    exam_code       VARCHAR(20) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    subject_id      INT NOT NULL,
    created_by      INT NOT NULL,
    is_backup       BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'draft',
    CONSTRAINT fk_exam_parent FOREIGN KEY (parent_exam_id) REFERENCES exam(id),
    CONSTRAINT fk_exam_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_exam_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uq_exam_code UNIQUE (exam_code),
    CONSTRAINT chk_exam_status CHECK (status IN ('draft', 'confirmed')),
    CONSTRAINT chk_exam_not_self_parent CHECK (parent_exam_id IS NULL OR parent_exam_id <> id)
);

CREATE TABLE exam_question (
    exam_id             INT NOT NULL,
    bank_item_id        INT NOT NULL,
    position            INT NOT NULL,
    option_order        VARCHAR(4) NOT NULL DEFAULT 'ABCD',
    PRIMARY KEY (exam_id, bank_item_id),
    CONSTRAINT fk_eq_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
    CONSTRAINT fk_eq_bank_item FOREIGN KEY (bank_item_id) REFERENCES bank_item(id),
    CONSTRAINT uq_eq_position UNIQUE (exam_id, position),
    CONSTRAINT chk_eq_option_order CHECK (
        option_order ~ '^[ABCD]{4}$'
        AND strpos(substr(option_order, 2, 3), substr(option_order, 1, 1)) = 0
        AND strpos(substr(option_order, 3, 2), substr(option_order, 2, 1)) = 0
        AND substr(option_order, 3, 1) <> substr(option_order, 4, 1))
);