import type { QcAnswerCheck, QcDedup, QcTraceResponse, QcVersion } from "../features/exam/types/qcTrace.types";
import { describeJudgeAnswerValue } from "./exam.utils";

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
    generated: { label: "Mới soạn", tone: "default" },
    format_failed: { label: "Lỗi định dạng", tone: "error" },
    duplicate: { label: "Trùng lặp", tone: "error" },
    qc_passed: { label: "Đạt tự kiểm tra", tone: "success" },
    rejected: { label: "Bị loại", tone: "error" },
    in_bank: { label: "Đã lưu vào ngân hàng", tone: "success" },
};

export function describeQuestionStatus(status: string): Label {
    return questionStatusLabels[status] ?? { label: status, tone: "default" };
}

// Trạng thái chỗ (docs/api mục 7.4, 7.5)
const slotStateLabels: Record<string, Label> = {
    pending: { label: "Chờ soạn", tone: "default" },
    generating: { label: "Đang soạn", tone: "warning" },
    ready: { label: "Sẵn sàng", tone: "success" },
    kept: { label: "Đã lưu vào ngân hàng", tone: "success" },
    edited: { label: "Đã sửa", tone: "default" },
    failed: { label: "Chưa soạn được", tone: "error" },
};

export function describeSlotState(state: string): Label {
    return slotStateLabels[state] ?? { label: state, tone: "default" };
}

const notRunReasons: Record<string, string> = {
    format_failed: "câu lỗi định dạng",
    duplicate: "câu bị trùng",
    dedup_not_concluded: "chưa kết luận trùng lặp",
    solver_not_run: "chưa tự kiểm tra",
    superseded: "đã có bản mới hơn",
    not_recorded: "không có bản ghi",
};

export function describeAnswerCheck(check: QcAnswerCheck): Label {
    switch (check.state) {
        case "consistent":
            return { label: "Tự giải ra cùng đáp án", tone: "success" };
        case "inconsistent":
            return { label: "Tự giải ra đáp án khác", tone: "error" };
        case "error":
            return { label: "Chưa tự kiểm tra được đáp án", tone: "error" };
        case "pending":
            return { label: "Đang tự kiểm tra", tone: "warning" };
        case "not_run":
            return {
                // Lý do lạ thì không hiện mã thô
                label: `Chưa tự kiểm tra đáp án${check.reason && notRunReasons[check.reason] ? ` (${notRunReasons[check.reason]})` : ""}`,
                tone: "default",
            };
        default:
            return { label: String(check.state), tone: "default" };
    }
}

// Loại đáp số Solver trả (judge_sa_kind, docs/contract/enums.md); "number" là số bình thường nên không ghi chú thêm
const saKindLabels: Record<string, string> = {
    ambiguous: "đề có thể hiểu theo nhiều cách",
    unparseable: "không đọc được thành số",
};

/** Đáp án Solver đưa ra khi lệch, theo dạng câu. */
export function describeJudgeAnswer(check: QcAnswerCheck): string | null {
    if (check.judgeAnswer) return describeJudgeAnswerValue(check.judgeAnswer);
    const kind = check.judgeSaKind ? saKindLabels[check.judgeSaKind] : undefined;
    if (check.judgeSaAnswer) return kind ? `${check.judgeSaAnswer} (${kind})` : check.judgeSaAnswer;
    if (kind) return kind;
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
    keep: { label: "Lưu câu vào ngân hàng", tone: "success" },
};

export function describeAction(action: string): Label {
    return actionLabels[action] ?? { label: action, tone: "default" };
}

/** Câu chữ bước trùng lặp cho giáo viên, không hiện độ giống, ngưỡng hay mã câu (G22). */
export function describeDedup(dedup: QcDedup): string {
    if (dedup.passed) return "Không trùng";
    if (dedup.comparedTo?.inThisExam) return "Rất giống một câu trong đề này";
    if (dedup.comparedTo?.inBank) return "Rất giống một câu trong ngân hàng";
    return "Rất giống một câu đã có";
}

/** Phiên bản đang dùng của chỗ; không có cờ "đang dùng" thì lấy bản cuối của lần soạn cuối. */
export function currentVersion(trace: QcTraceResponse): QcVersion | null {
    for (const lineage of trace.lineages) {
        const current = lineage.versions.find((v) => v.current);
        if (current) return current;
    }
    const last = trace.lineages[trace.lineages.length - 1];
    return last?.versions[last.versions.length - 1] ?? null;
}

/** Một dòng tóm tắt ở đầu hộp kết quả kiểm tra; ok = null khi bước chưa chạy. */
export interface QcSummaryRow {
    label: string;
    value: string;
    ok: boolean | null;
}

/**
 * Ba dòng tóm tắt (G22) của một phiên bản: Trình bày, Trùng câu khác, Tự giải lại. Cùng cách nói với dòng tóm tắt trên thẻ
 * câu (summarizeQc) nhưng lấy từ kết quả kiểm tra thật của phiên bản.
 */
export function summarizeVersionQc(version: QcVersion): QcSummaryRow[] {
    const qc = version.qc;
    const format: QcSummaryRow = qc?.format
        ? { label: "Trình bày", value: qc.format.passed ? "đạt" : "cần xem lại", ok: qc.format.passed }
        : { label: "Trình bày", value: "chưa kiểm tra", ok: null };
    const dedup: QcSummaryRow = qc?.dedup
        ? {
            label: "Trùng câu khác",
            value: qc.dedup.passed ? "không" : describeDedup(qc.dedup).toLowerCase(),
            ok: qc.dedup.passed,
        }
        : { label: "Trùng câu khác", value: "chưa kiểm tra", ok: null };

    let solver: QcSummaryRow;
    const check = qc?.answerCheck;
    if (!check) {
        solver = { label: "Tự giải lại", value: "chưa kiểm tra", ok: null };
    } else if (check.state === "consistent") {
        solver = { label: "Tự giải lại", value: "khớp đáp án", ok: true };
    } else if (check.state === "inconsistent") {
        solver = { label: "Tự giải lại", value: "ra đáp án khác", ok: false };
    } else if (check.state === "pending") {
        solver = { label: "Tự giải lại", value: "đang tự giải lại…", ok: null };
    } else if (check.state === "error") {
        solver = { label: "Tự giải lại", value: "chưa giải lại được", ok: false };
    } else {
        solver = { label: "Tự giải lại", value: "chưa tự giải lại", ok: null };
    }
    return [format, dedup, solver];
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
