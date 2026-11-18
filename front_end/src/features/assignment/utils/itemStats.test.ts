import { describe, expect, it } from "vitest";

import { makeItemStats } from "../test/fixtures";
import { flagView, formatPercent, formatRatio, itemLabel, mcqRows, msUntilReady, saSummary, serverTimeToMs, tfRows } from "./itemStats";

describe("itemStats: thời gian máy chủ", () => {
    it("giờ máy chủ UTC+7 đổi đúng sang mốc tuyệt đối", () => {
        expect(serverTimeToMs("2026-11-20T17:02:00")).toBe(Date.UTC(2026, 10, 20, 10, 2, 0));
        expect(serverTimeToMs("2026-11-20T17:02:00.123456")).toBe(Date.UTC(2026, 10, 20, 10, 2, 0));
        expect(serverTimeToMs("2026-11-20T00:30")).toBe(Date.UTC(2026, 10, 19, 17, 30, 0));
    });

    it("chuỗi lạ hoặc rỗng → null", () => {
        expect(serverTimeToMs("hôm qua")).toBeNull();
        expect(serverTimeToMs(undefined)).toBeNull();
    });

    it("msUntilReady: chờ tới giờ cộng 1 giây, tối thiểu 3 giây, không đọc được thì 30 giây", () => {
        const at = Date.UTC(2026, 10, 20, 10, 2, 0);
        expect(msUntilReady("2026-11-20T17:02:00", at - 60_000)).toBe(61_000);
        expect(msUntilReady("2026-11-20T17:02:00", at + 60_000)).toBe(3000);
        expect(msUntilReady(undefined, at)).toBe(30_000);
    });
});

describe("itemStats: nhãn và số", () => {
    it("số câu theo phần, đánh số lại từ 1 mỗi phần", () => {
        expect(itemLabel("I", 1)).toBe("Phần I – Câu 1");
        expect(itemLabel("III", 4)).toBe("Phần III – Câu 4");
    });

    it("p, D theo kiểu Việt Nam; null → gạch ngang", () => {
        expect(formatRatio(0.5833)).toBe("0,58");
        expect(formatRatio(-0.1)).toBe("-0,10");
        expect(formatRatio(-0.001)).toBe("0,00");
        expect(formatRatio(null)).toBe("—");
        expect(formatPercent(0.5833)).toBe("58%");
    });
});

describe("itemStats: cờ → thẻ tiếng Việt (mã theo docs/contract/enums.md)", () => {
    it("các mã đã chốt", () => {
        expect(flagView("insufficient_sample", "mcq")).toMatchObject({ label: "Chưa đủ lượt", tone: "neutral" });
        expect(flagView("low_discrimination", "sa")).toMatchObject({ label: "Phân biệt kém", tone: "warn" });
        expect(flagView("unused_option: C", "mcq").label).toBe("Phương án C gần như không ai chọn");
        expect(flagView("key_suspect: B", "mcq")).toMatchObject({
            label: "Nghi đáp án sai (nhóm điểm cao chọn B)",
            tone: "danger",
        });
        expect(flagView("key_suspect: b", "tf").label).toBe("Nghi đáp án sai ở ý b");
        expect(flagView("key_suspect", "sa").label).toBe("Nghi đáp án sai");
    });

    it("mã lạ hiện nguyên mã, không ném lỗi", () => {
        expect(flagView("something_new: x", "mcq")).toMatchObject({ label: "something_new: x", tone: "neutral" });
    });
});

describe("itemStats: đọc choiceCounts", () => {
    const items = makeItemStats().items;

    it("mcq: bốn phương án và bỏ trống, đánh dấu đáp án đúng", () => {
        const rows = mcqRows(items[0]);
        expect(rows.map((r) => r.key)).toEqual(["A", "B", "C", "D", "blank"]);
        expect(rows.find((r) => r.correct)?.key).toBe("B");
        expect(rows[1]).toMatchObject({ all: 21, high: 6, low: 5 });
    });

    it("tf: nhãn từng ý lấy từ answerKey, p và D từng ý", () => {
        const rows = tfRows(items[1]);
        expect(rows.map((r) => r.label)).toEqual([true, false, false, true]);
        expect(rows[0]).toMatchObject({ t: 30, f: 6, correct: 30, p: 0.8333, d: 0.4 });
    });

    it("sa: số đúng, bỏ trống, đáp số sai hay gặp", () => {
        expect(saSummary(items[2])).toEqual({
            correct: 18,
            blank: 5,
            topWrong: [
                { value: "25", count: 6 },
                { value: "0,25", count: 3 },
            ],
        });
    });

    it("thiếu dữ liệu thì về 0 thay vì ném lỗi", () => {
        const broken = { ...items[0], choiceCounts: {} };
        expect(mcqRows(broken).every((r) => r.all === 0)).toBe(true);
        expect(saSummary({ ...items[2], choiceCounts: {} })).toEqual({ correct: 0, blank: 0, topWrong: [] });
    });
});
