import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { ExamMatrixSummary } from "../types/examMatrix.types";
import { ExamMatrixTable } from "./ExamMatrixTable";

const MATRIX: ExamMatrixSummary = {
    id: "9", name: "Giữa kì I – Toán 8", subjectId: 1, gradeLevel: 8, createdAt: "2026-11-16T08:05:00",
    cellCount: 3, examCount: 0, questionCount: 18,
};

describe("ExamMatrixTable", () => {
    it("cột 'Số câu' hiện questionCount (đợt 3), không còn cột 'Số ô'", () => {
        render(<ExamMatrixTable matrices={[MATRIX]} subjectNames={{ 1: "Toán" }} searchText="" onEditMatrix={vi.fn()} />);

        expect(screen.getByRole("columnheader", { name: /Số câu/ })).toBeInTheDocument();
        expect(screen.queryByRole("columnheader", { name: /Số ô/ })).not.toBeInTheDocument();
        const row = screen.getByRole("row", { name: /Giữa kì I – Toán 8/ });
        expect(within(row).getByText("18")).toBeInTheDocument();
        expect(within(row).queryByText("3")).not.toBeInTheDocument();
    });
});
