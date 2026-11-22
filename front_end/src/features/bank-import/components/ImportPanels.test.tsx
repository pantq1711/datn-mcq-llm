import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { makeBatch, makeSummary } from "../test/fixtures";
import ImportSkippedPanel from "./ImportSkippedPanel";
import ImportSummary from "./ImportSummary";
import PendingImportsPanel from "./PendingImportsPanel";

describe("ImportSkippedPanel", () => {
    it("liệt kê từng lý do bỏ câu bằng lời kèm số câu và hướng xử lý", () => {
        render(
            <ImportSkippedPanel
                skipped={{ has_image: 3, image_limit: 1, image_convert_failed: 2, formula_unsupported: 1, has_table: 4 }}
                skippedItems={[{ locator: "Phần I, câu thứ 5", type: "mcq", reason: "has_image" }]}
            />
        );
        expect(screen.getByText("Công thức dạng ảnh, chưa nhập được")).toBeInTheDocument();
        expect(screen.getByText("Quá giới hạn hình")).toBeInTheDocument();
        expect(screen.getByText("Hình không chuyển được")).toBeInTheDocument();
        expect(screen.getByText("Công thức chưa chuyển được")).toBeInTheDocument();
        expect(screen.getByText("Có bảng dữ liệu")).toBeInTheDocument();
        expect(screen.getByText(/Tổng cộng 11 câu bị bỏ/)).toBeInTheDocument();
    });

    it("không có câu bị bỏ thì không hiện gì", () => {
        const { container } = render(<ImportSkippedPanel skipped={{}} skippedItems={[]} />);
        expect(container).toBeEmptyDOMElement();
    });

    it("vị trí câu bị bỏ nằm trong phần gập mở", async () => {
        render(
            <ImportSkippedPanel
                skipped={{ has_image: 1 }}
                skippedItems={[{ locator: "Phần I, câu thứ 5", type: "mcq", reason: "has_image" }]}
            />
        );
        await userEvent.click(screen.getByText(/Xem vị trí các câu bị bỏ/));
        expect(await screen.findByText(/Phần I, câu thứ 5/)).toBeInTheDocument();
    });
});

describe("ImportSummary", () => {
    it("nêu số câu đọc được, chọn được, bị chặn và giờ hết hạn (không đếm ngược)", () => {
        render(<ImportSummary batch={makeBatch({ questionCount: 20, importableCount: 15, blockedCount: 3 })} />);
        expect(screen.getByText("Khối 8 - Chương 1 - Bài 2 - Hằng đẳng thức")).toBeInTheDocument();
        expect(screen.getByText("20")).toBeInTheDocument();
        expect(screen.getByText("15")).toBeInTheDocument();
        expect(screen.getByText("3")).toBeInTheDocument();
        expect(screen.getByText(/Hết hạn lúc/)).toBeInTheDocument();
    });
});

describe("PendingImportsPanel", () => {
    const batches = [
        makeSummary({ id: 11, displayName: "Bài 1 - Số hữu tỉ", questionCount: 30 }),
        makeSummary({ id: 12, displayName: "Bài 2 - Số thực", questionCount: 18 }),
    ];

    it("không có lô chờ thì không hiện gì", () => {
        const { container } = render(<PendingImportsPanel batches={[]} onOpen={() => {}} onCancel={async () => {}} />);
        expect(container).toBeEmptyDOMElement();
    });

    it("mỗi lô chờ có tên, số câu, nút Mở và nút Hủy", async () => {
        const onOpen = vi.fn();
        render(<PendingImportsPanel batches={batches} onOpen={onOpen} onCancel={async () => {}} />);
        const row = screen.getByTestId("pending-import-11");
        expect(within(row).getByText("Bài 1 - Số hữu tỉ")).toBeInTheDocument();
        expect(within(row).getByText(/30 câu/)).toBeInTheDocument();
        await userEvent.click(within(row).getByRole("button", { name: /Mở lại/ }));
        expect(onOpen).toHaveBeenCalledWith(11);
    });

    it("hủy phải qua bước xác nhận", async () => {
        const onCancel = vi.fn().mockResolvedValue(undefined);
        render(<PendingImportsPanel batches={batches} onOpen={() => {}} onCancel={onCancel} />);
        const row = screen.getByTestId("pending-import-12");
        await userEvent.click(within(row).getByRole("button", { name: /Hủy lần nhập/ }));
        expect(onCancel).not.toHaveBeenCalled();
        await userEvent.click(await screen.findByRole("button", { name: "Hủy lần nhập này" }));
        expect(onCancel).toHaveBeenCalledWith(12);
    });

    it("đủ 3 lô chờ thì nhắc phải hủy hoặc xác nhận bớt trước khi tải tiếp", () => {
        const three = [...batches, makeSummary({ id: 13, displayName: "Bài 3" })];
        render(<PendingImportsPanel batches={three} onOpen={() => {}} onCancel={async () => {}} />);
        expect(screen.getByText(/đã đủ 3 lần nhập chờ xác nhận/i)).toBeInTheDocument();
    });
});
