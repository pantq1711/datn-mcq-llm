import type { ImportItem } from "../types/bankImport.types";

/** Cờ liên quan hình: giáo viên cần soát hình có đúng câu không (docs/design/cau-co-hinh.md H10). Luôn hiện ngay trên thẻ. */
export const FIGURE_FLAGS = ["has_figure", "figure_from_previous_question", "mentions_figure_no_image", "shared_context"] as const;

export type FlagTone = "figure" | "warn" | "info";

export interface FlagInfo {
    label: string;
    hint?: string;
    tone: FlagTone;
}

// Chữ do giao diện đặt (máy chủ chỉ có lý do bằng lời cho cờ chặn). Danh sách cờ: docs/contract/enums.md mục 3.
const FLAG_INFO: Record<string, FlagInfo> = {
    has_figure: { label: "Có hình", tone: "figure", hint: "Hình vẽ của câu đã được nhập kèm." },
    figure_from_previous_question: {
        label: "Hình từ câu trước",
        tone: "warn",
        hint: "Hình này nằm cạnh câu trước trong file Word và được chuyển sang câu này; hãy kiểm tra hình có đúng câu không.",
    },
    mentions_figure_no_image: {
        label: "Nhắc hình nhưng không có hình",
        tone: "warn",
        hint: "Đề có nhắc đến hình nhưng không thấy hình kèm theo trong file; hãy kiểm tra câu trước khi nhập.",
    },
    shared_context: {
        label: "Có ngữ cảnh chung",
        tone: "warn",
        hint: "Câu nhận đoạn, hình hoặc bảng ngữ cảnh dùng chung của Phần III trong file; hãy kiểm tra có đúng câu không.",
    },
    level_default: { label: "Mức mặc định", tone: "info", hint: "Câu chưa ghi mức trong file, nhận mức thầy/cô chọn lúc tải lên." },
    no_solution: { label: "Chưa có lời giải", tone: "info", hint: "File không có lời giải cho câu này." },
    stem_extra_text: { label: "Đã bỏ chữ thừa", tone: "info", hint: "Đã bỏ chữ giữ chỗ hoặc câu phụ a/ b/ thừa khỏi đề dẫn." },
    duplicate_in_bank: { label: "Giống câu trong ngân hàng", tone: "info", hint: "Câu giống một câu đã có trong ngân hàng của thầy/cô." },
    duplicate_in_batch: { label: "Giống câu khác trong file", tone: "info", hint: "Câu giống một câu khác trong cùng file." },
    latex_warning: { label: "Công thức cần xem lại", tone: "info", hint: "Công thức có điểm đáng ngờ nhưng vẫn đọc được." },
    solution_latex_error: { label: "Lỗi công thức ở lời giải", tone: "info", hint: "Công thức trong lời giải bị lỗi; đề và đáp án không bị ảnh hưởng." },
    formula_partial: { label: "Công thức chuyển một phần", tone: "info", hint: "Một phần công thức chưa chuyển được, hãy xem lại." },
    plain_math_vertalign: { label: "Công thức viết bằng chữ", tone: "info", hint: "Có công thức viết dạng chữ thường, hãy xem lại cách hiển thị." },
    hint_after_statements: { label: "Có dòng gợi ý sau các ý", tone: "info", hint: "Dòng gợi ý đứng sau các ý đã được tách riêng." },
    empty_formula: { label: "Có công thức rỗng", tone: "info", hint: "Có công thức trống trong câu, hãy xem lại." },
};

/** Nhãn của một cờ; cờ lạ (backend thêm sau) hiện nguyên mã, không ném lỗi. */
export function describeFlag(flag: string): FlagInfo {
    return FLAG_INFO[flag] ?? { label: flag, tone: "info" };
}

const BLOCKING_FLAGS = new Set(["level_missing", "sa_answer_format", "latex_error", "leak_suspect"]);

/** Giống ImportFlags.isBlocking của backend: tập cố định cùng mọi cờ answer_*, structure_*. */
export function isBlockingFlag(flag: string): boolean {
    return BLOCKING_FLAGS.has(flag) || flag.startsWith("answer_") || flag.startsWith("structure_");
}

/** Cờ không chặn của câu: cờ hình riêng (hiện nổi bật), cờ thông tin khác riêng. Cờ chặn đã có lý do bằng lời. */
export function splitItemFlags(item: Pick<ImportItem, "flags">): { figure: string[]; other: string[] } {
    const flags = (item.flags ?? []).filter((flag) => !isBlockingFlag(flag));
    const isFigure = (flag: string) => (FIGURE_FLAGS as readonly string[]).includes(flag);
    return { figure: flags.filter(isFigure), other: flags.filter((flag) => !isFigure(flag)) };
}
