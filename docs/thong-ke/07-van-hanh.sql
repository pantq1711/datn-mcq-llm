-- 07-van-hanh.sql
-- Số đo vận hành của hàng đợi job sinh câu theo ngày và loại job (generate, answer_check, grounding_check): số job đã kết thúc,
-- tỉ lệ thất bại, thời gian chờ và thời gian xử lý (trung vị p50 và p95, đơn vị giây). Bổ sung cho số liệu thời gian thực ở
-- Prometheus/Grafana (docs/deploy/README.md mục "Giám sát"): file này trả lời câu hỏi cho cả khoảng thời gian dài, kể cả khi
-- Prometheus đã xóa dữ liệu cũ (giữ 15 ngày).
--
-- Cách tính (từ generation_job):
--   * "đã kết thúc" = status 'done' hoặc 'failed' và đã được worker nhận (started_at IS NOT NULL).
--   * cho_*  = started_at - created_at: từ lúc xếp hàng tới LẦN NHẬN CUỐI. Gồm giãn cách giữa các lượt gọi Gemini
--     (app.llm.min-spacing-ms), thời gian chờ lùi (backoff) khi thử lại và thời gian hoãn khi hết hạn mức ngày.
--   * xu_ly_* = updated_at - started_at: từ lúc nhận tới lúc ghi trạng thái cuối. Gồm các lượt gọi Gemini và QC của job.
--   * so_stale_reset: job còn mang last_error = 'stale: reset' khi kết thúc. Job bị reset rồi chạy lại thành công có thể đã bị
--     ghi đè last_error nên con số này là CẬN DƯỚI; số đếm đầy đủ theo thời gian nằm ở metric datn_job_stale_reset_total.
--   * thời gian tạo đề (từ lúc bấm tạo tới lúc đề sẵn sàng) chưa có metric riêng vì phụ thuộc cả hàng đợi bất đồng bộ; xem
--     cho_p95_giay của job generate làm số đo gần đúng.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): chỉ job của đề chế độ giáo viên (exam.mode = 'teacher'), loại tài khoản của
-- nhóm phát triển (điền email vào ARRAY[...]); job không gắn chỗ trong đề (exam_slot_id IS NULL, ví dụ job Solver của câu
-- được sửa) vẫn được tính. Không lọc theo origin vì job chỉ sinh ra cho câu LLM.
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (ARRAY[]::text[])   -- ví dụ ARRAY['nhom1@truong.edu.vn']
),
jobs AS (
    SELECT j.job_type, j.status, j.last_error, j.created_at, j.started_at, j.updated_at,
           j.created_at::date AS ngay
    FROM generation_job j
    LEFT JOIN exam_slot s ON s.id = j.exam_slot_id
    LEFT JOIN exam e ON e.id = s.exam_id
    WHERE j.status IN ('done', 'failed')
      AND j.started_at IS NOT NULL
      AND (j.exam_slot_id IS NULL
           OR (e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)))
)
SELECT ngay,
       job_type,
       count(*)                                                        AS so_job_ket_thuc,
       count(*) FILTER (WHERE status = 'failed')                       AS so_that_bai,
       round(100.0 * count(*) FILTER (WHERE status = 'failed') / count(*), 1) AS ti_le_that_bai_phan_tram,
       count(*) FILTER (WHERE last_error = 'stale: reset')             AS so_stale_reset,
       round((percentile_cont(0.5)  WITHIN GROUP (ORDER BY extract(epoch FROM (started_at - created_at))))::numeric, 1) AS cho_p50_giay,
       round((percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM (started_at - created_at))))::numeric, 1) AS cho_p95_giay,
       round((percentile_cont(0.5)  WITHIN GROUP (ORDER BY extract(epoch FROM (updated_at - started_at))))::numeric, 1) AS xu_ly_p50_giay,
       round((percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM (updated_at - started_at))))::numeric, 1) AS xu_ly_p95_giay
FROM jobs
GROUP BY ngay, job_type
ORDER BY ngay DESC, job_type;
