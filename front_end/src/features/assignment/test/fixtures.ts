import type { AttemptView, PublicAssignmentInfo } from "../types/assignment.types";

export const INFO: PublicAssignmentInfo = {
    title: "Kiểm tra 15 phút - Hàm số",
    questionCount: 3,
    durationMinutes: 45,
    closesAt: "2026-11-20T17:00:00",
    status: "open",
    showScore: true,
    showSolutions: false,
    classes: [
        { classLabel: "8A1", size: 35 },
        { classLabel: "8A2", size: 36 },
    ],
};

/** Lượt đang làm: một câu mỗi dạng. */
export function makeAttemptView(overrides: Partial<AttemptView> = {}): AttemptView {
    return {
        classLabel: "8A1",
        seatNo: 12,
        duplicateSeat: false,
        startedAt: "2026-11-20T09:00:00",
        deadlineAt: "2026-11-20T09:45:00",
        serverNow: "2026-11-20T09:01:00",
        remainingSeconds: 44 * 60,
        submitted: false,
        resultsReleased: false,
        resultsAt: "2026-11-20T17:02:00",
        parts: [
            {
                part: "I",
                items: [
                    {
                        slotId: 101,
                        number: 1,
                        questionType: "mcq",
                        questionText: "Tính $1+1$.",
                        options: { A: "1", B: "2", C: "3", D: "4" },
                    },
                ],
            },
            {
                part: "II",
                items: [
                    {
                        slotId: 102,
                        number: 1,
                        questionType: "tf",
                        questionText: "Cho hàm số $y=x$.",
                        statements: [
                            { key: "a", text: "Hàm số đồng biến." },
                            { key: "b", text: "Đồ thị đi qua gốc tọa độ." },
                        ],
                    },
                ],
            },
            {
                part: "III",
                items: [{ slotId: 103, number: 1, questionType: "sa", questionText: "Tính $2+0,5$." }],
            },
        ],
        ...overrides,
    };
}

export function axiosError(status: number | undefined, data?: Record<string, unknown>, headers?: Record<string, string>) {
    if (status === undefined) return Object.assign(new Error("Network Error"), { isAxiosError: true });
    return Object.assign(new Error(`Request failed with status code ${status}`), {
        isAxiosError: true,
        response: { status, data: data ?? {}, headers: headers ?? {} },
    });
}
