import { useMemo } from "react";
import katex from "katex";
import { splitMath } from "./mathSegments";
import QuestionImage, { FigureCompact, FigureFallback } from "./QuestionImage";
import type { QuestionImageInfo } from "./questionImage.types";

interface MathTextProps {
    text?: string | null;
    /** Ảnh của câu (DTO `images`); token ⟦img:id⟧ trong `text` được thay bằng ảnh có đúng id. Không truyền thì token hiện chữ thay thế. */
    images?: QuestionImageInfo[] | null;
    /** Chỉ một dòng chữ (ô bảng): ảnh thành nhãn "[Hình]", không tải ảnh. */
    compact?: boolean;
}

/** Hiển thị văn bản có công thức LaTeX và ảnh của câu (token ⟦img:id⟧) (CSS KaTeX nạp một lần ở main.tsx). */
export default function MathText({ text, images, compact = false }: MathTextProps) {
    const rendered = useMemo(
        () =>
            splitMath(text ?? "").map((segment) =>
                segment.kind === "text" || segment.kind === "image"
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
            {rendered.map((segment, index) => {
                if (segment.kind === "image") {
                    const image = images?.find((candidate) => String(candidate.id) === segment.value);
                    if (!image) return <FigureFallback key={index} />;
                    if (compact) return <FigureCompact key={index} />;
                    // key theo url: URL mới sau khi tải lại dữ liệu thì thử tải ảnh lại, không giữ trạng thái lỗi cũ
                    return <QuestionImage key={`${index}:${image.url}`} image={image} block={segment.block} />;
                }
                return segment.kind === "text" ? (
                    // Docs cho phép xuống dòng thật ngoài công thức
                    <span key={index} style={{ whiteSpace: "pre-wrap" }}>
                        {segment.value}
                    </span>
                ) : (
                    <span key={index} dangerouslySetInnerHTML={{ __html: segment.value }} />
                );
            })}
        </>
    );
}
