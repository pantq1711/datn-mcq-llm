import type { ItemStat, PartCode, QuestionType } from "../types/assignment.types";

// Hàm thuần của tab "Phân tích câu": nhãn tiếng Việt, mã cờ (docs/contract/enums.md, "Mã cờ hậu kiểm"), đọc choiceCounts.

// Giờ máy chủ (LocalDateTime, Asia/Bangkok, không có DST) lệch UTC +7 giờ
const SERVER_UTC_OFFSET_HOURS = 7;
const SERVER_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/;

/** Mốc thời gian (ms) của chuỗi giờ máy chủ không kèm múi giờ; null khi không đọc được. */
export function serverTimeToMs(value: string | undefined | null): number | null {
    if (!value) return null;
    const m = SERVER_DATE_TIME.exec(value);
    if (!m) return null;
    const [, y, mo, d, h, mi, s] = m;
    return Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h) - SERVER_UTC_OFFSET_HOURS, Number(mi), Number(s ?? 0));
}

/**
 * Số ms cần chờ tới lúc có thống kê, cộng 1 giây dư cho lệch đồng hồ; không đọc được giờ thì chờ `fallbackMs`.
 * Luôn ít nhất `minMs`, để khi máy chủ vẫn báo chưa sẵn sàng thì không gọi dồn dập.
 */
export function msUntilReady(readyAt: string | undefined | null, nowMs: number, minMs = 3000, fallbackMs = 30000): number {
    const at = serverTimeToMs(readyAt);
    if (at === null) return fallbackMs;
    return Math.max(at - nowMs + 1000, minMs);
}

const PART_LABEL: Record<PartCode, string> = { I: "Phần I", II: "Phần II", III: "Phần III" };

/** "Phần II – Câu 3" (số câu đánh số lại từ 1 mỗi phần, khớp file Word). */
export function itemLabel(part: PartCode, number: number): string {
    return `${PART_LABEL[part] ?? `Phần ${part}`} – Câu ${number}`;
}

const TYPE_LABEL: Record<QuestionType, string> = {
    mcq: "Trắc nghiệm",
    tf: "Đúng/Sai",
    sa: "Trả lời ngắn",
};

export function questionTypeLabel(type: QuestionType): string {
    return TYPE_LABEL[type] ?? type;
}

/** 0,79; null → "—". */
export function formatRatio(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) return "—";
    // Làm tròn trước để số âm rất nhỏ không thành "-0,00"
    const rounded = Math.round(value * 100) / 100 || 0;
    return rounded.toLocaleString("vi-VN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** 0,79 → "79%". */
export function formatPercent(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) return "—";
    return `${Math.round(value * 100)}%`;
}

// ---------- Cờ ----------

export type FlagTone = "neutral" | "warn" | "danger";

export interface FlagView {
    code: string;
    label: string;
    tone: FlagTone;
}

/**
 * Cờ → thẻ tiếng Việt. Mã lạ (backend thêm cờ mới) thì hiện nguyên mã, màu trung tính, không làm vỡ bảng.
 * `type` quyết định cách đọc `key_suspect` (mcq: phương án; tf: ý; sa: đáp số).
 */
export function flagView(code: string, type: QuestionType): FlagView {
    const colon = code.indexOf(":");
    const base = (colon >= 0 ? code.slice(0, colon) : code).trim();
    const arg = colon >= 0 ? code.slice(colon + 1).trim() : "";
    switch (base) {
        case "insufficient_sample":
            return { code, label: "Chưa đủ lượt", tone: "neutral" };
        case "low_discrimination":
            return { code, label: "Phân biệt kém", tone: "warn" };
        case "unused_option":
            return { code, label: arg ? `Phương án ${arg} gần như không ai chọn` : "Có phương án gần như không ai chọn", tone: "warn" };
        case "key_suspect": {
            if (type === "tf" && arg) return { code, label: `Nghi đáp án sai ở ý ${arg}`, tone: "danger" };
            if (type === "mcq" && arg) return { code, label: `Nghi đáp án sai (nhóm điểm cao chọn ${arg})`, tone: "danger" };
            return { code, label: "Nghi đáp án sai", tone: "danger" };
        }
        default:
            return { code, label: code, tone: "neutral" };
    }
}

// ---------- Đọc choiceCounts ----------

function asNumber(value: unknown): number {
    return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function asRecord(value: unknown): Record<string, unknown> {
    return value !== null && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

export interface McqRow {
    /** "A".."D" hoặc "blank". */
    key: string;
    all: number;
    high: number;
    low: number;
    correct: boolean;
}

/** Dòng A–D và "bỏ trống" của câu trắc nghiệm; `answerKey` là đáp án đúng (để tô). */
export function mcqRows(item: ItemStat): McqRow[] {
    const counts = asRecord(item.choiceCounts);
    return ["A", "B", "C", "D", "blank"].map((key) => {
        const c = asRecord(counts[key]);
        return { key, all: asNumber(c.all), high: asNumber(c.high), low: asNumber(c.low), correct: key === item.answerKey };
    });
}

export interface TfRow {
    key: string;
    /** Nhãn đúng của ý: true = Đúng. */
    label: boolean | null;
    t: number;
    f: number;
    blank: number;
    correct: number;
    p: number | null;
    d: number | null;
}

/** Bốn ý a–d của câu đúng/sai; `answerKey` "TFFT" cho nhãn đúng của từng ý theo thứ tự. */
export function tfRows(item: ItemStat): TfRow[] {
    const counts = asRecord(item.choiceCounts);
    return ["a", "b", "c", "d"].map((key, i) => {
        const c = asRecord(counts[key]);
        const ch = item.answerKey?.charAt(i);
        return {
            key,
            label: ch === "T" ? true : ch === "F" ? false : null,
            t: asNumber(c.T),
            f: asNumber(c.F),
            blank: asNumber(c.blank),
            correct: asNumber(c.correct),
            p: typeof c.p === "number" ? c.p : null,
            d: typeof c.d === "number" ? c.d : null,
        };
    });
}

export interface SaSummary {
    correct: number;
    blank: number;
    topWrong: { value: string; count: number }[];
}

export function saSummary(item: ItemStat): SaSummary {
    const c = asRecord(item.choiceCounts);
    const raw = Array.isArray(c.topWrong) ? c.topWrong : [];
    return {
        correct: asNumber(c.correct),
        blank: asNumber(c.blank),
        topWrong: raw.map((w) => {
            const r = asRecord(w);
            return { value: String(r.value ?? ""), count: asNumber(r.count) };
        }),
    };
}
