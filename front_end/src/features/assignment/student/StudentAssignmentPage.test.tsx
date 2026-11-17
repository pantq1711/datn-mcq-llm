import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/publicAttemptApi";
import { axiosError, INFO, makeAttemptView } from "../test/fixtures";
import { STUDENT_ROUTE, studentPath } from "../studentPath";
import { loadSession, saveSession } from "../utils/attemptStorage";
import StudentAssignmentPage from "./StudentAssignmentPage";

vi.mock("../services/publicAttemptApi");

const mocked = vi.mocked(api);

function renderPage() {
    return render(
        <MemoryRouter initialEntries={[studentPath("ABC123")]}>
            <Routes>
                <Route path={STUDENT_ROUTE} element={<StudentAssignmentPage />} />
            </Routes>
        </MemoryRouter>
    );
}

beforeEach(() => {
    vi.resetAllMocks();
    mocked.getPublicAssignment.mockResolvedValue(INFO);
});

describe("StudentAssignmentPage: vào bài mới", () => {
    it("trang chào nêu tên đề, hạn nộp và nhắc không nhập họ tên", async () => {
        renderPage();

        expect(await screen.findByRole("heading", { name: INFO.title })).toBeInTheDocument();
        expect(screen.getByText(/Hạn nộp: 17:00 ngày 20\/11\/2026/)).toBeInTheDocument();
        expect(screen.getByText(/Không nhập họ tên/)).toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "8A1" })).toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "8A2" })).toBeInTheDocument();
    });

    it("chọn lớp, nhập số thứ tự → màn xác nhận → đúng thì mới tạo lượt làm và vào làm bài", async () => {
        const user = userEvent.setup();
        mocked.startAttempt.mockResolvedValue(makeAttemptView({ attemptToken: "tok-1", classLabel: "8A2", seatNo: 7 }));
        renderPage();

        await user.click(await screen.findByRole("radio", { name: "8A2" }));
        await user.type(screen.getByLabelText(/Số thứ tự của em/), "7");
        await user.click(screen.getByRole("button", { name: "Tiếp tục" }));

        expect(await screen.findByRole("heading", { name: "Em là lớp 8A2, số 7 — đúng không?" })).toBeInTheDocument();
        expect(mocked.startAttempt).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", { name: "Đúng, bắt đầu làm bài" }));

        expect(await screen.findByText("Đã làm 0/3")).toBeInTheDocument();
        expect(mocked.startAttempt).toHaveBeenCalledWith("ABC123", "8A2", 7);
        expect(loadSession("ABC123")).toEqual({ token: "tok-1", classLabel: "8A2", seatNo: 7 });
    });

    it("số thứ tự ngoài sĩ số của lớp thì báo ngay, chưa gọi máy chủ", async () => {
        const user = userEvent.setup();
        renderPage();

        await user.click(await screen.findByRole("radio", { name: "8A1" }));
        await user.type(screen.getByLabelText(/Số thứ tự của em/), "40");
        await user.click(screen.getByRole("button", { name: "Tiếp tục" }));

        expect(screen.getByText("Số thứ tự của lớp 8A1 phải từ 1 đến 35.")).toBeInTheDocument();
        expect(mocked.startAttempt).not.toHaveBeenCalled();
    });

    it("bài đã đóng: không cho nhập lớp", async () => {
        mocked.getPublicAssignment.mockResolvedValue({ ...INFO, status: "closed" });
        renderPage();

        expect(await screen.findByText("Bài đã đóng.")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Tiếp tục" })).not.toBeInTheDocument();
    });

    it("mã bài sai: báo không tìm thấy", async () => {
        mocked.getPublicAssignment.mockRejectedValue(axiosError(404));
        renderPage();

        expect(await screen.findByRole("heading", { name: "Không tìm thấy bài này" })).toBeInTheDocument();
    });
});

