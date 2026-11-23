import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { makeBlockedItem, makeFigureItem, makeImage, makeItem, makeTfItem } from "../test/fixtures";
import ImportItemCard from "./ImportItemCard";

function renderCard(item = makeItem(), props: Partial<React.ComponentProps<typeof ImportItemCard>> = {}) {
    const onToggle = vi.fn();
    render(<ImportItemCard item={item} selected={false} onToggle={onToggle} {...props} />);
    return { onToggle };
}

describe("ImportItemCard", () => {
    it("câu thường: có ô chọn, bật được", async () => {
        const { onToggle } = renderCard();
        const box = screen.getByRole("checkbox", { name: "Chọn câu mcq-001" });
        expect(box).toBeEnabled();
        await userEvent.click(box);
        expect(onToggle).toHaveBeenCalledWith("mcq-001");
        expect(screen.getByText("Phần I, câu thứ 1")).toBeInTheDocument();
    });

    it("câu bị chặn: ô chọn bị vô hiệu và hiện lý do bằng lời", () => {
        renderCard(makeBlockedItem());
        expect(screen.getByRole("checkbox", { name: "Chọn câu mcq-002" })).toBeDisabled();
        expect(screen.getByText(/Chưa thấy đáp án: tô vàng/)).toBeInTheDocument();
        expect(screen.getByText(/Không nhập được/)).toBeInTheDocument();
    });

    it("câu bị chặn mà lô cũ không có blockReasons: vẫn hiện câu dự phòng", () => {
        renderCard(makeBlockedItem({ blockReasons: null }));
        expect(screen.getByText(/sửa trong file Word rồi tải lại/)).toBeInTheDocument();
    });

    it("câu có hình: token thành thẻ ảnh với đúng URL, không lộ chuỗi token", () => {
        const { container } = render(<ImportItemCard item={makeFigureItem()} selected={false} onToggle={() => {}} />);
        const img = container.querySelector("img");
        expect(img).not.toBeNull();
        expect(img?.getAttribute("src")).toContain("/api/question-images/812?exp=1&u=1&sig=aa812");
        expect(container.textContent).not.toContain("⟦img");
        expect(screen.getByText("Có hình")).toBeInTheDocument();
    });

    it("hình ở phương án, ý đúng/sai và lời giải cũng hiện", () => {
        const item = makeItem({
            key: "mcq-020",
            options: [
                { key: "A", text: "⟦img:21⟧" },
                { key: "B", text: "$4$" },
                { key: "C", text: "$5$" },
                { key: "D", text: "$6$" },
            ],
            explanation: "Xem hình\n⟦img:23⟧",
            images: [makeImage(21, { role: "option_a" }), makeImage(23, { role: "solution" })],
            flags: ["has_figure"],
        });
        const { container } = render(<ImportItemCard item={item} selected={false} onToggle={() => {}} />);
        expect(container.querySelectorAll("img")).toHaveLength(2);

        const tf = makeTfItem({
            statements: [
                { key: "a", text: "Nhìn hình\n⟦img:31⟧", label: true },
                { key: "b", text: "Ý b", label: false },
                { key: "c", text: "Ý c", label: true },
                { key: "d", text: "Ý d", label: false },
            ],
            images: [makeImage(31, { role: "statement_a" })],
        });
        const second = render(<ImportItemCard item={tf} selected={false} onToggle={() => {}} />);
        expect(second.container.querySelectorAll("img")).toHaveLength(1);
    });

    it("token trỏ tới ảnh không có trong images: hiện chữ thay thế, không phải chuỗi token", () => {
        const item = makeItem({ stem: "Xem hình ⟦img:999⟧", flags: ["has_figure"], images: [] });
        const { container } = render(<ImportItemCard item={item} selected={false} onToggle={() => {}} />);
        expect(container.textContent).toContain("[Hình không tải được]");
        expect(container.textContent).not.toContain("⟦img");
    });

    it("hình lỗi lúc tải: chữ thay thế thay cho ảnh", () => {
        const { container } = render(<ImportItemCard item={makeFigureItem()} selected={false} onToggle={() => {}} />);
        fireEvent.error(container.querySelector("img") as HTMLImageElement);
        expect(container.querySelector("img")).toBeNull();
        expect(container.textContent).toContain("[Hình không tải được]");
    });

    it("bốn cờ hình hiện ngay trên thẻ kèm lời giải thích, không cần di chuột", () => {
        renderCard(makeItem({ flags: ["figure_from_previous_question", "mentions_figure_no_image", "shared_context", "has_figure"] }));
        expect(screen.getByText("Hình từ câu trước")).toBeInTheDocument();
        expect(screen.getByText(/nằm cạnh câu trước trong file Word/)).toBeInTheDocument();
        expect(screen.getByText("Có hình")).toBeInTheDocument();
        expect(screen.getByText(/Đề có nhắc đến hình/)).toBeInTheDocument();
        expect(screen.getByText(/ngữ cảnh dùng chung/)).toBeInTheDocument();
    });

    it("cờ thông tin khác hiện thành thẻ nhỏ", () => {
        renderCard(makeItem({ flags: ["level_default", "no_solution"] }));
        expect(screen.getByText("Mức mặc định")).toBeInTheDocument();
        expect(screen.getByText("Chưa có lời giải")).toBeInTheDocument();
    });

    it("câu đúng/sai hiện bốn ý kèm Đúng/Sai; câu trả lời ngắn hiện đáp số", () => {
        renderCard(makeTfItem());
        const card = screen.getByTestId("import-item-tf-001");
        expect(within(card).getAllByText("Đúng")).toHaveLength(2);
        expect(within(card).getAllByText("Sai")).toHaveLength(2);
    });

    it("câu trả lời ngắn hiện đáp số", () => {
        renderCard(makeItem({ key: "sa-001", type: "sa", options: null, correctOption: null, saAnswer: "12" }));
        expect(screen.getByText("12")).toBeInTheDocument();
    });

    it("câu thiếu options hoặc statements (null) không làm hỏng thẻ", () => {
        renderCard(makeItem({ options: null, statements: null }));
        expect(screen.getByText("Hai cộng hai bằng mấy?")).toBeInTheDocument();
    });
});
