-- 10-yeu-cau-giao-vien.sql
-- Yêu cầu của giáo viên (chip, PR B, docs/design/kiem-soat-do-kho.md mục 4.6): Solver kết luận câu có đúng yêu cầu không
-- (quality_check_result.request_verdict; Solver v5 kiểm trong cùng lượt gọi, từ 14/11 Solver v6 kiểm ở lượt thứ hai). Ba bảng trong MỘT kết quả (cột "bang"):
--   * 'ma'       : tỉ lệ met / partial / not_met theo từng mã chip; câu có nhiều mã được tính cho mỗi mã.
--   * 'scope'    : tỉ lệ theo mức lấy yêu cầu của job sinh câu (generation_job.request_scope: replace, cell, exam).
--   * 'doi_sau'  : số lượt Đổi câu ngay sau một câu not_met hay partial của cùng chỗ (exam_slot_action replace có
--                  generated_question_id là câu đó); "nhom" là kết luận của câu bị Đổi.
-- Cột: so_cau (số câu có kết luận met/partial/not_met; với 'doi_sau' là số lượt Đổi), met, partial, not_met (số câu),
-- chua_kiem (14/11, Solver hai lượt: số câu có yêu cầu mà không kiểm, request_verdict = 'unchecked'; không tính vào so_cau),
-- ti_le_*_phan_tram (trên so_cau).
--
-- Câu tính: câu LLM gốc (edited_from_question_id IS NULL, origin = 'llm') có dòng answer_check với request_verdict khác
-- NULL (câu không có yêu cầu hoặc Solver không trả kết luận hợp lệ thì không tính), gắn với một chỗ của đề giáo viên.
-- GIỚI HẠN: kết luận là của Solver (LLM), không phải của giáo viên; đối chiếu bằng đánh giá thủ công (mục 5 của tài liệu).
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; loại đề của tài khoản nhóm phát triển (biến psql
-- dev_emails).
--
-- dev_emails: email cần loại (nhóm phát triển, tài khoản dùng thử), cách nhau bằng dấu phẩy, truyền khi chạy
-- (KHÔNG ghi email thật vào file):
--   psql -v dev_emails='nhom1@truong.edu.vn,gv-thu@truong.edu.vn' -f 10-yeu-cau-giao-vien.sql
-- Không truyền thì rỗng (không loại ai). Xem docs/thong-ke/README.md.
\if :{?dev_emails}
\else
\set dev_emails ''
\endif
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
checked AS (
    SELECT g.id, g.request_codes, j.request_scope, a.request_verdict, j.exam_slot_id
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL AND g.origin = 'llm'
    JOIN quality_check_result a ON a.question_id = g.id AND a.check_type = 'answer_check'
        AND a.request_verdict IS NOT NULL
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    WHERE j.job_type = 'generate'
),
by_code AS (
    SELECT 'ma'::text AS bang, code AS nhom, request_verdict
    FROM checked CROSS JOIN LATERAL unnest(coalesce(request_codes, '{}'::text[])) AS code
),
by_scope AS (
    SELECT 'scope'::text AS bang, coalesce(request_scope, '(không rõ)') AS nhom, request_verdict FROM checked
),
rates AS (
    SELECT bang, nhom,
           count(*) FILTER (WHERE request_verdict <> 'unchecked') AS so_cau,
           count(*) FILTER (WHERE request_verdict = 'met')     AS met,
           count(*) FILTER (WHERE request_verdict = 'partial') AS partial,
           count(*) FILTER (WHERE request_verdict = 'not_met') AS not_met,
           count(*) FILTER (WHERE request_verdict = 'unchecked') AS chua_kiem
    FROM (SELECT * FROM by_code UNION ALL SELECT * FROM by_scope) x
    GROUP BY bang, nhom
),
replaced_after AS (
    SELECT 'doi_sau'::text AS bang, c.request_verdict AS nhom, count(*) AS so_cau,
           0::bigint AS met, 0::bigint AS partial, 0::bigint AS not_met, 0::bigint AS chua_kiem
    FROM checked c
    JOIN exam_slot_action x ON x.slot_id = c.exam_slot_id AND x.action = 'replace' AND x.generated_question_id = c.id
    WHERE c.request_verdict IN ('not_met', 'partial')
    GROUP BY c.request_verdict
)
SELECT bang, nhom, so_cau, met, partial, not_met, chua_kiem,
       CASE WHEN bang = 'doi_sau' THEN NULL ELSE round(100.0 * met / NULLIF(so_cau, 0), 1) END     AS ti_le_met_phan_tram,
       CASE WHEN bang = 'doi_sau' THEN NULL ELSE round(100.0 * partial / NULLIF(so_cau, 0), 1) END AS ti_le_partial_phan_tram,
       CASE WHEN bang = 'doi_sau' THEN NULL ELSE round(100.0 * not_met / NULLIF(so_cau, 0), 1) END AS ti_le_not_met_phan_tram
FROM (SELECT * FROM rates UNION ALL SELECT * FROM replaced_after) t
ORDER BY bang, nhom;
