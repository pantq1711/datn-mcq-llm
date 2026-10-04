-- ERD only: bang, cot, khoa chinh, khoa ngoai, UNIQUE. Khong co CHECK, DEFAULT, comment.

CREATE TABLE subject (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255),
    role VARCHAR(50)
);

CREATE TABLE question_template (
    id SERIAL PRIMARY KEY,
    subject_id INT NOT NULL,
    source_question TEXT NOT NULL,
    version INT,
    is_active BOOLEAN,
    CONSTRAINT fk_qt_subject FOREIGN KEY (subject_id) REFERENCES subject(id)
);

CREATE TABLE experiment_run (
    id SERIAL PRIMARY KEY,
    subject_id INT NOT NULL,
    strategy VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    temperature FLOAT NOT NULL,
    prompt_version VARCHAR(50),
    prompt_text TEXT NOT NULL,
    exemplar_ids TEXT,
    hyperparams TEXT,
    num_questions_requested INT,
    status VARCHAR(50),
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    CONSTRAINT fk_er_subject FOREIGN KEY (subject_id) REFERENCES subject(id)
);

CREATE TABLE generated_question (
    id SERIAL PRIMARY KEY,
    template_id INT NOT NULL,
    run_id INT NOT NULL,
    question_text TEXT,
    option_a TEXT,
    option_b TEXT,
    option_c TEXT,
    option_d TEXT,
    correct_option CHAR(1),
    raw_llm_output TEXT NOT NULL,
    llm_difficulty VARCHAR(10),
    difficulty_judge_model VARCHAR(100),
    difficulty_prompt_version VARCHAR(50),
    final_difficulty VARCHAR(10),
    parse_success BOOLEAN,
    token_count INT,
    latency_ms FLOAT,
    cost_usd FLOAT,
    attempt_count INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT fk_gq_template FOREIGN KEY (template_id) REFERENCES question_template(id),
    CONSTRAINT fk_gq_run FOREIGN KEY (run_id) REFERENCES experiment_run(id)
);

CREATE TABLE question_embedding (
    question_id INT PRIMARY KEY,
    embedding_model VARCHAR(100),
    embedding BYTEA,
    CONSTRAINT fk_qe_question FOREIGN KEY (question_id) REFERENCES generated_question(id)
);

CREATE TABLE quality_check_result (
    id SERIAL PRIMARY KEY,
    question_id INT NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    passed BOOLEAN NOT NULL,
    similarity_score FLOAT,
    compared_to_question_id INT,
    threshold_used FLOAT,
    checked_at TIMESTAMP,
    CONSTRAINT fk_qcr_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_qcr_compared FOREIGN KEY (compared_to_question_id) REFERENCES generated_question(id),
    CONSTRAINT uq_qcr_question_check UNIQUE (question_id, check_type)
);

CREATE TABLE label_assignment (
    question_id INT NOT NULL,
    reviewer_id INT NOT NULL,
    assigned_at TIMESTAMP,
    PRIMARY KEY (question_id, reviewer_id),
    CONSTRAINT fk_la_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_la_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

CREATE TABLE teacher_review (
    id SERIAL PRIMARY KEY,
    question_id INT NOT NULL,
    reviewer_id INT NOT NULL,
    review_purpose VARCHAR(20) NOT NULL,
    reviewer_answer VARCHAR(10) NOT NULL,
    answer_correct BOOLEAN NOT NULL,
    format_ok BOOLEAN NOT NULL,
    difficulty_label VARCHAR(10) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    final_option CHAR(1),
    edited BOOLEAN NOT NULL,
    edited_question_text TEXT,
    edited_option_a TEXT,
    edited_option_b TEXT,
    edited_option_c TEXT,
    edited_option_d TEXT,
    edit_distance INT,
    reviewed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tr_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_tr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CONSTRAINT uq_tr_question_reviewer UNIQUE (question_id, reviewer_id)
);

CREATE TABLE teacher_review_reason (
    review_id INT NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    detail TEXT,
    PRIMARY KEY (review_id, reason_code),
    CONSTRAINT fk_trr_review FOREIGN KEY (review_id) REFERENCES teacher_review(id)
);

CREATE TABLE dedup_gold_pair (
    id SERIAL PRIMARY KEY,
    question_id_1 INT NOT NULL,
    question_id_2 INT NOT NULL,
    sampling_stratum VARCHAR(20) NOT NULL,
    final_is_duplicate BOOLEAN,
    adjudicator_id INT,
    split VARCHAR(10),
    predicted_similarity FLOAT,
    CONSTRAINT fk_dgp_q1 FOREIGN KEY (question_id_1) REFERENCES generated_question(id),
    CONSTRAINT fk_dgp_q2 FOREIGN KEY (question_id_2) REFERENCES generated_question(id),
    CONSTRAINT fk_dgp_adjudicator FOREIGN KEY (adjudicator_id) REFERENCES users(id),
    CONSTRAINT uq_dgp_pair UNIQUE (question_id_1, question_id_2)
);

CREATE TABLE dedup_gold_label (
    pair_id INT NOT NULL,
    annotator_id INT NOT NULL,
    is_duplicate BOOLEAN NOT NULL,
    labeled_at TIMESTAMP,
    PRIMARY KEY (pair_id, annotator_id),
    CONSTRAINT fk_dgl_pair FOREIGN KEY (pair_id) REFERENCES dedup_gold_pair(id),
    CONSTRAINT fk_dgl_annotator FOREIGN KEY (annotator_id) REFERENCES users(id)
);

CREATE TABLE exam (
    id SERIAL PRIMARY KEY,
    exam_code VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    subject_id INT NOT NULL,
    created_by INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_exam_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_exam_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uq_exam_code UNIQUE (exam_code)
);

CREATE TABLE exam_question (
    exam_id INT NOT NULL,
    question_id INT NOT NULL,
    position INT NOT NULL,
    option_order CHAR(4) NOT NULL,
    PRIMARY KEY (exam_id, question_id),
    CONSTRAINT fk_eq_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
    CONSTRAINT fk_eq_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT uq_eq_position UNIQUE (exam_id, position)
);
