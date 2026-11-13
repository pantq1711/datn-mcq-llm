-- Yêu cầu của giáo viên (12/11, PR B, docs/design/kiem-soat-do-kho.md mục 4). Không sửa V1-V27.
-- Số migration tạm theo nhánh nền (feature/difficulty-control dừng ở V27); trước khi merge lấy lại số kế tiếp của main.
--
-- * request_codes: mảng mã chip yêu cầu (TeacherRequest, docs/contract/enums.md), chỉ gồm 6 mã đã biết. NULL là không có mã.
--   - exam_generation_context: yêu cầu của cả đề (cell_id IS NULL) hoặc của một ô; cùng dòng với extra_instruction (V19).
--   - exam_slot_action: nhật ký lượt Đổi câu. NULL khi lượt Đổi không gửi requestCodes; mảng rỗng khi gửi [] (bỏ hết chip).
--   - generation_job: yêu cầu hiệu lực của job, đã lọc theo dạng câu; kèm request_text (văn bản tự do đã chuẩn hóa) và
--     request_scope (mức lấy yêu cầu: replace | cell | exam). Lượt Đổi câu tự đặt yêu cầu mà bỏ hết thì scope = replace,
--     mã và văn bản NULL (worker bỏ yêu cầu của ô và đề).
--   - generated_question: chép từ job khi lưu câu; bản giáo viên sửa chép từ bản trước. Câu ngân hàng không có yêu cầu.
-- * quality_check_result.request_verdict / request_reason: kết luận của Solver (cùng lượt gọi) câu có đúng yêu cầu không,
--   chỉ trên dòng answer_check. NULL khi câu không có yêu cầu hoặc Solver không trả kết luận hợp lệ. Chỉ là cờ thông tin:
--   không đổi trạng thái câu.
-- * exam_slot_action.instruction: nâng giới hạn 1-500 (V9) lên 1-1000 ký tự, bằng giới hạn của đề và ô.
-- Mọi cột mới cho phép NULL, không có default (dòng cũ là "không yêu cầu").

ALTER TABLE exam_generation_context
    ADD COLUMN request_codes TEXT[],
    ADD CONSTRAINT chk_egc_request_codes CHECK (request_codes IS NULL OR request_codes <@ ARRAY['real_context',
        'multi_step', 'common_trap', 'extremum', 'geometry_link', 'tf_shared_context']::text[]);

ALTER TABLE exam_slot_action
    ADD COLUMN request_codes TEXT[],
    ADD CONSTRAINT chk_esa_request_codes CHECK (request_codes IS NULL OR (action = 'replace' AND request_codes <@
        ARRAY['real_context', 'multi_step', 'common_trap', 'extremum', 'geometry_link', 'tf_shared_context']::text[]));

ALTER TABLE generation_job
    ADD COLUMN request_codes TEXT[],
    ADD COLUMN request_text TEXT,
    ADD COLUMN request_scope VARCHAR(10),
    ADD CONSTRAINT chk_gj_request_codes CHECK (request_codes IS NULL OR request_codes <@ ARRAY['real_context',
        'multi_step', 'common_trap', 'extremum', 'geometry_link', 'tf_shared_context']::text[]),
    ADD CONSTRAINT chk_gj_request_scope CHECK (request_scope IS NULL OR request_scope IN ('replace', 'cell', 'exam'));

ALTER TABLE generated_question
    ADD COLUMN request_codes TEXT[],
    ADD COLUMN request_text TEXT,
    ADD CONSTRAINT chk_gq_request_codes CHECK (request_codes IS NULL OR request_codes <@ ARRAY['real_context',
        'multi_step', 'common_trap', 'extremum', 'geometry_link', 'tf_shared_context']::text[]);

ALTER TABLE quality_check_result
    ADD COLUMN request_verdict VARCHAR(10),
    ADD COLUMN request_reason TEXT,
    ADD CONSTRAINT chk_qcr_request_verdict CHECK (request_verdict IS NULL OR request_verdict IN ('met', 'partial',
        'not_met'));

ALTER TABLE exam_slot_action DROP CONSTRAINT chk_esa_instruction;
ALTER TABLE exam_slot_action
    ADD CONSTRAINT chk_esa_instruction CHECK (
        instruction IS NULL OR (action = 'replace' AND char_length(instruction) BETWEEN 1 AND 1000));

COMMENT ON COLUMN exam_slot_action.instruction IS
    'Yêu cầu bổ sung của giáo viên khi Đổi câu (1-1000 ký tự từ V28, chỉ action = replace); NULL khi không có.';
COMMENT ON COLUMN exam_slot_action.request_codes IS
    'Mã chip yêu cầu gửi ở lượt Đổi câu (V28); NULL khi không gửi, mảng rỗng khi gửi [].';
COMMENT ON COLUMN exam_generation_context.request_codes IS
    'Mã chip yêu cầu của cả đề hoặc của ô (V28); NULL khi không có.';
COMMENT ON COLUMN generation_job.request_scope IS
    'Mức lấy yêu cầu hiệu lực của job (V28): replace, cell hoặc exam; NULL khi job không có yêu cầu.';
COMMENT ON COLUMN quality_check_result.request_verdict IS
    'Kết luận Solver về yêu cầu của giáo viên (V28): met, partial, not_met; NULL khi không có yêu cầu hoặc không kết luận.';
