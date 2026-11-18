import { describe, expect, it } from "vitest";

import type { QcTraceResponse, QcVersion } from "../features/exam/types/qcTrace.types";
import { currentVersion, describeDedup, summarizeVersionQc } from "./qcTrace.utils";

function version(partial: Partial<QcVersion>): QcVersion {
    return { questionId: 1, kind: "llm", status: "qc_passed", current: false, ...partial };
}

describe("describeDedup (G22: không hiện độ giống, ngưỡng, mã câu)", () => {
    it("nói câu giống nằm ở đâu", () => {
        expect(describeDedup({ passed: true, similarity: 0.3, threshold: 0.9 })).toBe("Không trùng");
        expect(describeDedup({
            passed: false, similarity: 0.96, threshold: 0.9,
            comparedTo: { questionId: 338, inThisExam: true, inBank: false },
        })).toBe("Rất giống một câu trong đề này");
        expect(describeDedup({
            passed: false, similarity: 0.96, threshold: 0.9,
            comparedTo: { questionId: 338, inThisExam: false, inBank: true },
        })).toBe("Rất giống một câu trong ngân hàng");
        expect(describeDedup({ passed: false, similarity: null, threshold: null })).not.toMatch(/\d/);
    });
});

describe("summarizeVersionQc (G22)", () => {
    it("ba dòng Trình bày, Trùng câu khác, Tự giải lại", () => {
        const rows = summarizeVersionQc(version({
            qc: {
                format: { passed: true, errors: [], warnings: [], autoRepairs: [] },
                dedup: { passed: true, similarity: 0.2, threshold: 0.9 },
                answerCheck: { state: "inconsistent" },
            },
        }));
        expect(rows.map((r) => `${r.label}: ${r.value}`)).toEqual([
            "Trình bày: đạt",
            "Trùng câu khác: không",
            "Tự giải lại: ra đáp án khác",
        ]);
        expect(rows.map((r) => r.ok)).toEqual([true, true, false]);
    });

    it("bước chưa có kết quả ghi chưa kiểm tra", () => {
        const rows = summarizeVersionQc(version({ qc: null }));
        expect(rows.every((r) => r.value === "chưa kiểm tra" && r.ok === null)).toBe(true);
    });
});

describe("currentVersion", () => {
    const base = { examId: 1, slotId: 2, cellId: 3, slotNo: 1, part: "I", questionType: "mcq", state: "ready", actions: [] };

    it("ưu tiên bản đang dùng, không có thì lấy bản cuối của lần soạn cuối", () => {
        const withCurrent: QcTraceResponse = {
            ...base,
            lineages: [
                { origin: "generated", job: null, versions: [version({ questionId: 10, current: true })] },
                { origin: "generated", job: null, versions: [version({ questionId: 11 })] },
            ],
        };
        expect(currentVersion(withCurrent)?.questionId).toBe(10);

        const noCurrent: QcTraceResponse = {
            ...base,
            lineages: [{ origin: "generated", job: null, versions: [version({ questionId: 20 }), version({ questionId: 21 })] }],
        };
        expect(currentVersion(noCurrent)?.questionId).toBe(21);
        expect(currentVersion({ ...base, lineages: [] })).toBeNull();
    });
});
