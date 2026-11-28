import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as service from "../services/examMatrixService";
import type { ExamMatrixDetail } from "../types/examMatrix.types";
import { DuplicateMatrixModal } from "./DuplicateMatrixModal";

vi.mock("../services/examMatrixService");
const mocked = vi.mocked(service);

const COPY: ExamMatrixDetail = { id: "42", name: "Giữa kì I – Toán 8 – 28/11", subjectId: 1, gradeLevel: 8, cells: [] };

beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 28, 9, 0));
    mocked.duplicateExamMatrix.mockResolvedValue(COPY);
});

afterEach(() => {
    vi.useRealTimers();
});

describe("DuplicateMatrixModal", () => {
    it("điền sẵn '<tên gốc> – DD/MM' và gửi tên tới API sao chép", async () => {
        const onDuplicated = vi.fn();
        render(<DuplicateMatrixModal source={{ id: 7, name: "Giữa kì I – Toán 8" }} onCancel={vi.fn()}
                                     onDuplicated={onDuplicated} />);

        const input = screen.getByLabelText("Tên ma trận mới");
        expect(input).toHaveValue("Giữa kì I – Toán 8 – 28/11");
        await userEvent.click(screen.getByRole("button", { name: "Sao chép" }));

        await waitFor(() => expect(onDuplicated).toHaveBeenCalledWith(COPY));
        expect(mocked.duplicateExamMatrix).toHaveBeenCalledWith("7", "Giữa kì I – Toán 8 – 28/11");
    });

    it("tên gốc đã có ngày thì thay bằng ngày hôm nay; tên giáo viên sửa được trim trước khi gửi", async () => {
        render(<DuplicateMatrixModal source={{ id: 7, name: "Giữa kì I – Toán 8 – 20/11" }} onCancel={vi.fn()}
                                     onDuplicated={vi.fn()} />);
        const input = screen.getByLabelText("Tên ma trận mới");
        expect(input).toHaveValue("Giữa kì I – Toán 8 – 28/11");

        await userEvent.clear(input);
        await userEvent.type(input, "  Giữa kì I – 8A1  ");
        await userEvent.click(screen.getByRole("button", { name: "Sao chép" }));
        await waitFor(() => expect(mocked.duplicateExamMatrix).toHaveBeenCalledWith("7", "Giữa kì I – 8A1"));
    });

    it("tên trống thì báo lỗi, không gọi API", async () => {
        render(<DuplicateMatrixModal source={{ id: 7, name: "Giữa kì I" }} onCancel={vi.fn()} onDuplicated={vi.fn()} />);
        await userEvent.clear(screen.getByLabelText("Tên ma trận mới"));
        await userEvent.click(screen.getByRole("button", { name: "Sao chép" }));

        expect(await screen.findByText("Vui lòng nhập tên ma trận.")).toBeInTheDocument();
        expect(mocked.duplicateExamMatrix).not.toHaveBeenCalled();
    });

    it("source null thì không hiện hộp", () => {
        render(<DuplicateMatrixModal source={null} onCancel={vi.fn()} onDuplicated={vi.fn()} />);
        expect(screen.queryByLabelText("Tên ma trận mới")).not.toBeInTheDocument();
    });
});
