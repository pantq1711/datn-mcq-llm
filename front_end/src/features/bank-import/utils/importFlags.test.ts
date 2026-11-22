import { describe, expect, it } from "vitest";
import { FIGURE_FLAGS, describeFlag, isBlockingFlag, splitItemFlags } from "./importFlags";
import { describeSkipReason, skippedEntries } from "./importSkipReasons";
import { makeItem } from "../test/fixtures";

describe("cờ của câu nhập", () => {
    it("bốn cờ hình có nhãn và lời giải thích đầy đủ", () => {
        expect(FIGURE_FLAGS).toEqual(["has_figure", "figure_from_previous_question", "mentions_figure_no_image", "shared_context"]);
        for (const flag of FIGURE_FLAGS) {
            const info = describeFlag(flag);
            expect(info.label.length).toBeGreaterThan(2);
            expect(info.hint).toBeTruthy();
        }
        expect(describeFlag("figure_from_previous_question").hint).toContain("nằm cạnh câu trước");
    });

    it("mọi cờ thông tin backend đang dùng đều có nhãn riêng (không rơi về mã)", () => {
        const known = [
            "level_default", "no_solution", "stem_extra_text", "duplicate_in_bank", "duplicate_in_batch",
            "latex_warning", "solution_latex_error", "formula_partial", "plain_math_vertalign",
            "hint_after_statements", "empty_formula",
        ];
        for (const flag of known) {
            expect(describeFlag(flag).label, flag).not.toBe(flag);
        }
    });

    it("cờ lạ vẫn hiện được: nhãn là chính mã, không ném lỗi", () => {
        expect(describeFlag("flag_moi_cua_backend")).toMatchObject({ label: "flag_moi_cua_backend" });
    });

    it("nhận ra cờ chặn như backend (tập cố định và tiền tố answer_, structure_)", () => {
        for (const flag of ["level_missing", "sa_answer_format", "latex_error", "leak_suspect", "answer_missing", "answer_ambiguous", "structure_options_not_found", "structure_statements_3", "structure_empty_field"]) {
            expect(isBlockingFlag(flag), flag).toBe(true);
        }
        for (const flag of ["has_figure", "level_default", "no_solution", "latex_warning"]) {
            expect(isBlockingFlag(flag), flag).toBe(false);
        }
    });

    it("tách cờ của câu: cờ hình riêng, cờ thông tin khác riêng, bỏ cờ chặn (đã có lý do bằng lời)", () => {
        const item = makeItem({
            flags: ["answer_missing", "has_figure", "level_default", "shared_context", "duplicate_in_bank"],
        });
        expect(splitItemFlags(item)).toEqual({
            figure: ["has_figure", "shared_context"],
            other: ["level_default", "duplicate_in_bank"],
        });
    });
});

describe("lý do bỏ câu", () => {
    it("năm lý do có nhãn bằng lời", () => {
        expect(describeSkipReason("has_image").label).toBe("Công thức dạng ảnh, chưa nhập được");
        for (const reason of ["formula_unsupported", "has_table", "image_limit", "image_convert_failed"]) {
            const info = describeSkipReason(reason);
            expect(info.label, reason).not.toBe(reason);
            expect(info.hint, reason).toBeTruthy();
        }
    });

    it("mã lạ hiện nguyên mã, không ném lỗi", () => {
        expect(describeSkipReason("ly_do_moi")).toEqual({ label: "ly_do_moi", hint: "" });
    });

    it("liệt kê theo thứ tự cố định, bỏ số 0, giữ mã lạ ở cuối", () => {
        const entries = skippedEntries({ image_limit: 2, ly_do_moi: 1, has_image: 3, has_table: 0 });
        expect(entries.map((entry) => [entry.reason, entry.count])).toEqual([
            ["has_image", 3],
            ["image_limit", 2],
            ["ly_do_moi", 1],
        ]);
    });

    it("thiếu hoặc rỗng thì không có dòng nào", () => {
        expect(skippedEntries(undefined)).toEqual([]);
        expect(skippedEntries({})).toEqual([]);
    });
});
