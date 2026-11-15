-- 10-yeu-cau-giao-vien.sql
-- Yêu cầu của giáo viên (chip, PR B, docs/design/kiem-soat-do-kho.md mục 4.6): Solver kết luận câu có đúng yêu cầu không
-- (quality_check_result.request_verdict; Solver v5 kiểm trong cùng lượt gọi, từ 14/11 Solver v6 kiểm ở lượt thứ hai). Ba bảng trong MỘT kết quả (cột "bang"):
--   * 'ma'       : tỉ lệ met / partial / not_met theo từng mã chip; câu có nhiều mã được tính cho mỗi mã.
--   * 'scope'    : tỉ lệ theo mức lấy yêu cầu của job sinh câu (generation_job.request_scope: replace, cell, exam).
--                  D-08 (15/11): từ bản sửa này, Đổi câu gửi lại ĐÚNG yêu cầu thừa hưởng (hộp Đổi điền sẵn, giáo viên không
--                  sửa) giữ mức cell/exam; 'replace' chỉ còn là lượt Đổi giáo viên tự đặt yêu cầu khác. Dữ liệu deploy
--                  trước bản sửa: mọi lượt Đổi từ hộp Đổi câu đều là 'replace' (kể cả bản sao yêu cầu của ô/đề), nên ở
--                  khoảng đó bảng này là "câu sau Đổi so với câu lúc tạo đề", không phải "mức lấy yêu cầu".
--   * 'doi_sau'  : số lượt Đổi câu ngay sau một câu not_met hay partial của cùng chỗ (exam_slot_action replace có
--                  generated_question_id là câu đó HOẶC một bản giáo viên sửa của nó, D-09 15/11: gom theo chuỗi bản sửa
--                  như 01-ket-cuc-cau-llm.sql); "nhom" là kết luận của câu LLM gốc bị Đổi.
-- Cột: so_cau (số câu có kết luận met/partial/not_met; với 'doi_sau' là số lượt Đổi), met, partial, not_met (số câu),
-- chua_kiem (14/11, Solver hai lượt: số câu có yêu cầu mà không kiểm, request_verdict = 'unchecked'; không tính vào so_cau),
-- ti_le_*_phan_tram (trên so_cau), ti_le_met_ke_ca_chua_kiem_phan_tram và ti_le_chua_kiem_phan_tram (D-09: trên
-- so_cau + chua_kiem).
-- THIÊN LỆCH (D-09): chua_kiem gồm câu Solver gắn cờ đáp án ở lượt giải (không gọi lượt kiểm yêu cầu), tức đúng những câu dễ
-- KHÔNG đạt yêu cầu (chip common_trap hay ra câu nhiều đáp án đúng). Vì vậy ti_le_met_phan_tram (trên so_cau) là CẬN TRÊN,
-- ti_le_met_ke_ca_chua_kiem_phan_tram (coi câu chưa kiểm là chưa đạt) là CẬN DƯỚI. Báo cáo cả hai.
--
-- Câu tính: câu LLM gốc (edited_from_question_id IS NULL, origin = 'llm') có dòng answer_check với request_verdict khác
-- NULL (câu không có yêu cầu hoặc Solver không trả kết luận hợp lệ thì không tính), gắn với một chỗ của đề giáo viên.
-- GIỚI HẠN: kết luận là của Solver (LLM), không phải của giáo viên; đối chiếu bằng đánh giá thủ công (mục 5 của tài liệu).
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; loại đề của tài khoản nhóm phát triển (biến psql
-- dev_emails).
--
-- CỬA SỔ SOLVER (D-07, 15/11): chỉ tính dòng answer_check có checked_at trong [solver_tu, solver_den) (giờ VPS,
-- Asia/Bangkok). Solver v5 (một lượt, yêu cầu kiểm cùng lượt giải) và v6 (hai lượt) nằm chung bảng, không có cột phiên bản;
-- KHÔNG trộn hai bản trong một kết quả. Mặc định solver_tu = mốc merge của v6 (PR #76); thay bằng giờ deploy thật của v6
-- trên VPS và chọn cửa sổ của từng bản theo bảng "Mốc phiên bản prompt và Solver" ở docs/thong-ke/README.md:
--   psql -v dev_emails='...' -v solver_tu='2026-11-14 19:24:15' -f 10-yeu-cau-giao-vien.sql             (v6)
--   psql -v dev_emails='...' -v solver_tu='2026-11-13 23:24:18' -v solver_den='2026-11-14 19:24:15' -f ...  (v5)
--
-- dev_emails: email cần loại (nhóm phát triển, tài khoản dùng thử), cách nhau bằng dấu phẩy, truyền khi chạy
-- (KHÔNG ghi email thật vào file):
--   psql -v dev_emails='nhom1@truong.edu.vn,gv-thu@truong.edu.vn' -f 10-yeu-cau-giao-vien.sql
-- Không truyền thì rỗng (không loại ai) và psql in cảnh báo. Xem docs/thong-ke/README.md.
\if :{?dev_emails}
\else
\echo 'CANH BAO: chua truyen -v dev_emails, so lieu GOM CA tai khoan nhom phat trien va tai khoan dung thu'
\set dev_emails ''
\endif
\if :{?solver_tu}
\else
\set solver_tu '2026-11-14 19:24:15'
\endif
\if :{?solver_den}
\else
\set solver_den '9999-12-31 00:00:00'
\endif
\echo 'Chi tinh answer_check co checked_at tu' :'solver_tu' 'den truoc' :'solver_den'
WITH RECURSIVE
dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
checked AS (
    SELECT g.id, g.request_codes, j.request_scope, a.request_verdict, j.exam_slot_id
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL AND g.origin = 'llm'
    JOIN quality_check_result a ON a.question_id = g.id AND a.check_type = 'answer_check'
        AND a.request_verdict IS NOT NULL
        AND a.checked_at >= :'solver_tu'::timestamp AND a.checked_at < :'solver_den'::timestamp
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    WHERE j.job_type = 'generate'
),
-- câu LLM gốc bị gắn not_met / partial và mọi bản giáo viên sửa của nó (D-09)
chain AS (
    SELECT c.id AS root, c.id AS id FROM checked c WHERE c.request_verdict IN ('not_met', 'partial')
    UNION ALL
    SELECT ch.root, g.id FROM generated_question g JOIN chain ch ON g.edited_from_question_id = ch.id
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
    JOIN chain ch ON ch.root = c.id
    JOIN exam_slot_action x ON x.slot_id = c.exam_slot_id AND x.action = 'replace' AND x.generated_question_id = ch.id
    WHERE c.request_verdict IN ('not_met', 'partial')
    GROUP BY c.request_verdict
)
SELECT bang, nhom, so_cau, met, partial, not_met, chua_kiem,
       CASE WHEN bang = 'doi_sau' THEN NULL ELSE round(100.0 * met / NULLIF(so_cau, 0), 1) END     AS ti_le_met_phan_tram,
       CASE WHEN bang = 'doi_sau' THEN NULL ELSE round(100.0 * partial / NULLIF(so_cau, 0), 1) END AS ti_le_partial_phan_tram,
       CASE WHEN bang = 'doi_sau' THEN NULL ELSE round(100.0 * not_met / NULLIF(so_cau, 0), 1) END AS ti_le_not_met_phan_tram,
       CASE WHEN bang = 'doi_sau' THEN NULL
            ELSE round(100.0 * met / NULLIF(so_cau + chua_kiem, 0), 1) END AS ti_le_met_ke_ca_chua_kiem_phan_tram,
       CASE WHEN bang = 'doi_sau' THEN NULL
            ELSE round(100.0 * chua_kiem / NULLIF(so_cau + chua_kiem, 0), 1) END AS ti_le_chua_kiem_phan_tram
FROM (SELECT * FROM rates UNION ALL SELECT * FROM replaced_after) t
ORDER BY bang, nhom;
