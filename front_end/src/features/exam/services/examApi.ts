import axiosInstance from "../../../services/apiClient";
import { getApiErrorMessage } from "../../../utils/apiError";
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
): Promise<ExamItemDto> {
    // Backend trả câu của chỗ sau khi Giữ, kèm bankVisibility (câu vào ngân hàng chung hay riêng), docs/api mục 7.5
    const url = `/api/exams/${examId}/slots/${slotId}/keep`;
    const response = await axiosInstance.post<ExamItemDto>(url, questionId !== undefined ? { questionId } : {});
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
    } catch (error: unknown) {
        // D-15: backend trả câu báo lỗi ở trường "error" (getApiErrorMessage đọc cả "error" lẫn "message")
        throw new Error(getApiErrorMessage(error, "Không thể tải danh sách đề thi"));
    }
}

/**
 * NV-01: xóa đề của mình (backend xóa mềm, trả 204). Câu đã lưu vào ngân hàng vẫn được giữ. Đề còn đang soạn: 409
 * EXAM_GENERATING; đề không có hoặc đã xóa: 404. Lỗi ném ra là Error với câu báo lỗi của backend.
 */
export async function deleteExam(examId: number): Promise<void> {
    try {
        await axiosInstance.delete(`/api/exams/${examId}`);
    } catch (error: unknown) {
        throw new Error(getApiErrorMessage(error, "Không thể xóa đề thi"), { cause: error });
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
    /** Số công thức backend không đọc được, nằm ở dạng chữ trong file (chỉ khi xuất MathType). */
    failed: number;
    /** Câu chứa các công thức đó, dạng "phần.số câu" ("I.8"); rỗng khi không có hoặc không xác định được. */
    failedAt: string[];
    /** Tên file backend đặt trong Content-Disposition (ví dụ MT2610231200001.docx); không có thì undefined. */
    filename?: string;
}

/** Lấy tên file từ header Content-Disposition (ưu tiên filename*=UTF-8''...). */
const parseDispositionFilename = (disposition: unknown): string | undefined => {
    if (typeof disposition !== 'string') return undefined;
    const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    if (utf8?.[1]) {
        try {
            return decodeURIComponent(utf8[1]);
        } catch {
            // chuỗi mã hoá hỏng: thử dạng filename thường bên dưới
        }
    }
    const plain = /filename=["']?([^"';]+)["']?/i.exec(disposition);
    return plain?.[1]?.trim() || undefined;
};

/** Số nguyên dương trong header phản hồi; thiếu hoặc sai định dạng thì 0. */
const headerCount = (value: unknown): number => {
    const n = Number.parseInt(String(value ?? ''), 10);
    return Number.isFinite(n) && n > 0 ? n : 0;
};

/** Tùy chọn xuất Word (docs/api mục 7.6); trường nào không có thì không gửi, backend dùng mặc định. */
export interface ExportDocxOptions {
    /** Thời gian làm bài (phút, 5–180); không có thì file in "……". */
    durationMinutes?: number;
    /** Mã đề 100–999 in ở ô mã đề, chân trang, trang đáp án; không có thì 101. */
    examCode?: string;
    /**
     * "exam": file dừng ở dòng HẾT, không kèm đáp án và lời giải; "spec": bản đặc tả đề (mẫu Phụ lục 2 CV 7991, A4 ngang,
     * không dùng mã đề); không có thì "all".
     */
    content?: ExportContent;
}

/** Nội dung file Word (tham số content của docs/api mục 7.6). */
export type ExportContent = 'all' | 'exam' | 'spec';

export const getExamExportDocx = async (
    examId: string,
    equation: ExportEquationFormat = 'omml',
    options: ExportDocxOptions = {}
): Promise<ExportDocxResult> => {
    const params: Record<string, string | number> = {};
    if (equation === 'mathtype') params.equation = equation;
    if (options.durationMinutes !== undefined) params.durationMinutes = options.durationMinutes;
    if (options.examCode) params.examCode = options.examCode;
    if (options.content === 'exam' || options.content === 'spec') params.content = options.content;
    const resp = await axiosInstance.get(
        `/api/exams/${examId}/export/docx`,
        {
            responseType: 'blob',   // nhận dữ liệu dạng binary
            ...(Object.keys(params).length > 0 ? { params } : {}),
        }
    );
    return {
        blob: resp.data,   // Blob .docx
        keptOmml: headerCount(resp.headers['x-equation-kept-omml']),
        scaled: headerCount(resp.headers['x-equation-scaled']),
        failed: headerCount(resp.headers['x-equation-failed']),
        failedAt: String(resp.headers['x-equation-failed-at'] ?? '').split(',').map((s) => s.trim()).filter(Boolean),
        filename: parseDispositionFilename(resp.headers['content-disposition']),
    };
};

export const getQcTrace = async (examId: string, slotId: string) => {
    const { data } = await axiosInstance.get<QcTraceResponse>(
        `/api/exams/${examId}/slots/${slotId}/qc-trace`
    );
    return data;
};
