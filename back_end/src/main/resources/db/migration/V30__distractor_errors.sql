-- Phương án nhiễu kèm lỗi sai của học sinh (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md mục 6). Không sửa V1-V29.
-- Đổi từ V29 tạm thành V30 khi rebase lên main (15/11): V29 là của hotfix Solver hai lượt (#76). Câu có hình (I1) cũng đang
-- dùng số tạm; PR merge sau đổi số khi rebase (Flyway không chạy lệch thứ tự).
--
-- * distractor_errors (generated_question, bank_item): đối tượng JSON {"A": "...", "B": null, ...}, mỗi phương án sai một câu
--   mô tả lỗi sai của học sinh dẫn tới phương án đó, phương án đúng null. Chỉ câu mcq. NULL là "không có phân tích" (dữ liệu
--   cũ, câu nhập, câu sa/tf, model không trả). Bản giáo viên sửa chép nguyên từ bản trước.
-- * distractor_checks (quality_check_result, chỉ dòng answer_check của câu mcq; bank_item): đối tượng JSON
--   {"B": "match", "C": "mismatch", "D": "unknown"}, kết quả Solver ghép mù lỗi với phương án (cùng lượt gọi answer_check).
--   Chỉ có khóa của lỗi đã kiểm; "unchecked" (chưa kiểm) không lưu. bank_item lưu đúng trạng thái đang hiển thị lúc Giữ.
-- Chỉ là thông tin: không đổi trạng thái câu, không bật "cần xem kỹ". Mọi cột mới cho phép NULL, không có default.

ALTER TABLE generated_question
    ADD COLUMN distractor_errors JSONB,
    ADD CONSTRAINT chk_gq_distractor_errors CHECK (distractor_errors IS NULL
        OR (question_type = 'mcq' AND jsonb_typeof(distractor_errors) = 'object'));

ALTER TABLE bank_item
    ADD COLUMN distractor_errors JSONB,
    ADD COLUMN distractor_checks JSONB,
    ADD CONSTRAINT chk_bi_distractor_errors CHECK (distractor_errors IS NULL
        OR (question_type = 'mcq' AND jsonb_typeof(distractor_errors) = 'object')),
    ADD CONSTRAINT chk_bi_distractor_checks CHECK (distractor_checks IS NULL
        OR (question_type = 'mcq' AND jsonb_typeof(distractor_checks) = 'object'));

ALTER TABLE quality_check_result
    ADD COLUMN distractor_checks JSONB,
    ADD CONSTRAINT chk_qcr_distractor_checks CHECK (distractor_checks IS NULL
        OR (check_type = 'answer_check' AND question_type = 'mcq' AND jsonb_typeof(distractor_checks) = 'object'));

COMMENT ON COLUMN generated_question.distractor_errors IS
    'Lỗi sai của học sinh theo phương án A-D (V30, chỉ mcq); phương án đúng null; NULL khi không có phân tích.';
COMMENT ON COLUMN bank_item.distractor_errors IS
    'Lỗi sai của học sinh theo phương án (V30, chép lúc Giữ, chỉ phương án sai đang hiển thị); NULL khi không có.';
COMMENT ON COLUMN bank_item.distractor_checks IS
    'Kết quả kiểm lỗi lúc Giữ (V30): match, mismatch, unknown theo phương án; phương án có lỗi mà không có khóa là chưa kiểm.';
COMMENT ON COLUMN quality_check_result.distractor_checks IS
    'Kết quả Solver ghép mù lỗi sai với phương án (V30, chỉ answer_check của mcq, lượt 2): match, mismatch, unknown.';
