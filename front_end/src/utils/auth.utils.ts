import type { UserRole } from "../features/auth/types/auth.types";

// Giá trị role khớp backend (UserRole @JsonValue, chữ thường)
export const ROLE_TEACHER: UserRole = "teacher";
export const ROLE_ADMIN: UserRole = "admin";

/** Nhãn và màu hiển thị của vai trò (Header, Hồ sơ). */
export const ROLE_LABELS: Record<string, { label: string; color: string }> = {
    teacher: { label: "Giáo viên", color: "blue" },
    admin: { label: "Quản trị viên", color: "red" },
    researcher: { label: "Nghiên cứu viên", color: "green" },
};

export function describeRole(role?: string | null): { label: string; color: string } {
    if (!role) return { label: "Không xác định", color: "default" };
    return ROLE_LABELS[role.toLowerCase()] ?? { label: role, color: "default" };
}

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
