import { configure, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/bankImportApi";
import { makeBatch, makeBlockedItem, makeFigureItem, makeImage, makeItem, makeSummary, makeTfItem } from "../test/fixtures";
import type { ImportBatch } from "../types/bankImport.types";
import BankImportPage from "./BankImportPage";

vi.mock("../services/bankImportApi");
const mocked = vi.mocked(api);

// Bản xem trước dựng KaTeX cho nhiều câu; khi cả bộ test chạy song song, 1 giây mặc định của findBy/waitFor không đủ
configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 20000 });

function apiError(status: number, code: string, error: string) {
    return { response: { status, data: { code, error } } };
}

function Where() {
    return <div data-testid="where">{useLocation().pathname}</div>;
}

function renderPage(path = "/question-bank/import") {
    return render(
        <MemoryRouter initialEntries={[path]}>
            <Where />
            <Routes>
                <Route path="/question-bank/import" element={<BankImportPage />} />
                <Route path="/question-bank/import/:id" element={<BankImportPage />} />
                <Route path="/question-bank" element={<div>TRANG NGAN HANG</div>} />
            </Routes>
        </MemoryRouter>
    );
}

function docx(name = "KHỐI 8 - CHƯƠNG 1 - BÀI 2 - Hằng đẳng thức.docx") {
    return new File(["x"], name);
}

function chooseFile(file: File) {
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    return userEvent.setup({ applyAccept: false }).upload(input, file);
}

beforeEach(() => {
    vi.resetAllMocks();
    mocked.listPendingImports.mockResolvedValue([]);
});

