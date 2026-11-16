-- V30__bank_item_removed.sql
-- Rà soát logic nghiệp vụ (NV-21, quyết định 16/11 mục 3b): "Gỡ khỏi ngân hàng" là ẩn mềm. Không sửa V1-V29.
-- Lưu ý số migration: PR câu có hình (#77) và nhánh học sinh đang giữ V30 TẠM; PR merge sau đổi số khi rebase.
--
-- * bank_item.removed_at / removed_by: thời điểm và người gỡ câu khỏi ngân hàng (cùng NULL hoặc cùng có). Câu đã gỡ không
--   hiện trong tìm kiếm, chủ đề, chi tiết ngân hàng và không là ứng viên cho đề mới (tạo đề, Đổi lấy từ ngân hàng); dòng
--   bank_item KHÔNG bị xóa, nên đề cũ đang có câu (exam_slot.bank_item_id, exam_question.bank_item_id) vẫn hiển thị và
--   xuất được. Người gỡ: người Giữ câu (teacher_review.reviewer_id; với câu nhập từ Word là người tải) hoặc admin.
--   Không xóa dây chuyền khi xóa người dùng (giống visible_to_user_id).

ALTER TABLE bank_item
    ADD COLUMN removed_at TIMESTAMP,
    ADD COLUMN removed_by INT,
    ADD CONSTRAINT fk_bi_removed_by FOREIGN KEY (removed_by) REFERENCES users(id),
    ADD CONSTRAINT chk_bi_removed CHECK ((removed_at IS NULL) = (removed_by IS NULL));

CREATE INDEX idx_bi_removed ON bank_item (removed_at) WHERE removed_at IS NOT NULL;

COMMENT ON COLUMN bank_item.removed_at IS
    'NV-21: thời điểm gỡ câu khỏi ngân hàng (ẩn mềm); NULL = còn trong ngân hàng. Đề cũ vẫn hiển thị câu đã gỡ.';
COMMENT ON COLUMN bank_item.removed_by IS
    'NV-21: người gỡ câu (người Giữ câu hoặc admin); NULL khi câu còn trong ngân hàng.';
