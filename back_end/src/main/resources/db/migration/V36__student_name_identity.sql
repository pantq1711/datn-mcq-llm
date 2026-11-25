-- V36__student_name_identity.sql
-- Học sinh nhập HỌ TÊN thay cho số thứ tự khi làm bài trực tuyến (docs/design/lam-bai-truc-tuyen.md, quyết định ngày
-- 25/11/2026 của chủ dự án, THAY quyết định 16/11 "không thu họ tên"). Không sửa V1-V35.
--
-- * exam_assignment.identity_mode: chế độ nhận diện của TỪNG bài giao, 'name' (họ và tên) hoặc 'seat' (số thứ tự). Bài
--   giao đã có trước V36 nhận 'seat' để hành vi không đổi; bài giao chèn sau V36 không ghi chế độ thì là 'name'. Đổi chế
--   độ của bài giao đã giao: không có (ngoài phạm vi).
-- * assignment_attempt.student_name: họ tên học sinh gõ, đã chuẩn hóa ở service (bỏ khoảng trắng đầu cuối, gộp khoảng
--   trắng, Unicode NFC, 2-60 ký tự, không ký tự điều khiển); giữ hoa thường như em gõ. Chỉ lưu ở bảng này; xóa bài giao
--   thì xóa theo (ON DELETE CASCADE của V32). seat_no hết NOT NULL: lượt chế độ tên không có số thứ tự.
-- * chk_aa_identity: mỗi lượt có ĐÚNG MỘT trong hai (số thứ tự hoặc họ tên). Lượt phải khớp chế độ của bài giao: service
--   kiểm (CHECK không đọc được bảng khác).
-- * So trùng (lớp + tên: chữ thường, giữ dấu) làm ở service bằng một hàm Java cho cả báo trùng trước khi bắt đầu, cờ
--   "trùng" của bảng điểm và thống kê câu, để không phụ thuộc lower() theo collation của DB. idx_aa_identity_name phục vụ
--   việc đọc họ tên các lượt của một lớp trong bài giao (đọc được chỉ từ index).

ALTER TABLE exam_assignment
    ADD COLUMN identity_mode VARCHAR(4) NOT NULL DEFAULT 'seat';
ALTER TABLE exam_assignment
    ALTER COLUMN identity_mode SET DEFAULT 'name',
    ADD CONSTRAINT chk_ea_identity_mode CHECK (identity_mode IN ('name', 'seat'));

ALTER TABLE assignment_attempt
    ADD COLUMN student_name VARCHAR(60),
    ALTER COLUMN seat_no DROP NOT NULL,
    ADD CONSTRAINT chk_aa_identity CHECK ((seat_no IS NULL) <> (student_name IS NULL)),
    ADD CONSTRAINT chk_aa_student_name CHECK (student_name IS NULL OR char_length(student_name) BETWEEN 2 AND 60);

CREATE INDEX idx_aa_identity_name ON assignment_attempt (assignment_id, class_label, student_name)
    WHERE student_name IS NOT NULL;

COMMENT ON COLUMN exam_assignment.identity_mode IS 'Học sinh nhập: name = họ và tên, seat = số thứ tự; bài giao trước V36 là seat (V36).';
COMMENT ON COLUMN assignment_attempt.student_name IS 'Họ tên học sinh gõ (đã chuẩn hóa khoảng trắng, NFC); chỉ trả cho chủ bài giao và chính học sinh đó (V36).';
COMMENT ON TABLE assignment_attempt IS 'Lượt làm của học sinh: lớp + số thứ tự hoặc lớp + họ tên theo chế độ bài giao; không IP; lượt trùng giữ cả hai (V32, V36).';
