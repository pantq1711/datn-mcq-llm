/**
 * Tên kèm ngày để phân biệt ma trận/đề trùng tên (đợt 3, rà soát C5, D2): "<tên> – DD/MM".
 * Dùng chung cho tên đề tự điền khi tạo đề theo ma trận (FE-1) và tên gợi ý khi sao chép ma trận (FE-2).
 */

/** Tên tối đa 255 ký tự (exam.title, exam_matrix.name ở backend; cùng cách đếm độ dài của Java). */
export const MAX_NAME_LENGTH = 255;

/**
 * Đuôi ngày ở cuối tên: dấu nối (–, — hoặc -) rồi DD/MM hoặc DD/MM/YYYY. Cùng mẫu với `ExamHeader.DATE_SUFFIX` ở
 * backend (đầu đề Word bỏ đuôi này). Không có dấu nối ("Đề 28/11") thì không phải đuôi ngày.
 */
const DATE_SUFFIX = /\s*[–—-]\s*\d{1,2}\/\d{1,2}(?:\/\d{4})?\s*$/;

/** "DD/MM" theo giờ của máy giáo viên. */
export function formatDayMonth(date: Date): string {
    const dd = String(date.getDate()).padStart(2, "0");
    const mm = String(date.getMonth() + 1).padStart(2, "0");
    return `${dd}/${mm}`;
}

/**
 * Bỏ một đuôi ngày `– DD/MM` (hoặc `– DD/MM/YYYY`) ở cuối tên; tên chỉ có ngày (bỏ hết) thì giữ nguyên tên đã trim.
 * Dùng cho `datedName` và cho tên file mặc định khi xuất Word (`ExportWordDialog`, tránh lặp "… – 28 11 – 28-11-2026").
 */
export function stripDateSuffix(name: string): string {
    const trimmed = name.trim();
    const withoutDate = trimmed.replace(DATE_SUFFIX, "").trim();
    return withoutDate || trimmed;
}

/**
 * "<tên> – DD/MM" với ngày `date`. Tên đã có đuôi ngày (ví dụ ma trận sao chép hôm trước "… – 27/11") thì thay ngày cũ
 * bằng ngày mới, không ghép thêm; tên chỉ có ngày thì giữ nguyên tên làm phần gốc. Cắt phần tên cho vừa 255 ký tự.
 */
export function datedName(base: string, date: Date = new Date()): string {
    const core = stripDateSuffix(base);
    const suffix = ` – ${formatDayMonth(date)}`;
    return core.slice(0, MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix;
}

/**
 * Giá trị mới của ô "Tên đề thi hiển thị" khi chọn ma trận khác: ghi tên tự điền `next` khi ô đang trống hoặc vẫn là
 * giá trị tự điền trước đó `lastAuto`; giáo viên đã gõ tên khác thì giữ nguyên.
 */
export function nextAutoTitle(current: string, lastAuto: string | null, next: string): string {
    return current.trim() === "" || current === lastAuto ? next : current;
}
