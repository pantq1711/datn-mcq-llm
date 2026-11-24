import type { QuestionImageInfo } from "../../../components/common/MathText/questionImage.types";

export type { QuestionImageInfo };

/** Khớp BankImportDtos của backend (docs/api mục 10). */
export type ImportType = "mcq" | "tf" | "sa";
export type ImportLevel = "easy" | "medium" | "hard";
export type ImportStatus = "preview" | "confirmed" | "expired";

export interface ImportItemOption {
    key: string;
    text: string;
}

export interface ImportItemStatement {
    key: string;
    text: string;
    /** true = Đúng, false = Sai, null = chưa xác định (kèm cờ chặn). */
    label: boolean | null;
}

export interface ImportItem {
    /** Mã câu trong lô, ví dụ "mcq-007". */
    key: string;
    type: ImportType;
    /** Vị trí trong file Word ("Phần I, câu thứ 7"). */
    locator: string;
    levelRaw: string | null;
    level: ImportLevel | null;
    stem: string;
    options?: ImportItemOption[] | null;
    correctOption?: string | null;
    statements?: ImportItemStatement[] | null;
    saAnswer?: string | null;
    explanation?: string | null;
    flags: string[];
    /** Có cờ chặn: không chọn được khi xác nhận. */
    blocking: boolean;
    /** Lý do bằng lời của từng cờ chặn; null với lô lưu trước 16/11. */
    blockReasons?: string[] | null;
    /** Chỉ có khi câu có hình. */
    images?: QuestionImageInfo[];
}

export interface ImportSkippedItem {
    locator: string;
    type: string;
    reason: string;
}

export interface ImportBatch {
    id: number;
    displayName: string;
    status: ImportStatus;
    subjectId: number;
    gradeLevel: number;
    topic: string | null;
    createdAt: string;
    expiresAt: string;
    confirmedAt: string | null;
    questionCount: number;
    importableCount: number;
    blockedCount: number;
    importedCount: number | null;
    /** Số câu bị bỏ theo lý do (has_image, image_limit, ...). */
    skipped: Record<string, number>;
    /** Rỗng với bản tóm tắt (danh sách lô chờ) và lô đã xác nhận. */
    skippedItems?: ImportSkippedItem[] | null;
    items?: ImportItem[] | null;
}

export interface ConfirmResult {
    batchId: number;
    imported: number;
    bankItemIds: number[];
}

/** Tùy chọn lúc tải lên (docs/api mục 10.1). */
export interface ImportUploadOptions {
    gradeLevel?: number | null;
    topic?: string | null;
    defaultLevel?: ImportLevel | null;
}
