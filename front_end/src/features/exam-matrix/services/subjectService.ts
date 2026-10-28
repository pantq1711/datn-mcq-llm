import axiosInstance from "../../../services/apiClient";

/** Thông tin một môn học (có thể mở rộng). */
export interface Subject {
    /** Mã (id) của môn học – kiểu number hoặc string tùy backend. */
    id: number | string;
    /** Tên hiển thị của môn học. */
    name: string;
}

/**
 * Lấy thông tin môn học dựa trên `subjectId`.
 *
 * @param subjectId   Mã môn học cần tra cứu.
 * @returns          Đối tượng `Subject` chứa `id` và `name`.
 *
 * **Lưu ý:** API endpoint được giả định là `/api/subjects/:id`.
 * Nếu backend có API khác (ví dụ: `/api/subjects?code=`) chỉ cần thay
 * URL trong hàm dưới đây.
 */
export async function getSubjectById(subjectId: number | string): Promise<Subject> {
    const { data } = await axiosInstance.get<Subject>(`/api/subjects/${subjectId}`);
    return data;
}

/**
 * Lấy toàn bộ danh sách môn học (tùy chọn, hữu ích cho dropdown).
 *
 * @returns Mảng `Subject[]`.
 */
export async function listAllSubjects(): Promise<Subject[]> {
    const { data } = await axiosInstance.get<Subject[]>("/api/subjects");
    return data;
}
