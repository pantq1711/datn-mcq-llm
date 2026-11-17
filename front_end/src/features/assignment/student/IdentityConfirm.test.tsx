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
});
