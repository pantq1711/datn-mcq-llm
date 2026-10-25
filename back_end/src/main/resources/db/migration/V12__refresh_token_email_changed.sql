-- V12__refresh_token_email_changed.sql
-- W1-1 (ngày 24/10): admin đổi email của tài khoản (PATCH /api/admin/users/{id}) thì thu hồi mọi refresh token của
-- người đó với lý do 'email_changed'. Không sửa V1-V11 (V11 đã commit).

ALTER TABLE refresh_token DROP CONSTRAINT chk_rt_reason;
ALTER TABLE refresh_token
    ADD CONSTRAINT chk_rt_reason CHECK (revoke_reason IS NULL OR revoke_reason IN
        ('rotated', 'logout', 'reuse_detected', 'admin_disabled', 'password_changed', 'email_changed'));
