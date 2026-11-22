import type { ImportBatch, ImportItem, QuestionImageInfo } from "../types/bankImport.types";

/** Dữ liệu tự dựng cho test, không lấy từ bộ đề thật. URL ảnh là URL giả có dạng của backend. */

export function makeImage(id: number, overrides: Partial<QuestionImageInfo> = {}): QuestionImageInfo {
    return {
        id,
        role: "stem",
        ordinal: 1,
        mime: "image/png",
        widthPx: 300,
        heightPx: 200,
        displayWidthMm: 60,
        url: `/api/question-images/${id}?exp=1&u=1&sig=aa${id}`,
        ...overrides,
    };
}

export function makeItem(overrides: Partial<ImportItem> = {}): ImportItem {
    return {
        key: "mcq-001",
        type: "mcq",
        locator: "Phần I, câu thứ 1",
        levelRaw: "NB",
        level: "easy",
        stem: "Hai cộng hai bằng mấy?",
        options: [
            { key: "A", text: "$3$" },
            { key: "B", text: "$4$" },
            { key: "C", text: "$5$" },
            { key: "D", text: "$6$" },
        ],
        correctOption: "B",
        statements: null,
        saAnswer: null,
        explanation: "Cộng hai số.",
        flags: [],
        blocking: false,
        blockReasons: [],
        ...overrides,
    };
}

export function makeBlockedItem(overrides: Partial<ImportItem> = {}): ImportItem {
    return makeItem({
        key: "mcq-002",
        locator: "Phần I, câu thứ 2",
        flags: ["answer_missing"],
        correctOption: null,
        blocking: true,
        blockReasons: ["Chưa thấy đáp án: tô vàng (highlight màu vàng) phương án đúng."],
        ...overrides,
    });
}

export function makeTfItem(overrides: Partial<ImportItem> = {}): ImportItem {
    return makeItem({
        key: "tf-001",
        type: "tf",
        locator: "Phần II, câu thứ 1",
        stem: "Cho hàm số $y = x^2$.",
        options: null,
        correctOption: null,
        statements: [
            { key: "a", text: "Hàm số đồng biến", label: true },
            { key: "b", text: "Hàm số nghịch biến", label: false },
            { key: "c", text: "Đồ thị đi qua gốc tọa độ", label: true },
            { key: "d", text: "Đồ thị là đường thẳng", label: false },
        ],
        ...overrides,
    });
}

export function makeFigureItem(overrides: Partial<ImportItem> = {}): ImportItem {
    return makeItem({
        key: "mcq-003",
        locator: "Phần I, câu thứ 3",
        stem: "Cho tam giác $ABC$ vuông tại $A$ như hình vẽ.\n⟦img:812⟧",
        flags: ["has_figure"],
        images: [makeImage(812)],
        ...overrides,
    });
}

export function makeBatch(overrides: Partial<ImportBatch> = {}): ImportBatch {
    const items = overrides.items ?? [makeItem(), makeBlockedItem(), makeFigureItem(), makeTfItem()];
    const blocked = items.filter((item) => item.blocking).length;
    return {
        id: 7,
        displayName: "Khối 8 - Chương 1 - Bài 2 - Hằng đẳng thức",
        status: "preview",
        subjectId: 1,
        gradeLevel: 8,
        topic: "Hằng đẳng thức",
        createdAt: "2026-11-23T08:00:00",
        expiresAt: "2026-11-24T08:00:00",
        confirmedAt: null,
        questionCount: items.length + 2,
        importableCount: items.length - blocked,
        blockedCount: blocked,
        importedCount: null,
        skipped: { has_image: 1, image_limit: 1 },
        skippedItems: [
            { locator: "Phần I, câu thứ 5", type: "mcq", reason: "has_image" },
            { locator: "Phần III, câu thứ 2", type: "sa", reason: "image_limit" },
        ],
        items,
        ...overrides,
    };
}

/** Tóm tắt như GET /api/bank-imports?status=preview: không có items. */
export function makeSummary(overrides: Partial<ImportBatch> = {}): ImportBatch {
    return makeBatch({ items: [], skippedItems: [], ...overrides, importableCount: overrides.importableCount ?? 10, blockedCount: overrides.blockedCount ?? 2 });
}
