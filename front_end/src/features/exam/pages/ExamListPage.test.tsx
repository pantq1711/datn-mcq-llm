import { configure, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as examApi from "../services/examApi";
import type { ExamListItem } from "../types/exam.types";
import ExamListPage from "./ExamListPage";

vi.mock("../services/examApi");
const mocked = vi.mocked(examApi);

// Bảng antd và hộp thoại nặng trên jsdom: như ExamDetailPage.test
configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 20000 });

const EXAM: ExamListItem = {
    examId: 3, examCode: "MT1", title: "Giữa kì I – Toán 8 – 28/11", mode: "teacher", kind: "matrix", status: "draft",
    matrixId: 5, createdAt: "2026-11-28T08:00:00", totalSlots: 2, readySlots: 2, keptSlots: 0, needsReviewSlots: 0,
    generatingSlots: 0, failedSlots: 0,
} as ExamListItem;

function renderAt(url: string) {
    const router = createMemoryRouter([{ path: "/exams", element: <ExamListPage /> }], { initialEntries: [url] });
    render(<RouterProvider router={router} />);
    return router;
}

function lastParams() {
    return mocked.getMyExams.mock.calls.at(-1)?.[0];
}

beforeEach(() => {
    vi.resetAllMocks();
    mocked.getMyExams.mockResolvedValue({ items: [EXAM], page: 0, size: 10, totalItems: 1, totalPages: 1 });
});

describe("ExamListPage: tìm theo tên đề (đợt 3)", () => {
    it("mở trang có sẵn ?q= thì ô tìm điền sẵn và gọi API với q, giữ kind", async () => {
        renderAt("/exams?kind=matrix&q=Gi%E1%BB%AFa%20k%C3%AC");

        expect(screen.getByLabelText("Tìm theo tên đề")).toHaveValue("Giữa kì");
        await waitFor(() => expect(lastParams()).toEqual({ page: 0, size: 10, kind: "matrix", q: "Giữa kì" }));
        expect(screen.getByPlaceholderText("Tìm theo tên đề (gõ có dấu)")).toBeInTheDocument();
    });

    it("gõ vào ô tìm: chỉ gọi API sau khi ngừng gõ, ghi q lên URL (giữ kind), xóa ô thì bỏ q", async () => {
        const router = renderAt("/exams?kind=lesson");
        await waitFor(() => expect(lastParams()).toEqual({ page: 0, size: 10, kind: "lesson" }));
        const callsBefore = mocked.getMyExams.mock.calls.length;

        await userEvent.type(screen.getByLabelText("Tìm theo tên đề"), "cuối kì");
        expect(mocked.getMyExams.mock.calls.length).toBe(callsBefore);

        await waitFor(() => expect(lastParams()).toEqual({ page: 0, size: 10, kind: "lesson", q: "cuối kì" }));
        const search = new URLSearchParams(router.state.location.search);
        expect(search.get("q")).toBe("cuối kì");
        expect(search.get("kind")).toBe("lesson");
        expect(mocked.getMyExams.mock.calls.length).toBe(callsBefore + 1);

        await userEvent.clear(screen.getByLabelText("Tìm theo tên đề"));
        await waitFor(() => expect(new URLSearchParams(router.state.location.search).has("q")).toBe(false));
        await waitFor(() => expect(lastParams()).toEqual({ page: 0, size: 10, kind: "lesson" }));
    });

    it("không có đề khớp thì báo không có đề khớp, không mời tạo đề đầu tiên", async () => {
        mocked.getMyExams.mockResolvedValue({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 });
        renderAt("/exams?q=xyz");
        expect(await screen.findByText("Không có đề nào khớp tên đã tìm.")).toBeInTheDocument();
        expect(screen.queryByText("Tạo đề đầu tiên")).not.toBeInTheDocument();
    });
});

describe("ExamListPage: đổi tên đề (đợt 3)", () => {
    it("bấm Đổi tên, lưu tên mới thì gọi API rồi tải lại danh sách", async () => {
        mocked.renameExam.mockResolvedValue({ examId: 3, title: "Giữa kì I – 8A1" });
        renderAt("/exams");
        await userEvent.click(await screen.findByRole("button", { name: `Đổi tên đề ${EXAM.title}` }));

        const input = screen.getByLabelText("Tên đề");
        expect(input).toHaveValue(EXAM.title);
        await userEvent.clear(input);
        await userEvent.type(input, "Giữa kì I – 8A1");
        const callsBefore = mocked.getMyExams.mock.calls.length;
        await userEvent.click(screen.getByRole("button", { name: "Lưu tên" }));

        await waitFor(() => expect(mocked.renameExam).toHaveBeenCalledWith(3, "Giữa kì I – 8A1"));
        await waitFor(() => expect(mocked.getMyExams.mock.calls.length).toBe(callsBefore + 1));
    });
});
