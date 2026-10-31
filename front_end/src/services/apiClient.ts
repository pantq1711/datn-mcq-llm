import axios from "axios";
import type { LoginResponse } from "../features/auth/types/auth.types";

const clientConfig = {
    baseURL: import.meta.env.VITE_API_BASE_URL,
    headers: {
        "Content-Type": "application/json",
    },
    // Cho phép gửi cookie (refresh‑token) tới backend
    withCredentials: true,
};

const apiClient = axios.create(clientConfig);

// Instance riêng cho refresh, không có interceptor: lỗi refresh không kích hoạt refresh lần nữa.
// Refresh nằm ở đây (không import từ authApi) để tránh import vòng apiClient ↔ authApi.
const refreshClient = axios.create(clientConfig);

// Các API xác thực: lỗi 401 ở đây là kết quả thật, không được refresh (tránh gọi refresh lặp vô hạn)
const AUTH_NO_REFRESH_URLS = ["/api/auth/login", "/api/auth/refresh", "/api/auth/logout"];

/** AuthContext nghe sự kiện này để xóa token trong state và đưa người dùng về trang đăng nhập. */
export const AUTH_LOGOUT_EVENT = "auth:logout";

// Refresh đang chạy (nếu có). Backend xoay vòng refresh token, nên nhiều request cùng 401
// phải chờ chung một lần refresh; gọi song song thì lần sau bị REFRESH_INVALID.
let refreshPromise: Promise<LoginResponse> | null = null;

// Hai tab cùng làm mới: tab chậm hơn nhận 401 REFRESH_RACE; chờ 1 giây rồi thử lại đúng một lần (docs/api mục 8)
const REFRESH_RACE_WAIT_MS = 1000;

function isRefreshRace(error: unknown): boolean {
    const response = (error as { response?: { status?: number; data?: { code?: string } } })?.response;
    return response?.status === 401 && response.data?.code === "REFRESH_RACE";
}

async function requestRefresh(): Promise<LoginResponse> {
    let response;
    try {
        response = await refreshClient.post<LoginResponse>("/api/auth/refresh");
    } catch (error) {
        if (!isRefreshRace(error)) throw error;
        await new Promise((resolve) => setTimeout(resolve, REFRESH_RACE_WAIT_MS));
        // Lần này vẫn lỗi (kể cả REFRESH_RACE) thì lỗi được ném ra và phiên bị xóa như REFRESH_INVALID
        response = await refreshClient.post<LoginResponse>("/api/auth/refresh");
    }
    if (response.data.accessToken) {
        localStorage.setItem("accessToken", response.data.accessToken);
    }
    return response.data;
}

/** Đổi refresh token (cookie) lấy access token mới và lưu lại; các lời gọi đồng thời dùng chung một request. */
export function refreshOnce(): Promise<LoginResponse> {
    if (!refreshPromise) {
        refreshPromise = requestRefresh().finally(() => {
            refreshPromise = null;
        });
    }
    return refreshPromise;
}

// Gắn access token đang lưu vào mọi request
apiClient.interceptors.request.use((config) => {
    const token = localStorage.getItem("accessToken");
    if (token) {
        config.headers["Authorization"] = `Bearer ${token}`;
    }
    return config;
});

apiClient.interceptors.response.use(
    (res) => res,
    async (error) => {
        const originalRequest = error.config;
        const isAuthRequest = AUTH_NO_REFRESH_URLS.some((url) => originalRequest?.url?.startsWith(url));
        // Nếu lỗi 401 và chưa thử refresh
        if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthRequest) {
            originalRequest._retry = true;
            try {
                await refreshOnce(); // refreshOnce() tự lưu token mới, request interceptor sẽ gắn lại khi retry
                return apiClient(originalRequest); // retry request
            } catch (refreshError) {
                // Refresh thất bại → xóa phiên, người dùng phải đăng nhập lại
                console.error("Refresh token failed", refreshError);
                localStorage.removeItem("accessToken");
                window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT));
            }
        }
        return Promise.reject(error);
    }
);

export default apiClient;
