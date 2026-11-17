import katex from "katex";

/**
 * Các hàm thuần cho ô nhập có công thức của hộp Sửa câu. Quy ước tách công thức giống `splitMath` của MathText
 * (docs/api "Các trường văn bản"): `$...$` trong dòng, `$$...$$` riêng dòng, `\$` ngoài công thức là dấu đô la thường,
 * trong công thức `\x` là một ký tự đã escape.
 */

interface MathRange {
    /** Vị trí ký tự đầu của phần công thức (sau dấu mở). */
    contentStart: number;
    /** Vị trí dấu đóng; bằng độ dài chuỗi khi chưa có dấu đóng. */
    contentEnd: number;
    display: boolean;
    closed: boolean;
}

function scanMath(text: string): MathRange[] {
    const ranges: MathRange[] = [];
    let i = 0;
    while (i < text.length) {
        if (text[i] === "\\" && text[i + 1] === "$") {
            i += 2;
            continue;
        }
        if (text[i] !== "$") {
            i++;
            continue;
        }
        const display = text[i + 1] === "$";
        const open = display ? 2 : 1;
        const contentStart = i + open;
        let close = -1;
        for (let j = contentStart; j < text.length; j++) {
            if (text[j] === "\\") {
                j++;
                continue;
            }
            if (text[j] === "$" && (!display || text[j + 1] === "$")) {
                close = j;
                break;
            }
        }
        if (close === -1) {
            ranges.push({ contentStart, contentEnd: text.length, display, closed: false });
            break;
        }
        ranges.push({ contentStart, contentEnd: close, display, closed: true });
        i = close + open;
    }
    return ranges;
}

/** Có dấu `$` mở mà không có dấu đóng (MathText sẽ hiện phần đó như chữ thường, backend tự đóng có thể sai chỗ). */
export function hasUnclosedMath(text?: string | null): boolean {
    return scanMath(text ?? "").some((r) => !r.closed);
}

export const UNCLOSED_MATH_MESSAGE =
    "Công thức còn thiếu dấu $ đóng. Thầy/cô thêm dấu $ vào cuối công thức.";

/** Luật của Form: thiếu dấu `$` đóng thì báo đỏ dưới ô và không cho lưu (rà soát G10). */
export const unclosedMathRule = {
    validator: (_: unknown, value?: string) =>
        hasUnclosedMath(value) ? Promise.reject(new Error(UNCLOSED_MATH_MESSAGE)) : Promise.resolve(),
};

/** Con trỏ ở vị trí `pos` có đang nằm trong một công thức (kể cả công thức chưa đóng)? */
export function isInsideMath(text: string, pos: number): boolean {
    return scanMath(text).some((r) => pos >= r.contentStart && pos <= r.contentEnd);
}

/** Có công thức đã đóng mà KaTeX không vẽ được (lệnh gõ sai, thiếu ngoặc...)? Chỉ để nhắc, không chặn lưu. */
export function hasFormulaSyntaxError(text?: string | null): boolean {
    const source = text ?? "";
    return scanMath(source)
        .filter((r) => r.closed)
        .some((r) => {
            try {
                katex.renderToString(source.slice(r.contentStart, r.contentEnd), {
                    throwOnError: true,
                    displayMode: r.display,
                    strict: "ignore",
                });
                return false;
            } catch {
                return true;
            }
        });
}

/** Có công thức trong chuỗi không: chỉ khi đó mới cần dòng xem trước. */
export function containsMath(text?: string | null): boolean {
    return scanMath(text ?? "").length > 0;
}

export interface MathSnippet {
    /** Chữ trên nút. */
    label: string;
    /** Lời mô tả cho tooltip và trình đọc màn hình. */
    title: string;
    /** Lệnh LaTeX chèn vào; `{}` đầu tiên là chỗ con trỏ đứng sau khi chèn. */
    tex: string;
}

export const MATH_SNIPPETS: MathSnippet[] = [
    { label: "a/b", title: "Chèn phân số", tex: "\\frac{}{}" },
    { label: "√", title: "Chèn căn bậc hai", tex: "\\sqrt{}" },
    { label: "∛", title: "Chèn căn bậc ba", tex: "\\sqrt[3]{}" },
    { label: "x²", title: "Chèn số mũ", tex: "^{}" },
    { label: "x₁", title: "Chèn chỉ số dưới", tex: "_{}" },
    { label: "≤", title: "Chèn dấu nhỏ hơn hoặc bằng", tex: "\\le " },
    { label: "≥", title: "Chèn dấu lớn hơn hoặc bằng", tex: "\\ge " },
    { label: "≠", title: "Chèn dấu khác", tex: "\\ne " },
    { label: "±", title: "Chèn dấu cộng trừ", tex: "\\pm " },
    { label: "×", title: "Chèn dấu nhân", tex: "\\times " },
    { label: "°", title: "Chèn kí hiệu độ", tex: "^\\circ" },
    { label: "∠", title: "Chèn kí hiệu góc (gõ tên góc vào giữa)", tex: "\\widehat{}" },
    { label: "⊥", title: "Chèn dấu vuông góc", tex: "\\perp " },
    { label: "∥", title: "Chèn dấu song song", tex: "\\parallel " },
    { label: "△", title: "Chèn kí hiệu tam giác", tex: "\\triangle " },
    { label: "π", title: "Chèn số pi", tex: "\\pi " },
];

/**
 * Chèn một mẫu công thức vào vị trí con trỏ. Con trỏ ở ngoài công thức thì tự bọc `$...$`. Có chữ đang được chọn và
 * mẫu có chỗ trống `{}` thì chữ đó vào chỗ trống đầu tiên (chọn "1" rồi bấm phân số được `\frac{1}{}`).
 * Trả về chuỗi mới và vị trí con trỏ sau khi chèn.
 */
export function insertSnippet(
    text: string,
    selectionStart: number,
    selectionEnd: number,
    tex: string
): { text: string; caret: number } {
    const selected = text.slice(selectionStart, selectionEnd);
    let body = tex;
    const slot = body.indexOf("{}");
    let caret: number;
    if (slot >= 0) {
        body = body.slice(0, slot + 1) + selected + body.slice(slot + 1);
        const filled = slot + 1 + selected.length;
        // Đã điền chỗ trống thứ nhất thì con trỏ nhảy sang chỗ trống kế tiếp (mẫu số của phân số)
        const next = selected ? body.indexOf("{}", filled) : -1;
        caret = next >= 0 ? next + 1 : filled;
    } else {
        // Kí hiệu không có chỗ trống (≤, π...): giữ chữ đang chọn, chèn kí hiệu ngay sau
        body = selected + body;
        caret = body.length;
    }
    if (!isInsideMath(text, selectionStart)) {
        body = `$${body}$`;
        caret += 1;
    }
    return {
        text: text.slice(0, selectionStart) + body + text.slice(selectionEnd),
        caret: selectionStart + caret,
    };
}
