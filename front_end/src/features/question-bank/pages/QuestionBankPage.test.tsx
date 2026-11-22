import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/questionBankApi";
import QuestionBankPage from "./QuestionBankPage";

vi.mock("../services/questionBankApi");
const mocked = vi.mocked(api);

// Trang nạp antd và KaTeX nên lần dựng đầu chậm khi cả bộ test chạy song song
vi.setConfig({ testTimeout: 20000 });

describe("QuestionBankPage: nhập từ Word", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        mocked.getCatalogSubjects.mockResolvedValue([]);
        mocked.getBankTopics.mockResolvedValue([]);
        mocked.getBankItems.mockResolvedValue({ items: [], total: 0, totalItems: 0 } as never);
    });

    it("nút Nhập từ Word dùng được và dẫn tới màn nhập (không còn 'Sắp có')", async () => {
        render(
            <MemoryRouter initialEntries={["/question-bank"]}>
                <Routes>
                    <Route path="/question-bank" element={<QuestionBankPage />} />
                    <Route path="/question-bank/import" element={<div>MAN NHAP WORD</div>} />
                </Routes>
            </MemoryRouter>
        );
        const link = await screen.findByRole("link", { name: /Nhập từ Word/ });
        expect(link).toHaveAttribute("href", "/question-bank/import");
        expect(screen.queryByText(/Sắp có/)).toBeNull();
        await userEvent.setup().click(link);
        expect(await screen.findByText("MAN NHAP WORD")).toBeInTheDocument();
    });
});
