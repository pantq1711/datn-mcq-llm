import axiosInstance from "../../../services/apiClient";
import type {
    AdminRole,
    AdminUserItem,
    CreateTeacherPayload,
    ResetPasswordPayload,
    UpdateUserPayload,
} from "../types/adminUser.types";

/**
 * Lấy danh sách tài khoản người dùng, có thể lọc theo vai trò (chỉ role ADMIN).
 */
export async function getAdminUsers(role?: AdminRole): Promise<AdminUserItem[]> {
    try {
        const response = await axiosInstance.get<AdminUserItem[]>("/api/admin/users", {
            params: role ? { role } : undefined,
        });
        return response.data;
    } catch (error: any) {
        const errMsg = error.response?.data?.message || error.response?.data?.error || "Không thể tải danh sách tài khoản";
        throw new Error(errMsg);
    }
}

/**
 * Tạo tài khoản giáo viên mới.
 */
export async function createTeacher(data: CreateTeacherPayload): Promise<AdminUserItem> {
    try {
        const response = await axiosInstance.post<AdminUserItem>("/api/admin/users", data);
        return response.data;
    } catch (error: any) {
        const errMsg = error.response?.data?.message || error.response?.data?.error || "Không thể tạo tài khoản giáo viên";
        throw new Error(errMsg);
    }
}

/**
 * Cập nhật thông tin tài khoản (tên, email, trạng thái enabled).
 */
export async function updateUser(id: number, data: UpdateUserPayload): Promise<AdminUserItem> {
    try {
        const response = await axiosInstance.patch<AdminUserItem>(`/api/admin/users/${id}`, data);
        return response.data;
    } catch (error: any) {
        const errMsg = error.response?.data?.message || error.response?.data?.error || "Không thể cập nhật tài khoản";
        throw new Error(errMsg);
    }
}

/**
 * Đặt lại mật khẩu cho tài khoản (thu hồi mọi phiên).
 */
export async function resetUserPassword(id: number, data: ResetPasswordPayload): Promise<void> {
    try {
        await axiosInstance.post(`/api/admin/users/${id}/reset-password`, data);
    } catch (error: any) {
        const errMsg = error.response?.data?.message || error.response?.data?.error || "Không thể đặt lại mật khẩu";
        throw new Error(errMsg);
    }
}
