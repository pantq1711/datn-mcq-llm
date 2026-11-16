import axiosInstance from "../../../services/apiClient";
import type {
    Subject,
    BankItemParams,
    BankItemsResponse,
    BankItemDetail,
    BankTopic,
} from "../types/questionBank.types";

export async function getCatalogSubjects(): Promise<Subject[]> {
    const response = await axiosInstance.get<Subject[]>("/api/catalog/subjects");
    return response.data;
}

export async function getBankItems(params?: BankItemParams): Promise<BankItemsResponse> {
    // Spring Boot page bắt đầu từ 0
    const pageZeroIndexed = params?.page ? Math.max(0, params.page - 1) : 0;

    const response = await axiosInstance.get<BankItemsResponse>("/api/bank-items", {
        params: {
            page: pageZeroIndexed,
            size: params?.pageSize ?? 10,
            q: params?.keyword?.trim() || undefined,
            subjectId: params?.subjectId ?? undefined,
            gradeLevel: params?.gradeLevel ?? undefined,
            questionType: params?.questionType ?? undefined,
            topic: params?.topic?.trim() || undefined,
            difficulty: params?.difficulty ?? undefined,
            sort: params?.sort || undefined,
            usage: params?.usage ?? undefined,
            content: params?.content?.trim() || undefined,
            // Chỉ gửi khi là tf và đủ 4 mức, tránh 400
            levels: params?.questionType === "tf" && params.levels?.every(Boolean) ? params.levels.join(",") : undefined,
        },
    });

    const data = response.data;
    const total = data.totalItems ?? data.total ?? (data.items?.length || 0);

    const items = (data.items || []).map((item) => ({
        ...item,
        subject: item.subjectName || item.subject || "",
    }));

    return {
        ...data,
        items,
        total,
        totalItems: total,
    };
}

export async function getBankItemDetail(id: number | string): Promise<BankItemDetail> {
    const response = await axiosInstance.get<BankItemDetail>(`/api/bank-items/${id}`);
    const data = response.data;
    return {
        ...data,
        subject: data.subjectName || data.subject || "",
    };
}

/**
 * NV-21: gỡ câu khỏi ngân hàng (ẩn mềm, docs/api "Gỡ khỏi ngân hàng"): câu không còn được chọn cho đề mới, đề cũ vẫn giữ câu.
 * 204 khi gỡ xong; 403 BANK_ITEM_NOT_OWNER khi không phải câu của người gọi; 404 khi câu không còn trong ngân hàng.
 */
export async function removeBankItem(id: number | string): Promise<void> {
    await axiosInstance.delete(`/api/bank-items/${id}`);
}

export async function getBankTopics(subjectId?: number): Promise<BankTopic[]> {
    const response = await axiosInstance.get<BankTopic[]>("/api/bank-items/topics", {
        params: subjectId !== undefined ? { subjectId } : undefined,
    });
    return response.data;
}