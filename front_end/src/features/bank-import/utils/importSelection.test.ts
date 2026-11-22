import { describe, expect, it } from "vitest";
import { makeBlockedItem, makeFigureItem, makeItem, makeTfItem } from "../test/fixtures";
import { keysToConfirm, selectAllKeys, summarizeSelection, toggleKey } from "./importSelection";

const items = [makeItem(), makeBlockedItem(), makeFigureItem(), makeTfItem()];

describe("chọn câu để nhập", () => {
    it("chọn tất cả chỉ lấy câu không có cờ chặn", () => {
        expect([...selectAllKeys(items)]).toEqual(["mcq-001", "mcq-003", "tf-001"]);
    });

    it("câu bị chặn không bao giờ chọn được", () => {
        const next = toggleKey(new Set<string>(), "mcq-002", items);
        expect(next.has("mcq-002")).toBe(false);
    });

    it("bật rồi tắt một câu chọn được; không sửa tập cũ", () => {
        const empty = new Set<string>();
        const on = toggleKey(empty, "mcq-001", items);
        expect(on.has("mcq-001")).toBe(true);
        expect(empty.size).toBe(0);
        expect(toggleKey(on, "mcq-001", items).has("mcq-001")).toBe(false);
    });

    it("mã lạ không có trong lô bị bỏ qua", () => {
        expect(toggleKey(new Set<string>(), "mcq-999", items).size).toBe(0);
    });

    it("mã gửi đi xác nhận theo thứ tự trong file, loại câu chặn và mã không còn trong lô", () => {
        const selected = new Set(["tf-001", "mcq-002", "mcq-001", "mcq-999"]);
        expect(keysToConfirm(items, selected)).toEqual(["mcq-001", "tf-001"]);
    });

    it("tóm tắt: số câu, có câu có hình, có câu đúng/sai", () => {
        expect(summarizeSelection(items, new Set())).toEqual({ count: 0, hasFigure: false, hasTf: false });
        expect(summarizeSelection(items, new Set(["mcq-001"]))).toEqual({ count: 1, hasFigure: false, hasTf: false });
        expect(summarizeSelection(items, new Set(["mcq-003", "tf-001"]))).toEqual({
            count: 2,
            hasFigure: true,
            hasTf: true,
        });
    });

    it("câu có cờ has_figure nhưng images rỗng vẫn tính là có hình", () => {
        const flagged = [makeItem({ key: "mcq-010", flags: ["has_figure"] })];
        expect(summarizeSelection(flagged, new Set(["mcq-010"])).hasFigure).toBe(true);
    });
});
