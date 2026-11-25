import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import IdentityConfirm from "./IdentityConfirm";

function setup(props: Partial<Parameters<typeof IdentityConfirm>[0]> = {}) {
    const onConfirm = vi.fn();
    const onReject = vi.fn();
    render(
        <IdentityConfirm
            classLabel="8A1"
            seatNo={12}
            confirmText="Đúng, làm tiếp"
            rejectText="Không phải em, thoát"
            onConfirm={onConfirm}
            onReject={onReject}
            {...props}
        />
    );
    return { onConfirm, onReject };
}

describe("IdentityConfirm (AN-06)", () => {
    it("hỏi lại lớp và số thứ tự", () => {
        setup();
        expect(screen.getByRole("heading", { name: "Em là lớp 8A1, số 12 — đúng không?" })).toBeInTheDocument();
    });

    it("bấm xác nhận gọi onConfirm, bấm không phải gọi onReject", async () => {
        const user = userEvent.setup();
        const { onConfirm, onReject } = setup();

        await user.click(screen.getByRole("button", { name: "Đúng, làm tiếp" }));
        expect(onConfirm).toHaveBeenCalledTimes(1);
        expect(onReject).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", { name: "Không phải em, thoát" }));
        expect(onReject).toHaveBeenCalledTimes(1);
    });

    it("báo khi đã có bạn khác dùng cùng lớp và số", () => {
        setup({ duplicateSeat: true });
        expect(screen.getByText("Đã có bạn khác vào làm với lớp và số này.")).toBeInTheDocument();
    });

    it("không báo trùng khi không có trùng", () => {
        setup({ duplicateSeat: false });
        expect(screen.queryByText(/Đã có bạn khác/)).not.toBeInTheDocument();
    });

    it("chế độ họ tên: 'Em là <tên>, lớp <lớp> — đúng không?'", () => {
        setup({ seatNo: undefined, studentName: "Nguyễn Văn An" });
        expect(screen.getByRole("heading", { name: "Em là Nguyễn Văn An, lớp 8A1 — đúng không?" })).toBeInTheDocument();
    });

    it("chế độ họ tên, mở lại khi có lượt trùng: báo trùng tên", () => {
        setup({ seatNo: undefined, studentName: "Nguyễn Văn An", duplicateSeat: true });
        expect(screen.getByText("Đã có bạn khác vào làm với lớp và tên này.")).toBeInTheDocument();
    });

    it("báo trùng trước khi bắt đầu: hai nút 'Đúng là em, vẫn làm' và 'Nhập lại' thay cho hai nút thường", async () => {
        const user = userEvent.setup();
        const onConfirmDuplicate = vi.fn();
        const onRetype = vi.fn();
        const { onConfirm } = setup({
            seatNo: undefined,
            studentName: "Nguyễn Văn An",
            taken: true,
            onConfirmDuplicate,
            onRetype,
        });

        expect(screen.getByText("Đã có bạn vào làm với tên này.")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Đúng, làm tiếp" })).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Đúng là em, vẫn làm" }));
        expect(onConfirmDuplicate).toHaveBeenCalledTimes(1);
        await user.click(screen.getByRole("button", { name: "Nhập lại" }));
        expect(onRetype).toHaveBeenCalledTimes(1);
        expect(onConfirm).not.toHaveBeenCalled();
    });

    it("báo trùng số ở chế độ số thứ tự", () => {
        setup({ taken: true, onConfirmDuplicate: vi.fn(), onRetype: vi.fn() });
        expect(screen.getByText("Đã có bạn vào làm với số này.")).toBeInTheDocument();
    });
});
