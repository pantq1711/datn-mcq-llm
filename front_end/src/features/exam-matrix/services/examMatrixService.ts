import axiosInstance from "../../../services/apiClient";
import type { CreateExamMatrixPayload, ExamMatrix, ExamMatrixDetail, ExamMatrixSummary, DeleteResponse, ExamMatrixSample } from "../types/examMatrix.types";

/* ---------- CREATE ---------- */
export async function createExamMatrix(payload: CreateExamMatrixPayload) {
    const { data } = await axiosInstance.post<ExamMatrix>("/api/exam-matrices", payload);
    return data;
}

/* ---------- LIST ---------- */
export async function listExamMatrices(): Promise<ExamMatrixSummary[]> {
    const { data } = await axiosInstance.get<ExamMatrixSummary[]>("/api/exam-matrices");
    return data;
}

/* ---------- DETAIL ---------- */
export async function getExamMatrix(id: string): Promise<ExamMatrixDetail> {
    const { data } = await axiosInstance.get<ExamMatrixDetail>(`/api/exam-matrices/${id}`);
    return data;
}

/* ---------- UPDATE ---------- */
export async function updateExamMatrix(
    id: string,
    payload: CreateExamMatrixPayload
): Promise<ExamMatrixDetail> {
    const { data } = await axiosInstance.put<ExamMatrixDetail>(`/api/exam-matrices/${id}`, payload);
    return data;
}

/* ---------- DELETE ---------- */
export async function deleteExamMatrix(id: string): Promise<DeleteResponse> {
    const { data } = await axiosInstance.delete<DeleteResponse>(`/api/exam-matrices/${id}`);
    return data;
}

// Nhân bản ma trận hiện có -> trả về ExamMatrixDetail mới
export const duplicateExamMatrix = async (id: string) => {
    const { data } = await axiosInstance.post<ExamMatrixDetail>(`/api/exam-matrices/${id}/duplicate`);
    return data;
};

// Lấy danh sách mẫu ma trận có sẵn (samples)
export const listExamMatrixSamples = async () => {
    const { data } = await axiosInstance.get<ExamMatrixSample[]>(
        '/api/exam-matrices/samples'
    );
    return data;
};

// Tạo ma trận mới từ mẫu có sẵn theo key (ví dụ "giuaki1-toan8"); không gửi body thì backend dùng môn "Toán"
export const createExamMatrixFromSample = async (key: string) => {
    const { data } = await axiosInstance.post<ExamMatrixDetail>(
        `/api/exam-matrices/samples/${encodeURIComponent(key)}`
    );
    return data;   // trả về chi tiết ma trận mới được tạo
};