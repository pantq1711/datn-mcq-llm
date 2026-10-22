-- V4__question_types_tf_sa.sql
-- Thêm hai dạng câu của đề Toán THCS: Đúng/Sai (tf) và Trả lời ngắn (sa), bên cạnh trắc nghiệm 4 phương án (mcq).
-- Thiết kế đã duyệt ngày 22/10 (phương án b + khóa ngoại kép). Không sửa V1-V3.
-- Đổi enum phải cập nhật docs/contract/enums.md trong cùng commit.
--
-- Nguyên tắc giữ dữ liệu cũ:
--   * Mọi cột mới hoặc NULL, hoặc NOT NULL DEFAULT 'mcq', nên dòng cũ thành câu mcq.
--   * Nhánh mcq của mỗi CHECK được viết lại tương đương CHECK cũ (V1, V2, V3), chỉ thêm "cột của dạng khác IS NULL".
--   * Các cột bỏ NOT NULL vẫn bắt buộc với mcq qua CHECK theo dạng câu.
--
-- Khóa ngoại kép: bảng con trỏ tới (id, question_type) của bảng cha. DB tự chặn ý TF gắn vào câu mcq,
-- bank_item khác dạng câu gốc, đổi dạng một câu đã có dòng con. Bảng chỉ chứa ý TF có question_type
-- là hằng 'tf' (CHECK), chỉ dùng cho khóa ngoại.
--
-- Đáp số SA lưu dạng TEXT, đúng chuỗi được viết (đã trim), không có CHECK định dạng; việc phân tích và so
-- theo giá trị làm trong Java. Không lưu dạng chuẩn hóa: tính lại được từ chuỗi gốc.

-- 1. experiment_run: TF/SA chỉ cho production, strategy ghi 'zero-shot' (quyết định 2 ngày 22/10).
--    Phân tích C1/C2/C3 lọc question_type = 'mcq'.
ALTER TABLE experiment_run
    ADD COLUMN question_type VARCHAR(10) NOT NULL DEFAULT 'mcq',
    ADD CONSTRAINT chk_er_question_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    ADD CONSTRAINT uq_er_id_type UNIQUE (id, question_type),
    ADD CONSTRAINT chk_er_tf_sa_production CHECK (
        question_type = 'mcq' OR (purpose = 'production' AND strategy = 'zero-shot'));

-- 2. generated_question
ALTER TABLE generated_question
    ADD COLUMN question_type VARCHAR(10) NOT NULL DEFAULT 'mcq',
    ADD COLUMN sa_answer TEXT,
    ADD CONSTRAINT chk_gq_question_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    ADD CONSTRAINT uq_gq_id_type UNIQUE (id, question_type),
    ADD CONSTRAINT fk_gq_run_type FOREIGN KEY (run_id, question_type)
        REFERENCES experiment_run (id, question_type),
    -- cột riêng của từng dạng: mcq không có đáp số SA; tf/sa không dùng 4 phương án và correct_option;
    -- tf ghi mức độ ở từng ý nên mức độ cấp câu để NULL
    ADD CONSTRAINT chk_gq_type_columns CHECK (
        (question_type = 'mcq' AND sa_answer IS NULL)
        OR (question_type IN ('tf', 'sa')
            AND option_a IS NULL AND option_b IS NULL AND option_c IS NULL AND option_d IS NULL
            AND correct_option IS NULL
            AND (question_type = 'sa'
                 OR (sa_answer IS NULL AND llm_difficulty IS NULL AND final_difficulty IS NULL)))),
    DROP CONSTRAINT chk_gq_in_bank_complete,
    -- câu trong ngân hàng phải đủ nội dung theo dạng; tf đủ 4 ý do service kiểm (không CHECK được qua bảng con)
    ADD CONSTRAINT chk_gq_in_bank_complete CHECK (
        status <> 'in_bank' OR (question_text IS NOT NULL AND (
            (question_type = 'mcq'
                AND option_a IS NOT NULL AND option_b IS NOT NULL
                AND option_c IS NOT NULL AND option_d IS NOT NULL
                AND correct_option IS NOT NULL
                AND final_difficulty IS NOT NULL)
            OR (question_type = 'sa' AND sa_answer IS NOT NULL AND final_difficulty IS NOT NULL)
            OR question_type = 'tf')));

