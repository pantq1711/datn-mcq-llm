import { describe, expect, it } from "vitest";

import { makeAttemptView, makeNameAttemptView } from "../test/fixtures";
import { identityLine, isNameMode, normalizeStudentName } from "./identity";

describe("identity (quyết định 25/11)", () => {
    it("chuẩn hóa họ tên như backend: NFC, bỏ khoảng trắng đầu cuối, gộp khoảng trắng, 2–60 ký tự", () => {
        expect(normalizeStudentName("  Nguyễn   Văn An\t")).toBe("Nguyễn Văn An");
        expect(normalizeStudentName("Ơn")).toBe("Ơn");
        expect(normalizeStudentName("Ữ".repeat(60))).toBe("Ữ".repeat(60));
        for (const bad of ["", "   ", " A ", "Ữ".repeat(61), "Lan\u0007Anh", "Lan​Anh"]) {
            expect(normalizeStudentName(bad)).toBeNull();
        }
    });

    it("chế độ: thiếu identityMode (máy chủ cũ) là số thứ tự", () => {
        expect(isNameMode("name")).toBe(true);
        expect(isNameMode("seat")).toBe(false);
        expect(isNameMode(undefined)).toBe(false);
    });

    it("dòng danh tính: số thứ tự hoặc họ tên kèm lớp", () => {
        expect(identityLine(makeAttemptView())).toBe("Lớp 8A1, số 12");
        expect(identityLine(makeNameAttemptView())).toBe("Nguyễn Văn An, lớp 8A1");
    });
});
