import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import QuestionContent from "./QuestionContent";
import type { ExamQuestionItem } from "../types/exam.types";

// Biểu tượng antd cũng có role="img": chỉ đếm ảnh của câu theo alt
const figures = () => screen.queryAllByRole("img", { name: "Hình minh họa" });

const figure = (id: number, role: string) =>({ id, role, url: `/api/question-images/${id}?exp=1&u=7&sig=abc` });

const mcq: ExamQuestionItem = {
    id: 1,
    questionType: "mcq",
    questionText: "Cho hình vẽ:\n⟦img:1⟧\nTính x.",
    optionA: "3",
    optionB: "4",
    optionC: "⟦img:3⟧",
    optionD: "6",
    correctOption: "A",
    explanation: "Theo hình ⟦img:2⟧ ta có x = 3.",
    images: [figure(1, "stem"), figure(2, "solution"), figure(3, "option_c")],
};

describe("QuestionContent: ảnh của câu", () => {
    it("vẽ ảnh ở câu dẫn, phương án và lời giải, không còn chuỗi token", () => {
        const { container } = render(<QuestionContent question={mcq} />);
        expect(figures()).toHaveLength(3);
        expect(container.textContent).not.toMatch(/⟦|⟧|img:/);
    });

    it("câu không có images (câu sinh, màn cũ) hiện chữ thay thế thay vì chuỗi token", () => {
        const { container } = render(<QuestionContent question={{ ...mcq, images: undefined }} />);
        expect(figures()).toHaveLength(0);
        expect(container).toHaveTextContent("[Hình không tải được]");
        expect(container.textContent).not.toMatch(/⟦|⟧|img:/);
    });

    it("câu đúng/sai: token ở ý a", () => {
        const tf: ExamQuestionItem = {
            id: 2,
            questionType: "tf",
            questionText: "Cho hình vẽ.",
            statements: [{ key: "a", text: "Theo ⟦img:5⟧", label: "T" }],
            images: [figure(5, "statement_a")],
        };
        const { container } = render(<QuestionContent question={tf} />);
        expect(figures()).toHaveLength(1);
        expect(container.textContent).not.toMatch(/⟦|⟧|img:/);
    });
});
