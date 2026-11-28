import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/examApi";
import { RenameExamModal } from "./RenameExamModal";

vi.mock("../services/examApi");
const mocked = vi.mocked(api);

beforeEach(() => {
    vi.resetAllMocks();
});

describe("RenameExamModal", () => {
    it("điền sẵn tên hiện tại; gửi tên đã trim và báo tên backend trả về", async () => {
        mocked.renameExam.mockResolvedValue({ examId: 12, title: "Giữa kì I – 8A1" });
        const onRenamed = vi.fn();
        render(<RenameExamModal target={{ examId: 12, title: "Giữa kì I – Toán 8" }} onCancel={vi.fn()}
                                onRenamed={onRenamed} />);

        const input = screen.getByLabelText("Tên đề");
        expect(input).toHaveValue("Giữa kì I – Toán 8");
        await userEvent.clear(input);
        await userEvent.type(input, "  Giữa kì I – 8A1 ");
        await userEvent.click(screen.getByRole("button", { name: "Lưu tên" }));

        await waitFor(() => expect(onRenamed).toHaveBeenCalledWith("Giữa kì I – 8A1"));
        expect(mocked.renameExam).toHaveBeenCalledWith(12, "Giữa kì I – 8A1");
        expect(screen.getByText(/Bài đã giao cho học sinh vẫn giữ tên cũ/)).toBeInTheDocument();
    });

    it("tên trống thì báo lỗi, không gọi API", async () => {
        render(<RenameExamModal target={{ examId: 12, title: "Đề" }} onCancel={vi.fn()} onRenamed={vi.fn()} />);
        await userEvent.clear(screen.getByLabelText("Tên đề"));
        await userEvent.click(screen.getByRole("button", { name: "Lưu tên" }));

        expect(await screen.findByText("Vui lòng nhập tên đề.")).toBeInTheDocument();
        expect(mocked.renameExam).not.toHaveBeenCalled();
    });

    it("tên không đổi thì đóng hộp, không gọi API", async () => {
        const onCancel = vi.fn();
        render(<RenameExamModal target={{ examId: 12, title: "Đề" }} onCancel={onCancel} onRenamed={vi.fn()} />);
        await userEvent.click(screen.getByRole("button", { name: "Lưu tên" }));

        expect(onCancel).toHaveBeenCalled();
        expect(mocked.renameExam).not.toHaveBeenCalled();
    });
});
