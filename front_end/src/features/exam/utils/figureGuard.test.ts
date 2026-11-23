import { describe, expect, it } from "vitest";
import { countFigureItems, figureAssignBlockReason } from "./figureGuard";

describe("countFigureItems", () => {
    it("đề không có hình: 0", () => {
        const parts = [{ items: [{ state: "kept" }, { state: "kept", images: [] }] }, { items: [{ state: "edited" }] }];
        expect(countFigureItems(parts)).toBe(0);
    });

    it("đếm đúng các chỗ có images không rỗng, qua nhiều phần", () => {
        const img = { id: 1, role: "stem", url: "/api/question-images/1?exp=1&u=1&sig=x" };
        const parts = [
            { items: [{ state: "kept", images: [img] }, { state: "kept" }] },
            { items: [{ state: "kept", images: [img, { ...img, id: 2 }] }] },
        ];
        expect(countFigureItems(parts)).toBe(2);
    });

    it("không có parts: 0", () => {
        expect(countFigureItems(undefined)).toBe(0);
        expect(countFigureItems([])).toBe(0);
    });

    it("images không phải mảng thì không tính", () => {
        expect(countFigureItems([{ items: [{ state: "kept", images: "x" } as { state: string }] }])).toBe(0);
    });
});

describe("figureAssignBlockReason", () => {
    it("không hình: rỗng, để không hiện Tooltip", () => {
        expect(figureAssignBlockReason(0)).toBe("");
    });

    it("có hình: nêu số câu và hướng xử lý", () => {
        const reason = figureAssignBlockReason(3);
        expect(reason).toContain("3 câu có hình");
        expect(reason).toContain("chưa hỗ trợ");
    });
});
