-- docs/experiments/trial/queries.sql
-- Truy vấn THỐNG KÊ DÙNG THỬ (B7 tối thiểu): chỉ SELECT, không ghi gì. Chạy từng khối bằng psql (hoặc công cụ SQL) trên
-- database của hệ thống đã triển khai; mỗi khối là một câu lệnh độc lập, kết thúc bằng dấu chấm phẩy.
--
-- BỘ LỌC CHUNG (như docs/thong-ke/README.md): chỉ đề chế độ giáo viên (exam.mode = 'teacher'); loại đề của tài khoản
-- nhóm phát triển và tài khoản dùng thử: biến psql dev_emails (khai báo một lần ở dưới, dùng chung cho mọi khối).
--
-- GIỚI HẠN (phải ghi trong báo cáo): giáo viên nhìn thấy đáp án, lời giải và cờ "cần xem kỹ" khi quyết định, nên Giữ /
-- Sửa / Đổi chỉ phản ánh mức chấp nhận, không phải độ đúng; Đổi không có nghĩa câu sai. Cột "co_co" bên dưới là cờ ƯỚC
-- TÍNH từ dữ liệu đã lưu (Solver lệch hoặc chưa chạy, dedup chưa chạy hoặc trùng, cảnh báo format không thuộc nhóm tự sửa,
-- grounding không khớp hoặc không kết luận). Cờ thật (reviewReasons) tính lúc xem đề và còn có similar_in_exam nên có
-- thể khác; muốn đúng từng lý do thì lấy từ API GET /api/exams/{id}/slots/{slotId}/qc-trace.
-- replace_reason (V25) có giá trị: wrong_answer, ambiguous_options, off_matrix, duplicate_pattern, latex_format,
-- too_hard, too_easy, other; NULL khi giáo viên không chọn lý do (hoặc dòng Đổi trước V25).
--
-- dev_emails: email cần loại, cách nhau bằng dấu phẩy, truyền khi chạy (KHÔNG ghi email thật vào file):
--   psql -v dev_emails='nhom1@truong.edu.vn,gv-thu@truong.edu.vn' -f queries.sql
-- Không truyền thì rỗng (không loại ai). Chạy riêng một khối trong công cụ khác psql thì thay :'dev_emails' bằng chuỗi
-- email, ví dụ 'nhom1@truong.edu.vn,gv-thu@truong.edu.vn'. Xem docs/thong-ke/README.md.
\if :{?dev_emails}
\else
\set dev_emails ''
\endif

-- ===========================================================================================================
-- Q1. Quy mô: số đề, số chỗ (câu), số chỗ theo trạng thái
-- ===========================================================================================================
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
trial_exams AS (
    SELECT e.id FROM exam e
    WHERE e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
)
SELECT (SELECT count(*) FROM trial_exams) AS so_de,
       count(*) AS so_cau,
       count(*) FILTER (WHERE s.state = 'ready') AS dang_cho_thao_tac,
       count(*) FILTER (WHERE s.state = 'kept') AS da_giu,
       count(*) FILTER (WHERE s.state = 'edited') AS da_sua_roi_giu,
       count(*) FILTER (WHERE s.state = 'failed') AS hong,
       count(*) FILTER (WHERE s.state IN ('generating', 'pending')) AS dang_sinh
FROM exam_slot s
WHERE s.exam_id IN (SELECT id FROM trial_exams);

