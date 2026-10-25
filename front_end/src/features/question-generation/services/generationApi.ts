import axiosInstance from "../../../services/apiClient";

export interface ProcessNextJobResult {
  job?: GenerationJob;
  status: string;
}




import type { QuestionTemplate, Subject, GenerateQuestionPayload, GenerateResponse, GenerationStatusResponse, GenerationResultResponse, GenerationHistoryItem, GenerationJob } from "../types/generation.types";

export async function createGenerationTask(
    payload: GenerateQuestionPayload
): Promise<GenerateResponse> {
    const response = await axiosInstance.post<GenerateResponse>("/api/generation/generate-sync", payload);
    return response.data;
}

export async function getGenerationStatus(
    taskId: string | number
): Promise<GenerationStatusResponse> {
    const response = await axiosInstance.get<GenerationStatusResponse>("/api/generate/status", {
        params: { taskId },
    });
    return response.data;
}

export async function getGenerationResult(
    taskId: string | number
): Promise<GenerationResultResponse> {
    const response = await axiosInstance.get<GenerationResultResponse>("/api/generate/result", {
        params: { taskId },
    });
    return response.data;
}

export async function getCatalogSubjects(): Promise<Subject[]> {
    const response = await axiosInstance.get<Subject[]>("/api/catalog/subjects");
    return response.data;
}

export async function getCatalogTemplates(subjectId?: number): Promise<QuestionTemplate[]> {
    const response = await axiosInstance.get<QuestionTemplate[]>("/api/catalog/templates", {
        params: subjectId ? { subjectId } : undefined,
    });
    return response.data;
}

export async function processNextJob(runAnswerCheck = true): Promise<ProcessNextJobResult> {
    const response = await axiosInstance.post<ProcessNextJobResult>("/api/generation/jobs/process-next", null, {
        params: { runAnswerCheck },
    });
    return response.data;
}

export async function getGenerationJobs(): Promise<GenerationJob[]> {
    const response = await axiosInstance.get<GenerationJob[]>("/api/generation/jobs");
    return response.data;
}