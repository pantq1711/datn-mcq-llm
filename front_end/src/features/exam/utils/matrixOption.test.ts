import { describe, expect, it } from "vitest";

import type { ExamMatrixSummary } from "../../exam-matrix/types/examMatrix.types";
import { describeMatrixOption } from "./matrixOption";

function matrix(overrides: Partial<ExamMatrixSummary> = {}): ExamMatrixSummary {
    return {
        id: "5", name: "Giữa kì I – Toán 8", subjectId: 1, gradeLevel: 8, createdAt: "2026-11-16T08:05:00",
        cellCount: 3, examCount: 0, questionCount: 18,
        ...overrides,
    };
}

describe("describeMatrixOption", () => {
    it("dòng phụ ghi tổng số câu (questionCount), không ghi số ô", () => {
        const text = describeMatrixOption(matrix({ examCount: 4 }));
        expect(text.meta).toBe("Tạo lúc 16/11/2026 08:05 · 18 câu · Đã dùng cho 4 đề");
        expect(text.meta).not.toContain("ô");
        expect(text.label).toBe("Giữa kì I – Toán 8 · Lớp 8 · 16/11/2026");
    });

    it("chưa có đề thì ghi 'Chưa dùng cho đề nào'; thiếu questionCount thì 0 câu", () => {
        const text = describeMatrixOption(matrix({ questionCount: undefined as unknown as number }));
        expect(text.meta).toBe("Tạo lúc 16/11/2026 08:05 · 0 câu · Chưa dùng cho đề nào");
    });
});
