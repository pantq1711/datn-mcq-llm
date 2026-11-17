import { useRef } from "react";
import { Tooltip, Typography } from "antd";

import "./ExamQuestionNav.css";
import type { ExamQuestionItem } from "../types/exam.types";
import { navStatusOf, type ExamNavStatus as NavStatus } from "../../../utils/exam.utils";

const { Text } = Typography;

const STATUS_LABELS: Record<NavStatus, string> = {
    kept: "Đã lưu",
    review: "Cần xem kỹ",
    failed: "Chưa soạn được",
    generating: "Đang soạn",
    unreviewed: "Chưa lưu",
};

const STATUS_MARKS: Partial<Record<NavStatus, string>> = {
    review: "⚠",
    failed: "!",
};

export interface ExamQuestionNavPart {
    part: string;
    questions: ExamQuestionItem[];
}

interface ExamQuestionNavProps {
    parts: ExamQuestionNavPart[];
    getNumber: (question: ExamQuestionItem, index: number) => number;
    onJump: (question: ExamQuestionItem) => void;
}

/** Mục lục câu cố định bên trái trang đề: mỗi câu một ô màu theo trạng thái, bấm để cuộn tới câu. */
export default function ExamQuestionNav({ parts, getNumber, onJump }: ExamQuestionNavProps) {
    // Tooltip gắn vào khung bao mục lục (nằm trong cột dính) thay vì body, nên đứng yên cùng mục lục khi trang cuộn.
    // Không gắn vào <nav> vì nav có overflow (cuộn dọc) sẽ cắt mất tooltip.
    const rootRef = useRef<HTMLDivElement>(null);
    const getPopupContainer = () => rootRef.current ?? document.body;

    return (
        <div ref={rootRef} className="exam-question-nav-root">
        <nav className="exam-question-nav" aria-label="Mục lục câu hỏi">
            <Text strong className="exam-question-nav-title">Mục lục</Text>

            {parts.map((p) => (
                <div key={p.part} className="exam-question-nav-part">
                    <Text type="secondary" className="exam-question-nav-part-title">Phần {p.part}</Text>
                    <div className="exam-question-nav-grid">
                        {p.questions.map((question, index) => {
                            const status = navStatusOf(question);
                            const number = getNumber(question, index);
                            return (
                                <Tooltip
                                    key={question.slotId ?? question.id}
                                    title={`Phần ${p.part} – Câu ${number}: ${STATUS_LABELS[status]}`}
                                    getPopupContainer={getPopupContainer}
                                >
                                    <button
                                        type="button"
                                        className={`exam-question-nav-cell exam-question-nav-${status}`}
                                        aria-label={`Phần ${p.part} – Câu ${number}: ${STATUS_LABELS[status]}`}
                                        onClick={() => onJump(question)}
                                    >
                                        {STATUS_MARKS[status] && (
                                            <span className="exam-question-nav-mark" aria-hidden>{STATUS_MARKS[status]}</span>
                                        )}
                                        {number}
                                    </button>
                                </Tooltip>
                            );
                        })}
                    </div>
                </div>
            ))}

            <ul className="exam-question-nav-legend">
                {(["kept", "review", "failed", "generating", "unreviewed"] as NavStatus[]).map((status) => (
                    <li key={status}>
                        <span className={`exam-question-nav-swatch exam-question-nav-${status}`} aria-hidden />
                        {STATUS_LABELS[status]}
                    </li>
                ))}
            </ul>
        </nav>
        </div>
    );
}
