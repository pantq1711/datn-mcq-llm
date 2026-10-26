-- 05-grounding.sql
-- Kết quả QC "bám tài liệu" (W2-3) của câu LLM gốc nằm trong chỗ của đề giáo viên, theo dạng câu và theo việc câu được sinh
-- CÓ hay KHÔNG có tài liệu trong prompt: SUPPORTED / NOT_SUPPORTED / UNKNOWN, kèm các trạng thái chưa có kết quả
-- (dang_cho: job grounding còn chờ hoặc đang chạy; loi: job hết lượt thử; khong_cham: chưa có job), số câu có trích dẫn
-- kiểm chứng được, và số câu SUPPORTED bị hạ xuống UNKNOWN vì trích dẫn không có thật (lý do bắt đầu bằng 'evidence_not_found:').
-- Dùng cho thử nghiệm "tài liệu bài học": so tỉ lệ SUPPORTED giữa nhóm có và không có tài liệu.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; chỉ câu LLM gốc (edited_from_question_id IS NULL,
-- run production 'matrix-%'); loại đề của tài khoản nhóm phát triển (điền email vào ARRAY[...]). Bản giáo viên sửa cũng được
-- chấm lại và lưu ở question_grounding nhưng không phải đầu ra LLM nên không tính ở đây.
-- GIỚI HẠN: judge là một LLM cùng họ với bộ sinh câu nên có thể thiên về SUPPORTED; kết quả là cảnh báo chứ không phải độ
-- đúng. UNKNOWN nghĩa là các đoạn tài liệu đã đưa không đủ để kết luận (hoặc trích dẫn không kiểm chứng được). Nhóm KHÔNG có
-- tài liệu (co_tai_lieu = FALSE) chỉ có dòng 'khong_cham' cho tới khi có đường chấm riêng cho thí nghiệm; đừng suy ra tỉ lệ
-- SUPPORTED của nhóm đó từ file này.
WITH
dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (ARRAY[]::text[])   -- ví dụ ARRAY['nhom1@truong.edu.vn']
),
roots AS (
    SELECT DISTINCT ON (j.question_id) j.question_id AS id, (j.material_context IS NOT NULL) AS co_tai_lieu
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL
    JOIN experiment_run r ON r.id = g.run_id AND r.purpose = 'production' AND r.prompt_version LIKE 'matrix-%'
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    WHERE j.job_type = 'generate' AND j.question_id IS NOT NULL
    ORDER BY j.question_id, j.id DESC
),
per_question AS (
    SELECT g.question_type,
           ro.co_tai_lieu,
           CASE
               WHEN qg.question_id IS NOT NULL THEN qg.verdict
               WHEN gj.status IN ('pending', 'running') THEN 'dang_cho'
               WHEN gj.status = 'failed' THEN 'loi'
               ELSE 'khong_cham'
           END AS trang_thai,
           qg.evidence_found,
           coalesce(qg.reason LIKE 'evidence_not_found:%', FALSE) AS ha_vi_thieu_trich_dan
    FROM roots ro
    JOIN generated_question g ON g.id = ro.id
    LEFT JOIN question_grounding qg ON qg.question_id = ro.id
    LEFT JOIN LATERAL (
        SELECT status FROM generation_job
        WHERE question_id = ro.id AND job_type = 'grounding_check'
        ORDER BY id DESC LIMIT 1
    ) gj ON TRUE
)
SELECT question_type,
       co_tai_lieu,
       trang_thai,
       count(*) AS so_cau_llm,
       round(100.0 * count(*) / sum(count(*)) OVER (PARTITION BY question_type, co_tai_lieu), 1) AS ti_le_phan_tram,
       count(*) FILTER (WHERE evidence_found) AS trich_dan_kiem_chung_duoc,
       count(*) FILTER (WHERE ha_vi_thieu_trich_dan) AS ha_vi_thieu_trich_dan
FROM per_question
GROUP BY question_type, co_tai_lieu, trang_thai
ORDER BY question_type, co_tai_lieu DESC, so_cau_llm DESC, trang_thai;
