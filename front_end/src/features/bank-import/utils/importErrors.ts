import { getApiErrorMessage } from "../../../utils/apiError";

export type ImportErrorKind =
    /** 410 hoặc 404: lô hết hạn, bị xóa hoặc không phải của tôi. */
    | "gone"
    /** 422 IMPORT_ITEM_BLOCKED: có câu còn cờ chặn. */
    | "blocked"
    /** 409 IMPORT_NOT_PREVIEW: lô đã được xác nhận. */
    | "not_preview"
    /** 400 IMPORT_LEVEL_DEFAULT_REQUIRED: file có câu chưa ghi mức, cần mức mặc định. */
    | "level_required"
    /** 503 EXTRACTOR_BUSY: máy đang bận, thử lại sau ít giây. */
    | "busy"
    | "other";

export interface ClassifiedImportError {
    kind: ImportErrorKind;
    message: string;
}

function statusOf(error: unknown): number | undefined {
    return (error as { response?: { status?: number } })?.response?.status;
}

function codeOf(error: unknown): string | undefined {
    return (error as { response?: { data?: { code?: string } } })?.response?.data?.code;
}

/** Phân loại lỗi của /api/bank-imports (docs/api mục 10); `message` là câu tiếng Việt để hiện cho giáo viên. */
export function classifyImportError(error: unknown, fallback: string): ClassifiedImportError {
    const status = statusOf(error);
    const code = codeOf(error);
    const message = getApiErrorMessage(error, fallback);
    if (status === 410 || status === 404) return { kind: "gone", message };
    if (code === "IMPORT_ITEM_BLOCKED") return { kind: "blocked", message };
    if (code === "IMPORT_NOT_PREVIEW") return { kind: "not_preview", message };
    if (code === "IMPORT_LEVEL_DEFAULT_REQUIRED") return { kind: "level_required", message };
    if (code === "EXTRACTOR_BUSY" || status === 503) return { kind: "busy", message };
    return { kind: "other", message };
}

/** 409 IMPORT_PENDING_LIMIT: đã đủ số lô chờ xác nhận; danh sách lô chờ cần tải lại để thầy/cô hủy bớt. */
export function isPendingLimitError(error: unknown): boolean {
    return codeOf(error) === "IMPORT_PENDING_LIMIT";
}
