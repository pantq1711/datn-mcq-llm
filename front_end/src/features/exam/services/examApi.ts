import axiosInstance from "../../../services/apiClient";
import type { Subject, GenerateExamPayload, GeneratedExamCode, ExamQuestionItem, ExamListResponse, GetMyExamsParams, ExamDetail, ExamProgress } from "../types/exam.types";

export async function getCatalogSubjects(): Promise<Subject[]> {
    const response = await axiosInstance.get<Subject[]>("/api/catalog/subjects");
    return response.data;
}

export async function generateExam(payload: GenerateExamPayload): Promise<GeneratedExamCode[]> {
    const response = await axiosInstance.post<GeneratedExamCode[]>("/api/exams/build", payload);
    return response.data;
}

export async function keepQuestion(
    examId: number,
    slotId: number,
    questionId: number
): Promise<{ success: boolean }> {
    const url = `/api/exams/${examId}/slots/${slotId}/keep`;
    const response = await axiosInstance.post(url, { questionId });
    if (!response.status.toString().startsWith('2')) {
        // Thông báo lỗi chi tiết từ backend nếu có
        const err = response.data?.message ?? 'Lỗi khi giữ câu hỏi';
        throw new Error(err);
    }
    return response.data;
}

export async function replaceQuestion(
    examId: number,
    slotId: number,
    originalQuestionId: number,
    replacementQuestionId: number
): Promise<{ success: boolean }> {
    const url = `/api/exams/${examId}/slots/${slotId}/replace`;
    const response = await axiosInstance.post(url, {
        originalQuestionId,
        replacementQuestionId,
    });

    if (!response.status.toString().startsWith('2')) {
        const err = response.data?.message ?? 'Lỗi khi thay thế câu hỏi';
        throw new Error(err);
    }
    return response.data;
}

export async function updateQuestion(
    examId: number,
    slotId: number,
    question: ExamQuestionItem
): Promise<ExamQuestionItem> {
    const url = `/api/exams/${examId}/slots/${slotId}/question`;
    const response = await axiosInstance.put<ExamQuestionItem>(url, question);
    if (!response.status.toString().startsWith("2")) {
        const err = response.data?.message ?? "Lỗi khi cập nhật câu hỏi";
        throw new Error(err);
    }
    return response.data;
}

export async function getMyExams(params?: GetMyExamsParams): Promise<ExamListResponse> {
    try {
        const cleanParams: Record<string, any> = {};
        if (params?.page !== undefined) cleanParams.page = params.page;
        if (params?.size !== undefined) cleanParams.size = params.size;
        if (params?.kind) cleanParams.kind = params.kind;

        const response = await axiosInstance.get<ExamListResponse>("/api/exams", {
            params: cleanParams,
        });
        return response.data;
    } catch (error: any) {
        const errMsg = error.response?.data?.message || "Không thể tải danh sách đề thi";
        throw new Error(errMsg);
    }
}

// Lấy chi tiết đề (câu, đáp án, lời giải, flag "cần xem kỹ")
export const getExamDetail = async (examId: string) => {
    const { data } = await axiosInstance.get<ExamDetail>(`/api/exams/${examId}`);
    return data;   // kiểu ExamDetail định nghĩa trong types
};

// src/features/exam/services/examApi.ts
export const getExamProgress = async (examId: string) => {
    const { data } = await axiosInstance.get<ExamProgress>(`/api/exams/${examId}/progress`);
    return data; // kiểu ExamProgress cần khai báo trong exam.types.ts
};