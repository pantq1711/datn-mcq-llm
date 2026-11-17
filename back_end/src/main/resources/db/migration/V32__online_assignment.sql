-- V32__online_assignment.sql
-- Học sinh làm đề trực tuyến qua link (docs/design/lam-bai-truc-tuyen.md, An duyệt 14/11/2026, chốt thêm 16/11/2026 sau
-- rà soát an ninh 07). Không sửa V1-V31.
--
-- * exam_assignment: một lần giao đề. public_code là mã trong link và QR (lưu thô: giáo viên cần hiện lại link; mã chỉ mở
--   trang chào, không mở lượt làm của ai). title và scoring là ảnh chụp lúc giao (tên đề, bảng điểm app.exam.scoring), để
--   API công khai không đọc bảng exam và đổi cấu hình điểm không đổi điểm cũ. closed_at: bấm Đóng thì là lúc bấm; hết hạn
--   thì bằng closes_at (đóng lười, không có scheduler). show_solutions (16/11): sau khi bài đóng, học sinh xem đáp án và
--   lời giải từng câu bằng token của mình.
-- * assignment_class (16/11): các lớp được làm bài và sĩ số, do giáo viên khai lúc giao; học sinh chọn lớp trong danh sách,
--   số thứ tự phải trong 1..sĩ số.
-- * assignment_item: thành phần đề CHỐT lúc giao. Chỗ đã Giữ vẫn Đổi được (exam_slot.bank_item_id đổi tại chỗ), nên chấm
--   và thống kê đọc bank_item_id ở đây, không đọc lại exam_slot.
-- * assignment_attempt: một lượt làm của học sinh, định danh bằng lớp + số thứ tự (KHÔNG họ tên, KHÔNG IP, KHÔNG
--   user-agent). token_hash là SHA-256 (hex) của token ngẫu nhiên 32 byte; token thô chỉ trả một lần lúc bắt đầu.
--   (16/11) Không có ràng buộc duy nhất trên lớp + số thứ tự: hai lượt trùng đều được giữ và cùng bị đánh dấu "trùng"
--   lúc đọc (không lưu cờ), không lượt nào ghi đè lượt nào.
-- * attempt_answer: câu trả lời và điểm từng câu, ghi một lần lúc nộp, đủ mọi câu kể cả câu bỏ trống.

CREATE TABLE exam_assignment (
    id                      SERIAL PRIMARY KEY,
    exam_id                 INT NOT NULL,
    created_by              INT NOT NULL,
    public_code             VARCHAR(12) NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    duration_minutes        INT NOT NULL,
    closes_at               TIMESTAMP NOT NULL,
    show_score              BOOLEAN NOT NULL,
    show_solutions          BOOLEAN NOT NULL DEFAULT FALSE,
    status                  VARCHAR(10) NOT NULL DEFAULT 'open',
    closed_at               TIMESTAMP,
    scoring                 JSONB NOT NULL,
    consent_confirmed_at    TIMESTAMP NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ea_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
    CONSTRAINT fk_ea_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uq_ea_code UNIQUE (public_code),
    CONSTRAINT chk_ea_code CHECK (public_code ~ '^[A-Z2-9]{10}$'),
    CONSTRAINT chk_ea_duration CHECK (duration_minutes BETWEEN 5 AND 180),
    CONSTRAINT chk_ea_status CHECK (status IN ('open', 'closed')),
    CONSTRAINT chk_ea_closed CHECK ((status = 'closed') = (closed_at IS NOT NULL)),
    CONSTRAINT chk_ea_scoring CHECK (jsonb_typeof(scoring) = 'object')
);

CREATE INDEX idx_ea_exam ON exam_assignment (exam_id);

CREATE TABLE assignment_class (
    assignment_id   INT NOT NULL,
    class_label     VARCHAR(10) NOT NULL,
    roster_size     INT NOT NULL,
    position        INT NOT NULL,
    PRIMARY KEY (assignment_id, class_label),
    CONSTRAINT fk_ac_assignment FOREIGN KEY (assignment_id) REFERENCES exam_assignment(id) ON DELETE CASCADE,
    CONSTRAINT uq_ac_position UNIQUE (assignment_id, position),
    -- Dạng đã chuẩn hóa ở service: viết hoa, không khoảng trắng, chỉ chữ Latin, số, / . -
    CONSTRAINT chk_ac_label CHECK (class_label ~ '^[A-Z0-9/.-]{1,10}$'),
    CONSTRAINT chk_ac_roster CHECK (roster_size BETWEEN 1 AND 99),
    CONSTRAINT chk_ac_position CHECK (position >= 1)
);

