import axios from "axios";
import { refresh } from "../features/auth/services/authApi";

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    headers: {
        "Content-Type": "application/json",
    },
    // Cho phép gửi cookie (refresh‑token) tới backend
    withCredentials: true,
});

apiClient.interceptors.response.use(
    (res) => res,
    async (error) => {
        const originalRequest = error.config;
        // Nếu lỗi 401 và chưa thử refresh
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;
            try {
                const { accessToken } = await refresh(); // gọi refresh endpoint
                // Gắn token mới vào header Authorization
                apiClient.defaults.headers.common["Authorization"] = `Bearer ${accessToken}`;
                originalRequest.headers["Authorization"] = `Bearer ${accessToken}`;
                return apiClient(originalRequest); // retry request
            } catch (refreshError) {
                // Refresh thất bại → người dùng phải đăng nhập lại
                console.error("Refresh token failed", refreshError);
                // Có thể chuyển hướng tới /auth/login ở đây
            }
        }
        return Promise.reject(error);
    }
);

export default apiClient;
