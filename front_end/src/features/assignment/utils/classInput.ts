import type { AssignmentClass } from "../types/assignment.types";

// Quy tắc nhãn lớp ở backend (docs/api mục 11.1): bỏ mọi khoảng trắng, viết hoa; chỉ chữ không dấu, số, / . -; 1–10 ký tự.

export const MAX_CLASSES = 10;
export const MAX_CLASS_SIZE = 99;

export function normalizeClassLabel(raw: string | undefined | null): string {
    return (raw ?? "").replace(/\s+/g, "").toUpperCase();
}

export function isValidClassLabel(normalized: string): boolean {
    return /^[A-Z0-9/.-]{1,10}$/.test(normalized);
}

export interface RawClassRow {
    classLabel?: string;
    size?: number | null;
}

/** Lỗi của danh sách lớp (chữ tiếng Việt cho thầy/cô) hoặc undefined nếu hợp lệ. */
export function validateClassRows(rows: RawClassRow[] | undefined): string | undefined {
    if (!rows || rows.length === 0) return "Thêm ít nhất một lớp.";
    if (rows.length > MAX_CLASSES) return `Tối đa ${MAX_CLASSES} lớp.`;
    const seen = new Set<string>();
    for (const row of rows) {
        const label = normalizeClassLabel(row.classLabel);
        if (!label) return "Mỗi lớp cần có tên.";
        if (!isValidClassLabel(label)) {
            return `Tên lớp "${label}" không hợp lệ. Dùng chữ không dấu, số, và các dấu / . - (tối đa 10 ký tự).`;
        }
        if (seen.has(label)) return `Lớp ${label} bị nhập hai lần.`;
        seen.add(label);
        const size = row.size;
        if (typeof size !== "number" || !Number.isInteger(size) || size < 1 || size > MAX_CLASS_SIZE) {
            return `Sĩ số lớp ${label} phải là số nguyên từ 1 đến ${MAX_CLASS_SIZE}.`;
        }
    }
    return undefined;
}

export function toClassPayload(rows: RawClassRow[]): AssignmentClass[] {
    return rows.map((row) => ({ classLabel: normalizeClassLabel(row.classLabel), size: row.size as number }));
}
