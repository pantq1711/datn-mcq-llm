import type { AttemptView, ItemStats, PublicAssignmentInfo } from "../types/assignment.types";

export const INFO: PublicAssignmentInfo = {
    title: "Kiểm tra 15 phút - Hàm số",
    questionCount: 3,
    durationMinutes: 45,
    closesAt: "2026-11-20T17:00:00",
    status: "open",
    showScore: true,
    showSolutions: false,
    identityMode: "seat",
    classes: [
        { classLabel: "8A1", size: 35 },
        { classLabel: "8A2", size: 36 },
    ],
};

/** Bài giao chế độ họ tên (quyết định 25/11, mặc định của bài giao mới). */
export const INFO_NAME: PublicAssignmentInfo = { ...INFO, identityMode: "name" };

/** Lượt đang làm (chế độ số thứ tự): một câu mỗi dạng. */
export function makeAttemptView(overrides: Partial<AttemptView> = {}): AttemptView {
    return {
        identityMode: "seat",
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

/** Lượt đang làm ở chế độ họ tên: có `studentName`, không có `seatNo`. */
export function makeNameAttemptView(overrides: Partial<AttemptView> = {}): AttemptView {
    const { seatNo: _omit, ...base } = makeAttemptView({ identityMode: "name", studentName: "Nguyễn Văn An" });
    void _omit;
    return { ...base, ...overrides };
}

/** Bộ thống kê đủ mẫu (n = 36): một câu mỗi dạng, đúng thứ tự trang đề; câu mcq có cờ, câu tf đã được đổi sau khi giao. */
export function makeItemStats(overrides: Partial<ItemStats> = {}): ItemStats {
    return {
        assignmentId: 7,
        n: 36,
        groupSize: 10,
        computedAt: "2026-11-20T17:10:00",
        sampleSufficient: true,
        minSampleSize: 20,
        sampleNote: null,
        excluded: { duplicate: 2, notSubmitted: 3 },
        items: [
            {
                slotId: 101,
                part: "I",
                number: 1,
                questionType: "mcq",
                bankItemId: 11,
                answerKey: "B",
                p: 0.5833,
                d: 0.1,
                choiceCounts: {
                    A: { all: 9, high: 1, low: 3 },
                    B: { all: 21, high: 6, low: 5 },
                    C: { all: 6, high: 3, low: 2 },
                    D: { all: 0, high: 0, low: 0 },
                    blank: { all: 0, high: 0, low: 0 },
                },
                flags: ["low_discrimination", "unused_option: D", "key_suspect: C"],
                slotChanged: false,
            },
            {
                slotId: 102,
                part: "II",
                number: 1,
                questionType: "tf",
                bankItemId: 12,
                answerKey: "TFFT",
                p: 0.7,
                d: 0.45,
                choiceCounts: {
                    a: { T: 30, F: 6, blank: 0, correct: 30, p: 0.8333, d: 0.4 },
                    b: { T: 10, F: 24, blank: 2, correct: 24, p: 0.6667, d: 0.5 },
                    c: { T: 12, F: 22, blank: 2, correct: 22, p: 0.6111, d: 0.3 },
                    d: { T: 27, F: 8, blank: 1, correct: 27, p: 0.75, d: 0.6 },
                },
                flags: [],
                slotChanged: true,
            },
            {
                slotId: 103,
                part: "III",
                number: 1,
                questionType: "sa",
                bankItemId: 13,
                answerKey: "2,5",
                p: 0.5,
                d: 0.7,
                choiceCounts: {
                    correct: 18,
                    blank: 5,
                    topWrong: [
                        { value: "25", count: 6 },
                        { value: "0,25", count: 3 },
                    ],
                },
                flags: [],
                slotChanged: false,
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
