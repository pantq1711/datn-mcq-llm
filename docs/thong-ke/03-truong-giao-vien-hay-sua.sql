-- 03-truong-giao-vien-hay-sua.sql
-- Giáo viên hay sửa trường nào của câu LLM: question_text, option_a..d, correct_option, sa_answer, statement_a..d, label_a..d,
-- explanation. Mỗi lần Sửa (exam_slot_action.action = 'edit') liệt kê các trường đã đổi ở changed_fields (V13).
-- Gom theo dạng câu; so_cho là số chỗ khác nhau có sửa trường đó.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; run production 'matrix-%'; loại đề của tài khoản
-- nhóm phát triển (biến psql dev_emails). Các bản sửa là thao tác của giáo viên nên không lọc edited_from_question_id
-- (bản sửa luôn có giá trị đó); số lần sửa nối với câu trước đó qua nhật ký, không qua cột đầu ra LLM.
-- GIỚI HẠN: số lần sửa phản ánh mức chấp nhận, không phải độ đúng; một câu sửa nhiều lần được đếm nhiều lần.
--
-- dev_emails: email cần loại (nhóm phát triển, tài khoản dùng thử), cách nhau bằng dấu phẩy, truyền khi chạy
-- (KHÔNG ghi email thật vào file):
--   psql -v dev_emails='nhom1@truong.edu.vn,gv-thu@truong.edu.vn' -f 03-truong-giao-vien-hay-sua.sql
-- Không truyền thì rỗng (không loại ai). Xem docs/thong-ke/README.md.
\if :{?dev_emails}
\else
\set dev_emails ''
\endif
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
)
SELECT g.question_type,
       fld AS truong,
       count(*) AS so_lan_sua,
       count(DISTINCT a.slot_id) AS so_cho,
       round(100.0 * count(*) / sum(count(*)) OVER (PARTITION BY g.question_type), 1) AS ti_le_phan_tram
FROM exam_slot_action a
JOIN exam_slot s ON s.id = a.slot_id
JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
JOIN generated_question g ON g.id = a.generated_question_id
JOIN experiment_run r ON r.id = g.run_id AND r.purpose = 'production' AND r.prompt_version LIKE 'matrix-%'
CROSS JOIN LATERAL jsonb_array_elements_text(a.changed_fields) AS fld
WHERE a.action = 'edit'
GROUP BY g.question_type, fld
ORDER BY g.question_type, so_lan_sua DESC, fld;
