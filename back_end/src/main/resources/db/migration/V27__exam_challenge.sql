-- Độ sâu đề (12/11, góp ý dùng thử 11/11 "đề chưa có chiều sâu, chưa thấy câu nâng cao"). Không sửa V1-V26.
--
-- * exam.challenge: mức thử thách giáo viên chọn khi tạo đề theo ma trận hoặc theo bài học. 'standard' (mặc định, mọi đề
--   cũ và đề thủ công) hoặc 'advanced' (prompt của ô hoặc ý tf mức Vận dụng có thêm khối "Mức NÂNG CAO"; ô đó không lấy
--   câu ngân hàng). Đổi câu trong đề nâng cao giữ mức. API từ chối giá trị khác (400) trước khi ghi; CHECK dưới đây là chốt
--   chặn cuối ở mức database.

ALTER TABLE exam ADD COLUMN challenge VARCHAR(10) NOT NULL DEFAULT 'standard';

ALTER TABLE exam
    ADD CONSTRAINT chk_exam_challenge CHECK (challenge IN ('standard', 'advanced'));

COMMENT ON COLUMN exam.challenge IS
    'Độ sâu (12/11): mức thử thách của đề, standard (mặc định) hoặc advanced (khối nâng cao ở ô Vận dụng).';
