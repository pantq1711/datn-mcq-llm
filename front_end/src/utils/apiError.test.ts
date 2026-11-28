import { describe, expect, it } from "vitest";

import { getApiErrorMessage, isLimitError, limitMessageLines } from "./apiError";

// Câu báo 429 GENERATION_QUEUE_LIMIT của backend từ đợt 3 (GenerationQuotaService.queueMessage): kèm số câu đang chờ,
// mức tối đa và số câu yêu cầu cần. Mã lỗi giữ nguyên nên frontend chỉ hiện nguyên câu, không tự viết lời.
const QUEUE_MESSAGE = "Thầy/cô đang có 58 câu chờ soạn (tối đa 60 câu cùng lúc). Yêu cầu này cần thêm 6 câu."
    + " Vui lòng đợi các đề hiện tại soạn xong rồi tạo tiếp.";

function queueError() {
    return { response: { status: 429, data: { code: "GENERATION_QUEUE_LIMIT", error: QUEUE_MESSAGE }, headers: {} } };
}

describe("lỗi hàng đợi sinh câu (GENERATION_QUEUE_LIMIT)", () => {
    it("là lỗi giới hạn và dùng nguyên câu của backend", () => {
        expect(isLimitError(queueError())).toBe(true);
        expect(getApiErrorMessage(queueError(), "dự phòng")).toBe(QUEUE_MESSAGE);
    });

    it("hộp lỗi tách mỗi câu một dòng, giữ đủ số liệu", () => {
        expect(limitMessageLines("GENERATION_QUEUE_LIMIT", QUEUE_MESSAGE)).toEqual([
            "Thầy/cô đang có 58 câu chờ soạn (tối đa 60 câu cùng lúc).",
            "Yêu cầu này cần thêm 6 câu.",
            "Vui lòng đợi các đề hiện tại soạn xong rồi tạo tiếp.",
        ]);
    });

    it("câu cũ (backend trước đợt 3) vẫn hiện được", () => {
        const old = "Thầy/cô đang có quá nhiều câu chờ soạn. Vui lòng đợi các đề hiện tại soạn xong rồi tạo tiếp.";
        expect(limitMessageLines("GENERATION_QUEUE_LIMIT", old)).toEqual([
            "Thầy/cô đang có quá nhiều câu chờ soạn.",
            "Vui lòng đợi các đề hiện tại soạn xong rồi tạo tiếp.",
        ]);
    });
});
