-- V20__question_grounding.sql
-- W2-3 bước 6 (ngày 26/10): QC "bám tài liệu" (grounding). Không sửa V1-V19.
--
-- * generation_job.job_type thêm 'grounding_check': job chấm một câu đã sinh từ đề có tài liệu bài học, xếp cùng transaction
--   với lúc job sinh câu xong. Như answer_check (V13) nó bắt buộc có question_id và không gắn chỗ (exam_slot_id NULL), để
--   đối soát chỗ không nhầm là job sinh câu. Ràng buộc chk_gj_answer_check được tổng quát thành chk_gj_question_job (mọi
--   loại job khác 'generate'). Job grounding bắt buộc có material_context (V19): chấm đúng đoạn tài liệu model sinh câu đã
--   thấy. Mỗi câu có tối đa MỘT job grounding (chỉ mục duy nhất một phần): chặn gọi judge hai lần cho một câu.
-- * question_grounding: kết quả chấm của một câu. verdict supported / not_supported / unknown; reason (lý do ngắn);
--   evidence_quote là trích dẫn nguyên văn của judge và evidence_found cho biết server có tìm thấy trích dẫn đó trong đoạn
--   tài liệu đã đưa vào prompt hay không. 'supported' bắt buộc có trích dẫn đã kiểm chứng (chk_qg_supported_evidence): judge
--   nói "bám tài liệu" mà không chỉ ra được chỗ nào trong tài liệu thì không được tin. material_refs chép từ job (đoạn đã
--   dùng). generated_with_material (mặc định TRUE) phân biệt nhóm sinh CÓ tài liệu với nhóm sinh KHÔNG có tài liệu khi
--   đo tỉ lệ SUPPORTED ở chương thử nghiệm. Kết quả chỉ là cảnh báo: KHÔNG đổi generated_question.status (Q-2, M-10).
--   Không xóa dây chuyền theo generated_question; source_job_id SET NULL khi job bị xóa.

ALTER TABLE generation_job
    DROP CONSTRAINT chk_gj_job_type,
    ADD CONSTRAINT chk_gj_job_type CHECK (job_type IN ('generate', 'answer_check', 'grounding_check')),
    DROP CONSTRAINT chk_gj_answer_check,
    ADD CONSTRAINT chk_gj_question_job CHECK (
        job_type = 'generate' OR (question_id IS NOT NULL AND exam_slot_id IS NULL)),
    ADD CONSTRAINT chk_gj_grounding_ctx CHECK (
        job_type <> 'grounding_check' OR material_context IS NOT NULL);

CREATE UNIQUE INDEX uq_gj_grounding_question ON generation_job (question_id) WHERE job_type = 'grounding_check';

CREATE TABLE question_grounding (
    id                      SERIAL PRIMARY KEY,
    question_id             INT NOT NULL,
    verdict                 VARCHAR(15) NOT NULL,
    reason                  TEXT NOT NULL,
    evidence_quote          TEXT,
    evidence_found          BOOLEAN,
    judge_model             VARCHAR(100) NOT NULL,
    material_refs           JSONB NOT NULL,
    generated_with_material BOOLEAN NOT NULL DEFAULT TRUE,
    source_job_id           INT,
    checked_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qg_question FOREIGN KEY (question_id) REFERENCES generated_question(id),
    CONSTRAINT fk_qg_job FOREIGN KEY (source_job_id) REFERENCES generation_job(id) ON DELETE SET NULL,
    CONSTRAINT uq_qg_question UNIQUE (question_id),
    CONSTRAINT chk_qg_verdict CHECK (verdict IN ('supported', 'not_supported', 'unknown')),
    CONSTRAINT chk_qg_reason CHECK (char_length(reason) <= 600),
    CONSTRAINT chk_qg_quote CHECK (evidence_quote IS NULL OR char_length(evidence_quote) <= 400),
    CONSTRAINT chk_qg_evidence CHECK (evidence_quote IS NOT NULL OR evidence_found IS NULL),
    CONSTRAINT chk_qg_supported_evidence CHECK (
        verdict <> 'supported' OR (evidence_quote IS NOT NULL AND evidence_found IS TRUE)),
    CONSTRAINT chk_qg_judge CHECK (length(btrim(judge_model)) > 0),
    CONSTRAINT chk_qg_refs CHECK (jsonb_typeof(material_refs) = 'array')
);

COMMENT ON TABLE question_grounding IS
    'W2-3: kết quả QC "bám tài liệu" của một câu; chỉ cảnh báo, không đổi generated_question.status.';
COMMENT ON COLUMN question_grounding.evidence_found IS
    'Server có tìm thấy trích dẫn của judge trong đoạn tài liệu đã đưa vào prompt không; NULL khi judge không trích dẫn.';
COMMENT ON COLUMN question_grounding.generated_with_material IS
    'TRUE: câu được sinh CÓ tài liệu trong prompt; FALSE dành cho nhóm đối chứng không tài liệu khi đo thử nghiệm.';