describe("BankImportPage: bước tải lên", () => {
    it("chưa chọn file thì nút Đọc file bị khóa", async () => {
        renderPage();
        expect(await screen.findByText(/Kéo thả hoặc bấm để chọn file Word/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Đọc file" })).toBeDisabled();
    });

    it("file không phải .docx bị từ chối ngay, không gọi máy chủ", async () => {
        renderPage();
        await chooseFile(docx("de.pdf"));
        expect(await screen.findByText(/chỉ nhận file Word \(\.docx\)/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Đọc file" })).toBeDisabled();
        expect(mocked.uploadBankImport).not.toHaveBeenCalled();
    });

    it("file quá 10 MB bị từ chối ngay", async () => {
        renderPage();
        const big = docx("de.docx");
        Object.defineProperty(big, "size", { value: 11 * 1024 * 1024 });
        await chooseFile(big);
        expect(await screen.findByText(/vượt quá giới hạn 10 MB/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Đọc file" })).toBeDisabled();
    });

    it("đang đọc file: nút khóa và có dòng báo có thể mất tới một phút; xong thì sang bản xem trước", async () => {
        let resolve!: (batch: ImportBatch) => void;
        mocked.uploadBankImport.mockReturnValue(new Promise((r) => (resolve = r)));
        renderPage();
        await chooseFile(docx());
        const user = userEvent.setup();
        await user.click(screen.getByRole("button", { name: "Đọc file" }));

        expect(await screen.findByText(/có thể mất tới một phút/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Đang đọc file/ })).toBeDisabled();
        expect(mocked.uploadBankImport).toHaveBeenCalledTimes(1);

        resolve(makeBatch());
        expect(await screen.findByText("Khối 8 - Chương 1 - Bài 2 - Hằng đẳng thức")).toBeInTheDocument();
        expect(screen.getByTestId("where")).toHaveTextContent("/question-bank/import/7");
        // đã có bản xem trước từ lần tải lên, không gọi GET lại
        expect(mocked.getBankImport).not.toHaveBeenCalled();
    });

    it("gửi file cùng tùy chọn khối, chủ đề, mức mặc định", async () => {
        mocked.uploadBankImport.mockResolvedValue(makeBatch());
        renderPage();
        const file = docx();
        await chooseFile(file);
        const user = userEvent.setup();
        await user.type(screen.getByLabelText("Chủ đề"), "Hằng đẳng thức");
        await user.click(screen.getByLabelText("Mức mặc định cho câu chưa ghi mức"));
        await user.click(await screen.findByText("Nhận biết", { selector: ".ant-select-item-option-content" }));
        await user.click(screen.getByRole("button", { name: "Đọc file" }));

        await waitFor(() => expect(mocked.uploadBankImport).toHaveBeenCalled());
        const [sent, options] = mocked.uploadBankImport.mock.calls[0];
        expect(sent).toBe(file);
        expect(options).toMatchObject({ topic: "Hằng đẳng thức", defaultLevel: "easy" });
    });

    it("thiếu mức mặc định: hiện lời của máy chủ, giữ nguyên file để chọn mức rồi đọc lại", async () => {
        mocked.uploadBankImport
            .mockRejectedValueOnce(apiError(400, "IMPORT_LEVEL_DEFAULT_REQUIRED", "File có 3 câu chưa ghi mức, thầy/cô chọn mức mặc định rồi tải lại."))
            .mockResolvedValueOnce(makeBatch());
        renderPage();
        await chooseFile(docx());
        const user = userEvent.setup();
        await user.click(screen.getByRole("button", { name: "Đọc file" }));
        expect(await screen.findByText(/File có 3 câu chưa ghi mức/)).toBeInTheDocument();
        // file vẫn còn, bấm đọc lại được (tên nút có thể còn biểu tượng "loading" một lúc ngắn sau khi tải xong)
        const again = await screen.findByRole("button", { name: /Đọc file/ });
        expect(again).toBeEnabled();
        await user.click(again);
        await waitFor(() => expect(mocked.uploadBankImport).toHaveBeenCalledTimes(2));
    });

    it("máy bận (503): báo thử lại sau ít giây và thử lại được", async () => {
        mocked.uploadBankImport
            .mockRejectedValueOnce(apiError(503, "EXTRACTOR_BUSY", "Máy đang bận."))
            .mockResolvedValueOnce(makeBatch());
        renderPage();
        await chooseFile(docx());
        const user = userEvent.setup();
        await user.click(screen.getByRole("button", { name: "Đọc file" }));
        expect(await screen.findByText(/Máy đang bận/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Thử lại" }));
        await waitFor(() => expect(mocked.uploadBankImport).toHaveBeenCalledTimes(2));
    });

    it("lỗi khác (429, 409, 422): hiện nguyên câu của máy chủ", async () => {
        mocked.uploadBankImport.mockRejectedValue(apiError(429, "TOO_MANY_UPLOADS", "Tải file quá nhiều lần."));
        renderPage();
        await chooseFile(docx());
        await userEvent.setup().click(screen.getByRole("button", { name: "Đọc file" }));
        expect(await screen.findByText("Tải file quá nhiều lần.")).toBeInTheDocument();
    });
});

describe("BankImportPage: lô đang chờ xác nhận", () => {
    it("hiện các lô chờ lấy từ danh sách, mở lại được", async () => {
        mocked.listPendingImports.mockResolvedValue([makeSummary({ id: 11, displayName: "Bài 1 - Số hữu tỉ" })]);
        mocked.getBankImport.mockResolvedValue(makeBatch({ id: 11, displayName: "Bài 1 - Số hữu tỉ" }));
        renderPage();
        expect(await screen.findByText("Lần nhập đang chờ xác nhận")).toBeInTheDocument();
        await userEvent.setup().click(screen.getByRole("button", { name: /Mở lại/ }));
        expect(await screen.findByTestId("where")).toHaveTextContent("/question-bank/import/11");
        await waitFor(() => expect(mocked.getBankImport).toHaveBeenCalledWith(11));
        expect(await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" })).toBeInTheDocument();
    });

    it("hủy một lô chờ: gọi máy chủ rồi bỏ khỏi danh sách", async () => {
        mocked.listPendingImports.mockResolvedValue([
            makeSummary({ id: 11, displayName: "Bài 1 - Số hữu tỉ" }),
            makeSummary({ id: 12, displayName: "Bài 2 - Số thực" }),
        ]);
        mocked.deleteBankImport.mockResolvedValue(undefined);
        renderPage();
        const row = await screen.findByTestId("pending-import-11");
        const user = userEvent.setup();
        await user.click(within(row).getByRole("button", { name: /Hủy lần nhập/ }));
        await user.click(await screen.findByRole("button", { name: "Hủy lần nhập này" }));
        await waitFor(() => expect(mocked.deleteBankImport).toHaveBeenCalledWith(11));
        await waitFor(() => expect(screen.queryByTestId("pending-import-11")).toBeNull());
        expect(screen.getByTestId("pending-import-12")).toBeInTheDocument();
    });

    it("không tải được danh sách lô chờ thì vẫn tải lên được", async () => {
        mocked.listPendingImports.mockRejectedValue(new Error("Network Error"));
        renderPage();
        expect(await screen.findByText(/Kéo thả hoặc bấm để chọn file Word/)).toBeInTheDocument();
        expect(screen.queryByText("Lần nhập đang chờ xác nhận")).toBeNull();
    });

    it("đủ 3 lô chờ và tải lên báo đã đủ: tải lại danh sách để thầy cô hủy bớt", async () => {
        mocked.uploadBankImport.mockRejectedValue(apiError(409, "IMPORT_PENDING_LIMIT", "Thầy/cô đang có 3 lần nhập chờ xác nhận. Hãy xác nhận hoặc hủy bớt rồi tải tiếp."));
        renderPage();
        await chooseFile(docx());
        await userEvent.setup().click(screen.getByRole("button", { name: "Đọc file" }));
        expect(await screen.findByText(/Hãy xác nhận hoặc hủy bớt/)).toBeInTheDocument();
        await waitFor(() => expect(mocked.listPendingImports).toHaveBeenCalledTimes(2));
    });
});

describe("BankImportPage: bản xem trước", () => {
    function openPreview(batch: ImportBatch = makeBatch()) {
        mocked.getBankImport.mockResolvedValue(batch);
        return renderPage(`/question-bank/import/${batch.id}`);
    }

    it("hiện tóm tắt, câu bị bỏ theo lý do, từng câu với hình thật và cờ", async () => {
        const { container } = openPreview();
        expect(await screen.findByText("Công thức dạng ảnh, chưa nhập được")).toBeInTheDocument();
        expect(screen.getByText("Quá giới hạn hình")).toBeInTheDocument();
        expect(container.querySelector("img")?.getAttribute("src")).toContain("/api/question-images/812");
        expect(screen.getByText("Có hình")).toBeInTheDocument();
        expect(screen.getByText(/Chưa thấy đáp án: tô vàng/)).toBeInTheDocument();
        expect(screen.getByRole("checkbox", { name: "Chọn câu mcq-002" })).toBeDisabled();
    });

    it("mặc định chưa chọn câu nào; chọn tất cả chỉ lấy câu chọn được; nút nhập ghi số câu", async () => {
        openPreview();
        const user = userEvent.setup();
        const confirm = await screen.findByRole("button", { name: /Nhập 0 câu đã chọn/ });
        expect(confirm).toBeDisabled();
        await user.click(screen.getByRole("button", { name: "Chọn tất cả câu chọn được" }));
        expect(screen.getByRole("button", { name: "Nhập 3 câu đã chọn" })).toBeEnabled();
        expect(screen.getByRole("checkbox", { name: "Chọn câu mcq-002" })).not.toBeChecked();
        await user.click(screen.getByRole("button", { name: "Bỏ chọn tất cả" }));
        expect(screen.getByRole("button", { name: "Nhập 0 câu đã chọn" })).toBeDisabled();
    });

    it("cảnh báo mức chung của câu đúng/sai chỉ hiện khi đã chọn câu đúng/sai", async () => {
        openPreview();
        const user = userEvent.setup();
        await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" });
        await user.click(screen.getByRole("checkbox", { name: "Chọn câu mcq-001" }));
        expect(screen.queryByText(/một mức chung cho cả 4 ý/)).toBeNull();
        await user.click(screen.getByRole("checkbox", { name: "Chọn câu tf-001" }));
        expect(screen.getByText(/một mức chung cho cả 4 ý/)).toBeInTheDocument();
    });

    it("xác nhận chỉ gửi mã các câu đã chọn rồi hiện kết quả", async () => {
        mocked.confirmBankImport.mockResolvedValue({ batchId: 7, imported: 2, bankItemIds: [1, 2] });
        openPreview();
        const user = userEvent.setup();
        await user.click(await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" }));
        await user.click(screen.getByRole("checkbox", { name: "Chọn câu tf-001" }));
        await user.click(screen.getByRole("button", { name: "Nhập 2 câu đã chọn" }));
        await waitFor(() => expect(mocked.confirmBankImport).toHaveBeenCalledWith(7, ["mcq-001", "tf-001"]));
        expect(await screen.findByText(/Đã nhập 2 câu vào ngân hàng riêng/)).toBeInTheDocument();
        expect(screen.queryByText(/Câu có hình đã vào ngân hàng/)).toBeNull();
        await user.click(screen.getByRole("button", { name: "Xem ngân hàng câu hỏi" }));
        expect(await screen.findByText("TRANG NGAN HANG")).toBeInTheDocument();
    });

    it("A2: đã nhập câu có hình thì không còn câu báo chưa dùng để tạo đề được", async () => {
        mocked.confirmBankImport.mockResolvedValue({ batchId: 7, imported: 1, bankItemIds: [3] });
        openPreview();
        const user = userEvent.setup();
        await user.click(await screen.findByRole("checkbox", { name: "Chọn câu mcq-003" }));
        await user.click(screen.getByRole("button", { name: "Nhập 1 câu đã chọn" }));
        expect(await screen.findByText(/Đã nhập 1 câu vào ngân hàng riêng/)).toBeInTheDocument();
        expect(screen.queryByText(/chưa dùng để tạo đề được/)).toBeNull();
    });

    it("422 IMPORT_ITEM_BLOCKED: tải lại lô, hiện lời máy chủ, giữ lựa chọn còn hợp lệ", async () => {
        mocked.confirmBankImport.mockRejectedValue(apiError(422, "IMPORT_ITEM_BLOCKED", "Câu còn cờ chặn: mcq-001 (Chưa thấy đáp án)"));
        const first = makeBatch();
        // sau khi tải lại, câu mcq-001 bị chặn còn mcq-003 vẫn chọn được
        const refreshed = makeBatch({
            items: [makeBlockedItem({ key: "mcq-001" }), makeBlockedItem(), makeFigureItem(), makeTfItem()],
        });
        mocked.getBankImport.mockResolvedValueOnce(first).mockResolvedValueOnce(refreshed);
        renderPage("/question-bank/import/7");
        const user = userEvent.setup();
        await user.click(await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" }));
        await user.click(screen.getByRole("checkbox", { name: "Chọn câu mcq-003" }));
        await user.click(screen.getByRole("button", { name: "Nhập 2 câu đã chọn" }));

        expect(await screen.findByText(/Câu còn cờ chặn: mcq-001/)).toBeInTheDocument();
        await waitFor(() => expect(mocked.getBankImport).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(screen.getByRole("checkbox", { name: "Chọn câu mcq-001" })).toBeDisabled());
        expect(screen.getByRole("checkbox", { name: "Chọn câu mcq-003" })).toBeChecked();
        expect(await screen.findByRole("button", { name: /Nhập 1 câu đã chọn/ })).toBeEnabled();
    });

    it("410 khi xác nhận: tải lại lô thấy đã hết hạn thì hiện màn hết hạn", async () => {
        mocked.confirmBankImport.mockRejectedValue(apiError(410, "IMPORT_EXPIRED", "Bản xem trước đã hết hạn và bị xóa. Hãy tải lại file."));
        mocked.getBankImport
            .mockResolvedValueOnce(makeBatch())
            .mockRejectedValueOnce(apiError(410, "IMPORT_EXPIRED", "Bản xem trước đã hết hạn và bị xóa. Hãy tải lại file."));
        renderPage("/question-bank/import/7");
        const user = userEvent.setup();
        await user.click(await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" }));
        await user.click(screen.getByRole("button", { name: "Nhập 1 câu đã chọn" }));
        expect(await screen.findByText(/hết hạn hoặc không còn/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Tải file lại" }));
        expect(await screen.findByText(/Kéo thả hoặc bấm để chọn file Word/)).toBeInTheDocument();
        expect(screen.getByTestId("where")).toHaveTextContent(/^\/question-bank\/import$/);
    });

    it("lỗi khác khi xác nhận (409 IMPORT_CONFLICT): giữ nguyên lựa chọn để thử lại", async () => {
        mocked.confirmBankImport.mockRejectedValue(apiError(409, "IMPORT_CONFLICT", "Có thao tác đồng thời, hãy thử lại."));
        openPreview();
        const user = userEvent.setup();
        await user.click(await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" }));
        await user.click(screen.getByRole("button", { name: "Nhập 1 câu đã chọn" }));
        expect(await screen.findByText("Có thao tác đồng thời, hãy thử lại.")).toBeInTheDocument();
        expect(screen.getByRole("checkbox", { name: "Chọn câu mcq-001" })).toBeChecked();
        expect(mocked.getBankImport).toHaveBeenCalledTimes(1);
    });

    it("Tải lại hình: lấy lại lô, đổi URL ảnh, giữ lựa chọn", async () => {
        const stale = makeBatch();
        const fresh = makeBatch({
            items: [
                makeItem(),
                makeBlockedItem(),
                makeFigureItem({ images: [makeImage(812, { url: "/api/question-images/812?exp=2&u=1&sig=moi" })] }),
                makeTfItem(),
            ],
        });
        mocked.getBankImport.mockResolvedValueOnce(stale).mockResolvedValueOnce(fresh);
        const { container } = renderPage("/question-bank/import/7");
        const user = userEvent.setup();
        await user.click(await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" }));
        await user.click(screen.getByRole("button", { name: "Tải lại hình" }));
        await waitFor(() => expect(container.querySelector("img")?.getAttribute("src")).toContain("sig=moi"));
        expect(screen.getByRole("checkbox", { name: "Chọn câu mcq-001" })).toBeChecked();
        expect(mocked.getBankImport).toHaveBeenCalledTimes(2);
    });

    it("không có câu nào có hình thì không có nút Tải lại hình", async () => {
        openPreview(makeBatch({ items: [makeItem()] }));
        await screen.findByRole("checkbox", { name: "Chọn câu mcq-001" });
        expect(screen.queryByRole("button", { name: "Tải lại hình" })).toBeNull();
    });

    it("hủy lần nhập đang xem: hỏi lại, gọi máy chủ rồi về bước tải lên", async () => {
        mocked.deleteBankImport.mockResolvedValue(undefined);
        openPreview();
        const user = userEvent.setup();
        await user.click(await screen.findByRole("button", { name: /Hủy lần nhập/ }));
        await user.click(await screen.findByRole("button", { name: "Hủy lần nhập này" }));
        await waitFor(() => expect(mocked.deleteBankImport).toHaveBeenCalledWith(7));
        expect(await screen.findByText(/Kéo thả hoặc bấm để chọn file Word/)).toBeInTheDocument();
    });
});

describe("BankImportPage: mở lô không còn hoặc đã xong", () => {
    it.each([
        [410, "IMPORT_EXPIRED"],
        [404, "IMPORT_NOT_FOUND"],
    ])("GET trả %i: màn hết hạn kèm nút tải file lại", async (status, code) => {
        mocked.getBankImport.mockRejectedValue(apiError(status, code, "Không còn."));
        renderPage("/question-bank/import/7");
        expect(await screen.findByText(/hết hạn hoặc không còn/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Tải file lại" })).toBeInTheDocument();
    });

    it("mã lô trên URL không phải số: coi như không còn, không gọi máy chủ", async () => {
        renderPage("/question-bank/import/abc");
        expect(await screen.findByText(/hết hạn hoặc không còn/)).toBeInTheDocument();
        expect(mocked.getBankImport).not.toHaveBeenCalled();
    });

    it("lô đã xác nhận: hiện kết quả với số câu đã nhập, không nói gì về hình", async () => {
        mocked.getBankImport.mockResolvedValue(
            makeBatch({ status: "confirmed", importedCount: 12, items: [], skippedItems: [], confirmedAt: "2026-11-23T09:00:00" })
        );
        renderPage("/question-bank/import/7");
        expect(await screen.findByText(/Đã nhập 12 câu vào ngân hàng riêng/)).toBeInTheDocument();
        expect(screen.queryByText(/Câu có hình đã vào ngân hàng/)).toBeNull();
    });

    it("lỗi khác khi mở lô (mất mạng): báo lỗi và cho thử lại", async () => {
        mocked.getBankImport.mockRejectedValueOnce(new Error("Network Error")).mockResolvedValueOnce(makeBatch());
        renderPage("/question-bank/import/7");
        expect(await screen.findByText("Không kết nối được máy chủ. Vui lòng thử lại.")).toBeInTheDocument();
        await userEvent.setup().click(screen.getByRole("button", { name: "Thử lại" }));
        expect(await screen.findByText("Khối 8 - Chương 1 - Bài 2 - Hằng đẳng thức")).toBeInTheDocument();
    });
});
