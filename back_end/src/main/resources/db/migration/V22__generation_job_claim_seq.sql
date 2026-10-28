-- V22__generation_job_claim_seq.sql
-- W2-4b (A) (ngày 27/10): hàng rào claim cho kết quả của worker. Không sửa V1-V21.
--
-- * generation_job.claim_seq: tăng 1 MỖI LẦN worker nhận job (claimNextPendingJob) và KHÔNG BAO GIỜ giảm. Worker giữ
--   giá trị đã nhận; mọi ghi kết quả (lưu câu, finish, fail, kết quả answer_check/grounding) chỉ có hiệu lực khi
--   UPDATE ... WHERE id = ? AND status = 'running' AND claim_seq = ? tác động đúng một dòng. Worker chậm bị job trả về
--   pending (StaleJobRecovery) rồi worker khác nhận lại thì claim_seq đã khác, nên kết quả muộn của worker cũ bị bỏ.
-- * Không dùng attempt làm hàng rào: StaleJobRecovery hoàn lại lượt attempt khi trả job kẹt (GREATEST(attempt - 1, 0)),
--   nên worker nhận lại có cùng giá trị attempt với worker cũ.
-- Dòng cũ có claim_seq = 0; job đang running lúc nâng cấp (nhận trước V22) giữ 0 cho tới lần nhận kế tiếp.

ALTER TABLE generation_job
    ADD COLUMN claim_seq INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_gj_claim_seq CHECK (claim_seq >= 0);

COMMENT ON COLUMN generation_job.claim_seq IS
    'W2-4b: tăng mỗi lần worker nhận job, không bao giờ giảm; hàng rào cho mọi ghi kết quả của worker (khác attempt, bị hoàn lại khi reset job kẹt).';

