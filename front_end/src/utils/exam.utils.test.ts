import { describe, expect, it } from "vitest";

import type { ExamQuestionItem } from "../features/exam/types/exam.types";
import { isBulkKeepCandidate } from "./exam.utils";

type Candidate = Pick<ExamQuestionItem, "state" | "qcState" | "needsReview" | "reviewReasons">;

describe("isBulkKeepCandidate (Lưu hàng loạt, CT-7)", () => {
    const clean: Candidate = { state: "ready", qcState: "passed", needsReview: false, reviewReasons: [] };

    it("câu đã soạn xong, chưa lưu, không cờ thì được lưu", () => {
        expect(isBulkKeepCandidate({ ...clean })).toBe(true);
        // Câu lấy từ ngân hàng không có qcState
        expect(isBulkKeepCandidate({ state: "ready", needsReview: false })).toBe(true);
    });

    it("câu có lý do cần xem kỹ thì không lưu, kể cả chỉ có similar_in_exam", () => {
        expect(isBulkKeepCandidate({ ...clean, needsReview: true, reviewReasons: ["solver_inconsistent"] })).toBe(false);
        expect(isBulkKeepCandidate({ ...clean, needsReview: true, reviewReasons: ["similar_in_exam: I.3"] })).toBe(false);
    });

    it("câu đang tự kiểm tra lại bản sửa thì không lưu", () => {
        expect(isBulkKeepCandidate({ ...clean, qcState: "checking" })).toBe(false);
    });

    it("chỗ đã lưu, đang soạn, chưa soạn được thì không lưu", () => {
        for (const state of ["kept", "edited", "pending", "generating", "failed"] as const) {
            expect(isBulkKeepCandidate({ ...clean, state })).toBe(false);
        }
    });
});
