-- V33__item_stat.sql
-- Phân tích câu sau khi làm bài (PR B, docs/design/lam-bai-truc-tuyen.md mục 1.5, 5 và 14). Không sửa V1-V32.
--
-- * exam_assignment.stats_computed_at: lúc tính thống kê gần nhất; NULL = chưa tính hoặc đã bị vô hiệu (giáo viên xóa
--   lượt làm sau khi tính). Thống kê chỉ tính khi bài đã đóng VÀ đã qua ân hạn nộp 2 phút tính từ lúc đóng (lượt có hạn
--   không muộn hơn lúc đóng vẫn nộp được trong ân hạn), lúc giáo viên đọc lần đầu hoặc bấm Tính lại.
-- * item_stat: một dòng cho mỗi câu của bài giao. Chỉ tính lượt ĐÃ NỘP và KHÔNG trùng lớp + số thứ tự (cùng quy tắc
--   "trùng" của bảng điểm, tính lúc đọc). n = 0 thì không có dòng nào. bank_item_id, question_type chép từ
--   assignment_item. d NULL khi group_size = 0 (n = 1). choice_counts, flags: mục 5.3, 5.4 của thiết kế.

ALTER TABLE exam_assignment
    ADD COLUMN stats_computed_at TIMESTAMP;

CREATE TABLE item_stat (
    assignment_id   INT NOT NULL,
    slot_id         INT NOT NULL,
    bank_item_id    INT NOT NULL,
    question_type   VARCHAR(10) NOT NULL,
    n               INT NOT NULL,
    group_size      INT NOT NULL,
    p               NUMERIC(5,4) NOT NULL,
    d               NUMERIC(5,4),
    choice_counts   JSONB NOT NULL,
    flags           JSONB NOT NULL,
    computed_at     TIMESTAMP NOT NULL,
    PRIMARY KEY (assignment_id, slot_id),
    CONSTRAINT fk_is_item FOREIGN KEY (assignment_id, slot_id)
        REFERENCES assignment_item (assignment_id, slot_id) ON DELETE CASCADE,
    CONSTRAINT chk_is_type CHECK (question_type IN ('mcq', 'tf', 'sa')),
    CONSTRAINT chk_is_n CHECK (n >= 1),
    CONSTRAINT chk_is_group CHECK (group_size >= 0 AND 2 * group_size <= n),
    CONSTRAINT chk_is_p CHECK (p BETWEEN 0 AND 1),
    CONSTRAINT chk_is_d CHECK (d BETWEEN -1 AND 1),
    CONSTRAINT chk_is_d_null CHECK ((group_size = 0) = (d IS NULL)),
    CONSTRAINT chk_is_counts CHECK (jsonb_typeof(choice_counts) = 'object'),
    CONSTRAINT chk_is_flags CHECK (jsonb_typeof(flags) = 'array')
);

CREATE INDEX idx_is_bank_item ON item_stat (bank_item_id);

COMMENT ON COLUMN exam_assignment.stats_computed_at IS 'Lúc tính item_stat gần nhất; NULL = chưa tính hoặc đã vô hiệu khi xóa lượt (V33).';
COMMENT ON TABLE item_stat IS 'Thống kê câu (p, D, lượt chọn, cờ hậu kiểm) của một bài giao; chỉ lượt đã nộp, không trùng (V33).';
