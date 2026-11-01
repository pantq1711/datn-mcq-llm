-- V24__generation_quota.sql
-- D1 (soát backend trước deploy, ngày 30/10): giới hạn số câu yêu cầu sinh mỗi ngày theo từng giáo viên. Không sửa V1-V23.
--
-- * generation_quota: MỘT dòng mỗi giáo viên. requested là số câu đã yêu cầu hệ thống sinh (tạo đề, sinh theo bài, Đổi
--   câu bằng cách sinh mới) trong ngày usage_date; câu lấy từ ngân hàng không tính. usage_date là ngày theo giờ Việt Nam
--   (Asia/Ho_Chi_Minh) do ứng dụng tính; sang ngày mới thì dòng được ghi đè (ngày mới, đếm lại từ đầu), không cần dọn.
-- * Dòng này cũng là khóa tuần tự hóa theo giáo viên: ứng dụng khóa nó (FOR NO KEY UPDATE) rồi mới kiểm trần và tạo job
--   trong cùng transaction, nên hai request song song của cùng giáo viên không cùng lọt trần.

CREATE TABLE generation_quota (
    user_id     INT PRIMARY KEY,
    usage_date  DATE NOT NULL,
    requested   INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_gq_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_gq_requested CHECK (requested >= 0)
);

COMMENT ON TABLE generation_quota IS
    'D1: số câu giáo viên đã yêu cầu sinh trong ngày usage_date (giờ Việt Nam); dòng cũng là khóa tuần tự hóa theo giáo viên khi kiểm trần.';
