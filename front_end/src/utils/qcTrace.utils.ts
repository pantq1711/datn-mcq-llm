import type { QcAnswerCheck } from "../features/exam/types/qcTrace.types";

export type QcTone = "success" | "error" | "warning" | "default";

interface Label {
    label: string;
    tone: QcTone;
}

const toTagColor: Record<QcTone, string> = {
    success: "success",
    error: "error",
    warning: "warning",
    default: "blue",
};

export function tagColor(tone: QcTone): string {
    return toTagColor[tone];
}

// Trạng thái câu (docs/api mục 3)
const questionStatusLabels: Record<string, Label> = {
    generated: { label: "Đã sinh", tone: "default" },
    format_failed: { label: "Lỗi định dạng", tone: "error" },
    duplicate: { label: "Trùng lặp", tone: "error" },
    qc_passed: { label: "Qua QC", tone: "success" },
    rejected: { label: "Bị loại", tone: "error" },
    in_bank: { label: "Đã vào ngân hàng", tone: "success" },
};

export function describeQuestionStatus(status: string): Label {
    return questionStatusLabels[status] ?? { label: status, tone: "default" };
}

// Trạng thái chỗ (docs/api mục 7.4, 7.5)
const slotStateLabels: Record<string, Label> = {
    pending: { label: "Chờ sinh", tone: "default" },
    generating: { label: "Đang sinh", tone: "warning" },
    ready: { label: "Sẵn sàng", tone: "success" },
    kept: { label: "Đã giữ", tone: "success" },
    edited: { label: "Đã sửa", tone: "default" },
    failed: { label: "Sinh lỗi", tone: "error" },
};

export function describeSlotState(state: string): Label {
    return slotStateLabels[state] ?? { label: state, tone: "default" };
}

const notRunReasons: Record<string, string> = {
    format_failed: "câu lỗi định dạng",
    duplicate: "câu bị trùng",
    dedup_not_concluded: "chưa kết luận trùng lặp",
    solver_not_run: "Solver chưa chạy",
    superseded: "đã có bản mới hơn",
    not_recorded: "không có bản ghi",
};

export function describeAnswerCheck(check: QcAnswerCheck): Label {
    switch (check.state) {
        case "consistent":
            return { label: "Solver khớp đáp án", tone: "success" };
        case "inconsistent":
            return { label: "Solver lệch đáp án", tone: "error" };
        case "error":
            return { label: `Solver lỗi${check.error ? `: ${check.error}` : ""}`, tone: "error" };
        case "pending":
            return { label: "Solver đang chạy", tone: "warning" };
        case "not_run":
            return {
                label: `Chưa chạy Solver${check.reason ? ` (${notRunReasons[check.reason] ?? check.reason})` : ""}`,
                tone: "default",
            };
        default:
            return { label: String(check.state), tone: "default" };
    }
}

/** Đáp án Solver đưa ra khi lệch, theo dạng câu. */
export function describeJudgeAnswer(check: QcAnswerCheck): string | null {
    if (check.judgeAnswer) return check.judgeAnswer;
    if (check.judgeSaAnswer) return check.judgeSaKind ? `${check.judgeSaAnswer} (${check.judgeSaKind})` : check.judgeSaAnswer;
    if (check.judgeLabels) return formatLabels(check.judgeLabels);
    return null;
}

/** Nhãn Đúng/Sai của 4 ý, nhận cả dạng mảng ["T","F",...] lẫn object {a:"T",...}. */
export function formatLabels(labels: Record<string, string> | string[]): string {
    const entries = Array.isArray(labels)
        ? labels.map((value, index) => [String.fromCharCode(97 + index), value] as const)
        : Object.entries(labels);
    return entries.map(([key, value]) => `${key}) ${value === "T" ? "Đúng" : value === "F" ? "Sai" : value}`).join(", ");
}

const groundingLabels: Record<string, Label> = {
    supported: { label: "Bám tài liệu", tone: "success" },
    not_supported: { label: "Không khớp tài liệu", tone: "error" },
    unknown: { label: "Tài liệu không đủ để kết luận", tone: "warning" },
    pending: { label: "Đang kiểm tra bám tài liệu", tone: "warning" },
    error: { label: "Kiểm tra bám tài liệu bị lỗi", tone: "error" },
};

export function describeGrounding(state: string): Label {
    return groundingLabels[state.toLowerCase()] ?? { label: state, tone: "default" };
}

const actionLabels: Record<string, Label> = {
    edit: { label: "Sửa câu", tone: "default" },
    replace: { label: "Đổi câu", tone: "warning" },
    keep: { label: "Giữ câu (vào ngân hàng)", tone: "success" },
};

export function describeAction(action: string): Label {
    return actionLabels[action] ?? { label: action, tone: "default" };
}

export function formatQcTime(value?: string | null): string {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
    }).format(date);
}
