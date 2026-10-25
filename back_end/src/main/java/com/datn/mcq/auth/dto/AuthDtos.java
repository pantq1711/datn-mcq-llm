package com.datn.mcq.auth.dto;

import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;

/** DTO của API đăng nhập (W1-1). Refresh token không bao giờ nằm trong JSON (chỉ ở cookie HttpOnly). */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(String email, String password) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    /** @param role giá trị viết thường như DB: "teacher" hoặc "admin" */
    public record UserDto(Integer id, String name, String email, UserRole role) {
        public static UserDto of(UserAccount u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole());
        }
    }

    /**
     * @param tokenType luôn "Bearer"
     * @param expiresIn số giây access token còn hiệu lực
     */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {}
}
