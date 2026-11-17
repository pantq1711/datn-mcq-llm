import type { SubmitResult } from "../types/assignment.types";

// Nộp bài tự thử lại khi gặp lỗi tạm thời (docs/api mục 11.2; AN-03): lỗi mạng, 429 (theo lượt làm, tôn trọng
// Retry-After) và 502/503/504 khi máy chủ đang khởi động lại. Lỗi khác (400, 401, 409) không thử lại.

export const MAX_SUBMIT_ATTEMPTS = 8;
const BASE_DELAY_MS = 1000;
const MAX_BACKOFF_MS = 15000;
const MAX_RETRY_AFTER_MS = 60000;

interface ErrorLike {
    response?: {
        status?: number;
        data?: { code?: string; error?: string };
        headers?: Record<string, unknown>;
    };
}

export type SubmitFailureKind =
    | "network" // không có phản hồi
    | "rate_limited" // 429
    | "server" // 502/503/504
    | "closed" // 409 ASSIGNMENT_CLOSED
    | "expired" // 409 ATTEMPT_EXPIRED
    | "token_invalid" // 401
    | "invalid" // 400
    | "other";

export function isNoResponse(error: unknown): boolean {
    const e = error as ErrorLike | undefined;
    return !!e && typeof e === "object" && !e.response;
}

export function classifySubmitError(error: unknown): SubmitFailureKind {
    const e = error as ErrorLike | undefined;
    const status = e?.response?.status;
    if (!e?.response) return "network";
    if (status === 429) return "rate_limited";
    if (status === 502 || status === 503 || status === 504) return "server";
    if (status === 401) return "token_invalid";
    if (status === 400) return "invalid";
    if (status === 409) {
        const code = e.response.data?.code;
        if (code === "ASSIGNMENT_CLOSED") return "closed";
        if (code === "ATTEMPT_EXPIRED") return "expired";
    }
    return "other";
}

export function isRetryable(error: unknown): boolean {
    const kind = classifySubmitError(error);
    return kind === "network" || kind === "rate_limited" || kind === "server";
}

export function isAlreadySubmitted(error: unknown): boolean {
    const e = error as ErrorLike | undefined;
    return e?.response?.status === 409 && e.response.data?.code === "ATTEMPT_ALREADY_SUBMITTED";
}

/**
 * Số giây chờ do máy chủ yêu cầu. Nguồn chính là header Retry-After (cùng origin luôn đọc được; khác origin cần backend
 * mở header trong CORS). Chỉ khi không có header hợp lệ mới dự phòng bằng câu báo của backend ("Thử lại sau N giây.").
 * undefined nếu không có cả hai.
 */
export function getRetryAfterSeconds(error: unknown): number | undefined {
    const e = error as ErrorLike | undefined;
    const raw = e?.response?.headers?.["retry-after"];
    const fromHeader = typeof raw === "string" || typeof raw === "number" ? Number(raw) : NaN;
    if (Number.isFinite(fromHeader) && fromHeader > 0) return fromHeader;
    const text = e?.response?.data?.error;
    const match = typeof text === "string" ? /sau\s+(\d+)\s*giây/i.exec(text) : null;
    const fromText = match ? Number(match[1]) : NaN;
    return Number.isFinite(fromText) && fromText > 0 ? fromText : undefined;
}

/** Thời gian chờ trước lần thử thứ `retryIndex` (0 = lần thử lại đầu): lùi dần 1, 2, 4, 8, 15 giây; không ngắn hơn Retry-After. */
export function nextDelayMs(retryIndex: number, error: unknown, random: () => number = Math.random): number {
    const backoff = Math.min(MAX_BACKOFF_MS, BASE_DELAY_MS * 2 ** retryIndex);
    // Cả lớp nộp cùng lúc: lệch ngẫu nhiên tới 20% để các máy không thử lại cùng một giây
    const jittered = Math.round(backoff * (1 + 0.2 * random()));
    const retryAfter = getRetryAfterSeconds(error);
    if (retryAfter === undefined) return jittered;
    return Math.max(jittered, Math.min(MAX_RETRY_AFTER_MS, retryAfter * 1000));
}

export interface SubmitRetryOptions {
    maxAttempts?: number;
    /** Chờ ms; tách ra để test không phải chờ thật. */
    sleep?: (ms: number) => Promise<void>;
    random?: () => number;
    /** Gọi trước mỗi lần chờ: để màn hình báo "thử lại sau N giây". */
    onRetry?: (info: { attempt: number; waitMs: number; kind: SubmitFailureKind }) => void;
    /** Trả true để dừng thử lại (ví dụ người dùng rời trang). */
    shouldStop?: () => boolean;
}

export interface SubmitOutcome {
    /** Kết quả của máy chủ; undefined khi máy chủ báo đã nộp từ trước (409 ATTEMPT_ALREADY_SUBMITTED). */
    result?: SubmitResult;
    alreadySubmitted: boolean;
    attempts: number;
}

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Gọi `send` tới khi thành công hoặc gặp lỗi không thể thử lại (ném lại lỗi đó), hoặc hết số lần thử (ném lỗi cuối).
 * "Đã nộp rồi" (409) cũng coi là xong: lần gửi trước có thể đã tới máy chủ dù trình duyệt không nhận được phản hồi.
 */
export async function submitWithRetry(
    send: () => Promise<SubmitResult>,
    options: SubmitRetryOptions = {}
): Promise<SubmitOutcome> {
    const maxAttempts = options.maxAttempts ?? MAX_SUBMIT_ATTEMPTS;
    const sleep = options.sleep ?? defaultSleep;
    let attempt = 0;
    for (;;) {
        attempt += 1;
        try {
            const result = await send();
            return { result, alreadySubmitted: false, attempts: attempt };
        } catch (error) {
            if (isAlreadySubmitted(error)) {
                return { alreadySubmitted: true, attempts: attempt };
            }
            if (!isRetryable(error) || attempt >= maxAttempts || options.shouldStop?.()) {
                throw error;
            }
            const waitMs = nextDelayMs(attempt - 1, error, options.random);
            options.onRetry?.({ attempt, waitMs, kind: classifySubmitError(error) });
            await sleep(waitMs);
            if (options.shouldStop?.()) throw error;
        }
    }
}
