-- d2-truoc-sau.sql
-- D2 (chương 4), kế hoạch phân tích mục 6: số câu sinh ra / qua format / bị dedup đánh dấu trùng theo từng đề giáo viên,
-- cho hai dòng "Định dạng hợp lệ" và "Không trùng" của bảng trước/sau QC. CHỈ ĐỌC, một câu SELECT.
--
-- Chạy (An tự chạy, chỉ trên DB thực nghiệm datn_mcq_d2):
--   psql -X -A -t -v ON_ERROR_STOP=1 -d datn_mcq_d2 -f tools/measurement/sql/d2-truoc-sau.sql -o d2-qc-dem.jsonl
--   rồi: python -m do_luong.phan_tich_d2 bao-cao ... --lay-mau-log lay-mau-log.json --qc-dem d2-qc-dem.jsonl
--   phan_tich_d2 chỉ lấy các đề trong lay-mau-log (de_thi_id) và cảnh báo nếu tổng qua_format khác số câu trong log.
--
-- Phạm vi câu: GIỐNG d2-xuat-cau.sql (câu LLM gốc do job 'generate' của đề chế độ giáo viên sinh ra, đúng phiên bản prompt),
-- nhưng KHÔNG lọc format: cau_sinh_ra = mọi câu đã lưu (job generate có câu), qua_format = câu có dòng format passed.
-- Lượt gọi LLM không ra được câu nào (không lưu generated_question) không có trong số này.
--   trung     câu qua format bị dedup kết luận trùng (passed = false, có câu bị so trùng).
--   dedup_loi câu qua format mà dedup lỗi hoặc thiếu phạm vi (passed = false, không có câu bị so trùng): không tính là trùng.
--   dedup_chua_chay câu qua format chưa có dòng dedup.
--
-- Biến psql prompt_versions: như d2-xuat-cau.sql (test_sql_xuat giữ khớp phiên bản main).
-- Không dùng chuỗi có dấu "\" (một số DB tắt standard_conforming_strings).
\if :{?prompt_versions}
\else
\set prompt_versions 'matrix-mcq-v9-draft,matrix-tf-v12-draft,matrix-sa-v12-draft'
\endif
WITH llm_jobs AS (
    SELECT j.question_id, min(s.id) AS slot_id
    FROM generation_job j
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher'
    WHERE j.job_type = 'generate' AND j.question_id IS NOT NULL
    GROUP BY j.question_id
),
cau AS (
    SELECT g.id, lj.slot_id
    FROM llm_jobs lj
    JOIN generated_question g ON g.id = lj.question_id
    JOIN experiment_run r ON r.id = g.run_id
    WHERE g.origin = 'llm'
      AND g.edited_from_question_id IS NULL
      AND r.prompt_version = ANY (string_to_array(replace(:'prompt_versions', ' ', ''), ','))
)
SELECT json_build_object(
    'de_thi_id', s.exam_id,
    'cau_sinh_ra', count(*),
    'qua_format', count(*) FILTER (WHERE f.passed),
    'trung', count(*) FILTER (WHERE f.passed AND d.passed = false AND d.compared_to_question_id IS NOT NULL),
    'dedup_loi', count(*) FILTER (WHERE f.passed AND d.passed = false AND d.compared_to_question_id IS NULL),
    'dedup_chua_chay', count(*) FILTER (WHERE f.passed AND d.id IS NULL)
)
FROM cau c
JOIN exam_slot s ON s.id = c.slot_id
LEFT JOIN quality_check_result f ON f.question_id = c.id AND f.check_type = 'format'
LEFT JOIN quality_check_result d ON d.question_id = c.id AND d.check_type = 'dedup'
GROUP BY s.exam_id
ORDER BY s.exam_id;
