import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/assignmentApi";
import { makeItemStats } from "../test/fixtures";
import type { Assignment, AttemptRow } from "../types/assignment.types";
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

function makeAttempt(attemptId: number, seatNo: number, duplicate = false): AttemptRow {
    return {
        attemptId,
        classLabel: "8A1",
        seatNo,
        state: "submitted",
        duplicate,
        startedAt: "2026-11-20T09:00:00",
        submittedAt: "2026-11-20T09:30:00",
        score: 1,
        score10: 5.7,
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

describe("AssignmentDetailPage: xóa lượt làm (backend cho xóa ở mọi trạng thái, quyết định 16/11)", () => {
    const attempts = [makeAttempt(1, 5, true), makeAttempt(2, 5, true), makeAttempt(3, 9)];

    it.each(["open", "closed"] as const)("bài %s: có ô chọn từng lượt và nút xóa các lượt đã chọn", async (status) => {
        const user = userEvent.setup();
        mocked.getAssignment.mockResolvedValue(
            makeAssignment({
                status,
                closedAt: status === "closed" ? "2026-11-20T17:00:00" : undefined,
                attempts,
                duplicateCount: 2,
            })
        );
        mocked.bulkDeleteAttempts.mockResolvedValue(1);
        renderPage();

        await screen.findByText("Có 2 lượt trùng số thứ tự");
        const boxes = screen.getAllByRole("checkbox");
        expect(boxes.length).toBeGreaterThanOrEqual(attempts.length);

        const deleteButton = screen.getByRole("button", { name: "Xóa các lượt đã chọn" });
        expect(deleteButton).toBeDisabled();

        await user.click(screen.getAllByRole("checkbox")[3]); // [0] là ô chọn tất cả
        expect(screen.getByText("Đã chọn 1 lượt")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Xóa các lượt đã chọn" })).toBeEnabled();
    });

    it("bài đã đóng: không còn câu báo 'không xóa được lượt'", async () => {
        mocked.getAssignment.mockResolvedValue(makeAssignment({ status: "closed", attempts, duplicateCount: 2 }));
        renderPage();

        await screen.findByText("Có 2 lượt trùng số thứ tự");
        expect(screen.queryByText(/không xóa được lượt/)).not.toBeInTheDocument();
        expect(screen.getByText(/Xóa lượt sẽ tính lại phần Phân tích câu/)).toBeInTheDocument();
    });

    it("bài đã đóng: 'Chọn các lượt trùng' chọn đúng các lượt trùng rồi xóa nhiều lượt gọi API với các id đó", async () => {
        const user = userEvent.setup();
        mocked.getAssignment.mockResolvedValue(makeAssignment({ status: "closed", attempts, duplicateCount: 2 }));
        mocked.bulkDeleteAttempts.mockResolvedValue(2);
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Chọn các lượt trùng" }));
        expect(screen.getByText("Đã chọn 2 lượt")).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Xóa các lượt đã chọn" }));
        // Hộp xác nhận có nút cùng tên: nút cuối cùng trong DOM là nút của hộp
        const confirmButtons = await screen.findAllByRole("button", { name: "Xóa các lượt đã chọn" });
        await user.click(confirmButtons[confirmButtons.length - 1]);

        await waitFor(() => expect(mocked.bulkDeleteAttempts).toHaveBeenCalledWith("5", "7", [1, 2]));
    });
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
