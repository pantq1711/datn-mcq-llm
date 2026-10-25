-- 02-buoc-qc-bat-loi.sql
-- Bước QC nào phát hiện vấn đề nhiều nhất trên câu LLM sinh cho đề giáo viên: format, trùng, Solver lệch, Solver lỗi /
-- chưa chạy; kèm số lần hệ thống tự sửa (escape LaTeX, tự đóng $). Gom theo dạng câu. Mỗi câu thuộc đúng một nhóm kết cục
-- cuối cùng (qua_ca_ba_buoc + các nhóm "bị chặn ở bước ..."), các cột tu_sua_* đếm riêng.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; chỉ câu LLM gốc (edited_from_question_id IS NULL,
-- run production 'matrix-%'); loại đề của tài khoản nhóm phát triển (điền email vào ARRAY[...]).
-- GIỚI HẠN: "bắt lỗi" nghĩa là bước đó báo vấn đề, không chứng minh vấn đề có thật (Solver lệch có thể do chính Solver
-- sai). Không dùng làm độ chính xác của Solver. Dữ liệu trước V14: Solver lỗi nằm chung trong solver_khong_chay_hoac_loi.
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (ARRAY[]::text[])   -- ví dụ ARRAY['nhom1@truong.edu.vn']
),
llm_questions AS (
    SELECT DISTINCT j.question_id AS id
    FROM generation_job j
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    WHERE j.job_type = 'generate' AND j.question_id IS NOT NULL
)
SELECT g.question_type,
       count(*) AS tong_cau_llm,
       count(*) FILTER (WHERE f.passed = FALSE) AS bi_chan_o_format,
       count(*) FILTER (WHERE f.passed AND d.passed = FALSE) AS bi_chan_o_trung,
       count(*) FILTER (WHERE f.passed AND d.id IS NULL) AS dedup_chua_ket_luan,
       count(*) FILTER (WHERE f.passed AND d.passed AND a.passed = FALSE) AS solver_lech,
       count(*) FILTER (WHERE f.passed AND d.passed AND a.id IS NULL AND g.solver_error IS NOT NULL) AS solver_loi,
       count(*) FILTER (WHERE f.passed AND d.passed AND a.id IS NULL AND g.solver_error IS NULL)
           AS solver_khong_chay_hoac_loi,
       count(*) FILTER (WHERE f.passed AND d.passed AND a.passed) AS qua_ca_ba_buoc,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM jsonb_array_elements_text(f.warnings) w
                                      WHERE w LIKE 'json: đã sửa %')) AS tu_sua_escape_latex,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM jsonb_array_elements_text(f.warnings) w
                                      WHERE w LIKE '%: đã tự đóng $')) AS tu_dong_dau_dola
FROM llm_questions q
JOIN generated_question g ON g.id = q.id AND g.edited_from_question_id IS NULL
JOIN experiment_run r ON r.id = g.run_id AND r.purpose = 'production' AND r.prompt_version LIKE 'matrix-%'
LEFT JOIN quality_check_result f ON f.question_id = g.id AND f.check_type = 'format'
LEFT JOIN quality_check_result d ON d.question_id = g.id AND d.check_type = 'dedup'
LEFT JOIN quality_check_result a ON a.question_id = g.id AND a.check_type = 'answer_check'
GROUP BY g.question_type
ORDER BY g.question_type;
