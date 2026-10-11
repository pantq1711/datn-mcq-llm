import axiosInstance from "../../../services/apiClient";
import type { GenerateQuestionPayload, GenerateResponse, GenerationStatusResponse, GenerationResultResponse } from "../types/generation.types";

export async function createGenerationTask(
    payload: GenerateQuestionPayload
): Promise<GenerateResponse> {
    const response = await axiosInstance.post<GenerateResponse>("/api/generate", payload);
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