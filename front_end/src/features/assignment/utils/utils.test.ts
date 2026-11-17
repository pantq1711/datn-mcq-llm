import { describe, expect, it, vi } from "vitest";

import { makeAttemptView } from "../test/fixtures";
import { buildSubmitPayload, countAnswered, looksLikeThousandsDot, sanitizeSaInput } from "./answers";
import { clearSession, emptyDraft, loadDraft, loadSession, saveDraft, saveSession } from "./attemptStorage";
import { isValidClassLabel, normalizeClassLabel, validateClassRows } from "./classInput";
import { isExamHasAssignmentsError } from "./examDeleteError";
import { formatCountdown, formatServerTime } from "./timeFormat";

describe("answers", () => {
    it("ô đáp số chỉ giữ chữ số , . - và tối đa 10 ký tự", () => {
        expect(sanitizeSaInput("a1b,5 cm")).toBe("1,5");
        expect(sanitizeSaInput("−3")).toBe("-3");
        expect(sanitizeSaInput("1234567890123")).toBe("1234567890");
    });

    it("nhận ra dạng 1.234 nhưng không báo 0.125 hay 12.5", () => {
        expect(looksLikeThousandsDot("1.234")).toBe(true);
        expect(looksLikeThousandsDot("-12.345")).toBe(true);
        expect(looksLikeThousandsDot("0.125")).toBe(false);
        expect(looksLikeThousandsDot("12.5")).toBe(false);
        expect(looksLikeThousandsDot("1,234")).toBe(false);
    });

    it("chỉ gửi câu đã làm; ý tf bỏ trống không gửi; bản nháp cũ có slot lạ bị bỏ", () => {
        const view = makeAttemptView();
        const draft = {
            mcq: { "101": "C", "999": "A" },
            tf: { "102": { a: "F" as const } },
            sa: { "103": "  " },
        };
        expect(buildSubmitPayload(view.parts, draft)).toEqual([
            { slotId: 101, choice: "C" },
            { slotId: 102, tf: { a: "F" } },
        ]);
        expect(countAnswered(view.parts, draft)).toBe(2);
    });
});

describe("attemptStorage", () => {
    it("lưu và đọc lại token, câu trả lời; xóa phiên xóa cả hai", () => {
        saveSession("X", { token: "t", classLabel: "8A1", seatNo: 3 });
        saveDraft("X", { mcq: { "1": "A" }, tf: {}, sa: {} });
        expect(loadSession("X")).toEqual({ token: "t", classLabel: "8A1", seatNo: 3 });
        expect(loadDraft("X").mcq).toEqual({ "1": "A" });

        clearSession("X");
        expect(loadSession("X")).toBeNull();
        expect(loadDraft("X")).toEqual(emptyDraft());
    });

    it("localStorage ném lỗi thì không làm hỏng trang", () => {
        const boom = () => {
            throw new Error("blocked");
        };
        const get = vi.spyOn(Storage.prototype, "getItem").mockImplementation(boom);
        const set = vi.spyOn(Storage.prototype, "setItem").mockImplementation(boom);
        const remove = vi.spyOn(Storage.prototype, "removeItem").mockImplementation(boom);
        try {
            expect(loadSession("X")).toBeNull();
            expect(loadDraft("X")).toEqual(emptyDraft());
            expect(saveSession("X", { token: "t", classLabel: "8A1", seatNo: 3 })).toBe(false);
            expect(() => saveDraft("X", emptyDraft())).not.toThrow();
            expect(() => clearSession("X")).not.toThrow();
        } finally {
            get.mockRestore();
            set.mockRestore();
            remove.mockRestore();
        }
    });

    it("dữ liệu hỏng trong localStorage coi như không có", () => {
        localStorage.setItem("online-attempt:X", "{không phải json");
        localStorage.setItem("online-answers:X", "[1,2");
        expect(loadSession("X")).toBeNull();
        expect(loadDraft("X")).toEqual(emptyDraft());
    });
});

describe("classInput", () => {
    it("chuẩn hóa nhãn lớp như backend", () => {
        expect(normalizeClassLabel(" 8a 1 ")).toBe("8A1");
        expect(isValidClassLabel("8A1")).toBe(true);
        expect(isValidClassLabel("10/3")).toBe(true);
        expect(isValidClassLabel("LỚP8")).toBe(false);
        expect(isValidClassLabel("ABCDEFGHIJK")).toBe(false);
    });

    it("kiểm danh sách lớp: trống, trùng, sĩ số ngoài 1–99, quá 10 lớp", () => {
        expect(validateClassRows([])).toMatch(/ít nhất một lớp/);
        expect(validateClassRows([{ classLabel: "8A1", size: 35 }])).toBeUndefined();
        expect(validateClassRows([{ classLabel: "8a1", size: 35 }, { classLabel: "8 A1", size: 30 }])).toMatch(/bị nhập hai lần/);
        expect(validateClassRows([{ classLabel: "8A1", size: 0 }])).toMatch(/Sĩ số lớp 8A1/);
        expect(validateClassRows([{ classLabel: "8A1", size: 100 }])).toMatch(/Sĩ số lớp 8A1/);
        expect(validateClassRows([{ classLabel: "", size: 30 }])).toMatch(/cần có tên/);
        const many = Array.from({ length: 11 }, (_, i) => ({ classLabel: `L${i}`, size: 10 }));
        expect(validateClassRows(many)).toMatch(/Tối đa 10 lớp/);
    });
});

describe("examDeleteError", () => {
    it("nhận ra 409 EXAM_HAS_ASSIGNMENTS kể cả khi bị bọc trong Error.cause", () => {
        const axiosLike = { response: { status: 409, data: { code: "EXAM_HAS_ASSIGNMENTS" } } };
        expect(isExamHasAssignmentsError(axiosLike)).toBe(true);
        expect(isExamHasAssignmentsError(new Error("x", { cause: axiosLike }))).toBe(true);
        expect(isExamHasAssignmentsError(new Error("x", { cause: { response: { status: 409, data: { code: "EXAM_GENERATING" } } } }))).toBe(false);
        expect(isExamHasAssignmentsError(new Error("x"))).toBe(false);
    });
});

describe("timeFormat", () => {
    it("hiển thị giờ máy chủ đúng chữ, không đổi múi giờ", () => {
        expect(formatServerTime("2026-11-20T17:02:00")).toBe("17:02 ngày 20/11/2026");
        expect(formatServerTime(undefined)).toBe("");
    });

    it("đồng hồ đếm ngược", () => {
        expect(formatCountdown(45 * 60)).toBe("45:00");
        expect(formatCountdown(307)).toBe("5:07");
        expect(formatCountdown(3905)).toBe("1:05:05");
        expect(formatCountdown(-4)).toBe("0:00");
    });
});
