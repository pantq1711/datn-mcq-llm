-- 04-solver-lech-giao-vien-xu-ly.sql
-- Với câu LLM mà Solver báo LỆCH (answer_check.passed = FALSE) và đã nằm trong một chỗ của đề giáo viên: giáo viên Giữ
-- nguyên, Sửa rồi Giữ, Sửa rồi Đổi, Đổi, chỉ Sửa, hay chưa thao tác. Dùng cho thử nghiệm "Solver bắt lỗi" (phần phản ứng
-- của giáo viên với cờ Solver), cùng cách tính kết cục với 01-ket-cuc-cau-llm.sql.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; chỉ câu LLM gốc (edited_from_question_id IS NULL,
-- run production 'matrix-%'); loại đề của tài khoản nhóm phát triển (biến psql dev_emails).
-- GIỚI HẠN: giáo viên nhìn thấy cờ và đáp án của hệ thống khi quyết định. Giữ một câu bị Solver báo lệch KHÔNG có nghĩa
-- Solver sai (giáo viên có thể chưa xem kỹ), và Đổi không có nghĩa câu sai. Độ đúng phải do nhóm tự kiểm tra.
--
-- dev_emails: email cần loại (nhóm phát triển, tài khoản dùng thử), cách nhau bằng dấu phẩy, truyền khi chạy
-- (KHÔNG ghi email thật vào file):
--   psql -v dev_emails='nhom1@truong.edu.vn,gv-thu@truong.edu.vn' -f 04-solver-lech-giao-vien-xu-ly.sql
-- Không truyền thì rỗng (không loại ai). Xem docs/thong-ke/README.md.
\if :{?dev_emails}
\else
\set dev_emails ''
\endif
WITH RECURSIVE
dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
roots AS (
    SELECT DISTINCT j.question_id AS root, j.exam_slot_id AS slot_id
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL AND g.origin = 'llm'
    JOIN experiment_run r ON r.id = g.run_id AND r.purpose = 'production' AND r.prompt_version LIKE 'matrix-%'
    JOIN quality_check_result a ON a.question_id = g.id AND a.check_type = 'answer_check' AND a.passed = FALSE
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    WHERE j.job_type = 'generate' AND j.question_id IS NOT NULL
),
chain AS (
    SELECT root, slot_id, root AS id FROM roots
    UNION ALL
    SELECT c.root, c.slot_id, g.id
    FROM generated_question g JOIN chain c ON g.edited_from_question_id = c.id
),
per_root AS (
    SELECT c.root,
           bool_or(a.action = 'keep')    AS da_giu,
           bool_or(a.action = 'edit')    AS da_sua,
           bool_or(a.action = 'replace') AS da_doi,
           bool_or(a.id IS NOT NULL OR s.generated_question_id = c.id) AS tung_nam_trong_cho
    FROM chain c
    JOIN exam_slot s ON s.id = c.slot_id
    LEFT JOIN exam_slot_action a ON a.slot_id = c.slot_id AND a.generated_question_id = c.id
    GROUP BY c.root, c.slot_id
)
SELECT g.question_type,
       CASE
           WHEN p.da_giu AND p.da_sua THEN 'giu_sau_khi_sua'
           WHEN p.da_giu THEN 'giu_nguyen'
           WHEN p.da_sua AND p.da_doi THEN 'sua_roi_doi'
           WHEN p.da_doi THEN 'doi'
           WHEN p.da_sua THEN 'chi_sua'
           ELSE 'chua_thao_tac'
       END AS ket_cuc,
       count(*) AS so_cau_llm_solver_lech
FROM per_root p
JOIN generated_question g ON g.id = p.root
WHERE p.tung_nam_trong_cho
GROUP BY g.question_type, ket_cuc
ORDER BY g.question_type, so_cau_llm_solver_lech DESC;
