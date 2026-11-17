import apiClient from "../../../services/apiClient";
import type { Assignment, CreateAssignmentPayload, ItemStats } from "../types/assignment.types";
import { studentPath } from "../studentPath";

// API giáo viên của bài giao trực tuyến (docs/api mục 11.1). Lỗi do axios ném ra, nơi gọi dùng showApiError.

const base = (examId: number | string) => `/api/exams/${examId}/assignments`;

export async function createAssignment(examId: number | string, payload: CreateAssignmentPayload): Promise<Assignment> {
    const { data } = await apiClient.post<Assignment>(base(examId), payload);
    return data;
}

export async function listAssignments(examId: number | string): Promise<Assignment[]> {
    const { data } = await apiClient.get<Assignment[]>(base(examId));
    return data;
}

export async function getAssignment(examId: number | string, assignmentId: number | string): Promise<Assignment> {
    const { data } = await apiClient.get<Assignment>(`${base(examId)}/${assignmentId}`);
    return data;
}

export async function closeAssignment(examId: number | string, assignmentId: number | string): Promise<Assignment> {
    const { data } = await apiClient.post<Assignment>(`${base(examId)}/${assignmentId}/close`);
    return data;
}

export async function deleteAssignment(examId: number | string, assignmentId: number | string): Promise<void> {
    await apiClient.delete(`${base(examId)}/${assignmentId}`);
}

export async function bulkDeleteAttempts(
    examId: number | string,
    assignmentId: number | string,
    attemptIds: number[]
): Promise<number> {
    const { data } = await apiClient.post<{ deleted: number }>(
        `${base(examId)}/${assignmentId}/attempts/bulk-delete`,
        { attemptIds }
    );
    return data.deleted;
}

/** B1: thống kê câu của bài đã đóng. 409 `ASSIGNMENT_OPEN` / `STATS_NOT_READY` (errors[0] là lúc có thống kê). */
export async function getItemStats(examId: number | string, assignmentId: number | string): Promise<ItemStats> {
    const { data } = await apiClient.get<ItemStats>(`${base(examId)}/${assignmentId}/item-stats`);
    return data;
}

/** B2: tính lại rồi trả như B1. */
export async function recomputeItemStats(examId: number | string, assignmentId: number | string): Promise<ItemStats> {
    const { data } = await apiClient.post<ItemStats>(`${base(examId)}/${assignmentId}/item-stats/recompute`);
    return data;
}

/** Link học sinh mở bài (`<origin>/lam-bai/<mã>`): backend không biết tên miền công khai nên frontend ghép. */
export function buildStudentLink(publicCode: string, origin: string = window.location.origin): string {
    return `${origin}${studentPath(publicCode)}`;
}
