import type { ImportItem } from "../types/bankImport.types";

/** Câu có cờ chặn không chọn được (máy chủ cũng từ chối: 422 IMPORT_ITEM_BLOCKED). */
function isSelectable(item: ImportItem): boolean {
    return !item.blocking;
}

/** Mã mọi câu chọn được, theo thứ tự trong file. */
export function selectAllKeys(items: ImportItem[]): Set<string> {
    return new Set(items.filter(isSelectable).map((item) => item.key));
}

/** Bật hoặc tắt một câu; câu bị chặn hay mã không có trong lô thì giữ nguyên. Trả tập mới. */
export function toggleKey(selected: Set<string>, key: string, items: ImportItem[]): Set<string> {
    const item = items.find((candidate) => candidate.key === key);
    const next = new Set(selected);
    if (!item || !isSelectable(item)) return next;
    if (next.has(key)) next.delete(key);
    else next.add(key);
    return next;
}

/** Mã gửi đi xác nhận: theo thứ tự trong file, chỉ câu còn chọn được trong lô hiện tại. */
export function keysToConfirm(items: ImportItem[], selected: Set<string>): string[] {
    return items.filter((item) => isSelectable(item) && selected.has(item.key)).map((item) => item.key);
}

export interface SelectionSummary {
    count: number;
    hasFigure: boolean;
    hasTf: boolean;
}

export function summarizeSelection(items: ImportItem[], selected: Set<string>): SelectionSummary {
    const chosen = items.filter((item) => isSelectable(item) && selected.has(item.key));
    return {
        count: chosen.length,
        hasFigure: chosen.some((item) => (item.images?.length ?? 0) > 0 || item.flags.includes("has_figure")),
        hasTf: chosen.some((item) => item.type === "tf"),
    };
}
