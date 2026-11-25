import type { AttemptView, IdentityMode } from "../types/assignment.types";

// Danh tính học sinh theo chế độ của bài giao (quyết định 25/11): họ và tên (mặc định của bài giao mới) hoặc số thứ tự
// (bài giao cũ). Quy tắc họ tên khớp backend (StudentNames.normalize): NFC, bỏ khoảng trắng đầu cuối, gộp khoảng trắng,
// 2–60 ký tự, không ký tự điều khiển hay ký tự vô hình.

export const NAME_MIN = 2;
export const NAME_MAX = 60;

/** Thiếu chế độ (máy chủ trước 25/11) là số thứ tự. */
export function isNameMode(mode: IdentityMode | undefined): boolean {
    return mode === "name";
}

// Cc (điều khiển) và Cf (định dạng vô hình như zero-width space, ký tự đảo chiều)
const HIDDEN = /[\p{Cc}\p{Cf}]/u;

/** Dạng gửi lên của họ tên, hoặc null khi không hợp lệ. */
export function normalizeStudentName(raw: string): string | null {
    const s = raw.normalize("NFC").replace(/[\s\p{Z}]+/gu, " ").trim();
    const length = Array.from(s).length;
    if (length < NAME_MIN || length > NAME_MAX || HIDDEN.test(s)) return null;
    return s;
}

/** "Lớp 8A1, số 12" hoặc "Nguyễn Văn An, lớp 8A1". */
export function identityLine(view: Pick<AttemptView, "classLabel" | "seatNo" | "studentName">): string {
    return view.studentName ? `${view.studentName}, lớp ${view.classLabel}` : `Lớp ${view.classLabel}, số ${view.seatNo}`;
}
