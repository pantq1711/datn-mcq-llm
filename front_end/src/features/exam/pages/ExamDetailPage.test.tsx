import { configure, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as examApi from "../services/examApi";
import type { ExamDetail, ExamItemDto } from "../types/exam.types";
import ExamDetailPage from "./ExamDetailPage";

vi.mock("../services/examApi");
vi.mock("../../exam-matrix/services/examMatrixService");
const mocked = vi.mocked(examApi);

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 20000 });

function item(overrides: Partial<ExamItemDto>): ExamItemDto {
    return {
        slotId: 1,
        cellId: 1,
        slotNo: 1,
        part: "I",
        topic: "Đa thức",
        questionType: "mcq",
        state: "kept",
        source: "bank",
        bankItemId: 11,
        questionText: "Câu hỏi",
        optionA: "1",
        optionB: "2",
        optionC: "3",
        optionD: "4",
        correctOption: "B",
        explanation: "Lời giải",
        difficulty: "easy",
        needsReview: false,
        reviewReasons: [],
        infoReasons: [],
        edited: false,
        ...overrides,
    };
}

function detail(items: ExamItemDto[]): ExamDetail {
    return {
        examId: 5,
        examCode: "DE-5",
        title: "Đề có hình",
        mode: "teacher",
        status: "draft",
        matrixId: null,
        createdAt: "2026-11-24T08:00:00",
        buildStatus: "done",
        needsReviewCount: 0,
        parts: [{ part: "I", required: items.length, ready: items.length, items }],
        kind: "matrix",
    } as ExamDetail;
}

// Data router như ứng dụng thật (App.tsx dùng createBrowserRouter): trang dùng useBlocker, không chạy dưới MemoryRouter
function renderPage() {
    const router = createMemoryRouter([{ path: "/exams/:id", element: <ExamDetailPage /> }], {
        initialEntries: ["/exams/5"],
    });
    return render(<RouterProvider router={router} />);
}

describe("ExamDetailPage: nút Giao bài online", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        mocked.getExamProgress.mockRejectedValue(new Error("không cần tiến độ"));
    });

    it("A2: đề có câu có hình, mọi câu đã Giữ: nút Giao bài bấm được", async () => {
        mocked.getExamDetail.mockResolvedValue(detail([
            item({
                slotId: 1,
                questionText: "Cho hình vẽ sau.\n⟦img:7⟧",
                images: [{ id: 7, role: "stem", url: "/api/question-images/7?exp=1&u=1&sig=x" }],
            }),
            item({ slotId: 2, slotNo: 2, bankItemId: 12, questionText: "Câu không hình" }),
        ]));
        renderPage();

        const button = await screen.findByRole("button", { name: /Giao bài online/ });
        expect(button).toBeEnabled();
        expect(screen.queryByText(/chưa hỗ trợ câu có hình/)).toBeNull();
    });

    it("X1: câu ngân hàng có hình hiện ảnh trên trang đề, không hiện chữ thay thế", async () => {
        mocked.getExamDetail.mockResolvedValue(detail([
            item({
                slotId: 1,
                questionText: "Cho hình vẽ sau.\n⟦img:7⟧",
                images: [{ id: 7, role: "stem", url: "/api/question-images/7?exp=1&u=1&sig=x" }],
            }),
        ]));
        const { container } = renderPage();

        await screen.findByRole("button", { name: /Giao bài online/ });
        const img = container.querySelector('img[src*="/api/question-images/7"]');
        expect(img).not.toBeNull();
        expect(screen.queryByText(/Hình không tải được/)).toBeNull();
    });

    it("còn câu chưa Giữ thì vẫn làm mờ nút Giao bài", async () => {
        mocked.getExamDetail.mockResolvedValue(detail([item({ slotId: 1, state: "ready" })]));
        renderPage();

        const button = await screen.findByRole("button", { name: /Giao bài online/ });
        expect(button).toBeDisabled();
    });
});

describe("ExamDetailPage: đổi tên đề ở đầu trang (đợt 3)", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        mocked.getExamProgress.mockRejectedValue(new Error("không cần tiến độ"));
    });

    it("bấm nút đổi tên, lưu tên mới thì tiêu đề trang đổi theo", async () => {
        mocked.getExamDetail.mockResolvedValue(detail([item({ slotId: 1 })]));
        mocked.renameExam.mockResolvedValue({ examId: 5, title: "Giữa kì I – 8A1" });
        renderPage();

        await userEvent.click(await screen.findByRole("button", { name: "Đổi tên đề" }));
        const input = screen.getByLabelText("Tên đề");
        expect(input).toHaveValue("Đề có hình");
        await userEvent.clear(input);
        await userEvent.type(input, "Giữa kì I – 8A1");
        await userEvent.click(screen.getByRole("button", { name: "Lưu tên" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Giữa kì I – 8A1" })).toBeInTheDocument();
        expect(mocked.renameExam).toHaveBeenCalledWith(5, "Giữa kì I – 8A1");
    });
});
