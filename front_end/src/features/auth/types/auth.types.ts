export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  user: {
    id: string;
    name: string;
    email: string;
    // Thêm các trường khác nếu cần
  };
}

/** Vai trò do GET /api/auth/me trả về (chữ thường như DB). */
export type UserRole = "teacher" | "admin" | "researcher";

export interface UserInfo {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  // các trường bổ sung nếu backend trả về
}
