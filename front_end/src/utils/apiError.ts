import { Modal, message } from "antd";

interface ApiErrorBody {
    error?: string;
    message?: string;
    code?: string;
}

// Mã 429 do giới hạn của hệ thống (docs/api mục 7.2b, 8.2): hiện nổi bật, không tự thử lại
const LIMIT_ERROR_CODES = ["GENERATION_QUEUE_LIMIT", "DAILY_GENERATION_LIMIT", "TOO_MANY_ATTEMPTS"];

function getBody(error: unknown): ApiErrorBody | undefined {
    return (error as { response?: { data?: ApiErrorBody } })?.response?.data;
}

/**
 * Câu báo lỗi cho giáo viên: lấy nguyên trường `error` tiếng Việt của backend;
 * chỉ khi backend không trả thì dùng `fallback`.
 */
export function getApiErrorMessage(error: unknown, fallback: string): string {
    const body = getBody(error);
    const fromBackend = body?.error || body?.message;
    if (typeof fromBackend === "string" && fromBackend.trim()) return fromBackend;
    const response = (error as { response?: unknown })?.response;
    // Có phản hồi mà không có câu của backend thì dùng câu dự phòng của nơi gọi; không phản hồi là mất kết nối
    return response ? fallback : "Không kết nối được máy chủ. Vui lòng thử lại.";
}

export function isLimitError(error: unknown): boolean {
    const status = (error as { response?: { status?: number } })?.response?.status;
    const code = getBody(error)?.code;
    return status === 429 && code !== undefined && LIMIT_ERROR_CODES.includes(code);
}

/** Báo lỗi API: lỗi giới hạn hiện hộp thoại phải bấm đóng, lỗi khác hiện thông báo ngắn. */
export function showApiError(error: unknown, fallback: string): void {
    const text = getApiErrorMessage(error, fallback);
    if (isLimitError(error)) {
        Modal.warning({ title: "Chưa tạo được lúc này", content: text, okText: "Đã hiểu" });
        return;
    }
    message.error(text);
}
