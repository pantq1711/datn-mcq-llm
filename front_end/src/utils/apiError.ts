import { Modal, message } from "antd";
import { createElement } from "react";

interface ApiErrorBody {
    error?: string;
    message?: string;
    code?: string;
}

// Hệ thống đã hết lượt gọi Gemini trong ngày (429 kèm Retry-After, docs/api mục 7.2b): dùng chung cho cả hệ thống
const DAILY_QUOTA_CODE = "LLM_DAILY_QUOTA_EXHAUSTED";

// Mã 429 do giới hạn của hệ thống (docs/api mục 7.2b, 8.2): hiện nổi bật, không tự thử lại
const LIMIT_ERROR_CODES = ["GENERATION_QUEUE_LIMIT", "DAILY_GENERATION_LIMIT", "TOO_MANY_ATTEMPTS", DAILY_QUOTA_CODE];

function getBody(error: unknown): ApiErrorBody | undefined {
    return (error as { response?: { data?: ApiErrorBody } })?.response?.data;
}

/** Số giây trong header Retry-After của phản hồi lỗi (axios hạ chữ thường tên header); undefined nếu không có hoặc sai. */
function getRetryAfterSeconds(error: unknown): number | undefined {
    const headers = (error as { response?: { headers?: Record<string, unknown> } })?.response?.headers;
    const raw = headers?.["retry-after"];
    const seconds = typeof raw === "string" || typeof raw === "number" ? Number(raw) : NaN;
    return Number.isFinite(seconds) && seconds > 0 ? seconds : undefined;
}

/**
 * Câu báo khi hệ thống hết lượt soạn câu trong ngày: "thử lại sau HH:MM" theo giờ Việt Nam, tính từ Retry-After
 * (giây kể từ `nowMs`). Không có Retry-After thì không nêu giờ.
 */
export function dailyQuotaMessage(retryAfterSeconds: number | undefined, nowMs: number = Date.now()): string {
    const base = "Hệ thống đã dùng hết lượt soạn câu hôm nay, thầy/cô thử lại sau";
    if (retryAfterSeconds === undefined) return `${base}.`;
    const time = new Intl.DateTimeFormat("vi-VN", {
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
        timeZone: "Asia/Ho_Chi_Minh",
    }).format(new Date(nowMs + retryAfterSeconds * 1000));
    return `${base} ${time}.`;
}

/**
 * Câu báo lỗi cho giáo viên: lấy nguyên trường `error` tiếng Việt của backend;
 * chỉ khi backend không trả thì dùng `fallback`. Riêng hết lượt soạn câu trong ngày thì tự ghép giờ mở lại.
 */
export function getApiErrorMessage(error: unknown, fallback: string): string {
    const body = getBody(error);
    if (body?.code === DAILY_QUOTA_CODE) {
        return dailyQuotaMessage(getRetryAfterSeconds(error));
    }
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

// Câu hết hạn mức ngày của backend (GenerationQuotaService.dailyMessage): rút số liệu để viết lại thành dòng ngắn
const DAILY_LIMIT_PATTERN =
    /tối đa (\d+) câu mỗi ngày[\s\S]*?đã dùng (\d+) câu[\s\S]*?yêu cầu này cần (\d+) câu[\s\S]*?từ 0 giờ ngày ([\d/]+)/;

/** Tách theo câu: cắt sau dấu chấm có khoảng trắng theo sau (ngày "17/11/2026", giờ "07:00" không bị cắt). */
function splitSentences(text: string): string[] {
    return text.split(/(?<=\.)\s+/).map((s) => s.trim()).filter(Boolean);
}

/**
 * Các dòng hiện trong hộp lỗi giới hạn (rà soát E14). Hết hạn mức ngày: viết lại thành dòng ngắn từ số liệu trong câu
 * backend; câu không khớp mẫu (backend đổi lời) thì tách theo câu như mọi lỗi giới hạn khác, không mất chữ nào.
 */
export function limitMessageLines(code: string | undefined, text: string): string[] {
    if (code === "DAILY_GENERATION_LIMIT") {
        const m = DAILY_LIMIT_PATTERN.exec(text);
        if (m) {
            const [, limit, used, requested, date] = m;
            return [
                `Hôm nay đã dùng ${used}/${limit} câu.`,
                `Yêu cầu này cần ${requested} câu.`,
                `Tạo tiếp được từ 0 giờ ngày ${date}, hoặc dùng câu có sẵn trong ngân hàng.`,
            ];
        }
    }
    return splitSentences(text);
}

/** Thông báo lỗi thường giữ 7 giây (mặc định antd 3 giây quá nhanh để đọc hết; rà soát A5). */
const ERROR_MESSAGE_SECONDS = 7;

/** Báo lỗi API: lỗi giới hạn hiện hộp thoại phải bấm đóng (mỗi ý một dòng), lỗi khác hiện thông báo 7 giây. */
export function showApiError(error: unknown, fallback: string): void {
    const text = getApiErrorMessage(error, fallback);
    if (isLimitError(error)) {
        const lines = limitMessageLines(getBody(error)?.code, text);
        Modal.warning({
            title: "Chưa tạo được lúc này",
            content: createElement("div", null, ...lines.map((line, i) => createElement("div", { key: i }, line))),
            okText: "Đã hiểu",
        });
        return;
    }
    message.error(text, ERROR_MESSAGE_SECONDS);
}