-- ===========================================================================================================
-- Q2. Tỉ lệ câu GIỮ NGUYÊN / SỬA / ĐỔI theo chỗ (một chỗ chỉ tính một kết cục, ưu tiên Đổi > Sửa > Giữ nguyên):
--     doi = có ít nhất một thao tác Đổi; sua = có Sửa (kể cả đã Giữ sau khi Sửa) mà không Đổi;
--     giu_nguyen = Giữ mà chưa từng Sửa hay Đổi; chua_thao_tac = còn lại.
-- ===========================================================================================================
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
per_slot AS (
    SELECT s.id AS slot_id,
           bool_or(a.action = 'replace') AS da_doi,
           bool_or(a.action = 'edit') AS da_sua,
           bool_or(a.action = 'keep') AS da_giu
    FROM exam_slot s
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    LEFT JOIN exam_slot_action a ON a.slot_id = s.id
    GROUP BY s.id
),
outcome AS (
    SELECT CASE
               WHEN coalesce(da_doi, FALSE) THEN 'doi'
               WHEN coalesce(da_sua, FALSE) THEN 'sua'
               WHEN coalesce(da_giu, FALSE) THEN 'giu_nguyen'
               ELSE 'chua_thao_tac'
           END AS ket_cuc
    FROM per_slot
)
SELECT ket_cuc,
       count(*) AS so_cho,
       round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS ty_le_phan_tram
FROM outcome
GROUP BY ket_cuc
ORDER BY so_cho DESC;

-- ===========================================================================================================
-- Q3. Các lần ĐỔI theo lý do (replace_reason) và theo nguồn câu thay thế (bank = ngân hàng, generate = sinh mới)
-- ===========================================================================================================
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
)
SELECT coalesce(a.replace_reason, '(không chọn)') AS ly_do_doi,
       a.replace_source AS nguon_thay_the,
       count(*) AS so_lan_doi,
       round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS ty_le_phan_tram
FROM exam_slot_action a
JOIN exam_slot s ON s.id = a.slot_id
JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
WHERE a.action = 'replace'
GROUP BY a.replace_reason, a.replace_source
ORDER BY so_lan_doi DESC;

