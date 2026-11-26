-- V35__replace_latex_balance_macro.sql
-- Sửa dữ liệu câu hỏi bị mô hình viết lệnh LaTeX bịa \balance thay cho \parallel (phát hiện 24/11/2026 trên VPS:
-- 21 câu Ta-lét ở generated_question, 7 câu đã vào bank_item; pandoc không nhận ra \balance nên xuất Word in nguyên
-- "$MN \balance BC$"). Không sửa V1-V34.
--
-- Quy tắc thay thế:
-- * Chỉ thay đúng lệnh \balance (không đứng ngay sau dấu "\" khác như "\\balance", và ký tự kế tiếp không phải chữ cái
--   để không đụng "\balanced"...) thành \parallel.
-- * Áp dụng trên mọi bảng/cột chứa chữ câu:
--   - generated_question: question_text, option_a..d, explanation, sa_answer (GIỮ NGUYÊN raw_llm_output);
--   - generated_question_statement: statement_text;
--   - bank_item: question_text, option_a..d, explanation, sa_answer (kể cả dòng đã ẩn mềm removed_at);
--   - bank_item_statement: statement_text;
--   - teacher_review: edited_question_text, edited_option_a..d, edited_explanation, reviewer_sa_answer, final_sa_answer;
--   - teacher_review_statement: edited_statement_text.
-- * Ghi số dòng được cập nhật của từng bảng ra RAISE NOTICE khi chạy migration.

DO $$
DECLARE
    v_pattern     CONSTANT TEXT := '(?<!\\)\\balance(?![[:alpha:]])';
    v_replacement CONSTANT TEXT := '\\parallel';
    v_gq          BIGINT := 0;
    v_gqs         BIGINT := 0;
    v_bi          BIGINT := 0;
    v_bis         BIGINT := 0;
    v_tr          BIGINT := 0;
    v_trs         BIGINT := 0;
BEGIN
    UPDATE generated_question
       SET question_text = regexp_replace(question_text, v_pattern, v_replacement, 'g'),
           option_a      = regexp_replace(option_a,      v_pattern, v_replacement, 'g'),
           option_b      = regexp_replace(option_b,      v_pattern, v_replacement, 'g'),
           option_c      = regexp_replace(option_c,      v_pattern, v_replacement, 'g'),
           option_d      = regexp_replace(option_d,      v_pattern, v_replacement, 'g'),
           explanation   = regexp_replace(explanation,   v_pattern, v_replacement, 'g'),
           sa_answer     = regexp_replace(sa_answer,     v_pattern, v_replacement, 'g')
     WHERE question_text ~ v_pattern
        OR option_a      ~ v_pattern
        OR option_b      ~ v_pattern
        OR option_c      ~ v_pattern
        OR option_d      ~ v_pattern
        OR explanation   ~ v_pattern
        OR sa_answer     ~ v_pattern;
    GET DIAGNOSTICS v_gq = ROW_COUNT;

    UPDATE generated_question_statement
       SET statement_text = regexp_replace(statement_text, v_pattern, v_replacement, 'g')
     WHERE statement_text ~ v_pattern;
    GET DIAGNOSTICS v_gqs = ROW_COUNT;

    UPDATE bank_item
       SET question_text = regexp_replace(question_text, v_pattern, v_replacement, 'g'),
           option_a      = regexp_replace(option_a,      v_pattern, v_replacement, 'g'),
           option_b      = regexp_replace(option_b,      v_pattern, v_replacement, 'g'),
           option_c      = regexp_replace(option_c,      v_pattern, v_replacement, 'g'),
           option_d      = regexp_replace(option_d,      v_pattern, v_replacement, 'g'),
           explanation   = regexp_replace(explanation,   v_pattern, v_replacement, 'g'),
           sa_answer     = regexp_replace(sa_answer,     v_pattern, v_replacement, 'g')
     WHERE question_text ~ v_pattern
        OR option_a      ~ v_pattern
        OR option_b      ~ v_pattern
        OR option_c      ~ v_pattern
        OR option_d      ~ v_pattern
        OR explanation   ~ v_pattern
        OR sa_answer     ~ v_pattern;
    GET DIAGNOSTICS v_bi = ROW_COUNT;

    UPDATE bank_item_statement
       SET statement_text = regexp_replace(statement_text, v_pattern, v_replacement, 'g')
     WHERE statement_text ~ v_pattern;
    GET DIAGNOSTICS v_bis = ROW_COUNT;

    UPDATE teacher_review
       SET edited_question_text = regexp_replace(edited_question_text, v_pattern, v_replacement, 'g'),
           edited_option_a      = regexp_replace(edited_option_a,      v_pattern, v_replacement, 'g'),
           edited_option_b      = regexp_replace(edited_option_b,      v_pattern, v_replacement, 'g'),
           edited_option_c      = regexp_replace(edited_option_c,      v_pattern, v_replacement, 'g'),
           edited_option_d      = regexp_replace(edited_option_d,      v_pattern, v_replacement, 'g'),
           edited_explanation   = regexp_replace(edited_explanation,   v_pattern, v_replacement, 'g'),
           reviewer_sa_answer   = regexp_replace(reviewer_sa_answer,   v_pattern, v_replacement, 'g'),
           final_sa_answer      = regexp_replace(final_sa_answer,      v_pattern, v_replacement, 'g')
     WHERE edited_question_text ~ v_pattern
        OR edited_option_a      ~ v_pattern
        OR edited_option_b      ~ v_pattern
        OR edited_option_c      ~ v_pattern
        OR edited_option_d      ~ v_pattern
        OR edited_explanation   ~ v_pattern
        OR reviewer_sa_answer   ~ v_pattern
        OR final_sa_answer      ~ v_pattern;
    GET DIAGNOSTICS v_tr = ROW_COUNT;

    UPDATE teacher_review_statement
       SET edited_statement_text = regexp_replace(edited_statement_text, v_pattern, v_replacement, 'g')
     WHERE edited_statement_text ~ v_pattern;
    GET DIAGNOSTICS v_trs = ROW_COUNT;

    RAISE NOTICE 'V35 replace \balance -> \parallel: generated_question=%, generated_question_statement=%, bank_item=%, bank_item_statement=%, teacher_review=%, teacher_review_statement=%',
        v_gq, v_gqs, v_bi, v_bis, v_tr, v_trs;
END $$;
