import type { DraftAnswers } from "../types/assignment.types";

// Token lượt làm và câu trả lời tạm nằm ở localStorage của máy học sinh. localStorage có thể ném lỗi hoặc rỗng
// (cửa sổ riêng tư, chặn dữ liệu trang), nên mọi đọc/ghi đều bọc try/catch và trang vẫn chạy khi không lưu được.

/**
 * Phiên trên máy: token và lớp; `seatNo` chỉ ở chế độ số thứ tự. Cố ý KHÔNG lưu họ tên (quyết định 25/11: họ tên chỉ nằm
 * ở máy chủ); màn mở lại lấy tên từ P3.
 */
export interface StoredSession {
    token: string;
    classLabel: string;
    seatNo?: number;
}

const sessionKey = (code: string) => `online-attempt:${code}`;
const draftKey = (code: string) => `online-answers:${code}`;

export function emptyDraft(): DraftAnswers {
    return { mcq: {}, tf: {}, sa: {} };
}

export function loadSession(code: string): StoredSession | null {
    try {
        const raw = localStorage.getItem(sessionKey(code));
        if (!raw) return null;
        const value = JSON.parse(raw) as Partial<StoredSession>;
        if (typeof value.token !== "string" || !value.token) return null;
        return {
            token: value.token,
            classLabel: typeof value.classLabel === "string" ? value.classLabel : "",
            ...(typeof value.seatNo === "number" ? { seatNo: value.seatNo } : {}),
        };
    } catch {
        return null;
    }
}

/** Trả về false khi không lưu được (khi đó lần sau mở lại link sẽ không làm tiếp được; trang nên báo). */
export function saveSession(code: string, session: StoredSession): boolean {
    try {
        localStorage.setItem(sessionKey(code), JSON.stringify(session));
        return true;
    } catch {
        return false;
    }
}

export function loadDraft(code: string): DraftAnswers {
    try {
        const raw = localStorage.getItem(draftKey(code));
        if (!raw) return emptyDraft();
        const value = JSON.parse(raw) as Partial<DraftAnswers>;
        return {
            mcq: value.mcq && typeof value.mcq === "object" ? value.mcq : {},
            tf: value.tf && typeof value.tf === "object" ? value.tf : {},
            sa: value.sa && typeof value.sa === "object" ? value.sa : {},
        };
    } catch {
        return emptyDraft();
    }
}

export function saveDraft(code: string, draft: DraftAnswers): void {
    try {
        localStorage.setItem(draftKey(code), JSON.stringify(draft));
    } catch {
        // Không ghi được thì câu trả lời vẫn còn trong bộ nhớ của trang
    }
}

/** Xóa câu trả lời tạm (sau khi server đã nhận bài). */
export function clearDraft(code: string): void {
    try {
        localStorage.removeItem(draftKey(code));
    } catch {
        // bỏ qua
    }
}

/** "Thoát" / "Xong": xóa token và câu trả lời khỏi máy này. Không xóa lượt làm trên server (AN-06). */
export function clearSession(code: string): void {
    try {
        localStorage.removeItem(sessionKey(code));
    } catch {
        // bỏ qua
    }
    clearDraft(code);
}
