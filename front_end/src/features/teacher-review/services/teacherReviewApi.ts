import axiosInstance from "../../../services/apiClient";
import type { ReviewQuestionDetail, SubmitReviewPayload } from "../types/review.types";

export async function getReviewQuestionDetail(id: string | number): Promise<ReviewQuestionDetail> {
    const response = await axiosInstance.get<ReviewQuestionDetail>(`/api/reviews/questions/${id}`);
    return response.data;
}

export async function submitQuestionReview(
    id: string | number,
    payload: SubmitReviewPayload
): Promise<{ success: boolean; message?: string }> {
    const response = await axiosInstance.post<{ success: boolean; message?: string }>(
        `/api/reviews/questions/${id}`,
        payload
    );
    return response.data;
}

export async function seedDemoMathQuestions(): Promise<{ count?: number; message?: string }> {
    const response = await axiosInstance.post<{ count?: number; message?: string }>(
        "/api/reviews/demo/seed-math"
    );
    return response.data;
}
