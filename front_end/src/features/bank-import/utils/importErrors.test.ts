import { describe, expect, it } from "vitest";
import { classifyImportError } from "./importErrors";

function apiError(status: number, code?: string, error?: string) {
    return { response: { status, data: { code, error } } };
}

describe("phân loại lỗi nhập", () => {
    it("410 và 404 là lô không còn", () => {
        expect(classifyImportError(apiError(410, "IMPORT_EXPIRED", "Bản xem trước đã hết hạn."), "x").kind).toBe("gone");
        expect(classifyImportError(apiError(404, "IMPORT_NOT_FOUND", "Không tìm thấy lần nhập này."), "x").kind).toBe("gone");
    });

    it("422 IMPORT_ITEM_BLOCKED: có câu bị chặn, giữ thông báo của máy chủ", () => {
        const result = classifyImportError(apiError(422, "IMPORT_ITEM_BLOCKED", "Câu còn cờ chặn: mcq-003 (Chưa thấy đáp án)"), "x");
        expect(result.kind).toBe("blocked");
        expect(result.message).toContain("mcq-003");
    });

    it("409 IMPORT_NOT_PREVIEW: lô đã được xác nhận", () => {
        expect(classifyImportError(apiError(409, "IMPORT_NOT_PREVIEW", "Đã xác nhận."), "x").kind).toBe("not_preview");
    });

    it("400 IMPORT_LEVEL_DEFAULT_REQUIRED: cần chọn mức mặc định", () => {
        const result = classifyImportError(apiError(400, "IMPORT_LEVEL_DEFAULT_REQUIRED", "File có 3 câu chưa ghi mức."), "x");
        expect(result.kind).toBe("level_required");
        expect(result.message).toBe("File có 3 câu chưa ghi mức.");
    });

    it("503 EXTRACTOR_BUSY: máy đang bận, có thể thử lại", () => {
        expect(classifyImportError(apiError(503, "EXTRACTOR_BUSY", "Máy đang bận."), "x").kind).toBe("busy");
    });

    it("lỗi khác: dùng câu của máy chủ, không có thì dùng câu dự phòng", () => {
        expect(classifyImportError(apiError(429, "TOO_MANY_UPLOADS", "Tải quá nhiều lần."), "dự phòng")).toEqual({
            kind: "other",
            message: "Tải quá nhiều lần.",
        });
        expect(classifyImportError(apiError(500), "dự phòng").message).toBe("dự phòng");
    });

    it("không có phản hồi: báo mất kết nối", () => {
        expect(classifyImportError(new Error("Network Error"), "x")).toEqual({
            kind: "other",
            message: "Không kết nối được máy chủ. Vui lòng thử lại.",
        });
    });
});
