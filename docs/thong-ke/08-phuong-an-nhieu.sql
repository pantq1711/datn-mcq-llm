-- 08-phuong-an-nhieu.sql
-- Phương án nhiễu kèm lỗi sai của học sinh (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md mục 8). Dạng dài, mỗi dòng
-- một chỉ số (cột "chi_so") của một nhóm (cột "nhom"): so_cau là tử số, mau_so là mẫu số, ti_le_phan_tram = so_cau/mau_so.
--   * 'du_loi'          : câu mcq có chữ lỗi ở đủ 3 phương án sai / tổng câu mcq (nhom 'mcq').
--   * 'ket_qua_loi'     : theo TỪNG LỖI (mẫu số: số lỗi có chữ ở phương án sai): nhom match / mismatch / unknown /
--                         unchecked (unchecked: câu không có dòng answer_check hoặc dòng không có khóa của lỗi đó).
--   * 'ca_3_khop'       : câu có đủ 3 lỗi và cả 3 match / câu có đủ 3 lỗi (nhom 'mcq').
--   * 'giu'             : câu được Giữ (kể cả sau khi sửa) / số câu trong nhóm; nhom co_mismatch (ít nhất một lỗi mismatch)
--                         hoặc khong_mismatch (câu có ít nhất một lỗi, không lỗi nào mismatch).
--   * 'doi'             : câu bị Đổi / số câu trong nhóm, cùng hai nhóm trên.
--   * 'doi_mo_ho'       : số lượt Đổi có replace_reason = 'ambiguous_options' (mẫu số: số lượt Đổi của nhóm), cùng hai
--                         nhóm trên.
-- Kết quả kiểm lấy ở dòng answer_check của CHÍNH câu LLM gốc (lúc sinh), không phải của bản giáo viên sửa.
--
-- Câu tính: câu LLM gốc mcq (edited_from_question_id IS NULL, origin = 'llm') của run production có prompt_version
-- 'matrix-mcq-v10-draft' trở đi (bản prompt đầu tiên yêu cầu distractor_errors), gắn với một chỗ của đề giáo viên. Giữ/Đổi
-- gom theo cả chuỗi bản sửa như file 01.
-- GIỚI HẠN: "match" chỉ nói Solver (LLM, cùng họ model với bộ sinh) đồng ý rằng áp lỗi đã nêu ra đúng phương án; không nói
-- lỗi đó là lỗi học sinh thật hay mắc (đối chiếu bằng đánh giá thủ công, thiết kế mục 8.1).
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; loại đề của tài khoản nhóm phát triển (biến psql
-- dev_emails).
--
-- dev_emails: email cần loại (nhóm phát triển, tài khoản dùng thử), cách nhau bằng dấu phẩy, truyền khi chạy
-- (KHÔNG ghi email thật vào file):
--   psql -v dev_emails='nhom1@truong.edu.vn,gv-thu@truong.edu.vn' -f 08-phuong-an-nhieu.sql
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
    SELECT DISTINCT g.id AS root, j.exam_slot_id AS slot_id, g.correct_option, g.distractor_errors,
           a.id AS answer_row, a.distractor_checks
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL AND g.origin = 'llm'
        AND g.question_type = 'mcq'
    JOIN experiment_run r ON r.id = g.run_id AND r.purpose = 'production'
        AND r.prompt_version ~ '^matrix-mcq-v[0-9]+-'
        AND substring(r.prompt_version FROM '^matrix-mcq-v([0-9]+)-')::int >= 10
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    LEFT JOIN quality_check_result a ON a.question_id = g.id AND a.check_type = 'answer_check'
    WHERE j.job_type = 'generate'
),
errors AS (
    SELECT r.root,
           CASE WHEN r.answer_row IS NULL OR (r.distractor_checks ->> e.key) IS NULL THEN 'unchecked'
                ELSE r.distractor_checks ->> e.key END AS ket_qua
    FROM roots r
    CROSS JOIN LATERAL jsonb_each_text(coalesce(r.distractor_errors, '{}'::jsonb)) AS e(key, value)
    WHERE e.key <> coalesce(r.correct_option, '') AND btrim(coalesce(e.value, '')) <> ''
),
per_root AS (
    SELECT r.root, r.slot_id,
           count(e.root)                                    AS so_loi,
           count(e.root) FILTER (WHERE e.ket_qua = 'match')    AS so_match,
           count(e.root) FILTER (WHERE e.ket_qua = 'mismatch') AS so_mismatch
    FROM roots r LEFT JOIN errors e ON e.root = r.root
    GROUP BY r.root, r.slot_id
),
chain AS (
    SELECT root, slot_id, root AS id FROM per_root
    UNION ALL
    SELECT c.root, c.slot_id, g.id
    FROM generated_question g JOIN chain c ON g.edited_from_question_id = c.id
),
outcome AS (
    SELECT p.root,
           CASE WHEN p.so_mismatch > 0 THEN 'co_mismatch' ELSE 'khong_mismatch' END AS nhom,
           bool_or(a.action = 'keep')    AS da_giu,
           bool_or(a.action = 'replace') AS da_doi,
           count(a.id) FILTER (WHERE a.action = 'replace')                                          AS luot_doi,
           count(a.id) FILTER (WHERE a.action = 'replace' AND a.replace_reason = 'ambiguous_options') AS luot_doi_mo_ho
    FROM per_root p
    JOIN chain c ON c.root = p.root
    LEFT JOIN exam_slot_action a ON a.slot_id = c.slot_id AND a.generated_question_id = c.id
    WHERE p.so_loi > 0
    GROUP BY p.root, p.so_mismatch
),
chi_tieu AS (
    SELECT 1 AS thu_tu, 'du_loi'::text AS chi_so, 'mcq'::text AS nhom,
           count(*) FILTER (WHERE so_loi >= 3) AS so_cau, count(*) AS mau_so
    FROM per_root
    UNION ALL
    SELECT 2, 'ket_qua_loi', k.ket_qua, (SELECT count(*) FROM errors e WHERE e.ket_qua = k.ket_qua),
           (SELECT count(*) FROM errors)
    FROM (VALUES ('match'), ('mismatch'), ('unknown'), ('unchecked')) AS k(ket_qua)
    UNION ALL
    SELECT 3, 'ca_3_khop', 'mcq', count(*) FILTER (WHERE so_match >= 3), count(*) FILTER (WHERE so_loi >= 3)
    FROM per_root
    UNION ALL
    SELECT 4, 'giu', g.nhom, (SELECT count(*) FROM outcome o WHERE o.nhom = g.nhom AND o.da_giu),
           (SELECT count(*) FROM outcome o WHERE o.nhom = g.nhom)
    FROM (VALUES ('co_mismatch'), ('khong_mismatch')) AS g(nhom)
    UNION ALL
    SELECT 5, 'doi', g.nhom, (SELECT count(*) FROM outcome o WHERE o.nhom = g.nhom AND o.da_doi),
           (SELECT count(*) FROM outcome o WHERE o.nhom = g.nhom)
    FROM (VALUES ('co_mismatch'), ('khong_mismatch')) AS g(nhom)
    UNION ALL
    SELECT 6, 'doi_mo_ho', g.nhom, (SELECT coalesce(sum(o.luot_doi_mo_ho), 0) FROM outcome o WHERE o.nhom = g.nhom),
           (SELECT coalesce(sum(o.luot_doi), 0) FROM outcome o WHERE o.nhom = g.nhom)
    FROM (VALUES ('co_mismatch'), ('khong_mismatch')) AS g(nhom)
)
SELECT chi_so, nhom, so_cau, mau_so,
       CASE WHEN mau_so = 0 THEN NULL ELSE round(100.0 * so_cau / mau_so, 1) END AS ti_le_phan_tram
FROM chi_tieu
ORDER BY thu_tu, nhom;
