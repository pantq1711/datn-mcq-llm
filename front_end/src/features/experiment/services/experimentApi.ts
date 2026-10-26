import axiosInstance from "../../../services/apiClient";
import type { Subject } from "../types/experiment.types";

export async function getCatalogSubjects(): Promise<Subject[]> {
    const response = await axiosInstance.get<Subject[]>("/api/catalog/subjects");
    return response.data;
}

// export async function createExperiment(payload: CreateExperimentPayload) { }