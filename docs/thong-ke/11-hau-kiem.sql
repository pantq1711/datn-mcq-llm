-- 11-hau-kiem.sql
-- Hậu kiểm bằng dữ liệu học sinh (PR B, docs/design/lam-bai-truc-tuyen.md mục 5 và 14): mỗi câu của mỗi bài giao đã có
-- thống kê một dòng, gồm n, k, p, D và cờ hậu kiểm. TỰ TÍNH LẠI từ bài làm gốc (assignment_attempt, attempt_answer), KHÔNG
-- đọc bảng item_stat: số liệu đủ cả những bài giao giáo viên chưa mở tab "Phân tích câu", và là bản đối chiếu độc lập của
-- service (test HauKiemSqlTest bắt hai bên khớp n, k, p, D, cờ trên cùng dữ liệu). Sửa công thức thì sửa cả
-- ItemStatCalculator.java và file này.
--
-- Bài giao được tính: đã đóng VÀ đã qua 2 phút ân hạn nộp tính từ lúc đóng (trong ân hạn còn lượt nộp được). Lượt được
-- tính: đã nộp và KHÔNG trùng lớp + số thứ tự với lượt nào khác của bài giao, kể cả lượt bỏ dở (cùng quy tắc "trùng" của
-- bảng điểm). Câu không có lượt hợp lệ nào thì không có dòng.
-- (V36, quyết định 25/11) Bài giao chế độ họ tên có seat_no NULL: trùng là cùng lớp + họ tên chữ thường (giữ dấu), như
-- service. Service hạ chữ bằng Java (Locale.ROOT); file này dùng lower() của PostgreSQL, nên hai bên chỉ khớp khi DB dùng
-- collation UTF-8 có bảng chữ thường Unicode (mặc định của image postgres; HauKiemSqlTest kiểm trên DB test).
--
-- Công thức (thiết kế mục 5.2-5.4):
--   * Xếp lượt theo điểm giảm dần, hòa thì theo tổng số câu/ý đúng (correct_count) giảm dần, rồi id tăng dần.
--     k = round(0,27 x n) (làm tròn nửa lên); nhóm cao = k lượt đầu, nhóm thấp = k lượt cuối.
--   * mcq, sa: p = c / n, D = (c_cao - c_thap) / k. tf: từng ý như một câu đúng/sai (so tf_choices với nhãn ngân hàng);
--     cấp câu p = tổng ý đúng / 4n, D = (tổng ý đúng nhóm cao - nhóm thấp) / 4k. Bỏ trống là sai. k = 0 thì D NULL.
--     p, D làm tròn 4 chữ số.
--   * Cờ chỉ khi n >= 20, dưới ngưỡng chỉ có "insufficient_sample". low_discrimination: D < 0,2. mcq: unused_option: X
--     (phương án sai X dưới 5% lượt chọn), key_suspect: X (phương án sai X: nhóm cao chọn nhiều hơn nhóm thấp và ít nhất
--     max(2, 20% x k) lượt nhóm cao). tf: key_suspect: <ý> (nhóm thấp đúng ý đó nhiều hơn nhóm cao ít nhất 2 lượt). sa:
--     key_suspect (nhóm thấp đúng nhiều hơn nhóm cao ít nhất 2 lượt). Ngưỡng so bằng số nguyên, không qua D đã làm tròn.
--
-- Cột: assignment_id, exam_id, slot_id, bank_item_id, question_type, part, number_in_part, origin (generated_question.origin
-- của câu nguồn: llm hoặc import), n, group_size (k), p, d, flags (mảng JSON).
-- Bảng chéo "cờ tiền kiểm x cờ hậu kiểm" cho chương 4 KHÔNG làm bằng SQL (An chốt 17/11): xuất kết quả file này rồi ghép
-- với cờ tiền kiểm bằng công cụ Python (tools/measurement/do_luong/co_qc.py) qua bank_item_id, để không có bản sao thứ ba
-- của logic cờ.
-- GIỚI HẠN (thiết kế mục 5.7): một lớp n khoảng 38 thì D dao động khoảng +-0,4 do ngẫu nhiên; cờ là gợi ý xem lại, không
-- phải kết luận câu kém. Học sinh không được chọn ngẫu nhiên, số liệu mô tả đúng lớp đó.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): loại bài giao của tài khoản nhóm phát triển (biến psql dev_emails, so với
-- exam_assignment.created_by). Giờ so ân hạn theo giờ phiên psql (LOCALTIMESTAMP; VPS: Asia/Bangkok).
--
-- dev_emails: email cần loại (nhóm phát triển, tài khoản dùng thử), cách nhau bằng dấu phẩy, truyền khi chạy
-- (KHÔNG ghi email thật vào file):
--   psql -v dev_emails='nhom1@truong.edu.vn,gv-thu@truong.edu.vn' -f 11-hau-kiem.sql
-- Không truyền thì rỗng (không loại ai) và psql in cảnh báo. Xem docs/thong-ke/README.md.
\if :{?dev_emails}
\else
\echo 'CANH BAO: chua truyen -v dev_emails, so lieu GOM CA tai khoan nhom phat trien va tai khoan dung thu'
\set dev_emails ''
\endif
WITH dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (string_to_array(replace(lower(:'dev_emails'), ' ', ''), ','))
),
bai AS (
    SELECT ea.id, ea.exam_id
    FROM exam_assignment ea
    WHERE ea.status = 'closed'
      AND ea.closed_at + INTERVAL '2 minutes' <= LOCALTIMESTAMP
      AND ea.created_by NOT IN (SELECT id FROM dev_users)
),
luot AS (
    -- mọi lượt, kể cả chưa nộp, để xác định "trùng"
    SELECT a.id, a.assignment_id, a.score, a.submitted_at,
           count(*) OVER (PARTITION BY a.assignment_id, a.class_label, a.seat_no, lower(a.student_name))
               AS so_luot_cung_cho
    FROM assignment_attempt a
    JOIN bai b ON b.id = a.assignment_id
),
hop_le AS (
    SELECT l.id, l.assignment_id, l.score,
           (SELECT COALESCE(sum(x.correct_count), 0) FROM attempt_answer x WHERE x.attempt_id = l.id) AS tong_dung
    FROM luot l
    WHERE l.submitted_at IS NOT NULL AND l.so_luot_cung_cho = 1
),
xep AS (
    SELECT h.id, h.assignment_id,
           row_number() OVER (PARTITION BY h.assignment_id ORDER BY h.score DESC, h.tong_dung DESC, h.id) AS hang,
           count(*) OVER (PARTITION BY h.assignment_id) AS n
    FROM hop_le h
),
nhom AS (
    SELECT x.id, x.assignment_id, x.n::int AS n, round(0.27 * x.n)::int AS k, x.hang
    FROM xep x
),
nhan_tf AS (
    SELECT bank_item_id, string_agg(label, '' ORDER BY position) AS nhan
    FROM bank_item_statement
    GROUP BY bank_item_id
),
dong AS (
    -- một dòng cho mỗi (lượt hợp lệ, câu); thiếu câu trả lời thì là bỏ trống
    SELECT g.assignment_id, g.n, g.k, ai.slot_id, ai.bank_item_id, ai.question_type, ai.part, ai.number_in_part,
           ai.position, bi.correct_option AS dap_an,
           g.hang <= g.k AS cao,
           g.hang > g.n - g.k AS thap,
           x.mcq_choice,
           COALESCE(x.correct_count, 0) AS dung,
           CASE WHEN substr(x.tf_choices, 1, 1) <> '-' AND substr(x.tf_choices, 1, 1) = substr(nt.nhan, 1, 1)
                THEN 1 ELSE 0 END AS y1,
           CASE WHEN substr(x.tf_choices, 2, 1) <> '-' AND substr(x.tf_choices, 2, 1) = substr(nt.nhan, 2, 1)
                THEN 1 ELSE 0 END AS y2,
           CASE WHEN substr(x.tf_choices, 3, 1) <> '-' AND substr(x.tf_choices, 3, 1) = substr(nt.nhan, 3, 1)
                THEN 1 ELSE 0 END AS y3,
           CASE WHEN substr(x.tf_choices, 4, 1) <> '-' AND substr(x.tf_choices, 4, 1) = substr(nt.nhan, 4, 1)
                THEN 1 ELSE 0 END AS y4
    FROM nhom g
    JOIN assignment_item ai ON ai.assignment_id = g.assignment_id
    JOIN bank_item bi ON bi.id = ai.bank_item_id
    LEFT JOIN nhan_tf nt ON nt.bank_item_id = ai.bank_item_id
    LEFT JOIN attempt_answer x ON x.attempt_id = g.id AND x.slot_id = ai.slot_id
),
cau AS (
    SELECT assignment_id, slot_id, bank_item_id, question_type, part, number_in_part, position, n, k, dap_an,
           CASE WHEN question_type = 'tf' THEN 4 ELSE 1 END AS m,
           sum(CASE WHEN question_type = 'tf' THEN y1 + y2 + y3 + y4 ELSE dung END) AS c,
           COALESCE(sum(CASE WHEN question_type = 'tf' THEN y1 + y2 + y3 + y4 ELSE dung END) FILTER (WHERE cao), 0)
               AS c_cao,
           COALESCE(sum(CASE WHEN question_type = 'tf' THEN y1 + y2 + y3 + y4 ELSE dung END) FILTER (WHERE thap), 0)
               AS c_thap,
           count(*) FILTER (WHERE mcq_choice = 'A') AS a_tat_ca,
           count(*) FILTER (WHERE mcq_choice = 'A' AND cao) AS a_cao,
           count(*) FILTER (WHERE mcq_choice = 'A' AND thap) AS a_thap,
           count(*) FILTER (WHERE mcq_choice = 'B') AS b_tat_ca,
           count(*) FILTER (WHERE mcq_choice = 'B' AND cao) AS b_cao,
           count(*) FILTER (WHERE mcq_choice = 'B' AND thap) AS b_thap,
           count(*) FILTER (WHERE mcq_choice = 'C') AS c_tat_ca,
           count(*) FILTER (WHERE mcq_choice = 'C' AND cao) AS c_cao_pa,
           count(*) FILTER (WHERE mcq_choice = 'C' AND thap) AS c_thap_pa,
           count(*) FILTER (WHERE mcq_choice = 'D') AS d_tat_ca,
           count(*) FILTER (WHERE mcq_choice = 'D' AND cao) AS d_cao,
           count(*) FILTER (WHERE mcq_choice = 'D' AND thap) AS d_thap,
           COALESCE(sum(y1) FILTER (WHERE cao), 0) AS y1_cao, COALESCE(sum(y1) FILTER (WHERE thap), 0) AS y1_thap,
           COALESCE(sum(y2) FILTER (WHERE cao), 0) AS y2_cao, COALESCE(sum(y2) FILTER (WHERE thap), 0) AS y2_thap,
           COALESCE(sum(y3) FILTER (WHERE cao), 0) AS y3_cao, COALESCE(sum(y3) FILTER (WHERE thap), 0) AS y3_thap,
           COALESCE(sum(y4) FILTER (WHERE cao), 0) AS y4_cao, COALESCE(sum(y4) FILTER (WHERE thap), 0) AS y4_thap
    FROM dong
    GROUP BY assignment_id, slot_id, bank_item_id, question_type, part, number_in_part, position, n, k, dap_an
)
SELECT c.assignment_id, b.exam_id, c.slot_id, c.bank_item_id, c.question_type, c.part, c.number_in_part,
       g.origin,
       c.n, c.k AS group_size,
       round(c.c::numeric / (c.m * c.n), 4) AS p,
       CASE WHEN c.k = 0 THEN NULL ELSE round((c.c_cao - c.c_thap)::numeric / (c.m * c.k), 4) END AS d,
       to_jsonb(CASE WHEN c.n < 20 THEN ARRAY['insufficient_sample'] ELSE array_remove(ARRAY[
           CASE WHEN 5 * (c.c_cao - c.c_thap) < c.m * c.k THEN 'low_discrimination' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'A' AND 20 * c.a_tat_ca < c.n THEN 'unused_option: A' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'B' AND 20 * c.b_tat_ca < c.n THEN 'unused_option: B' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'C' AND 20 * c.c_tat_ca < c.n THEN 'unused_option: C' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'D' AND 20 * c.d_tat_ca < c.n THEN 'unused_option: D' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'A' AND c.a_cao > c.a_thap AND c.a_cao >= 2
                     AND 5 * c.a_cao >= c.k THEN 'key_suspect: A' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'B' AND c.b_cao > c.b_thap AND c.b_cao >= 2
                     AND 5 * c.b_cao >= c.k THEN 'key_suspect: B' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'C' AND c.c_cao_pa > c.c_thap_pa AND c.c_cao_pa >= 2
                     AND 5 * c.c_cao_pa >= c.k THEN 'key_suspect: C' END,
           CASE WHEN c.question_type = 'mcq' AND c.dap_an <> 'D' AND c.d_cao > c.d_thap AND c.d_cao >= 2
                     AND 5 * c.d_cao >= c.k THEN 'key_suspect: D' END,
           CASE WHEN c.question_type = 'tf' AND c.y1_thap - c.y1_cao >= 2 THEN 'key_suspect: a' END,
           CASE WHEN c.question_type = 'tf' AND c.y2_thap - c.y2_cao >= 2 THEN 'key_suspect: b' END,
           CASE WHEN c.question_type = 'tf' AND c.y3_thap - c.y3_cao >= 2 THEN 'key_suspect: c' END,
           CASE WHEN c.question_type = 'tf' AND c.y4_thap - c.y4_cao >= 2 THEN 'key_suspect: d' END,
           CASE WHEN c.question_type = 'sa' AND c.c_thap - c.c_cao >= 2 THEN 'key_suspect' END
       ], NULL) END) AS flags
FROM cau c
JOIN bai b ON b.id = c.assignment_id
JOIN bank_item bi ON bi.id = c.bank_item_id
LEFT JOIN generated_question g ON g.id = bi.source_question_id
ORDER BY c.assignment_id, c.position;
