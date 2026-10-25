package com.datn.mcq.common.security;

/**
 * Người đang gọi API, lấy từ access token (claim sub = id, role = TEACHER/ADMIN). Controller nhận làm tham số (qua
 * {@link CurrentUserArgumentResolver}) rồi truyền id xuống service; service không đọc SecurityContext, nên worker và
 * test service gọi được mà không cần đăng nhập.
 */
public record CurrentUser(Integer id, String role) {

    public static final String TEACHER = "TEACHER";
    public static final String ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
