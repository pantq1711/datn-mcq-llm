// Backend trả LocalDateTime giờ máy chủ (Asia/Bangkok), không kèm múi giờ. Hiển thị đúng chữ máy chủ gửi, không đổi
// múi giờ theo máy người xem, để thầy cô và học sinh cùng đọc một giờ.

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/;

/** "17:05 ngày 20/11/2026"; chuỗi lạ thì trả nguyên. */
export function formatServerTime(value: string | undefined | null): string {
    if (!value) return "";
    const m = LOCAL_DATE_TIME.exec(value);
    if (!m) return value;
    return `${m[4]}:${m[5]} ngày ${m[3]}/${m[2]}/${m[1]}`;
}

/** "17:05, 20/11" gọn cho bảng. */
export function formatServerTimeShort(value: string | undefined | null): string {
    if (!value) return "";
    const m = LOCAL_DATE_TIME.exec(value);
    if (!m) return value;
    return `${m[4]}:${m[5]}, ${m[3]}/${m[2]}`;
}

/** Đồng hồ đếm ngược: 45:00, 5:07, 0:09 (từ 1 giờ: 1:05:00). Số giây âm coi là 0. */
export function formatCountdown(totalSeconds: number): string {
    const s = Math.max(0, Math.floor(totalSeconds));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    const two = (n: number) => String(n).padStart(2, "0");
    return h > 0 ? `${h}:${two(m)}:${two(sec)}` : `${m}:${two(sec)}`;
}

/** Điểm kiểu Việt Nam: 7,5; 12,25. */
export function formatPoints(value: number | undefined | null): string {
    if (value === undefined || value === null || !Number.isFinite(value)) return "";
    return value.toLocaleString("vi-VN", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}
