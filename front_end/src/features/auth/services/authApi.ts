import axiosInstance from "../../../services/apiClient";
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

export async function refresh(): Promise<LoginResponse> {
  const response = await axiosInstance.post<LoginResponse>("/api/auth/refresh");
  // Lưu token mới (nếu có)
  if (response.data.accessToken) {
    localStorage.setItem("accessToken", response.data.accessToken);
  }
  return response.data;
}

export async function logout(): Promise<void> {
  await axiosInstance.post("/api/auth/logout");
  localStorage.removeItem("accessToken");
}

export async function getMe(): Promise<UserInfo> {
  const response = await axiosInstance.get<UserInfo>("/api/auth/me");
  return response.data;
}

export async function changePassword(email: string) {
  const response = await axiosInstance.post("/api/auth/password", { email });
  return response.data;   // tùy API trả về gì
}