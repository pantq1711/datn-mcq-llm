import { useMemo } from "react";
import katex from "katex";

interface MathSegment {
    kind: "text" | "inline" | "display";
    value: string;
}

/**
 * Tách văn bản tiếng Việt xen LaTeX theo quy ước docs/api ("Các trường văn bản"):
 * `$...$` trong dòng, `$$...$$` riêng dòng, `\$` ngoài công thức là dấu đô la thường.
 * Dấu mở không có dấu đóng thì giữ nguyên như chữ.
 */
export function splitMath(text: string): MathSegment[] {
    const segments: MathSegment[] = [];
    let buffer = "";
    let i = 0;

    const pushText = () => {
        if (buffer) segments.push({ kind: "text", value: buffer });
        buffer = "";
    };

    while (i < text.length) {
        if (text[i] === "\\" && text[i + 1] === "$") {
            buffer += "$";
            i += 2;
            continue;
        }

        if (text[i] === "$") {
            const display = text[i + 1] === "$";
            const open = display ? 2 : 1;
            const close = findClosing(text, i + open, display);
            if (close === -1) {
                // Không có dấu đóng: phần còn lại là chữ thường
                buffer += text.slice(i);
                break;
            }
            pushText();
            segments.push({ kind: display ? "display" : "inline", value: text.slice(i + open, close) });
            i = close + open;
            continue;
        }

        buffer += text[i];
        i++;
    }

    pushText();
    return segments;
}

/** Vị trí dấu đóng `$` (hoặc `$$`) đầu tiên không đứng sau `\`, -1 nếu không có. */
function findClosing(text: string, from: number, display: boolean): number {
    for (let j = from; j < text.length; j++) {
        if (text[j] === "\\") {
            j++; // bỏ qua ký tự được escape trong công thức, ví dụ `\$` hay `\\`
            continue;
        }
        if (text[j] === "$" && (!display || text[j + 1] === "$")) return j;
    }
    return -1;
}

interface MathTextProps {
    text?: string | null;
}

/** Hiển thị văn bản có công thức LaTeX bằng KaTeX (CSS nạp một lần ở main.tsx). */
export default function MathText({ text }: MathTextProps) {
    const rendered = useMemo(
        () =>
            splitMath(text ?? "").map((segment) =>
                segment.kind === "text"
                    ? segment
                    : {
                        ...segment,
                        // throwOnError false: LaTeX lỗi chỉ hiện đỏ tại chỗ, không làm hỏng cả câu
                        value: katex.renderToString(segment.value, {
                            throwOnError: false,
                            displayMode: segment.kind === "display",
                        }),
                    }
            ),
        [text]
    );

    return (
        <>
            {rendered.map((segment, index) =>
                segment.kind === "text" ? (
                    // Docs cho phép xuống dòng thật ngoài công thức
                    <span key={index} style={{ whiteSpace: "pre-wrap" }}>
                        {segment.value}
                    </span>
                ) : (
                    <span key={index} dangerouslySetInnerHTML={{ __html: segment.value }} />
                )
            )}
        </>
    );
}
