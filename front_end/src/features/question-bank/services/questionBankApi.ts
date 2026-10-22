import axiosInstance from "../../../services/apiClient";
import type { Subject, BankItemParams, BankItemsResponse, BankItemDetail } from "../types/questionBank.types";

export async function getCatalogSubjects(): Promise<Subject[]> {
    const response = await axiosInstance.get<Subject[]>("/api/catalog/subjects");
    return response.data;
}

export async function getBankItems(params?: BankItemParams): Promise<BankItemsResponse> {
    const response = await axiosInstance.get<BankItemsResponse>("/api/bank-items", {
        params: {
            page: params?.page ?? 1,
            pageSize: params?.pageSize ?? 10,
            keyword: params?.keyword || undefined,
            subjectId: params?.subjectId ?? undefined,
            difficulty: params?.difficulty ?? undefined,
            status: params?.status ?? undefined,
        },
    });
    return response.data;
}

export async function getBankItemDetail(id: number | string): Promise<BankItemDetail> {
    const response = await axiosInstance.get<BankItemDetail>(`/api/bank-items/${id}`);
    return response.data;
}