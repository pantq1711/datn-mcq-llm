import { EXAM_HAS_ASSIGNMENTS } from "../types/assignment.types";

interface ErrorWithCode {
    response?: { status?: number; data?: { code?: string } };
    cause?: unknown;
}

/**
 * Xóa đề bị chặn vì còn bài giao trực tuyến (409 EXAM_HAS_ASSIGNMENTS). `deleteExam` bọc lỗi axios trong Error và đặt lỗi
 * gốc ở `cause`, nên kiểm cả hai chỗ.
 */
export function isExamHasAssignmentsError(error: unknown): boolean {
    const direct = error as ErrorWithCode | undefined;
    if (direct?.response?.status === 409 && direct.response.data?.code === EXAM_HAS_ASSIGNMENTS) return true;
    const cause = direct?.cause as ErrorWithCode | undefined;
    return cause?.response?.status === 409 && cause.response.data?.code === EXAM_HAS_ASSIGNMENTS;
}
