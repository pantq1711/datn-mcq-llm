import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/assignmentApi";
import { makeItemStats } from "../test/fixtures";
import type { Assignment } from "../types/assignment.types";
import AssignmentDetailPage from "./AssignmentDetailPage";

vi.mock("../services/assignmentApi");

const mocked = vi.mocked(api);

function makeAssignment(overrides: Partial<Assignment> = {}): Assignment {
    return {
        assignmentId: 7,
        publicCode: "ABC123",
        title: "Kiểm tra 15 phút - Hàm số",
        status: "closed",
        durationMinutes: 45,
        closesAt: "2026-11-20T17:00:00",
        closedAt: "2026-11-20T17:00:00",
        showScore: true,
        showSolutions: false,
        createdAt: "2026-11-19T08:00:00",
        classes: [{ classLabel: "8A1", size: 35 }],
        questionCount: 3,
        maxScore: 1.75,
        maxAttempts: 35,
        startedCount: 30,
        submittedCount: 28,
        duplicateCount: 0,
        attempts: [],
        ...overrides,
    };
}

function renderPage() {
    return render(
        <MemoryRouter initialEntries={["/exams/5/assignments/7"]}>
            <Routes>
                <Route path="/exams/:id/assignments/:assignmentId" element={<AssignmentDetailPage />} />
            </Routes>
        </MemoryRouter>
    );
}

beforeEach(() => {
    vi.resetAllMocks();
});

describe("AssignmentDetailPage: tab Phân tích câu", () => {
    it("mặc định ở Bảng điểm và chưa gọi thống kê; chuyển tab thì mới tải", async () => {
        const user = userEvent.setup();
        mocked.getAssignment.mockResolvedValue(makeAssignment());
        mocked.getItemStats.mockResolvedValue(makeItemStats());
        renderPage();

        expect(await screen.findByText("Chưa có học sinh nào vào làm.")).toBeInTheDocument();
        expect(mocked.getItemStats).not.toHaveBeenCalled();

        await user.click(screen.getByRole("tab", { name: "Phân tích câu" }));

        expect(await screen.findByText("Phần I – Câu 1")).toBeInTheDocument();
        expect(mocked.getItemStats).toHaveBeenCalledWith("5", "7");
        expect(screen.queryByText("Chưa có học sinh nào vào làm.")).not.toBeInTheDocument();
    });

    it("bài còn mở: tab báo thống kê có sau khi đóng bài, không gọi API", async () => {
        const user = userEvent.setup();
        mocked.getAssignment.mockResolvedValue(makeAssignment({ status: "open", closedAt: undefined }));
        renderPage();

        await screen.findByText("Chưa có học sinh nào vào làm.");
        await user.click(screen.getByRole("tab", { name: "Phân tích câu" }));

        expect(await screen.findByText("Thống kê có sau khi đóng bài")).toBeInTheDocument();
        expect(mocked.getItemStats).not.toHaveBeenCalled();
    });
});