describe("StudentAssignmentPage: mở lại bài (AN-06)", () => {
    beforeEach(() => {
        saveSession("ABC123", { token: "tok-old", classLabel: "8A1", seatNo: 12 });
    });

    it("mở lại link hỏi 'Em là lớp 8A1, số 12 — đúng không?' trước khi cho làm tiếp", async () => {
        const user = userEvent.setup();
        mocked.getCurrentAttempt.mockResolvedValue(makeAttemptView());
        renderPage();

        expect(await screen.findByRole("heading", { name: "Em là lớp 8A1, số 12 — đúng không?" })).toBeInTheDocument();
        expect(mocked.getCurrentAttempt).toHaveBeenCalledWith("tok-old");
        // Chưa lộ câu hỏi khi chưa xác nhận
        expect(screen.queryByText("Đã làm 0/3")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Đúng, làm tiếp" }));
        expect(await screen.findByText("Đã làm 0/3")).toBeInTheDocument();
    });

    it("'Không phải em' xóa token và câu trả lời khỏi máy rồi về trang chào, không gọi xóa lượt trên máy chủ", async () => {
        const user = userEvent.setup();
        localStorage.setItem("online-answers:ABC123", JSON.stringify({ mcq: { "101": "A" }, tf: {}, sa: {} }));
        mocked.getCurrentAttempt.mockResolvedValue(makeAttemptView());
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Không phải em, thoát" }));

        expect(await screen.findByRole("heading", { name: INFO.title })).toBeInTheDocument();
        expect(loadSession("ABC123")).toBeNull();
        expect(localStorage.getItem("online-answers:ABC123")).toBeNull();
    });

    it("đã nộp, bài còn mở: sau xác nhận chỉ thấy 'Đã nộp, kết quả có sau …'", async () => {
        const user = userEvent.setup();
        mocked.getCurrentAttempt.mockResolvedValue(
            makeAttemptView({ submitted: true, submittedAt: "2026-11-20T09:30:00", parts: undefined })
        );
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Đúng, xem tiếp" }));

        expect(await screen.findByRole("heading", { name: "Đã nộp, kết quả có sau 17:02 ngày 20/11/2026" })).toBeInTheDocument();
        expect(screen.queryByText(/Điểm thô/)).not.toBeInTheDocument();
    });

    it("đã nộp, bài đã đóng và bật hiện điểm: hiện điểm theo phần", async () => {
        const user = userEvent.setup();
        mocked.getCurrentAttempt.mockResolvedValue(
            makeAttemptView({
                submitted: true,
                submittedAt: "2026-11-20T09:30:00",
                parts: undefined,
                resultsReleased: true,
                result: {
                    score: 5.25,
                    maxScore: 7,
                    score10: 7.5,
                    parts: [{ part: "I", correct: 10, total: 12, score: 2.5 }],
                },
            })
        );
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Đúng, xem tiếp" }));

        expect(await screen.findByText("7,5")).toBeInTheDocument();
        expect(screen.getByText(/10\/12 câu đúng/)).toBeInTheDocument();
    });

    it("token không còn hợp lệ (401): bỏ token cũ, về trang chào kèm lời giải thích", async () => {
        mocked.getCurrentAttempt.mockRejectedValue(axiosError(401, { code: "ATTEMPT_TOKEN_INVALID" }));
        renderPage();

        expect(await screen.findByText(/Bài làm cũ trên máy này không còn dùng được/)).toBeInTheDocument();
        await waitFor(() => expect(loadSession("ABC123")).toBeNull());
    });

    it("mất mạng khi mở lại: giữ token, cho thử lại", async () => {
        const user = userEvent.setup();
        mocked.getCurrentAttempt.mockRejectedValueOnce(axiosError(undefined)).mockResolvedValueOnce(makeAttemptView());
        renderPage();

        expect(await screen.findByRole("heading", { name: "Chưa mở được bài" })).toBeInTheDocument();
        expect(loadSession("ABC123")).not.toBeNull();

        await user.click(screen.getByRole("button", { name: "Thử lại" }));
        expect(await screen.findByRole("heading", { name: "Em là lớp 8A1, số 12 — đúng không?" })).toBeInTheDocument();
    });
});
