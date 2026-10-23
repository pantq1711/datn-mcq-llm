-- Dữ liệu nền cho demo. Chạy nhiều lần không tạo bản trùng.
INSERT INTO subject (name)
SELECT 'Toán' WHERE NOT EXISTS (SELECT 1 FROM subject WHERE name = 'Toán');

INSERT INTO users (name, email, role)
SELECT 'Giáo viên demo', 'demo@example.com', 'teacher'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo@example.com');