import axiosInstance from "../../../services/apiClient";
import type { Subject, GenerateExamPayload, GeneratedExamCode } from "../types/exam.types";

export async function getCatalogSubjects(): Promise<Subject[]> {
    const response = await axiosInstance.get<Subject[]>("/api/catalog/subjects");
    return response.data;
}

export async function generateExam(payload: GenerateExamPayload): Promise<GeneratedExamCode[]> {
    const response = await axiosInstance.post<GeneratedExamCode[]>("/api/exams/generate", payload);
    return response.data;
}
