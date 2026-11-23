import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { FIGURE_FALLBACK_TEXT } from "../../../components/common/MathText/questionImage.utils";
import { makeAttemptView } from "../test/fixtures";
import type { AttemptView } from "../types/assignment.types";
import AttemptOutcome from "./AttemptOutcome";

const RESULT = {
    score: 5.25,
    maxScore: 7,
    score10: 7.5,
    parts: [
        { part: "I" as const, correct: 10, total: 12, score: 2.5 },
        { part: "II" as const, correct: 5, total: 8, score: 0.75 },
        { part: "III" as const, correct: 4, total: 4, score: 2 },
    ],
};

const REVIEW = [
    {
        part: "I" as const,
        items: [
            {
                slotId: 101,
                number: 1,
                questionType: "mcq" as const,
                questionText: "Tính $1+1$.",
                options: { A: "1", B: "2", C: "3", D: "4" },
                yourChoice: "C",
                correctOption: "B",
                correctCount: 0,
                score: 0,
                explanation: "Cộng hai số được 2.",
            },
        ],
    },
    {
        part: "III" as const,
        items: [
            {
                slotId: 103,
                number: 1,
                questionType: "sa" as const,
                questionText: "Tính $2+0,5$.",
                yourText: "2,6",
                saAnswer: "2,5",
                correctCount: 0,
                score: 0,
            },
        ],
    },
];

function setup(view: AttemptView, flags = { showScore: true, showSolutions: false }) {
    const onDone = vi.fn();
    const onRefresh = vi.fn();
    render(<AttemptOutcome view={view} {...flags} onRefresh={onRefresh} onDone={onDone} />);
    return { onDone, onRefresh };
}

const submittedOpen = () =>
    makeAttemptView({ submitted: true, submittedAt: "2026-11-20T09:30:00", parts: undefined, resultsReleased: false });

describe("AttemptOutcome: ẩn / hiện kết quả theo trạng thái", () => {
    it("bài còn mở: chỉ báo đã nộp và giờ có kết quả, không có điểm, đáp án, lời giải", () => {
        setup(submittedOpen());

        expect(screen.getByRole("heading", { name: "Đã nộp, kết quả có sau 17:02 ngày 20/11/2026" })).toBeInTheDocument();
        expect(screen.queryByText(/điểm/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/Đáp án/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Lời giải/)).not.toBeInTheDocument();
    });

    it("bài còn mở: dù máy chủ lỡ gửi kèm điểm và đáp án thì màn hình vẫn không hiện", () => {
        setup(
            { ...submittedOpen(), result: RESULT, review: REVIEW },
            { showScore: true, showSolutions: true }
        );

        expect(screen.getByRole("heading", { name: /Đã nộp, kết quả có sau/ })).toBeInTheDocument();
        expect(screen.queryByText("7,5")).not.toBeInTheDocument();
        expect(screen.queryByText(/Đáp án đúng/)).not.toBeInTheDocument();
        expect(screen.queryByText("Cộng hai số được 2.")).not.toBeInTheDocument();
    });

    it("bài đã đóng và giáo viên bật hiện điểm: hiện điểm theo phần, không có đáp án", () => {
        setup({ ...submittedOpen(), resultsReleased: true, result: RESULT });

        expect(screen.getByText("7,5")).toBeInTheDocument();
        expect(screen.getByText(/Điểm thô: 5,25 \/ 7/)).toBeInTheDocument();
        expect(screen.getByText("Phần I. Trắc nghiệm nhiều phương án")).toBeInTheDocument();
        expect(screen.getByText(/10\/12 câu đúng · 2,5 điểm/)).toBeInTheDocument();
        // Phần II đếm theo ý
        expect(screen.getByText(/5\/8 ý đúng · 0,75 điểm/)).toBeInTheDocument();
        expect(screen.queryByText(/Đáp án đúng/)).not.toBeInTheDocument();
        expect(screen.queryByText("Lời giải")).not.toBeInTheDocument();
    });

    it("bài đã đóng và bật đáp án + lời giải: hiện đáp án đúng, lựa chọn của em và lời giải", () => {
        setup(
            { ...submittedOpen(), resultsReleased: true, result: RESULT, review: REVIEW },
            { showScore: true, showSolutions: true }
        );

        expect(screen.getByText("Đáp án đúng")).toBeInTheDocument();
        expect(screen.getByText("Em chọn")).toBeInTheDocument();
        expect(screen.getByText("Lời giải")).toBeInTheDocument();
        expect(screen.getByText("Cộng hai số được 2.")).toBeInTheDocument();
        expect(screen.getByText("2,5")).toBeInTheDocument();
        expect(screen.getByText("2,6")).toBeInTheDocument();
    });

    it("bài đã đóng, tắt điểm nhưng bật đáp án: không hiện điểm, vẫn hiện đáp án", () => {
        setup(
            { ...submittedOpen(), resultsReleased: true, review: REVIEW },
            { showScore: false, showSolutions: true }
        );

        expect(screen.getByText(/Điểm không được công khai/)).toBeInTheDocument();
        expect(screen.getByText("Đáp án đúng")).toBeInTheDocument();
        expect(screen.queryByText(/điểm thô/i)).not.toBeInTheDocument();
    });

    it("xem lại có hình (S2): hình ở đề, phương án và lời giải; hình tải lỗi thì hiện chữ thay thế", () => {
        const url = (id: number) => `/api/public/question-images/${id}?exp=9&a=5&sig=abc`;
        const review = [
            {
                ...REVIEW[0],
                items: [
                    {
                        ...REVIEW[0].items[0],
                        questionText: "Cho hình:\n⟦img:11⟧\nTính góc.",
                        options: { A: "⟦img:12⟧", B: "2", C: "3", D: "4" },
                        explanation: "Kẻ thêm đường cao:\n⟦img:13⟧",
                        images: [
                            { id: 11, url: url(11), displayWidthMm: 100 },
                            { id: 12, url: url(12), displayWidthMm: 30 },
                            { id: 13, url: url(13), displayWidthMm: 60 },
                        ],
                    },
                ],
            },
        ];
        setup(
            { ...submittedOpen(), resultsReleased: true, review },
            { showScore: false, showSolutions: true }
        );

        // Thẻ <img> thật (biểu tượng antd cũng có role="img").
        const images = Array.from(document.querySelectorAll("img"));
        expect(images.map((img) => img.getAttribute("src"))).toEqual([url(11), url(12), url(13)]);
        expect(document.body.textContent).not.toContain("⟦img:");

        fireEvent.error(images[2]);
        expect(screen.getByText(FIGURE_FALLBACK_TEXT)).toBeInTheDocument();
        expect(document.querySelectorAll("img")).toHaveLength(2);
    });

    it("tắt cả điểm lẫn đáp án: không hứa có kết quả", () => {
        setup(submittedOpen(), { showScore: false, showSolutions: false });

        expect(screen.getByRole("heading", { name: "Đã nộp bài" })).toBeInTheDocument();
        expect(screen.queryByText(/kết quả có sau/)).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Xem kết quả" })).not.toBeInTheDocument();
    });

    it("bài đã đóng mà em chưa nộp: nói rõ là chưa nộp", () => {
        setup(makeAttemptView({ submitted: false, parts: undefined, resultsReleased: true }));
        expect(screen.getByText("Em chưa nộp bài này.")).toBeInTheDocument();
    });

    it("nút Xong gọi onDone để xóa bài làm khỏi máy", async () => {
        const { onDone } = setup(submittedOpen());
        screen.getByRole("button", { name: "Xong" }).click();
        expect(onDone).toHaveBeenCalledTimes(1);
    });
});
