-- V15__exam_matrix_kind.sql
-- W1-4 bước 1 (ngày 24/10): sinh theo bài. Đề theo bài là "ma trận một bài" ẩn, dùng lại nguyên luồng tạo đề theo ma
-- trận (V6-V7). Không sửa V1-V14.
--
-- * exam_matrix.kind: 'matrix' (ma trận giáo viên tạo, hiện trong danh sách; mọi ma trận cũ) hoặc 'lesson' (ma trận ẩn
--   của một lần sinh theo bài: không hiện trong danh sách, không xem/sửa/xóa/nhân bản/tạo đề lại theo id).
-- * Loại đề không có cột riêng: suy ra từ exam.matrix_id -> exam_matrix.kind (một nguồn sự thật; chk_exam_teacher_matrix
--   vẫn đúng vì đề theo bài vẫn có matrix_id).

ALTER TABLE exam_matrix
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'matrix',
    ADD CONSTRAINT chk_em_kind CHECK (kind IN ('matrix', 'lesson'));

COMMENT ON COLUMN exam_matrix.kind IS
    'matrix = ma trận giáo viên tạo; lesson = ma trận ẩn của một lần sinh theo bài (W1-4).';
