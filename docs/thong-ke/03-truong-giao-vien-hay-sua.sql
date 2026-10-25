-- 03-truong-giao-vien-hay-sua.sql
-- Giáo viên hay sửa trường nào của câu LLM: question_text, option_a..d, correct_option, sa_answer, statement_a..d, label_a..d,
-- explanation. Mỗi lần Sửa (exam_slot_action.action = 'edit') liệt kê các trường đã đổi ở changed_fields (V13).
-- Gom theo dạng câu; so_cho là số chỗ khác nhau có sửa trường đó.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; run production 'matrix-%'; loại đề của tài khoản
-- nhóm phát triển (điền email vào ARRAY[...]). Các bản sửa là thao tác của giáo viên nên không lọc edited_from_question_id
-- (bản sửa luôn có giá trị đó); số lần sửa nối với câu trước đó qua nhật ký, không qua cột đầu ra LLM.
-- GIỚI HẠN: số lần sửa phản ánh mức chấp nhận, không phải độ đúng; một câu sửa nhiều lần được đếm nhiều lần.
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (ARRAY[]::text[])   -- ví dụ ARRAY['nhom1@truong.edu.vn']
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