-- ===========================================================================================================
-- Q4. Câu LLM (cả chuỗi bản sửa gom về câu gốc, không tính câu ngân hàng) CÓ CỜ hay KHÔNG CÓ CỜ so với kết cục Giữ / Sửa /
--     Đổi. Mỗi câu gốc một dòng; kết cục như Q2 nhưng tính trên các thao tác áp lên câu đó hoặc bản sửa của nó.
-- ===========================================================================================================
WITH RECURSIVE
dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
roots AS (
    SELECT DISTINCT j.question_id AS root, j.exam_slot_id AS slot_id
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL AND g.origin = 'llm'
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
flags AS (
    SELECT g.id AS question_id,
           ARRAY_REMOVE(ARRAY[
               CASE WHEN a.id IS NULL THEN 'solver_chua_chay_hoac_loi' WHEN a.passed = FALSE THEN 'solver_lech' END,
               CASE WHEN d.id IS NULL THEN 'dedup_chua_chay' WHEN d.passed = FALSE THEN 'trung_cau' END,
               CASE WHEN EXISTS (
                       SELECT 1 FROM jsonb_array_elements_text(coalesce(f.warnings, '[]'::jsonb)) AS w(t)
                       WHERE NOT (t LIKE 'json: đã sửa%' OR t LIKE '%: đã tự đóng $' OR t LIKE '%: đã tự bọc $ quanh%'
                                  OR t ~ '^statement_[a-d]: đã bỏ tiền tố' OR t LIKE 'label: khác mẫu%')
                    ) THEN 'canh_bao_format' END,
               CASE WHEN gr.verdict IN ('not_supported', 'unknown') THEN 'grounding' END
           ], NULL) AS ly_do
    FROM generated_question g
    LEFT JOIN quality_check_result a ON a.question_id = g.id AND a.check_type = 'answer_check'
    LEFT JOIN quality_check_result d ON d.question_id = g.id AND d.check_type = 'dedup'
    LEFT JOIN quality_check_result f ON f.question_id = g.id AND f.check_type = 'format'
    LEFT JOIN question_grounding gr ON gr.question_id = g.id
),
per_root AS (
    SELECT c.root,
           bool_or(a.action = 'replace') AS da_doi,
           bool_or(a.action = 'edit') AS da_sua,
           bool_or(a.action = 'keep') AS da_giu
    FROM chain c
    LEFT JOIN exam_slot_action a ON a.slot_id = c.slot_id AND a.generated_question_id = c.id
    GROUP BY c.root, c.slot_id
)
SELECT CASE WHEN cardinality(fl.ly_do) > 0 THEN 'co_co' ELSE 'khong_co' END AS co_co,
       CASE
           WHEN coalesce(p.da_doi, FALSE) THEN 'doi'
           WHEN coalesce(p.da_sua, FALSE) THEN 'sua'
           WHEN coalesce(p.da_giu, FALSE) THEN 'giu_nguyen'
           ELSE 'chua_thao_tac'
       END AS ket_cuc,
       count(*) AS so_cau_llm
FROM per_root p
JOIN flags fl ON fl.question_id = p.root
GROUP BY 1, 2
ORDER BY 1, so_cau_llm DESC;

-- ===========================================================================================================
-- Q5. BẢNG CHÉO: lý do Đổi (replace_reason) x lý do cờ ước tính của câu bị đổi (câu sinh ra mà thao tác Đổi áp lên).
--     Một câu có nhiều cờ thì xuất hiện ở nhiều dòng (đếm theo cặp); câu không cờ có dòng '(không cờ)'.
--     Dòng Đổi lấy câu ngân hàng (generated_question_id NULL) hoặc câu ngân hàng không có dữ liệu QC thì không có cờ để so.
-- ===========================================================================================================
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
replaced AS (
    SELECT a.id AS action_id, a.replace_reason, a.generated_question_id AS question_id
    FROM exam_slot_action a
    JOIN exam_slot s ON s.id = a.slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    WHERE a.action = 'replace' AND a.generated_question_id IS NOT NULL
),
flags AS (
    SELECT r.action_id,
           ARRAY_REMOVE(ARRAY[
               CASE WHEN a.id IS NULL THEN 'solver_chua_chay_hoac_loi' WHEN a.passed = FALSE THEN 'solver_lech' END,
               CASE WHEN d.id IS NULL THEN 'dedup_chua_chay' WHEN d.passed = FALSE THEN 'trung_cau' END,
               CASE WHEN EXISTS (
                       SELECT 1 FROM jsonb_array_elements_text(coalesce(f.warnings, '[]'::jsonb)) AS w(t)
                       WHERE NOT (t LIKE 'json: đã sửa%' OR t LIKE '%: đã tự đóng $' OR t LIKE '%: đã tự bọc $ quanh%'
                                  OR t ~ '^statement_[a-d]: đã bỏ tiền tố' OR t LIKE 'label: khác mẫu%')
                    ) THEN 'canh_bao_format' END,
               CASE WHEN gr.verdict IN ('not_supported', 'unknown') THEN 'grounding' END
           ], NULL) AS ly_do
    FROM replaced r
    LEFT JOIN quality_check_result a ON a.question_id = r.question_id AND a.check_type = 'answer_check'
    LEFT JOIN quality_check_result d ON d.question_id = r.question_id AND d.check_type = 'dedup'
    LEFT JOIN quality_check_result f ON f.question_id = r.question_id AND f.check_type = 'format'
    LEFT JOIN question_grounding gr ON gr.question_id = r.question_id
)
SELECT coalesce(r.replace_reason, '(không chọn)') AS ly_do_doi,
       coalesce(x.co, '(không cờ)') AS ly_do_co,
       count(*) AS so_lan_doi
FROM replaced r
JOIN flags fl ON fl.action_id = r.action_id
LEFT JOIN LATERAL unnest(CASE WHEN cardinality(fl.ly_do) = 0 THEN ARRAY[NULL]::text[] ELSE fl.ly_do END) AS x(co) ON TRUE
GROUP BY 1, 2
ORDER BY 1, so_lan_doi DESC;
