import axiosInstance from "../../../services/apiClient";
import type { Subject, ExamItemDto, UpdateSlotQuestionPayload, ExamListResponse, GetMyExamsParams, ExamDetail, ExamProgress, BuildByLessonPayload, BuildResultDto, BuildExamRequest, ReplaceQuestionParams } from "../types/exam.types";
import type { QcTraceResponse } from "../types/qcTrace.types";

export async function getCatalogSubjects(): Promise<Subject[]> {
    const response = await axiosInstance.get<Subject[]>("/api/catalog/subjects");
    return response.data;
}

export async function buildByLesson(payload: BuildByLessonPayload): Promise<BuildResultDto> {
    const response = await axiosInstance.post<BuildResultDto>("/api/exams/build-by-lesson", payload);
    return response.data;
}

export async function buildExamByMatrix(payload: BuildExamRequest): Promise<BuildResultDto> {
    const response = await axiosInstance.post<BuildResultDto>("/api/exams/build", payload);
    return response.data;
}

export async function keepQuestion(
    examId: number,
    slotId: number,
    questionId?: number
): Promise<{ success: boolean }> {
    const url = `/api/exams/${examId}/slots/${slotId}/keep`;
    const response = await axiosInstance.post(url, questionId !== undefined ? { questionId } : {});
    return response.data;
}

export async function replaceQuestion(
    examId: number | string,
    slotId: number | string,
    params?: ReplaceQuestionParams
): Promise<ExamItemDto> {
    const url = `/api/exams/${examId}/slots/${slotId}/replace`;
    const response = await axiosInstance.post(url, params || {});
    return response.data;
}

export async function updateQuestion(
    examId: number,
    slotId: number,
    payload: UpdateSlotQuestionPayload
): Promise<ExamItemDto> {
    const url = `/api/exams/${examId}/slots/${slotId}/question`;
    // Lỗi 400/409 do axios ném ra; nội dung lỗi ở response.data.error, response.data.errors
    const response = await axiosInstance.put<ExamItemDto>(url, payload);
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

export const getExamProgress = async (examId: string) => {
    const { data } = await axiosInstance.get<ExamProgress>(`/api/exams/${examId}/progress`);
    return data; // kiểu ExamProgress cần khai báo trong exam.types.ts
};

/** Cách ghi công thức trong file Word: Equation gốc của Word (mặc định) hoặc đối tượng MathType. */
export type ExportEquationFormat = 'omml' | 'mathtype';

export interface ExportDocxResult {
    blob: Blob;
    /** Số công thức không chuyển được sang MathType, vẫn ở dạng công thức Word (chỉ khi xuất MathType). */
    keptOmml: number;
    /** Số công thức dài không ngắt dòng được nên đã thu nhỏ cho vừa trang (chỉ khi xuất MathType). */
    scaled: number;
}

/** Số nguyên dương trong header phản hồi; thiếu hoặc sai định dạng thì 0. */
const headerCount = (value: unknown): number => {
    const n = Number.parseInt(String(value ?? ''), 10);
    return Number.isFinite(n) && n > 0 ? n : 0;
};

export const getExamExportDocx = async (
    examId: string,
    equation: ExportEquationFormat = 'omml'
): Promise<ExportDocxResult> => {
    const resp = await axiosInstance.get(
        `/api/exams/${examId}/export/docx`,
        {
            responseType: 'blob',   // nhận dữ liệu dạng binary
            ...(equation === 'mathtype' ? { params: { equation } } : {}),
        }
    );
    return {
        blob: resp.data,   // Blob .docx
        keptOmml: headerCount(resp.headers['x-equation-kept-omml']),
        scaled: headerCount(resp.headers['x-equation-scaled']),
    };
};

export const getQcTrace = async (examId: string, slotId: string) => {
    const { data } = await axiosInstance.get<QcTraceResponse>(
        `/api/exams/${examId}/slots/${slotId}/qc-trace`
    );
    return data;
};
