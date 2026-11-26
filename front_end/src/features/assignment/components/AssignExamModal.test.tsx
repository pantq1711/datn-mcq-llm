import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/assignmentApi";
import AssignExamModal from "./AssignExamModal";

vi.mock("../services/assignmentApi");

// jsdom không vẽ canvas: thay QRCode của antd (hiện sau khi giao xong).
vi.mock("antd", async (importOriginal) => {
    const actual = await importOriginal<typeof import("antd")>();
    return { ...actual, QRCode: () => <div data-testid="qr" /> };
});

const mocked = vi.mocked(api);

const NAME_CONSENT =
    "Tôi xác nhận việc cho học sinh làm bài trên hệ thống này đã được nhà trường cho phép, phụ huynh đã được thông báo việc học sinh nhập họ tên để giáo viên xem điểm, và tôi chỉ dùng họ tên cho việc này.";
const SEAT_CONSENT =
    "Tôi xác nhận việc cho học sinh làm bài trên hệ thống này đã được nhà trường cho phép, phụ huynh đã được thông báo theo quy định của trường, và tôi sẽ không yêu cầu học sinh nhập họ tên.";

function renderModal() {
    return render(
        <MemoryRouter>
            <AssignExamModal open examId={5} onClose={vi.fn()} />
        </MemoryRouter>
    );
}

beforeEach(() => {
    vi.resetAllMocks();
});

describe("AssignExamModal: học sinh nhập họ tên hay số thứ tự (quyết định 25/11)", () => {
    it("mặc định 'Họ và tên', câu xác nhận nói về họ tên", () => {
        renderModal();

        expect(screen.getByRole("radio", { name: "Họ và tên (mặc định)" })).toBeChecked();
        expect(screen.getByRole("radio", { name: "Số thứ tự" })).not.toBeChecked();
        expect(screen.getByText(NAME_CONSENT)).toBeInTheDocument();
        expect(screen.queryByText(SEAT_CONSENT)).not.toBeInTheDocument();
    });

    it("chọn 'Số thứ tự' thì giữ câu xác nhận cũ", async () => {
        const user = userEvent.setup();
        renderModal();

        await user.click(screen.getByRole("radio", { name: "Số thứ tự" }));

        expect(screen.getByText(SEAT_CONSENT)).toBeInTheDocument();
        expect(screen.queryByText(NAME_CONSENT)).not.toBeInTheDocument();
    });

    it.each([
        ["Họ và tên (mặc định)", "name"],
        ["Số thứ tự", "seat"],
    ])("giao bài với '%s' gửi identityMode = %s", async (label, mode) => {
        const user = userEvent.setup();
        mocked.createAssignment.mockRejectedValue(new Error("dừng ở đây"));
        renderModal();

        await user.click(screen.getByRole("radio", { name: label }));
        await user.type(screen.getByLabelText("Tên lớp"), "8A1");
        await user.type(screen.getByLabelText("Sĩ số"), "35");
        await user.click(screen.getByRole("checkbox"));
        await user.click(screen.getByRole("button", { name: "Giao bài" }));

        await waitFor(() => expect(mocked.createAssignment).toHaveBeenCalledTimes(1));
        expect(mocked.createAssignment.mock.calls[0][1]).toMatchObject({
            identityMode: mode,
            consentConfirmed: true,
            classes: [{ classLabel: "8A1", size: 35 }],
        });
    });
});
