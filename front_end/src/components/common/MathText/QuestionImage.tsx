import { useState } from "react";
import { FIGURE_COMPACT_TEXT, FIGURE_FALLBACK_TEXT, resolveImageUrl, widthPxFromMm } from "./questionImage.utils";
import type { QuestionImageView } from "./questionImage.types";
import "./QuestionImage.css";

export function FigureFallback() {
    return <span className="question-image-missing">{FIGURE_FALLBACK_TEXT}</span>;
}

export function FigureCompact() {
    return <span className="question-image-compact">{FIGURE_COMPACT_TEXT}</span>;
}

interface QuestionImageProps {
    image: QuestionImageView;
    /** Token đứng riêng một dòng: ảnh khối, căn giữa. */
    block?: boolean;
}

/** Ảnh của câu hỏi. Tải lỗi (thường do URL ký đã hết hạn) thì đổi sang chữ thay thế; trang tải lại dữ liệu sẽ có URL mới. */
export default function QuestionImage({ image, block = false }: QuestionImageProps) {
    const [failed, setFailed] = useState(false);
    if (failed) return <FigureFallback />;

    return (
        <span className={block ? "question-image question-image--block" : "question-image"}>
            <img
                src={resolveImageUrl(image.url)}
                alt="Hình minh họa"
                loading="lazy"
                style={{ width: widthPxFromMm(image.displayWidthMm), maxWidth: "100%", height: "auto" }}
                onError={() => setFailed(true)}
            />
        </span>
    );
}
