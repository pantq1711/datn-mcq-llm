-- 01-ket-cuc-cau-llm.sql
-- Mỗi câu LLM (đầu ra gốc) đã nằm trong một chỗ của đề giáo viên kết thúc ra sao: Giữ nguyên, Sửa rồi Giữ, Sửa rồi Đổi,
-- Đổi, chỉ Sửa (chưa Giữ), hay chưa thao tác. Gom theo dạng câu.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md):
--   * exam.mode = 'teacher' (đề tạo theo ma trận);
--   * chỉ câu LLM gốc: generated_question.edited_from_question_id IS NULL, run production có prompt_version 'matrix-%';
--     các bản giáo viên sửa được gom vào câu gốc qua chuỗi edited_from_question_id;
--   * loại đề của tài khoản nhóm phát triển: điền email vào ARRAY[...] bên dưới.
-- GIỚI HẠN: tỉ lệ Giữ/Sửa/Đổi là MỨC CHẤP NHẬN, không phải độ đúng (giáo viên đã thấy đáp án và lời giải của hệ thống).
WITH RECURSIVE
dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (ARRAY[]::text[])   -- ví dụ ARRAY['nhom1@truong.edu.vn']
),
roots AS (
    SELECT DISTINCT j.question_id AS root, j.exam_slot_id AS slot_id
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL
    JOIN experiment_run r ON r.id = g.run_id AND r.purpose = 'production' AND r.prompt_version LIKE 'matrix-%'
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
       count(*) AS so_cau_llm,
       round(100.0 * count(*) / sum(count(*)) OVER (PARTITION BY g.question_type), 1) AS ti_le_phan_tram
FROM per_root p
JOIN generated_question g ON g.id = p.root
WHERE p.tung_nam_trong_cho
GROUP BY g.question_type, ket_cuc
ORDER BY g.question_type, so_cau_llm DESC;
