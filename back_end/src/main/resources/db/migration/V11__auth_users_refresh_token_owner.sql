-- V11__auth_users_refresh_token_owner.sql
-- W1-1 (ngày 24/10): đăng nhập JWT 2 vai trò (teacher, admin) và quyền sở hữu dữ liệu. Không sửa V1-V10.
--
-- * users: mật khẩu (băm, DelegatingPasswordEncoder: "{bcrypt}..."), trạng thái, mốc thời gian; vai trò thêm 'admin';
--   email duy nhất không phân biệt hoa thường. User cũ giữ nguyên, password_hash NULL nên chưa đăng nhập được tới khi
--   admin đặt mật khẩu. Email rỗng đổi thành NULL. Còn email trùng thì dừng migration (không tự gộp dữ liệu).
-- * refresh_token: refresh token xoay vòng; chỉ lưu SHA-256 (hex) của token ngẫu nhiên 256 bit, không lưu token gốc.
--   family_id là một lần đăng nhập; dùng lại token đã thu hồi thì cả family bị thu hồi ('reuse_detected').
-- * exam_matrix.created_by: chủ sở hữu ma trận. Dữ liệu cũ: người tạo đề (id nhỏ nhất) dùng ma trận; ma trận chưa tạo
--   đề giao cho giáo viên có id nhỏ nhất (quyết định H7); chưa có giáo viên nào thì tạo giáo viên mặc định giống
--   UserService.getOrCreateDefaultTeacher (không mật khẩu). exam.created_by đã có từ V1.

-- 1. users
UPDATE users SET email = NULL WHERE email IS NOT NULL AND btrim(email) = '';

DO $$
DECLARE
    dup TEXT;
BEGIN
    SELECT string_agg(e, ', ') INTO dup
    FROM (SELECT lower(btrim(email)) AS e FROM users WHERE email IS NOT NULL
          GROUP BY lower(btrim(email)) HAVING count(*) > 1) d;
    IF dup IS NOT NULL THEN
        RAISE EXCEPTION 'V11: users có email trùng (không phân biệt hoa thường): %. Xử lý thủ công trước khi chạy V11.', dup;
    END IF;
END $$;

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN password_changed_at TIMESTAMP,
    ADD COLUMN last_login_at TIMESTAMP;

ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('teacher', 'researcher', 'admin')),
    ADD CONSTRAINT chk_users_login CHECK (password_hash IS NULL OR (email IS NOT NULL AND role IS NOT NULL));

CREATE UNIQUE INDEX uq_users_email_ci ON users (lower(btrim(email))) WHERE email IS NOT NULL;

COMMENT ON COLUMN users.password_hash IS
    'Mật khẩu băm (DelegatingPasswordEncoder, mặc định {bcrypt}); NULL = chưa đăng nhập được.';

-- 2. refresh_token
CREATE TABLE refresh_token (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             INT NOT NULL,
    token_hash          VARCHAR(64) NOT NULL,
    family_id           UUID NOT NULL,
    issued_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP NOT NULL,
    family_expires_at   TIMESTAMP NOT NULL,
    revoked_at          TIMESTAMP,
    revoke_reason       VARCHAR(20),
    replaced_by_id      BIGINT,
    user_agent          VARCHAR(255),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rt_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_token(id) ON DELETE SET NULL,
    CONSTRAINT uq_rt_hash UNIQUE (token_hash),
    CONSTRAINT chk_rt_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_rt_reason CHECK (revoke_reason IS NULL OR revoke_reason IN
        ('rotated', 'logout', 'reuse_detected', 'admin_disabled', 'password_changed')),
    CONSTRAINT chk_rt_revoked CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    CONSTRAINT chk_rt_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_rt_user ON refresh_token (user_id);
CREATE INDEX idx_rt_family ON refresh_token (family_id);

COMMENT ON TABLE refresh_token IS
    'Refresh token xoay vòng (W1-1). token_hash = SHA-256 hex của token gốc; token gốc chỉ nằm trong cookie HttpOnly.';

-- 3. exam_matrix.created_by
ALTER TABLE exam_matrix ADD COLUMN created_by INT;

UPDATE exam_matrix m
SET created_by = (SELECT min(e.created_by) FROM exam e WHERE e.matrix_id = m.id);

DO $$
DECLARE
    owner_id INT;
BEGIN
    IF EXISTS (SELECT 1 FROM exam_matrix WHERE created_by IS NULL) THEN
        SELECT min(id) INTO owner_id FROM users WHERE role = 'teacher';
        IF owner_id IS NULL THEN
            INSERT INTO users (name, email, role)
            VALUES ('Giáo viên Toán THCS',
                    CASE WHEN EXISTS (SELECT 1 FROM users WHERE lower(btrim(email)) = 'teacher.math@ptit.edu.vn')
                         THEN NULL ELSE 'teacher.math@ptit.edu.vn' END,
                    'teacher')
            RETURNING id INTO owner_id;
        END IF;
        UPDATE exam_matrix SET created_by = owner_id WHERE created_by IS NULL;
    END IF;
END $$;

ALTER TABLE exam_matrix
    ALTER COLUMN created_by SET NOT NULL,
    ADD CONSTRAINT fk_em_creator FOREIGN KEY (created_by) REFERENCES users(id);

CREATE INDEX idx_em_creator ON exam_matrix (created_by);
CREATE INDEX idx_exam_creator ON exam (created_by, created_at DESC);

COMMENT ON COLUMN exam_matrix.created_by IS 'Chủ sở hữu ma trận (W1-1); chỉ người này xem và tạo đề từ ma trận.';
