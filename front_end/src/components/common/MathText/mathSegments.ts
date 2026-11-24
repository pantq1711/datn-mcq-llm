export interface MathSegment {
    kind: "text" | "inline" | "display" | "image";
    /** text/inline/display: nội dung; image: id ảnh dạng chuỗi số, hoặc "" khi token hỏng. */
    value: string;
    /** Chỉ image: token đứng riêng một dòng (ảnh khối, căn giữa). */
    block?: boolean;
}

/** Token ảnh do bộ nhập Word tạo (docs/design/cau-co-hinh.md H2): ⟦img:<id>⟧. */
const IMAGE_TOKEN = /⟦img:(\d+)⟧/y;
/** Tiền tố ⟦img: không thành token hợp lệ (thiếu số, thiếu ⟧): nuốt đến ⟧ nếu có trên cùng dòng, để không lộ ký tự token. */
const BROKEN_IMAGE_TOKEN = /⟦img:[^⟧\n]*⟧?/y;

function isLineStart(text: string, index: number): boolean {
    let j = index - 1;
    while (j >= 0 && (text[j] === " " || text[j] === "\t")) j--;
    return j < 0 || text[j] === "\n";
}

function isLineEnd(text: string, index: number): boolean {
    let j = index;
    while (j < text.length && (text[j] === " " || text[j] === "\t")) j++;
    return j >= text.length || text[j] === "\n";
}

/**
 * Tách văn bản tiếng Việt xen LaTeX theo quy ước docs/api ("Các trường văn bản"):
 * `$...$` trong dòng, `$$...$$` riêng dòng, `\$` ngoài công thức là dấu đô la thường.
 * Dấu mở không có dấu đóng thì giữ nguyên như chữ.
 * Token ảnh `⟦img:<id>⟧` ngoài công thức thành đoạn `image` (khối nếu đứng riêng một dòng); tiền tố `⟦img:` hỏng thành đoạn
 * `image` có id rỗng. Chuỗi token không bao giờ nằm lại trong đoạn chữ.
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

        if (text[i] === "⟦" && text.startsWith("⟦img:", i)) {
            IMAGE_TOKEN.lastIndex = i;
            const valid = IMAGE_TOKEN.exec(text);
            if (valid) {
                const end = i + valid[0].length;
                const block = isLineStart(text, i) && isLineEnd(text, end);
                if (block) {
                    // Ảnh khối tự chiếm một dòng: bỏ dòng xuống và khoảng trắng kề hai bên để không sinh dòng trống
                    buffer = buffer.replace(/\n?[ \t]*$/, "");
                }
                pushText();
                segments.push({ kind: "image", value: valid[1], block });
                i = end;
                if (block) {
                    while (text[i] === " " || text[i] === "\t") i++;
                    if (text[i] === "\n") i++;
                }
                continue;
            }
            BROKEN_IMAGE_TOKEN.lastIndex = i;
            const broken = BROKEN_IMAGE_TOKEN.exec(text);
            pushText();
            segments.push({ kind: "image", value: "", block: false });
            i += broken ? broken[0].length : "⟦img:".length;
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

/**
 * Văn bản không có gì nhìn thấy được: rỗng, chỉ khoảng trắng, hoặc chỉ công thức rỗng như `$ $`.
 * Đoạn ảnh (kể cả token hỏng) tính là có nội dung vì luôn vẽ ra một ảnh hoặc chữ thay thế.
 */
export function isBlankMath(text?: string | null): boolean {
    return splitMath(text ?? "").every((segment) => segment.kind !== "image" && segment.value.trim() === "");
}
