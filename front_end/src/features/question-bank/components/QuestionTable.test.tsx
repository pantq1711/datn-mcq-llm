import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import QuestionTable from "./QuestionTable";
import type { QuestionTableItem } from "../types/questionBank.types";

const row = (over: Partial<QuestionTableItem> & { id: number; questionText: string }): QuestionTableItem => ({
    questionType: "sa",
    gradeLevel: 8,
    topic: "ĐỊNH LÝ PYTHAGORE",
    difficulty: "easy",
    visibility: "private",
    usageCount: 0,
    createdAt: "2026-11-22T10:00:00",
    ...over,
});

const figure = (id: number) => ({ id, role: "stem", url: `/api/question-images/${id}?exp=1&u=7&sig=abc` });

function bodyRow(index: number): HTMLElement {
    // hàng 0 là hàng tiêu đề
    return screen.getAllByRole("row")[index + 1];
}

describe("QuestionTable: câu có hình", () => {
    it("câu có images có nhãn Có hình; câu không có thì không", () => {
        render(
            <QuestionTable
                data={[
                    row({ id: 1, questionText: "⟦img:6⟧\nLăng Chủ tịch", images: [figure(6)] }),
                    row({ id: 2, questionText: "Tính cạnh huyền" }),
                ]}
            />,
        );
        expect(within(bodyRow(0)).getByText("Có hình")).toBeInTheDocument();
        expect(within(bodyRow(1)).queryByText("Có hình")).toBeNull();
    });

    it("ô câu hỏi không hiện chuỗi token và không tải ảnh trong bảng", () => {
        render(
            <QuestionTable
                data={[row({ id: 1, questionText: "⟦img:6⟧\nLăng Chủ tịch Hồ Chí Minh", images: [figure(6)] })]}
            />,
        );
        const cell = bodyRow(0);
        expect(cell).toHaveTextContent("[Hình]");
        expect(cell).toHaveTextContent("Lăng Chủ tịch Hồ Chí Minh");
        expect(cell.textContent).not.toMatch(/⟦|⟧|img:/);
        // biểu tượng antd cũng có role="img": chỉ kiểm ảnh của câu theo alt
        expect(within(cell).queryByRole("img", { name: "Hình minh họa" })).toBeNull();
    });

    it("câu chỉ có token vẫn có chữ nhìn thấy", () => {
        render(<QuestionTable data={[row({ id: 1, questionText: "⟦img:6⟧", images: [figure(6)] })]} />);
        expect(bodyRow(0)).toHaveTextContent("[Hình]");
    });

    it("token không có trong images (dữ liệu cũ) hiện chữ thay thế, không hiện token", () => {
        render(<QuestionTable data={[row({ id: 1, questionText: "⟦img:6⟧\nĐề" })]} />);
        expect(bodyRow(0)).toHaveTextContent("[Hình không tải được]");
        expect(bodyRow(0).textContent).not.toMatch(/⟦|⟧|img:/);
    });

    it("nội dung vẽ ra không có gì thì hiện dòng dự phòng, không để ô trống", () => {
        render(<QuestionTable data={[row({ id: 1, questionText: "$ $" })]} />);
        expect(bodyRow(0)).toHaveTextContent("(Không có nội dung hiển thị)");
    });
});
