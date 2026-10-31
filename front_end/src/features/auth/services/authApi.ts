import axiosInstance, { refreshOnce } from "../../../services/apiClient";
import type { LoginRequest, LoginResponse, UserInfo } from "../types/auth.types";

/**
 * Gửi yêu cầu đăng nhập tới API bằng axios.
 *
 * @param payload - Thông tin đăng nhập (email và password).
 * @returns Dữ liệu trả về từ server, bao gồm token và thông tin người dùng.
 */
export async function login(payload: LoginRequest): Promise<LoginResponse> {
  try {
    const response = await axiosInstance.post<LoginResponse>("/api/auth/login", payload);
    // Lưu access token để các request sau có thể sử dụng
    if (response.data.accessToken) {
      localStorage.setItem("accessToken", response.data.accessToken);
    }
    return response.data;
  } catch (error: any) {
    // Cố gắng lấy thông báo lỗi từ response
    const errMsg = error?.response?.data?.message || "Login failed";
    throw new Error(errMsg);
  }
}

/** Lấy access token mới từ refresh token (cookie); dùng chung với refresh tự động của apiClient. */
export async function refresh(): Promise<LoginResponse> {
  return refreshOnce();
}

export async function logout(): Promise<void> {
  try {
    await axiosInstance.post("/api/auth/logout");
  } finally {
    // Gọi API lỗi (mất mạng, phiên đã hết hạn) vẫn phải xóa token ở trình duyệt
    localStorage.removeItem("accessToken");
  }
}

export async function getMe(): Promise<UserInfo> {
  const response = await axiosInstance.get<UserInfo>("/api/auth/me");
  return response.data;
}

/** Đổi mật khẩu; 204 thì mọi phiên bị thu hồi (docs/api mục 8). */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await axiosInstance.post("/api/auth/password", { currentPassword, newPassword });
}