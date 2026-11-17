import axios from "axios";
import type {
    AttemptView,
    PublicAssignmentInfo,
    SubmitAnswerPayload,
    SubmitResult,
} from "../types/assignment.types";

// API công khai cho học sinh (docs/api mục 11.2). Dùng axios riêng, không có interceptor đăng nhập / refresh của
// apiClient và không gửi cookie: học sinh không có phiên giáo viên. Token lượt làm chỉ nằm ở header, không trong URL.
const publicClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    headers: { "Content-Type": "application/json" },
    withCredentials: false,
});

const TOKEN_HEADER = "X-Attempt-Token";

function tokenHeaders(token: string) {
    return { [TOKEN_HEADER]: token };
}

export async function getPublicAssignment(code: string): Promise<PublicAssignmentInfo> {
    const { data } = await publicClient.get<PublicAssignmentInfo>(`/api/public/assignments/${encodeURIComponent(code)}`);
    return data;
}

export async function startAttempt(code: string, classLabel: string, seatNo: number): Promise<AttemptView> {
    const { data } = await publicClient.post<AttemptView>(
        `/api/public/assignments/${encodeURIComponent(code)}/attempts`,
        { classLabel, seatNo }
    );
    return data;
}

export async function getCurrentAttempt(token: string): Promise<AttemptView> {
    const { data } = await publicClient.get<AttemptView>("/api/public/attempts/current", {
        headers: tokenHeaders(token),
    });
    return data;
}

export async function submitAttempt(token: string, answers: SubmitAnswerPayload[]): Promise<SubmitResult> {
    const { data } = await publicClient.post<SubmitResult>(
        "/api/public/attempts/current/submit",
        { answers },
        { headers: tokenHeaders(token) }
    );
    return data;
}
