import apiClient from "../../../services/apiClient";
import type { ConfirmResult, ImportBatch, ImportUploadOptions } from "../types/bankImport.types";

/**
 * Tải bộ câu hỏi .docx lên → bản xem trước (docs/api mục 10.1). Máy chủ đọc file ngay trong yêu cầu này (không có tiến độ),
 * có thể mất tới khoảng một phút khi file có hình EMF cần đổi. Chỉ gửi tùy chọn đã điền.
 */
export async function uploadBankImport(file: File, options: ImportUploadOptions = {}): Promise<ImportBatch> {
    const form = new FormData();
    form.append("file", file);
    if (options.gradeLevel != null) form.append("gradeLevel", String(options.gradeLevel));
    const topic = options.topic?.trim();
    if (topic) form.append("topic", topic);
    if (options.defaultLevel) form.append("defaultLevel", options.defaultLevel);

    const response = await apiClient.post<ImportBatch>("/api/bank-imports", form, {
        headers: { "Content-Type": "multipart/form-data" },
    });
    return response.data;
}

/** Các lần nhập của tôi đang chờ xác nhận, không kèm nội dung câu (docs/api mục 10.2). */
export async function listPendingImports(): Promise<ImportBatch[]> {
    const response = await apiClient.get<ImportBatch[]>("/api/bank-imports", { params: { status: "preview" } });
    return response.data;
}

/** Xem lại một lô; 410 IMPORT_EXPIRED khi quá hạn, 404 IMPORT_NOT_FOUND khi không phải lô của tôi. */
export async function getBankImport(id: number): Promise<ImportBatch> {
    const response = await apiClient.get<ImportBatch>(`/api/bank-imports/${id}`);
    return response.data;
}

/** Đưa các câu `keys` vào ngân hàng riêng của tôi (một giao dịch: lỗi thì không ghi câu nào). */
export async function confirmBankImport(id: number, keys: string[]): Promise<ConfirmResult> {
    const response = await apiClient.post<ConfirmResult>(`/api/bank-imports/${id}/confirm`, { keys });
    return response.data;
}

/** Hủy lô chưa xác nhận (204). */
export async function deleteBankImport(id: number): Promise<void> {
    await apiClient.delete(`/api/bank-imports/${id}`);
}
