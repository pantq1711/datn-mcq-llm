-- V19__exam_generation_context.sql
-- W2-3 bước 4 (ngày 25/10): gắn tài liệu bài học và "yêu cầu thêm cho AI" vào lần tạo đề. Không sửa V1-V18.
--
-- * exam_generation_context: ngữ cảnh sinh câu của MỘT đề (quyết định F: gắn theo từng đề, không gắn vào ma trận, vì ma
--   trận dùng lại được và id ô đổi khi sửa ma trận). cell_id NULL là phạm vi cả đề (sinh theo bài, hoặc "cả ma trận"); cell_id
--   khác NULL là phạm vi một ô. Mỗi đề có tối đa một dòng cho mỗi phạm vi (chỉ mục duy nhất, COALESCE vì NULL không trùng
--   nhau). Tài liệu hiệu lực của một ô là hợp của tài liệu cả đề và tài liệu của ô. extra_instruction: yêu cầu thêm của
--   giáo viên (1-1000 ký tự, NULL khi không có); là DỮ LIỆU cho prompt, không ghi đè định dạng, số lượng, mức độ hay QC.
--   Xóa đề xóa luôn ngữ cảnh. Ô không bị xóa khi đề còn (MATRIX_IN_USE).
-- * exam_context_material: tài liệu của một phạm vi. Khóa ngoại tới lesson_material KHÔNG xóa dây chuyền (quyết định G):
--   tài liệu đã gắn vào đề thì không xóa được (API trả 409 MATERIAL_IN_USE).
-- * generation_job.material_context / material_refs: ảnh chụp đúng đoạn tài liệu đã đưa vào prompt của job và danh sách
--   đoạn (materialId, sha256, from, to...), để truy lại được model đã thấy gì dù tài liệu sau đó bị sửa. Cột chỉ ghi ở
--   bước chọn đoạn (bước sau của task); cả hai cùng NULL hoặc cùng có.
-- * bank_item.visible_to_user_id: NULL là câu dùng chung (mọi câu cũ). Khác NULL: chỉ người dùng đó thấy và dùng lại câu
--   (không là ứng viên của người khác, không hiện trong tìm kiếm hay chủ đề; quyết định E). Đặt khi Giữ câu sinh từ tài
--   liệu của giáo viên. Không xóa dây chuyền: xóa người dùng còn câu riêng tư thì bị chặn, tránh biến câu riêng thành
--   câu dùng chung.

CREATE TABLE exam_generation_context (
    id                  SERIAL PRIMARY KEY,
    exam_id             INT NOT NULL,
    cell_id             INT,
    extra_instruction   TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_egc_exam FOREIGN KEY (exam_id) REFERENCES exam(id) ON DELETE CASCADE,
    CONSTRAINT fk_egc_cell FOREIGN KEY (cell_id) REFERENCES exam_matrix_cell(id),
    CONSTRAINT chk_egc_instruction CHECK (
        extra_instruction IS NULL OR char_length(extra_instruction) BETWEEN 1 AND 1000)
);

CREATE UNIQUE INDEX uq_egc_scope ON exam_generation_context (exam_id, COALESCE(cell_id, 0));

CREATE TABLE exam_context_material (
    context_id      INT NOT NULL,
    material_id     INT NOT NULL,
    PRIMARY KEY (context_id, material_id),
    CONSTRAINT fk_ecm_context FOREIGN KEY (context_id) REFERENCES exam_generation_context(id) ON DELETE CASCADE,
    CONSTRAINT fk_ecm_material FOREIGN KEY (material_id) REFERENCES lesson_material(id)
);

CREATE INDEX idx_ecm_material ON exam_context_material (material_id);

ALTER TABLE generation_job
    ADD COLUMN material_context TEXT,
    ADD COLUMN material_refs JSONB,
    ADD CONSTRAINT chk_gj_material_pair CHECK ((material_context IS NULL) = (material_refs IS NULL)),
    ADD CONSTRAINT chk_gj_material_refs CHECK (material_refs IS NULL OR jsonb_typeof(material_refs) = 'array'),
    ADD CONSTRAINT chk_gj_material_ctx CHECK (
        material_context IS NULL OR char_length(material_context) <= 12000);

ALTER TABLE bank_item
    ADD COLUMN visible_to_user_id INT,
    ADD CONSTRAINT fk_bi_visible_user FOREIGN KEY (visible_to_user_id) REFERENCES users(id);

CREATE INDEX idx_bi_visible ON bank_item (visible_to_user_id) WHERE visible_to_user_id IS NOT NULL;

COMMENT ON TABLE exam_generation_context IS
    'W2-3: ngữ cảnh sinh câu của một đề (tài liệu, yêu cầu thêm); cell_id NULL là cả đề.';
COMMENT ON COLUMN generation_job.material_context IS
    'W2-3: đúng đoạn tài liệu đã đưa vào prompt của job (ảnh chụp); NULL khi không có tài liệu.';
COMMENT ON COLUMN bank_item.visible_to_user_id IS
    'W2-3: NULL = dùng chung; khác NULL = chỉ người dùng đó thấy và dùng lại (câu sinh từ tài liệu của họ).';