-- 3. Ý a-d của câu TF do LLM sinh. llm_difficulty là mức mục tiêu khi sinh (quyết định 1 ngày 22/10;
--    Q-5 chốt thế nào áp cho cả llm_difficulty cấp câu). final_difficulty chép từ mức giáo viên khi vào ngân hàng.
--    Parse lỗi thì không có dòng nào; các cột nội dung được NULL để giữ dấu vết khi format lỗi.
CREATE TABLE generated_question_statement (
    question_id         INT NOT NULL,
    question_type       VARCHAR(10) NOT NULL DEFAULT 'tf',
    position            VARCHAR(1) NOT NULL,
    statement_text      TEXT,
    llm_label           VARCHAR(1),
    llm_difficulty      VARCHAR(10),
    final_difficulty    VARCHAR(10),
    PRIMARY KEY (question_id, position),
    CONSTRAINT fk_gqs_question FOREIGN KEY (question_id, question_type)
        REFERENCES generated_question (id, question_type),
    CONSTRAINT chk_gqs_type CHECK (question_type = 'tf'),
    CONSTRAINT chk_gqs_position CHECK (position IN ('a', 'b', 'c', 'd')),
    CONSTRAINT chk_gqs_llm_label CHECK (llm_label IN ('T', 'F')),
    CONSTRAINT chk_gqs_llm_difficulty CHECK (llm_difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_gqs_final_difficulty CHECK (final_difficulty IN ('easy', 'medium', 'hard'))
);

-- 4. generation_job: TF ghi mức mục tiêu của 4 ý theo thứ tự a,b,c,d; target_difficulty cấp câu để NULL.
ALTER TABLE generation_job
    ADD COLUMN question_type VARCHAR(10) NOT NULL DEFAULT 'mcq',
    ADD COLUMN target_statement_difficulties VARCHAR(40),
    ADD CONSTRAINT chk_gj_question_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    ADD CONSTRAINT fk_gj_run_type FOREIGN KEY (run_id, question_type)
        REFERENCES experiment_run (id, question_type),
    ADD CONSTRAINT chk_gj_target_statements CHECK (
        (question_type = 'tf' AND target_difficulty IS NULL
            AND target_statement_difficulties ~ '^(easy|medium|hard)(,(easy|medium|hard)){3}$')
        OR (question_type <> 'tf' AND target_statement_difficulties IS NULL));

-- 5. teacher_review
--    mcq: giữ nguyên ý nghĩa các cột cũ.
--    sa:  reviewer_sa_kind/reviewer_sa_answer là đáp số giáo viên nhập mù; final_sa_answer là đáp số cuối.
--         answer_correct được NULL, chỉ khi đáp số LLM đã lưu không còn phân tích được (quyết định A).
--    tf:  nhãn và mức độ từng ý ở teacher_review_statement; answer_correct và difficulty_label cấp câu để NULL.
ALTER TABLE teacher_review
    ADD COLUMN question_type VARCHAR(10) NOT NULL DEFAULT 'mcq',
    ADD COLUMN reviewer_sa_kind VARCHAR(20),
    ADD COLUMN reviewer_sa_answer TEXT,
    ADD COLUMN final_sa_answer TEXT,
    ALTER COLUMN reviewer_answer DROP NOT NULL,
    ALTER COLUMN answer_correct DROP NOT NULL,
    ALTER COLUMN difficulty_label DROP NOT NULL,
    ADD CONSTRAINT chk_tr_question_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    ADD CONSTRAINT uq_tr_id_type UNIQUE (id, question_type),
    ADD CONSTRAINT fk_tr_question_type FOREIGN KEY (question_id, question_type)
        REFERENCES generated_question (id, question_type),
    ADD CONSTRAINT chk_tr_sa_kind CHECK (reviewer_sa_kind IN ('number', 'ambiguous')),
    ADD CONSTRAINT chk_tr_type_columns CHECK (
        (question_type = 'mcq'
            AND reviewer_answer IS NOT NULL AND answer_correct IS NOT NULL AND difficulty_label IS NOT NULL
            AND reviewer_sa_kind IS NULL AND reviewer_sa_answer IS NULL AND final_sa_answer IS NULL)
        OR (question_type IN ('tf', 'sa')
            AND reviewer_answer IS NULL AND final_option IS NULL
            AND edited_option_a IS NULL AND edited_option_b IS NULL
            AND edited_option_c IS NULL AND edited_option_d IS NULL
            AND ((question_type = 'sa'
                    AND difficulty_label IS NOT NULL
                    AND reviewer_sa_kind IS NOT NULL
                    AND (reviewer_sa_kind = 'number') = (reviewer_sa_answer IS NOT NULL)
                    AND (reviewer_sa_kind <> 'ambiguous' OR answer_correct = FALSE))
              OR (question_type = 'tf'
                    AND answer_correct IS NULL AND difficulty_label IS NULL
                    AND reviewer_sa_kind IS NULL AND reviewer_sa_answer IS NULL AND final_sa_answer IS NULL)))),
    DROP CONSTRAINT chk_tr_final_consistency,
    -- duyệt thì có đáp án cuối, từ chối thì không; tf: nhãn cuối ở bảng con, service kiểm
    ADD CONSTRAINT chk_tr_final_consistency CHECK (
        (question_type = 'mcq'
            AND ((decision = 'rejected' AND final_option IS NULL)
                 OR (decision <> 'rejected' AND final_option IS NOT NULL)))
        OR (question_type = 'sa'
            AND ((decision = 'rejected' AND final_sa_answer IS NULL)
                 OR (decision <> 'rejected' AND final_sa_answer IS NOT NULL)))
        OR question_type = 'tf'),
    DROP CONSTRAINT chk_tr_as_is_own_answer,
    -- dùng nguyên trạng: đáp án cuối là đáp án giáo viên đã chọn (không so với đáp án LLM)
    ADD CONSTRAINT chk_tr_as_is_own_answer CHECK (
        decision <> 'approved_as_is'
        OR (question_type = 'mcq'
            AND reviewer_answer IN ('A', 'B', 'C', 'D') AND final_option = reviewer_answer)
        OR (question_type = 'sa'
            AND reviewer_sa_kind = 'number' AND final_sa_answer = reviewer_sa_answer)
        OR question_type = 'tf');

-- 6. Nhãn chấm mù từng ý TF. reviewer_label nhập trước khi thấy nhãn LLM; label_correct do server tính
--    (reviewer_label = llm_label). final_label NULL khi rejected (service kiểm). Đủ 4 ý: service kiểm.
CREATE TABLE teacher_review_statement (
    review_id               INT NOT NULL,
    question_type           VARCHAR(10) NOT NULL DEFAULT 'tf',
    position                VARCHAR(1) NOT NULL,
    reviewer_label          VARCHAR(20) NOT NULL,
    difficulty_label        VARCHAR(10) NOT NULL,
    label_correct           BOOLEAN NOT NULL,
    final_label             VARCHAR(1),
    edited_statement_text   TEXT,
    PRIMARY KEY (review_id, position),
    CONSTRAINT fk_trs_review FOREIGN KEY (review_id, question_type)
        REFERENCES teacher_review (id, question_type),
    CONSTRAINT chk_trs_type CHECK (question_type = 'tf'),
    CONSTRAINT chk_trs_position CHECK (position IN ('a', 'b', 'c', 'd')),
    CONSTRAINT chk_trs_reviewer_label CHECK (reviewer_label IN ('T', 'F', 'undecidable')),
    CONSTRAINT chk_trs_difficulty CHECK (difficulty_label IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_trs_final_label CHECK (final_label IN ('T', 'F')),
    CONSTRAINT chk_trs_undecidable_not_correct CHECK (reviewer_label <> 'undecidable' OR label_correct = FALSE)
);

-- 7. Mã lý do từ chối (quyết định 3 ngày 22/10): tập mới chứa trọn tập cũ.
--    statement_undecidable chỉ cho tf, answer_ambiguous chỉ cho sa; mã riêng mcq không dùng cho tf/sa: service kiểm.
ALTER TABLE teacher_review_reason
    DROP CONSTRAINT chk_trr_reason_code,
    ADD CONSTRAINT chk_trr_reason_code CHECK (reason_code IN (
        'content_inaccurate', 'content_off_curriculum', 'content_insufficient_info', 'content_unclear',
        'options_out_of_scope', 'options_indistinct',
        'presentation_question_hard_to_read', 'presentation_options_hard_to_read',
        'presentation_missing_question', 'presentation_grammar', 'presentation_formula_symbol_error',
        'answer_multiple_correct', 'answer_none_correct',
        'statement_undecidable', 'answer_ambiguous',
        'other'));

-- 8. quality_check_result: kết quả Solver theo dạng câu.
--    mcq: judge_answer như cũ. sa: judge_sa_kind + judge_sa_answer (chuỗi gốc Solver trả).
--    tf: nhãn từng ý ở answer_check_statement.
ALTER TABLE quality_check_result
    ADD COLUMN question_type VARCHAR(10) NOT NULL DEFAULT 'mcq',
    ADD COLUMN judge_sa_kind VARCHAR(20),
    ADD COLUMN judge_sa_answer TEXT,
    ADD CONSTRAINT chk_qcr_question_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    ADD CONSTRAINT uq_qcr_id_type UNIQUE (id, question_type),
    ADD CONSTRAINT fk_qcr_question_type FOREIGN KEY (question_id, question_type)
        REFERENCES generated_question (id, question_type),
    ADD CONSTRAINT chk_qcr_judge_sa_kind CHECK (judge_sa_kind IN ('number', 'ambiguous', 'unparseable')),
    DROP CONSTRAINT chk_qcr_judge_only_answer_check,
    ADD CONSTRAINT chk_qcr_judge_only_answer_check CHECK (
        (check_type <> 'answer_check'
            AND judge_answer IS NULL AND judge_model IS NULL
            AND judge_sa_kind IS NULL AND judge_sa_answer IS NULL)
        OR (check_type = 'answer_check' AND judge_model IS NOT NULL AND (
               (question_type = 'mcq'
                    AND judge_answer IS NOT NULL AND judge_sa_kind IS NULL AND judge_sa_answer IS NULL)
            OR (question_type = 'sa'
                    AND judge_answer IS NULL AND judge_sa_kind IS NOT NULL
                    AND (judge_sa_kind IN ('number', 'unparseable')) = (judge_sa_answer IS NOT NULL))
            OR (question_type = 'tf'
                    AND judge_answer IS NULL AND judge_sa_kind IS NULL AND judge_sa_answer IS NULL))));

-- 9. Nhãn Solver cho từng ý TF. Xóa dây chuyền theo dòng answer_check (chạy lại QC xóa hàng loạt bằng JPQL).
CREATE TABLE answer_check_statement (
    qc_result_id    INT NOT NULL,
    question_type   VARCHAR(10) NOT NULL DEFAULT 'tf',
    position        VARCHAR(1) NOT NULL,
    judge_label     VARCHAR(20) NOT NULL,
    PRIMARY KEY (qc_result_id, position),
    CONSTRAINT fk_acs_result FOREIGN KEY (qc_result_id, question_type)
        REFERENCES quality_check_result (id, question_type) ON DELETE CASCADE,
    CONSTRAINT chk_acs_type CHECK (question_type = 'tf'),
    CONSTRAINT chk_acs_position CHECK (position IN ('a', 'b', 'c', 'd')),
    CONSTRAINT chk_acs_label CHECK (judge_label IN ('T', 'F', 'undecidable'))
);

-- 10. bank_item
ALTER TABLE bank_item
    ADD COLUMN question_type VARCHAR(10) NOT NULL DEFAULT 'mcq',
    ADD COLUMN sa_answer TEXT,
    ALTER COLUMN option_a DROP NOT NULL,
    ALTER COLUMN option_b DROP NOT NULL,
    ALTER COLUMN option_c DROP NOT NULL,
    ALTER COLUMN option_d DROP NOT NULL,
    ALTER COLUMN correct_option DROP NOT NULL,
    ALTER COLUMN difficulty DROP NOT NULL,
    ADD CONSTRAINT chk_bi_question_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    ADD CONSTRAINT uq_bi_id_type UNIQUE (id, question_type),
    ADD CONSTRAINT fk_bi_source_type FOREIGN KEY (source_question_id, question_type)
        REFERENCES generated_question (id, question_type),
    ADD CONSTRAINT fk_bi_review_type FOREIGN KEY (review_id, question_type)
        REFERENCES teacher_review (id, question_type),
    DROP CONSTRAINT chk_bi_not_blank,
    ADD CONSTRAINT chk_bi_type_content CHECK (
        length(btrim(question_text)) > 0
        AND (
            (question_type = 'mcq'
                AND option_a IS NOT NULL AND option_b IS NOT NULL
                AND option_c IS NOT NULL AND option_d IS NOT NULL
                AND length(btrim(option_a)) > 0 AND length(btrim(option_b)) > 0
                AND length(btrim(option_c)) > 0 AND length(btrim(option_d)) > 0
                AND correct_option IS NOT NULL AND difficulty IS NOT NULL
                AND sa_answer IS NULL)
            OR (question_type IN ('tf', 'sa')
                AND option_a IS NULL AND option_b IS NULL AND option_c IS NULL AND option_d IS NULL
                AND correct_option IS NULL
                AND ((question_type = 'sa'
                        AND sa_answer IS NOT NULL AND length(btrim(sa_answer)) > 0
                        AND difficulty IS NOT NULL)
                  OR (question_type = 'tf' AND sa_answer IS NULL AND difficulty IS NULL)))));

-- 11. Ý a-d của câu TF trong ngân hàng (bản cuối giáo viên chốt). Xóa dây chuyền theo bank_item.
CREATE TABLE bank_item_statement (
    bank_item_id    INT NOT NULL,
    question_type   VARCHAR(10) NOT NULL DEFAULT 'tf',
    position        VARCHAR(1) NOT NULL,
    statement_text  TEXT NOT NULL,
    label           VARCHAR(1) NOT NULL,
    difficulty      VARCHAR(10) NOT NULL,
    PRIMARY KEY (bank_item_id, position),
    CONSTRAINT fk_bis_item FOREIGN KEY (bank_item_id, question_type)
        REFERENCES bank_item (id, question_type) ON DELETE CASCADE,
    CONSTRAINT chk_bis_type CHECK (question_type = 'tf'),
    CONSTRAINT chk_bis_position CHECK (position IN ('a', 'b', 'c', 'd')),
    CONSTRAINT chk_bis_label CHECK (label IN ('T', 'F')),
    CONSTRAINT chk_bis_difficulty CHECK (difficulty IN ('easy', 'medium', 'hard')),
    CONSTRAINT chk_bis_not_blank CHECK (length(btrim(statement_text)) > 0)
);
