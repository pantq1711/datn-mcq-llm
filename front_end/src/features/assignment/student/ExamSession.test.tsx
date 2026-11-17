import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { axiosError, makeAttemptView } from "../test/fixtures";
import type { SubmitAnswerPayload, SubmitResult } from "../types/assignment.types";
import { emptyDraft, loadDraft } from "../utils/attemptStorage";
import ExamSession from "./ExamSession";

const OK: SubmitResult = { submittedAt: "2026-11-20T09:30:00", resultsAt: "2026-11-20T17:02:00" };
const immediate = () => Promise.resolve();

function setup(options: {
    send: (token: string, answers: SubmitAnswerPayload[]) => Promise<SubmitResult>;
    remainingSeconds?: number;
}) {
    const onSubmitted = vi.fn();
    render(
        <ExamSession
            code="ABC123"
            title="Kiểm tra 15 phút"
            token="tok"
            view={makeAttemptView({ remainingSeconds: options.remainingSeconds ?? 44 * 60 })}
            initialDraft={emptyDraft()}
            send={options.send}
            sleep={immediate}
            onSubmitted={onSubmitted}
        />
    );
    return { onSubmitted };
}

async function answerAll(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole("radio", { name: /Phương án B/ }));
    await user.click(screen.getByRole("button", { name: "Ý a: Đúng" }));
    await user.click(screen.getByRole("button", { name: "Ý b: Sai" }));
    await user.type(screen.getByLabelText("Đáp số câu 1"), "2,5");
}

async function pressSubmit(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getAllByRole("button", { name: "Nộp bài" })[0]);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Nộp bài" }));
}

describe("ExamSession: làm bài", () => {
    it("hiện đủ ba dạng câu, đếm số câu đã làm và lưu câu trả lời tạm xuống máy", async () => {
        const user = userEvent.setup();
        setup({ send: vi.fn() });

        expect(screen.getByText("Đã làm 0/3")).toBeInTheDocument();
        await answerAll(user);

        expect(screen.getByText("Đã làm 3/3")).toBeInTheDocument();
        expect(loadDraft("ABC123")).toEqual({
            mcq: { "101": "B" },
            tf: { "102": { a: "T", b: "F" } },
            sa: { "103": "2,5" },
        });
    });

    it("ô đáp số chỉ nhận chữ số , . - và báo khi gõ dạng 1.234", async () => {
        const user = userEvent.setup();
        setup({ send: vi.fn() });
        const input = screen.getByLabelText("Đáp số câu 1");

        await user.type(input, "ab1x.234");
        expect(input).toHaveValue("1.234");
        expect(screen.getByRole("alert")).toHaveTextContent("Viết liền, không dùng dấu chấm ngăn hàng nghìn");

        await user.clear(input);
        await user.type(input, "0.125");
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
});

describe("ExamSession: nộp bài và thử lại", () => {
    it("gặp 429 thì tự thử lại rồi nộp được; gửi đúng câu trả lời", async () => {
        const user = userEvent.setup();
        const send = vi
            .fn()
            .mockRejectedValueOnce(axiosError(429, { code: "TOO_MANY_REQUESTS" }, { "retry-after": "2" }))
            .mockResolvedValueOnce(OK);
        const { onSubmitted } = setup({ send });

        await answerAll(user);
        await pressSubmit(user);

        await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
        expect(send).toHaveBeenCalledTimes(2);
        expect(send).toHaveBeenLastCalledWith("tok", [
            { slotId: 101, choice: "B" },
            { slotId: 102, tf: { a: "T", b: "F" } },
            { slotId: 103, text: "2,5" },
        ]);
        expect(onSubmitted.mock.calls[0][0].result).toEqual(OK);
    });

    it("mất mạng nhiều lần: báo rõ, không mất câu trả lời, bấm Nộp lại thì nộp được", async () => {
        const user = userEvent.setup();
        const send = vi.fn().mockRejectedValue(axiosError(undefined));
        const { onSubmitted } = setup({ send });

        await answerAll(user);
        await pressSubmit(user);

        expect(await screen.findByText(/Chưa nộp được sau nhiều lần thử/)).toBeInTheDocument();
        expect(onSubmitted).not.toHaveBeenCalled();
        expect(send).toHaveBeenCalledTimes(8);
        // Câu trả lời vẫn còn trên màn hình và trong máy
        expect(screen.getByText("Đã làm 3/3")).toBeInTheDocument();
        expect(screen.getByLabelText("Đáp số câu 1")).toHaveValue("2,5");
        expect(loadDraft("ABC123").sa["103"]).toBe("2,5");

        send.mockReset();
        send.mockResolvedValueOnce(OK);
        await user.click(screen.getByRole("button", { name: "Nộp lại" }));
        await waitFor(() => expect(onSubmitted).toHaveBeenCalledTimes(1));
    });

    it("thầy cô đã đóng bài: báo rõ, không thử lại, vẫn giữ câu trả lời", async () => {
        const user = userEvent.setup();
        const send = vi.fn().mockRejectedValue(axiosError(409, { code: "ASSIGNMENT_CLOSED" }));
        const { onSubmitted } = setup({ send });

        await answerAll(user);
        await pressSubmit(user);

        expect(await screen.findByText(/Thầy cô đã đóng bài trước khi em nộp/)).toBeInTheDocument();
        expect(send).toHaveBeenCalledTimes(1);
        expect(onSubmitted).not.toHaveBeenCalled();
        expect(screen.queryByRole("button", { name: "Nộp lại" })).not.toBeInTheDocument();
        expect(loadDraft("ABC123").mcq["101"]).toBe("B");
    });

    it("hộp xác nhận nêu số câu chưa làm", async () => {
        const user = userEvent.setup();
        setup({ send: vi.fn() });

        await user.click(screen.getByRole("radio", { name: /Phương án A/ }));
        await user.click(screen.getAllByRole("button", { name: "Nộp bài" })[0]);

        expect(await screen.findByText(/Em còn 2 câu chưa làm/)).toBeInTheDocument();
    });
});

describe("ExamSession: hết giờ", () => {
    it("hết giờ thì tự nộp một lần và báo còn trong 2 phút ân hạn", async () => {
        const send = vi.fn().mockRejectedValue(axiosError(undefined));
        setup({ send, remainingSeconds: 0 });

        await waitFor(() => expect(send).toHaveBeenCalled());
        expect(screen.getByText(/Đã hết giờ\. Hệ thống còn nhận bài trong [12]:\d\d nữa \(ân hạn 2 phút\)/)).toBeInTheDocument();
    });
});
