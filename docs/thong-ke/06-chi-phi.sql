-- 06-chi-phi.sql
-- Chi phí trung bình mỗi câu LLM (token và thời gian gọi LLM) = sinh câu + Solver + chấm "bám tài liệu" (grounding), theo dạng câu
-- và theo việc câu được sinh CÓ hay KHÔNG có tài liệu. "Mỗi câu" là một câu LLM gốc kèm cả chuỗi bản giáo viên sửa của nó: Solver
-- và grounding chạy lại cho bản sửa cũng được cộng vào câu gốc, vì đó là chi phí để có được câu cuối.
-- Nguồn số liệu: sinh = generated_question (token_count, latency_ms của lượt sinh, bản LLM gốc); Solver = quality_check_result
-- dòng answer_check; grounding = question_grounding (hai nguồn sau từ V21). Kết quả Solver CHÉP từ bản trước (bản chỉ đổi lời
-- giải) có số liệu NULL nên không bị đếm hai lần.
--
-- Cột tb_*_sinh / _solver / _grounding là trung bình trên các câu CÓ số liệu đó (NULL khi chưa câu nào có); tb_*_moi_cau là trung
-- bình của tổng ba phần với phần thiếu tính là 0. so_cau_thieu_so_do đếm câu thiếu số của một phần đáng ra phải có (sinh, Solver,
-- hoặc grounding với câu có tài liệu): dữ liệu trước V21, Solver lỗi hoặc chưa chạy, grounding còn chờ hoặc lỗi. Nếu số này lớn
-- thì tb_*_moi_cau thấp hơn thực tế.
--
-- BỘ LỌC CHUNG (xem docs/thong-ke/README.md): đề exam.mode = 'teacher'; chỉ câu LLM gốc (edited_from_question_id IS NULL,
-- run production 'matrix-%'); loại đề của tài khoản nhóm phát triển (điền email vào ARRAY[...]).
-- GIỚI HẠN: chỉ tính các lượt gọi thành công cho ra kết quả; lượt thử lại thất bại, các lần sinh bị loại rồi sinh lại (mỗi lần là
-- một câu LLM gốc khác) và lượt gọi của giáo viên Đổi câu không được gộp vào câu cuối của chỗ. Đây là token và thời gian gọi LLM
-- chứ không phải thời gian chờ của người dùng (chưa gồm hàng đợi và giãn cách job). Chưa có đơn giá: LlmClient không tính
-- cost_usd, nên nhân token với đơn giá của model ở báo cáo.
WITH RECURSIVE
dev_users AS (
    SELECT id FROM users WHERE lower(email) = ANY (ARRAY[]::text[])   -- ví dụ ARRAY['nhom1@truong.edu.vn']
),
roots AS (
    SELECT DISTINCT ON (j.question_id) j.question_id AS id, (j.material_context IS NOT NULL) AS co_tai_lieu
    FROM generation_job j
    JOIN generated_question g ON g.id = j.question_id AND g.edited_from_question_id IS NULL
    JOIN experiment_run r ON r.id = g.run_id AND r.purpose = 'production' AND r.prompt_version LIKE 'matrix-%'
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher' AND e.created_by NOT IN (SELECT id FROM dev_users)
    WHERE j.job_type = 'generate' AND j.question_id IS NOT NULL
    ORDER BY j.question_id, j.id DESC
),
chain AS (
    SELECT id AS root, id FROM roots
    UNION ALL
    SELECT c.root, g.id
    FROM generated_question g JOIN chain c ON g.edited_from_question_id = c.id
),
solver AS (
    SELECT c.root, sum(a.token_count) AS tokens, sum(a.latency_ms) AS ms
    FROM chain c
    JOIN quality_check_result a ON a.question_id = c.id AND a.check_type = 'answer_check'
    GROUP BY c.root
),
grounding AS (
    SELECT c.root, sum(qg.token_count) AS tokens, sum(qg.latency_ms) AS ms
    FROM chain c
    JOIN question_grounding qg ON qg.question_id = c.id
    GROUP BY c.root
),
per_question AS (
    SELECT g.question_type, ro.co_tai_lieu,
           g.token_count AS t_sinh, g.latency_ms AS ms_sinh,
           s.tokens AS t_solver, s.ms AS ms_solver,
           gr.tokens AS t_grounding, gr.ms AS ms_grounding
    FROM roots ro
    JOIN generated_question g ON g.id = ro.id
    LEFT JOIN solver s ON s.root = ro.id
    LEFT JOIN grounding gr ON gr.root = ro.id
)
SELECT question_type,
       co_tai_lieu,
       count(*) AS so_cau_llm,
       round(avg(t_sinh)::numeric, 1) AS tb_token_sinh,
       round(avg(t_solver)::numeric, 1) AS tb_token_solver,
       round(avg(t_grounding)::numeric, 1) AS tb_token_grounding,
       round(avg(coalesce(t_sinh, 0) + coalesce(t_solver, 0) + coalesce(t_grounding, 0))::numeric, 1) AS tb_token_moi_cau,
       round(avg(ms_sinh)::numeric, 1) AS tb_latency_ms_sinh,
       round(avg(ms_solver)::numeric, 1) AS tb_latency_ms_solver,
       round(avg(ms_grounding)::numeric, 1) AS tb_latency_ms_grounding,
       round(avg(coalesce(ms_sinh, 0) + coalesce(ms_solver, 0) + coalesce(ms_grounding, 0))::numeric, 1)
           AS tb_latency_ms_moi_cau,
       count(*) FILTER (WHERE t_sinh IS NULL OR t_solver IS NULL OR (co_tai_lieu AND t_grounding IS NULL))
           AS so_cau_thieu_so_do
FROM per_question
GROUP BY question_type, co_tai_lieu
ORDER BY question_type, co_tai_lieu DESC;
