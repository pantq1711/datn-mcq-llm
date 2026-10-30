import type { UserRole } from "../features/auth/types/auth.types";

// Giá trị role khớp backend (UserRole @JsonValue, chữ thường)
export const ROLE_TEACHER: UserRole = "teacher";
export const ROLE_ADMIN: UserRole = "admin";

/** Trang chủ theo vai trò; vai trò không được đăng nhập web thì về trang đăng nhập. */
export function getHomePath(role?: string | null): string {
    switch (role?.toLowerCase()) {
        case ROLE_TEACHER:
            return "/";
        case ROLE_ADMIN:
            return "/admin/users";
        default:
            return "/auth/login";
    }
}
