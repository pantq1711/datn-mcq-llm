/**
 * Bản đang sửa dở của một câu (CT-2), lưu tạm trong sessionStorage của tab để tải lại trang (F5) không mất chữ đang gõ.
 * Mỗi chỗ một bản, gắn với câu đang có ở chỗ đó (questionId): câu đã bị đổi thì bỏ bản cũ. Chỉ là tiện ích của trình
 * duyệt: lưu trữ bị chặn (cửa sổ ẩn danh, chính sách trình duyệt) thì mọi hàm lặng lẽ không làm gì.
 */

const PREFIX = "examEditDraft:";

interface StoredDraft<T> {
    questionId: number;
    values: T;
}

function draftKey(examId: number, slotId: number): string {
    return `${PREFIX}${examId}:${slotId}`;
}

export function saveEditDraft<T>(examId: number, slotId: number, questionId: number, values: T): void {
    try {
        const draft: StoredDraft<T> = { questionId, values };
        sessionStorage.setItem(draftKey(examId, slotId), JSON.stringify(draft));
    } catch {
        // không lưu được thì chỉ mất tiện ích khôi phục
    }
}

/** Bản đang sửa dở của chỗ, chỉ khi vẫn là đúng câu đó; không có hoặc đã khác câu thì null (và xóa bản cũ). */
export function loadEditDraft<T>(examId: number, slotId: number, questionId: number): T | null {
    try {
        const raw = sessionStorage.getItem(draftKey(examId, slotId));
        if (!raw) return null;
        const draft = JSON.parse(raw) as StoredDraft<T>;
        if (draft.questionId !== questionId) {
            sessionStorage.removeItem(draftKey(examId, slotId));
            return null;
        }
        return draft.values;
    } catch {
        return null;
    }
}

export function clearEditDraft(examId: number, slotId: number): void {
    try {
        sessionStorage.removeItem(draftKey(examId, slotId));
    } catch {
        // bỏ qua
    }
}

/** Các chỗ của đề đang có bản sửa dở (để mở lại form sau khi tải lại trang). */
export function findEditDraftSlots(examId: number): number[] {
    try {
        const prefix = `${PREFIX}${examId}:`;
        const slots: number[] = [];
        for (let i = 0; i < sessionStorage.length; i++) {
            const key = sessionStorage.key(i);
            if (key?.startsWith(prefix)) {
                const slotId = Number(key.slice(prefix.length));
                if (Number.isFinite(slotId)) slots.push(slotId);
            }
        }
        return slots;
    } catch {
        return [];
    }
}
