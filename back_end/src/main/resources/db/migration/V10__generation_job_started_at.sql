-- V10__generation_job_started_at.sql
-- Đợt A (ngày 23/10), sau báo cáo kiểm tra tải. Không sửa V1-V9.
--
-- generation_job.started_at: lúc worker nhận job (chuyển sang 'running'). Job 'running' có started_at quá
-- app.worker.stale-after-seconds (mặc định 300) được trả về 'pending' khi backend khởi động và định kỳ (StaleJobRecovery),
-- không tính vào attempt. NULL với job chưa từng được nhận, job đã reset, và mọi dòng trước V10 (khi đó xét updated_at).

ALTER TABLE generation_job
    ADD COLUMN started_at TIMESTAMP;

COMMENT ON COLUMN generation_job.started_at IS
    'Lúc worker nhận job; dùng để trả job running quá hạn về pending. NULL khi chưa nhận hoặc đã reset.';
