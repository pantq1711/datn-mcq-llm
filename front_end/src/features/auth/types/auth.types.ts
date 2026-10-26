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

export interface UserInfo {
  id: string;
  name: string;
  email: string;
  role: "GV" | "AD" | "STUDENT" | string; // tùy API
  // các trường bổ sung nếu backend trả về
}
