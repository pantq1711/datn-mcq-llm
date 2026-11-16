-- Xóa mềm đề, ma trận và tài liệu bài học (rà soát 15/11: NV-01, NV-55). Không sửa V1-V30.
-- Lưu ý số migration: V30 là của #81 (bank_item_removed, đã merge); nhánh này rebase lên sau #81 nên dùng V31. #77 và
-- nhánh học sinh còn đang giữ V30 TẠM; PR merge sau đổi số khi rebase.
--
-- * exam.deleted_at: giáo viên xóa đề của mình (DELETE /api/exams/{id}). Đề đã xóa không hiện ở đâu nữa (404 như không tồn
--   tại) nhưng KHÔNG xóa vật lý: exam_slot, exam_slot_action, generated_question là số liệu chương 4; thống kê vẫn tính.
-- * exam_matrix.deleted_at: ma trận chỉ còn đề đã xóa thì "Xóa" là xóa mềm (giữ ô, vì exam_slot.cell_id của đề đã xóa còn
--   trỏ tới); ma trận chưa từng có đề vẫn xóa vật lý như cũ.
-- * lesson_material.deleted_at: tài liệu chỉ còn gắn với đề đã xóa thì "Xóa" là xóa mềm (exam_context_material của đề đã
--   xóa còn trỏ tới, khóa ngoại không xóa dây chuyền theo quyết định G); tài liệu không gắn đề nào vẫn xóa vật lý.
-- NULL = còn dùng. Không đổi khóa ngoại nào.

ALTER TABLE exam ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE exam_matrix ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE lesson_material ADD COLUMN deleted_at TIMESTAMP;

COMMENT ON COLUMN exam.deleted_at IS
    'V31: thời điểm giáo viên xóa đề (xóa mềm); NULL là đề còn. Đề đã xóa: API trả 404, không tính vào MATRIX_IN_USE (trừ '
    'sửa cấu trúc ma trận), MATERIAL_IN_USE và số đã dùng của câu ngân hàng; thống kê vẫn tính.';
COMMENT ON COLUMN exam_matrix.deleted_at IS
    'V31: thời điểm xóa mềm ma trận chỉ còn đề đã xóa (ô giữ nguyên); NULL là ma trận còn. Ma trận đã xóa: API trả 404.';
COMMENT ON COLUMN lesson_material.deleted_at IS
    'V31: thời điểm xóa mềm tài liệu chỉ còn gắn với đề đã xóa; NULL là tài liệu còn. Không tính vào trần mỗi giáo viên; '
    'tải lại đúng file (cùng sha256) thì khôi phục bản này.';
