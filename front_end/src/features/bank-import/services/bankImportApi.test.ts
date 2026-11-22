import { beforeEach, describe, expect, it, vi } from "vitest";
import apiClient from "../../../services/apiClient";
import {
    confirmBankImport,
    deleteBankImport,
    getBankImport,
    listPendingImports,
    uploadBankImport,
} from "./bankImportApi";

vi.mock("../../../services/apiClient", () => ({
    default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));

const mocked = vi.mocked(apiClient);

describe("bankImportApi", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it("tải lên: multipart có phần file, chỉ gửi tùy chọn đã điền", async () => {
        mocked.post.mockResolvedValue({ data: { id: 7 } });
        const file = new File(["x"], "KHỐI 8.docx");
        const batch = await uploadBankImport(file, { gradeLevel: 8, topic: "  Hằng đẳng thức ", defaultLevel: "easy" });

        expect(batch).toEqual({ id: 7 });
        const [url, body, config] = mocked.post.mock.calls[0];
        expect(url).toBe("/api/bank-imports");
        expect(body).toBeInstanceOf(FormData);
        const form = body as FormData;
        expect(form.get("file")).toBeInstanceOf(File);
        expect(form.get("gradeLevel")).toBe("8");
        expect(form.get("topic")).toBe("Hằng đẳng thức");
        expect(form.get("defaultLevel")).toBe("easy");
        expect((config as { headers: Record<string, string> }).headers["Content-Type"]).toBe("multipart/form-data");
    });

    it("tải lên: bỏ tùy chọn rỗng, null hoặc chỉ có khoảng trắng", async () => {
        mocked.post.mockResolvedValue({ data: { id: 1 } });
        await uploadBankImport(new File(["x"], "a.docx"), { gradeLevel: null, topic: "   ", defaultLevel: null });
        const form = mocked.post.mock.calls[0][1] as FormData;
        expect(form.has("gradeLevel")).toBe(false);
        expect(form.has("topic")).toBe(false);
        expect(form.has("defaultLevel")).toBe(false);
        await uploadBankImport(new File(["x"], "a.docx"));
        expect((mocked.post.mock.calls[1][1] as FormData).has("topic")).toBe(false);
    });

    it("danh sách lô chờ: status=preview", async () => {
        mocked.get.mockResolvedValue({ data: [{ id: 1 }, { id: 2 }] });
        expect(await listPendingImports()).toEqual([{ id: 1 }, { id: 2 }]);
        expect(mocked.get).toHaveBeenCalledWith("/api/bank-imports", { params: { status: "preview" } });
    });

    it("xem lại một lô", async () => {
        mocked.get.mockResolvedValue({ data: { id: 7 } });
        expect(await getBankImport(7)).toEqual({ id: 7 });
        expect(mocked.get).toHaveBeenCalledWith("/api/bank-imports/7");
    });

    it("xác nhận gửi {keys}", async () => {
        mocked.post.mockResolvedValue({ data: { batchId: 7, imported: 2, bankItemIds: [1, 2] } });
        const result = await confirmBankImport(7, ["mcq-001", "tf-002"]);
        expect(result.imported).toBe(2);
        expect(mocked.post).toHaveBeenCalledWith("/api/bank-imports/7/confirm", { keys: ["mcq-001", "tf-002"] });
    });

    it("hủy lô", async () => {
        mocked.delete.mockResolvedValue({ status: 204 });
        await deleteBankImport(7);
        expect(mocked.delete).toHaveBeenCalledWith("/api/bank-imports/7");
    });
});
