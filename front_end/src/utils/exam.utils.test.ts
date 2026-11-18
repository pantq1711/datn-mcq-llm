import { describe, expect, it } from "vitest";

import type { ExamQuestionItem } from "../features/exam/types/exam.types";
import {
    describeBuildProgress,
    describeFailureReason,
    describeFormatIssue,
    describeFormatIssueList,
    describeReviewReason,
    describeReviewReasonList,
    isBulkKeepCandidate,
    parseSimilarInExam,
} from "./exam.utils";

type Candidate = Pick<ExamQuestionItem, "state" | "qcState" | "needsReview" | "reviewReasons">;

describe("gộp cảnh báo định dạng cùng mẫu (G4)", () => {
    const unknown = (field: string) => `${field}: Lệnh \\foo không hỗ trợ`;

    it("6 cảnh báo ký hiệu lạ thành một câu, đủ A–D thì ghi cả 4 phương án", () => {
        const issues = ["question_text", "option_a", "option_b", "option_c", "option_d", "explanation"].map(unknown);
        expect(describeFormatIssueList(issues)).toEqual([
            "Công thức ở câu dẫn, cả 4 phương án và lời giải có ký hiệu lạ, có thể gõ sai. Thầy/cô xem lại công thức.",
        ]);
    });

    it("thiếu một phương án thì liệt kê từng chỗ theo thứ tự trong câu", () => {
        expect(describeFormatIssueList([unknown("option_c"), unknown("option_a")])).toEqual([
            "Công thức ở phương án A và phương án C có ký hiệu lạ, có thể gõ sai. Thầy/cô xem lại công thức.",
        ]);
    });

    it("hai mẫu khác nhau không gộp; một cảnh báo đứng riêng giữ câu cũ", () => {
        const list = describeFormatIssueList([unknown("option_a"), "question_text: đã tự đóng $"]);
        expect(list).toHaveLength(2);
        expect(describeFormatIssueList([unknown("explanation")])).toEqual([describeFormatIssue(unknown("explanation"))]);
    });

    it("danh sách lý do: gộp warning, giữ lý do khác và vị trí câu gần giống", () => {
        const entries = describeReviewReasonList([
            `warning: ${unknown("option_a")}`,
            "similar_in_exam: I.6",
            `warning: ${unknown("option_b")}`,
        ]);
        expect(entries.map((e) => e.text)).toEqual([
            "Công thức ở phương án A và phương án B có ký hiệu lạ, có thể gõ sai. Thầy/cô xem lại công thức.",
            describeReviewReason("similar_in_exam: I.6"),
        ]);
        expect(entries[1].position).toEqual({ part: "I", number: 6 });
    });
});

describe("describeBuildProgress (G18: không ước lượng số phút)", () => {
    const base = { examId: 1, total: 18, ready: 17, generating: 1, failed: 0, fromBank: 6 } as const;

    it("đang soạn chỉ nói số câu và lời cố định, không có số phút", () => {
        const view = describeBuildProgress({ ...base, status: "generating" });
        expect(view.text).toBe("Đang soạn 17/18 câu. Thầy/cô có thể rời trang, đề vẫn tiếp tục được soạn.");
        expect(view.text).not.toMatch(/phút/);
    });

    it("AI hết lượt trong ngày thì kèm notice của backend", () => {
        const view = describeBuildProgress({ ...base, status: "generating", notice: "Hết lượt AI hôm nay, hệ thống chạy tiếp lúc 07:00" });
        expect(view.type).toBe("warning");
        expect(view.text).toContain("Hết lượt AI hôm nay, hệ thống chạy tiếp lúc 07:00");
    });

    it("đã xong hoặc còn câu lỗi", () => {
        expect(describeBuildProgress({ ...base, status: "ready", ready: 18, generating: 0 }).text).toBe("Đã soạn xong 18/18 câu.");
        expect(describeBuildProgress({ ...base, status: "partial", generating: 0, failed: 1 }).text)
            .toBe("Đã soạn xong 17/18 câu · 1 câu chưa soạn được (xem bên dưới).");
    });
});

describe("describeFailureReason (G20)", () => {
    it("nói theo giáo viên, không nhắc AI hay số lần thử", () => {
        expect(describeFailureReason("llm_error"))
            .toBe("Chưa soạn được câu này vì hệ thống soạn câu đang bận. Thầy/cô bấm Soạn lại.");
        expect(describeFailureReason("qc_exhausted"))
            .toBe("Hệ thống chưa soạn được câu đạt yêu cầu. Thầy/cô bấm Soạn lại, có thể ghi thêm yêu cầu cho câu mới.");
        expect(describeFailureReason(null)).toBe("Chưa soạn được câu này. Thầy/cô bấm Soạn lại.");
        for (const reason of ["llm_error", "qc_exhausted", undefined]) {
            expect(describeFailureReason(reason)).not.toMatch(/AI|3 lần|'/);
        }
    });
});

describe("parseSimilarInExam (liên kết trong lý do gần giống, G5)", () => {
    it("dạng từ 2/11 trả Phần và số câu trong Phần", () => {
        expect(parseSimilarInExam("similar_in_exam: II.3")).toEqual({ part: "II", number: 3 });
        expect(parseSimilarInExam("similar_in_exam:I.12")).toEqual({ part: "I", number: 12 });
    });

    it("dạng cũ chỉ có số, mã lạ hoặc lý do khác trả null", () => {
        expect(parseSimilarInExam("similar_in_exam: 14")).toBeNull();
        expect(parseSimilarInExam("similar_in_exam: IV.2")).toBeNull();
        expect(parseSimilarInExam("solver_inconsistent")).toBeNull();
    });

    it("câu chữ của lý do giữ nguyên cách ghi theo Phần", () => {
        expect(describeReviewReason("similar_in_exam: I.6"))
            .toBe("Gần giống Phần I – Câu 6 trong đề, nên đổi một trong hai câu.");
        expect(describeReviewReason("similar_in_exam: 14"))
            .toBe("Gần giống câu 14 trong đề, nên đổi một trong hai câu.");
    });
});

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
