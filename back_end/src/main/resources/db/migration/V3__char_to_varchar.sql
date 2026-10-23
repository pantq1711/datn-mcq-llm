-- V3__char_to_varchar.sql
-- Đổi 4 cột CHAR(1)/CHAR(4) sang VARCHAR(1)/VARCHAR(4) để khớp kiểu mặc định của Hibernate (ddl-auto: validate).
-- Đồng thời tạo lại các CHECK constraint liên quan để PostgreSQL không giữ ép kiểu ::bpchar cũ trong pg_constraint,
-- đảm bảo schema sau V1+V2+V3 khớp từng dòng với db/schema.sql.

ALTER TABLE generated_question
    DROP CONSTRAINT chk_gq_correct_option,
    ALTER COLUMN correct_option TYPE VARCHAR(1),
    ADD CONSTRAINT chk_gq_correct_option CHECK (correct_option IN ('A', 'B', 'C', 'D'));

ALTER TABLE teacher_review
    DROP CONSTRAINT chk_tr_final_option,
    DROP CONSTRAINT chk_tr_as_is_own_answer,
    ALTER COLUMN final_option TYPE VARCHAR(1),
    ADD CONSTRAINT chk_tr_final_option CHECK (final_option IN ('A', 'B', 'C', 'D')),
    ADD CONSTRAINT chk_tr_as_is_own_answer CHECK (
        decision <> 'approved_as_is'
        OR (reviewer_answer IN ('A', 'B', 'C', 'D') AND final_option = reviewer_answer));

ALTER TABLE bank_item
    DROP CONSTRAINT chk_bi_correct_option,
    ALTER COLUMN correct_option TYPE VARCHAR(1),
    ADD CONSTRAINT chk_bi_correct_option CHECK (correct_option IN ('A', 'B', 'C', 'D'));

ALTER TABLE exam_question
    DROP CONSTRAINT chk_eq_option_order,
    ALTER COLUMN option_order TYPE VARCHAR(4),
    ALTER COLUMN option_order SET DEFAULT 'ABCD',
    ADD CONSTRAINT chk_eq_option_order CHECK (
        option_order ~ '^[ABCD]{4}$'
        AND strpos(substr(option_order, 2, 3), substr(option_order, 1, 1)) = 0
        AND strpos(substr(option_order, 3, 2), substr(option_order, 2, 1)) = 0
        AND substr(option_order, 3, 1) <> substr(option_order, 4, 1));
