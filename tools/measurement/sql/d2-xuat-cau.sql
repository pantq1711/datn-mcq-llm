-- d2-xuat-cau.sql
-- D2 (chương 4): xuất câu LLM của đề giáo viên ra JSONL, mỗi dòng một câu, cho bộ chấm mù. CHỈ ĐỌC, một câu SELECT.
--
-- Chạy (An tự chạy trên VPS rồi chép file về; công cụ đo KHÔNG kết nối DB VPS):
--   psql -X -A -t -v ON_ERROR_STOP=1 -d <db> -f tools/measurement/sql/d2-xuat-cau.sql -o cau-he-thong.jsonl
--   -A -t: không căn lề, không tiêu đề, nên mỗi dòng ra đúng một đối tượng JSON (xuống dòng trong chuỗi đã là \n).
--
-- Biến psql (truyền bằng -v ten=gia_tri, không truyền thì dùng mặc định):
--   prompt_versions  phiên bản prompt sinh câu cần lấy, cách nhau bằng dấu phẩy. Mặc định là phiên bản hiện tại của main
--                    (MatrixPromptDraftV0.promptVersion: mcq v8, tf v10, sa v10). Prompt Solver không lưu trong DB; các
--                    phiên bản sinh này ra đời sau solver-v4 (31/10) nên câu của chúng được Solver v4 kiểm, trừ khi máy
--                    chạy đặt LLM_SOLVER_PROMPT_VERSION khác.
--
-- Phạm vi: câu LLM gốc (origin = 'llm', edited_from_question_id IS NULL) do job 'generate' của đề chế độ giáo viên sinh ra,
-- đã qua bước format (câu lỗi format không bao giờ đến tay giáo viên và không hiển thị được). KHÔNG loại tài khoản dùng thử
-- (D2 đo bộ sinh + QC, không đo hành vi giáo viên). Gồm cả câu bị Solver bắt rồi sinh lại (cột da_hien_cho_gv = false):
-- D2 đo QC trên mọi câu QC đã xét; bộ lấy mẫu có thể lọc theo cột này.
--
-- Cờ: file KHÔNG tính cờ. Cột "qc" chứa nguyên dữ liệu các bước QC đã lưu; công cụ Python (do_luong/co_qc.py) tính lý do
-- "cần xem kỹ" theo đúng QcSummaryService.QcSummary.reviewReasons + ExamViewService (job Solver/grounding chưa xong hoặc
-- thất bại), rồi "có cờ nội dung" theo hasContentFlag. Khớp hai bên được giữ bằng fixture chung
-- tools/measurement/tests/fixtures/co-qc-parity.json (test Java QcFlagParityTest và test Python test_co_qc).
--
-- Không dùng chuỗi có dấu "\" (một số DB tắt standard_conforming_strings).
\if :{?prompt_versions}
\else
\set prompt_versions 'matrix-mcq-v8-draft,matrix-tf-v10-draft,matrix-sa-v10-draft'
\endif
WITH llm_jobs AS (
    SELECT j.question_id, min(s.id) AS slot_id
    FROM generation_job j
    JOIN exam_slot s ON s.id = j.exam_slot_id
    JOIN exam e ON e.id = s.exam_id AND e.mode = 'teacher'
    WHERE j.job_type = 'generate' AND j.question_id IS NOT NULL
    GROUP BY j.question_id
),
cau AS (
    SELECT g.*, r.prompt_version, r.model_name, lj.slot_id
    FROM llm_jobs lj
    JOIN generated_question g ON g.id = lj.question_id
    JOIN experiment_run r ON r.id = g.run_id
    WHERE g.origin = 'llm'
      AND g.edited_from_question_id IS NULL
      AND r.prompt_version = ANY (string_to_array(replace(:'prompt_versions', ' ', ''), ','))
),
latest_grounding_job AS (
    SELECT DISTINCT ON (j.question_id) j.question_id, j.status
    FROM generation_job j
    WHERE j.job_type = 'grounding_check' AND j.question_id IN (SELECT id FROM cau)
    ORDER BY j.question_id, j.id DESC
),
latest_answer_job AS (
    SELECT DISTINCT ON (j.question_id) j.question_id, j.status
    FROM generation_job j
    WHERE j.job_type = 'answer_check' AND j.question_id IN (SELECT id FROM cau)
    ORDER BY j.question_id, j.id DESC
)
SELECT json_build_object(
    'id', c.id,
    'dang', c.question_type,
    'de', c.question_text,
    'phuong_an', CASE WHEN c.question_type = 'mcq'
        THEN json_build_object('A', c.option_a, 'B', c.option_b, 'C', c.option_c, 'D', c.option_d) END,
    'dap_an_mcq', c.correct_option,
    'dap_an_sa', c.sa_answer,
    'y', (SELECT json_agg(json_build_object('vi_tri', st.position, 'noi_dung', st.statement_text,
                                            'nhan', st.llm_label, 'muc_do', st.llm_difficulty)
                          ORDER BY st.position)
          FROM generated_question_statement st WHERE st.question_id = c.id),
    'loi_giai', c.explanation,
    'muc_do', c.llm_difficulty,
    'prompt_version', c.prompt_version,
    'model', c.model_name,
    'tao_luc', c.created_at,
    'de_thi_id', s.exam_id,
    'ma_tran_id', e.matrix_id,
    'ma_tran_mau', m.sample_key,
    'ma_tran_ten', m.name,
    'khoi', m.grade_level,
    'phan', cell.part,
    'chu_de', cell.topic,
    'bai', cell.content,
    'muc_do_o', cell.difficulty,
    'muc_do_y_o', cell.statement_difficulties,
    'da_hien_cho_gv', EXISTS (SELECT 1 FROM exam_slot s2
                              WHERE s2.generated_question_id = c.id OR s2.fallback_question_id = c.id),
    'qc', json_build_object(
        'format_passed', f.passed,
        'format_warnings', COALESCE(f.warnings, '[]'::jsonb),
        'dedup_ran', d.id IS NOT NULL,
        'dedup_passed', d.passed,
        'dedup_compared_to', d.compared_to_question_id,
        'dedup_similarity', d.similarity_score,
        'answer_ran', a.id IS NOT NULL,
        'answer_passed', a.passed,
        'judge_answer', a.judge_answer,
        'judge_sa_kind', a.judge_sa_kind,
        'judge_sa_answer', a.judge_sa_answer,
        'solver_option_check', a.solver_reasoning IS NOT NULL
                               AND position('[Xét từng phương án]' IN a.solver_reasoning) = 1,
        'has_undecidable', EXISTS (SELECT 1 FROM answer_check_statement acs
                                   WHERE acs.qc_result_id = a.id AND acs.judge_label = 'undecidable'),
        'solver_error', c.solver_error IS NOT NULL,
        'answer_job_status', aj.status,
        'grounding_verdict', gr.verdict,
        'grounding_job_status', gj.status
    )
)
FROM cau c
JOIN exam_slot s ON s.id = c.slot_id
JOIN exam e ON e.id = s.exam_id
JOIN exam_matrix_cell cell ON cell.id = s.cell_id
JOIN exam_matrix m ON m.id = cell.matrix_id
JOIN quality_check_result f ON f.question_id = c.id AND f.check_type = 'format' AND f.passed
LEFT JOIN quality_check_result d ON d.question_id = c.id AND d.check_type = 'dedup'
LEFT JOIN quality_check_result a ON a.question_id = c.id AND a.check_type = 'answer_check'
LEFT JOIN question_grounding gr ON gr.question_id = c.id
LEFT JOIN latest_grounding_job gj ON gj.question_id = c.id
LEFT JOIN latest_answer_job aj ON aj.question_id = c.id
ORDER BY c.id;
