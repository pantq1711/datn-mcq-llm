import { describe, expect, it } from "vitest";

import { MAX_NAME_LENGTH, datedName, formatDayMonth, nextAutoTitle, stripDateSuffix } from "./examName";

const NOV_28 = new Date(2026, 10, 28, 9, 30);

describe("stripDateSuffix", () => {
    it("bỏ một đuôi ngày ở cuối tên; giữ nguyên khi không có dấu nối hoặc tên chỉ có ngày", () => {
        expect(stripDateSuffix("Giữa kì I – Toán 8 – 28/11")).toBe("Giữa kì I – Toán 8");
        expect(stripDateSuffix("Giữa kì I - 27/11/2026")).toBe("Giữa kì I");
        expect(stripDateSuffix("Đề 28/11")).toBe("Đề 28/11");
        expect(stripDateSuffix("– 28/11")).toBe("– 28/11");
        expect(stripDateSuffix("   ")).toBe("");
    });
});

describe("datedName", () => {
    it("ghép ' – DD/MM' vào sau tên, có số 0 đầu", () => {
        expect(datedName("Giữa kì I – Toán 8", NOV_28)).toBe("Giữa kì I – Toán 8 – 28/11");
        expect(datedName("  Cuối kì I  ", new Date(2026, 8, 5))).toBe("Cuối kì I – 05/09");
        expect(formatDayMonth(new Date(2026, 0, 1))).toBe("01/01");
    });

    it("tên đã có đuôi ngày thì thay ngày cũ bằng ngày mới, không ghép thêm", () => {
        expect(datedName("Giữa kì I – Toán 8 – 27/11", NOV_28)).toBe("Giữa kì I – Toán 8 – 28/11");
        expect(datedName("Giữa kì I - 27/11/2026", NOV_28)).toBe("Giữa kì I – 28/11");
        expect(datedName("Giữa kì I — 3/9", NOV_28)).toBe("Giữa kì I – 28/11");
        expect(datedName("Giữa kì I – 28/11", NOV_28)).toBe("Giữa kì I – 28/11");
    });

    it("ngày không có dấu nối hoặc không ở cuối thì không phải đuôi ngày", () => {
        expect(datedName("Đề 27/11", NOV_28)).toBe("Đề 27/11 – 28/11");
        expect(datedName("Giữa kì I – 27/11 chiều", NOV_28)).toBe("Giữa kì I – 27/11 chiều – 28/11");
    });

    it("tên chỉ có ngày thì giữ nguyên tên làm phần gốc", () => {
        expect(datedName("– 27/11", NOV_28)).toBe("– 27/11 – 28/11");
    });

    it("cắt phần tên cho vừa 255 ký tự", () => {
        const name = datedName("a".repeat(300), NOV_28);
        expect(name).toHaveLength(MAX_NAME_LENGTH);
        expect(name.endsWith("a – 28/11")).toBe(true);
    });
});

describe("nextAutoTitle", () => {
    it("ô trống hoặc còn giá trị tự điền trước đó thì ghi tên mới", () => {
        expect(nextAutoTitle("", null, "B – 28/11")).toBe("B – 28/11");
        expect(nextAutoTitle("   ", "A – 28/11", "B – 28/11")).toBe("B – 28/11");
        expect(nextAutoTitle("A – 28/11", "A – 28/11", "B – 28/11")).toBe("B – 28/11");
    });

    it("giáo viên đã gõ tên khác thì không ghi đè", () => {
        expect(nextAutoTitle("Đề lớp 8A1", "A – 28/11", "B – 28/11")).toBe("Đề lớp 8A1");
        expect(nextAutoTitle("A – 28/11 sửa", "A – 28/11", "B – 28/11")).toBe("A – 28/11 sửa");
    });
});
