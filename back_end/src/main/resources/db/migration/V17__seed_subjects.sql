-- V17 (W1-5): môn học mặc định cho DB mới. Chưa có endpoint tạo môn, và trên DB trống tạo ma trận bằng JSON bị từ chối
-- (không có subjectId hợp lệ) cho tới khi có môn; "tạo từ mẫu" chỉ tự tạo môn "Toán".
--
-- Idempotent: bảng subject không có ràng buộc UNIQUE trên name (V1), nên không dùng ON CONFLICT mà chỉ chèn khi chưa có
-- môn cùng tên không phân biệt hoa thường, đúng cách CatalogService.getOrCreateSubject
-- (SubjectRepository.findByNameIgnoreCase) tìm môn. DB dev đã có "Toán" (db/dev-seed) thì không thêm bản trùng.
-- Tên "Toán" phải đúng MatrixService.DEFAULT_SUBJECT để "tạo từ mẫu" dùng lại hàng này.
INSERT INTO subject (name)
SELECT v.name
FROM (VALUES ('Toán'), ('Giáo dục công dân')) AS v(name)
WHERE NOT EXISTS (SELECT 1 FROM subject s WHERE lower(s.name) = lower(v.name));
