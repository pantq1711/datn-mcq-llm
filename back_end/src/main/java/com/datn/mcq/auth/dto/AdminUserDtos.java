package com.datn.mcq.auth.dto;

import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;

import java.time.LocalDateTime;

/** DTO của API quản trị tài khoản /api/admin/users (W1-1 đợt 2). Không bao giờ chứa mật khẩu hay mật khẩu băm. */
public final class AdminUserDtos {

    private AdminUserDtos() {}

    /** Admin tạo tài khoản giáo viên (vai trò luôn là teacher). Mật khẩu từ 8 ký tự, tối đa 72 byte. */
    public record CreateTeacherRequest(String name, String email, String password) {}

    /**
     * Sửa tài khoản: chỉ đổi trường được gửi; phải gửi ít nhất một trường.
     *
     * @param email email mới (chuẩn hóa chữ thường, bỏ khoảng trắng hai đầu); đổi email thì thu hồi phiên của người đó
     */
    public record UpdateUserRequest(String name, String email, Boolean enabled) {}

    public record ResetPasswordRequest(String newPassword) {}

    /**
     * @param hasPassword false với tài khoản cũ chưa đặt mật khẩu (chưa đăng nhập được)
     */
    public record AdminUserDto(Integer id, String name, String email, UserRole role, boolean enabled,
                               boolean hasPassword, LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        public static AdminUserDto of(UserAccount u) {
            return new AdminUserDto(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                    Boolean.TRUE.equals(u.getEnabled()), u.getPasswordHash() != null, u.getCreatedAt(),
                    u.getLastLoginAt());
        }
    }
}
