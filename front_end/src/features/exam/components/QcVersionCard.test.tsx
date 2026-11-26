import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { QcVersionCard } from "./QcVersionCard";
import type { QcVersion } from "../types/qcTrace.types";
import { FIGURE_FALLBACK_TEXT } from "../../../components/common/MathText/questionImage.utils";

// Test khóa (docs/design/cau-co-hinh.md mục 4.3, bảng 4.5 dòng "(nhỏ)"): vết QC không trả chữ đề hay ảnh, nên nếu lời giải
// của Solver có token ảnh thì thẻ chỉ hiện chữ thay thế, không bao giờ hiện chuỗi token hay thẻ <img>.
const version = (solverReasoning: string): QcVersion => ({
    questionId: 41,
    kind: "llm",
    createdAt: "2026-11-23T10:00:00",
    status: "qc_passed",
    current: true,
    qc: {
        format: { passed: true, errors: [], warnings: [], autoRepairs: [] },
        dedup: null,
        answerCheck: { state: "consistent", solverReasoning },
    },
});

describe("QcVersionCard: token ảnh trong lời giải của Solver", () => {
    it("không có images thì hiện chữ thay thế, không lộ token, không tải ảnh", () => {
        const { container } = render(<QcVersionCard version={version("Xét hình vẽ:\n⟦img:812⟧\nVậy chọn B.")} isLast />);

        expect(screen.getAllByText(FIGURE_FALLBACK_TEXT).length).toBeGreaterThan(0);
        expect(container.textContent).not.toContain("⟦img:");
        expect(container.querySelector("img")).toBeNull();
    });
});
