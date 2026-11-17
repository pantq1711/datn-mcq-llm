import type { DraftAnswers, PublicPart, PublicQuestion, SubmitAnswerPayload } from "../types/assignment.types";

/** Đáp số tối đa 10 ký tự (backend nhận tới 20). */
export const SA_MAX_LENGTH = 10;

/** Chỉ giữ chữ số, dấu phẩy, dấu chấm, dấu trừ (dấu trừ Unicode đổi về '-'); cắt còn 10 ký tự. */
export function sanitizeSaInput(raw: string): string {
    return raw
        .replace(/−/g, "-")
        .replace(/[^0-9.,-]/g, "")
        .slice(0, SA_MAX_LENGTH);
}

/**
 * Dạng "1.234": dấu chấm theo sau đúng 3 chữ số và phần nguyên khác 0, backend coi là dấu ngăn hàng nghìn nên chấm sai.
 * (0.125 vẫn là số thập phân.)
 */
export function looksLikeThousandsDot(text: string): boolean {
    return /^-?\d*[1-9]\d*\.\d{3}$/.test(text.trim());
}

export function allQuestions(parts: PublicPart[] | undefined): PublicQuestion[] {
    return (parts ?? []).flatMap((p) => p.items);
}

export function isAnswered(question: PublicQuestion, draft: DraftAnswers): boolean {
    const key = String(question.slotId);
    switch (question.questionType) {
        case "mcq":
            return !!draft.mcq[key];
        case "tf":
            return Object.keys(draft.tf[key] ?? {}).length > 0;
        case "sa":
            return (draft.sa[key] ?? "").trim() !== "";
        default:
            return false;
    }
}

export function countAnswered(parts: PublicPart[] | undefined, draft: DraftAnswers): number {
    return allQuestions(parts).filter((q) => isAnswered(q, draft)).length;
}

/**
 * Danh sách câu trả lời gửi lên. Chỉ gửi câu đã làm (câu thiếu là bỏ trống, docs/api mục 11.2); ý tf bỏ trống không gửi.
 * Chỉ nhận slotId có trong đề, nên bản nháp cũ không làm hỏng lần nộp.
 */
export function buildSubmitPayload(parts: PublicPart[] | undefined, draft: DraftAnswers): SubmitAnswerPayload[] {
    const out: SubmitAnswerPayload[] = [];
    for (const q of allQuestions(parts)) {
        const key = String(q.slotId);
        if (q.questionType === "mcq") {
            const choice = draft.mcq[key];
            if (choice) out.push({ slotId: q.slotId, choice });
        } else if (q.questionType === "tf") {
            const chosen = draft.tf[key] ?? {};
            const keys = (q.statements ?? []).map((s) => s.key).filter((k) => chosen[k] === "T" || chosen[k] === "F");
            if (keys.length > 0) {
                out.push({ slotId: q.slotId, tf: Object.fromEntries(keys.map((k) => [k, chosen[k]])) });
            }
        } else {
            const text = (draft.sa[key] ?? "").trim();
            if (text) out.push({ slotId: q.slotId, text });
        }
    }
    return out;
}