CREATE TABLE assignment_item (
    assignment_id   INT NOT NULL,
    slot_id         INT NOT NULL,
    bank_item_id    INT NOT NULL,
    question_type   VARCHAR(10) NOT NULL,
    part            VARCHAR(3) NOT NULL,
    number_in_part  INT NOT NULL,
    position        INT NOT NULL,
    PRIMARY KEY (assignment_id, slot_id),
    CONSTRAINT fk_ai_assignment FOREIGN KEY (assignment_id) REFERENCES exam_assignment(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_slot FOREIGN KEY (slot_id) REFERENCES exam_slot(id),
    CONSTRAINT fk_ai_bank_item FOREIGN KEY (bank_item_id, question_type) REFERENCES bank_item (id, question_type),
    CONSTRAINT uq_ai_position UNIQUE (assignment_id, position),
    CONSTRAINT chk_ai_part_type CHECK (
        (part = 'I' AND question_type = 'mcq')
        OR (part = 'II' AND question_type = 'tf')
        OR (part = 'III' AND question_type = 'sa')),
    CONSTRAINT chk_ai_numbers CHECK (number_in_part >= 1 AND position >= 1)
);

CREATE INDEX idx_ai_bank_item ON assignment_item (bank_item_id);

CREATE TABLE assignment_attempt (
    id              SERIAL PRIMARY KEY,
    assignment_id   INT NOT NULL,
    class_label     VARCHAR(10) NOT NULL,
    seat_no         INT NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    started_at      TIMESTAMP NOT NULL,
    deadline_at     TIMESTAMP NOT NULL,
    submitted_at    TIMESTAMP,
    score           NUMERIC(5,2),
    max_score       NUMERIC(5,2),
    CONSTRAINT fk_aa_assignment FOREIGN KEY (assignment_id) REFERENCES exam_assignment(id) ON DELETE CASCADE,
    CONSTRAINT fk_aa_class FOREIGN KEY (assignment_id, class_label)
        REFERENCES assignment_class (assignment_id, class_label) ON DELETE CASCADE,
    CONSTRAINT uq_aa_token UNIQUE (token_hash),
    -- Số thứ tự không vượt sĩ số của lớp: service kiểm; DB chỉ chặn ngoài 1..99.
    CONSTRAINT chk_aa_seat CHECK (seat_no BETWEEN 1 AND 99),
    CONSTRAINT chk_aa_token CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_aa_submitted CHECK ((submitted_at IS NULL) = (score IS NULL) AND (score IS NULL) = (max_score IS NULL))
);

-- Không UNIQUE (16/11): lượt trùng lớp + số thứ tự được giữ cả hai và đánh dấu lúc đọc.
CREATE INDEX idx_aa_identity ON assignment_attempt (assignment_id, class_label, seat_no);

CREATE TABLE attempt_answer (
    attempt_id      INT NOT NULL,
    slot_id         INT NOT NULL,
    mcq_choice      VARCHAR(1),
    tf_choices      VARCHAR(4),
    sa_text         VARCHAR(20),
    correct_count   SMALLINT NOT NULL,
    score           NUMERIC(4,2) NOT NULL,
    PRIMARY KEY (attempt_id, slot_id),
    CONSTRAINT fk_an_attempt FOREIGN KEY (attempt_id) REFERENCES assignment_attempt(id) ON DELETE CASCADE,
    CONSTRAINT chk_an_mcq CHECK (mcq_choice IS NULL OR mcq_choice IN ('A', 'B', 'C', 'D')),
    CONSTRAINT chk_an_tf CHECK (tf_choices IS NULL OR tf_choices ~ '^[TF-]{4}$'),
    CONSTRAINT chk_an_correct CHECK (correct_count BETWEEN 0 AND 4),
    CONSTRAINT chk_an_score CHECK (score >= 0)
);

COMMENT ON TABLE exam_assignment IS 'Bài giao: học sinh làm đề trực tuyến qua link (V32).';
COMMENT ON COLUMN exam_assignment.show_solutions IS 'Sau khi bài đóng, học sinh xem đáp án và lời giải bằng token của mình (V32).';
COMMENT ON TABLE assignment_class IS 'Lớp được làm bài và sĩ số, giáo viên khai lúc giao (V32).';
COMMENT ON TABLE assignment_item IS 'Thành phần đề chốt lúc giao; chấm và thống kê đọc bank_item_id ở đây (V32).';
COMMENT ON TABLE assignment_attempt IS 'Lượt làm của học sinh: lớp + số thứ tự, không họ tên, không IP; lượt trùng giữ cả hai (V32).';
COMMENT ON COLUMN assignment_attempt.token_hash IS 'SHA-256 (hex) của token lượt làm; token thô không lưu.';
COMMENT ON TABLE attempt_answer IS 'Câu trả lời và điểm từng câu, ghi một lần lúc nộp (V32).';
