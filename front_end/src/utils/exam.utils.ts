import type { ExamKind, ExamQuestionDifficulty, ExamQuestionItem, ExamQuestionType, ExamStatus } from "../features/exam/types/exam.types";

export const difficultyConfig: Record<
    ExamQuestionDifficulty,
    {
        label: string;
        color: string;
    }
> = {
    easy: {
        label: "Biết",
        color: "green",
    },
    medium: {
        label: "Hiểu",
        color: "orange",
    },
    hard: {
        label: "Vận dụng",
        color: "red",
    },
};

// Lý do "cần xem kỹ" theo docs/api mục 7.4, 7.5a, 7.7a
const reviewReasonLabels: Record<string, string> = {
    solver_inconsistent: "Solver cho đáp án khác",
    solver_ambiguous: "Solver không kết luận được",
    solver_unparseable: "Không đọc được đáp số của Solver",
    statement_undecidable: "Có ý không xác định được Đúng/Sai",
    solver_not_run: "Solver chưa chạy hoặc bị lỗi",
    solver_pending: "Solver đang kiểm lại bản sửa",
    solver_failed: "Kiểm lại bản sửa thất bại",
    dedup_not_run: "Chưa kiểm tra trùng lặp",
    grounding_not_supported: "Không khớp tài liệu bài học",
    grounding_unknown: "Tài liệu không đủ để kết luận",
    grounding_pending: "Đang kiểm tra bám tài liệu",
    grounding_failed: "Kiểm tra bám tài liệu bị lỗi",
};

export const examKindConfig: Record<ExamKind, { label: string; color: string }> = {
    matrix: { label: "Theo ma trận", color: "blue" },
    lesson: { label: "Theo bài", color: "purple" },
    manual: { label: "Đề cũ", color: "default" },
};

export const examStatusConfig: Record<ExamStatus, { label: string; color: string }> = {
    draft: { label: "Bản nháp", color: "default" },
    confirmed: { label: "Đã xác nhận", color: "success" },
};

export function describeReviewReason(reason: string): string {
    if (reason.startsWith("warning:")) {
        return `Cảnh báo định dạng: ${reason.slice("warning:".length).trim()}`;
    }
    if (reason.startsWith("duplicate:")) {
        return `Trùng với câu #${reason.slice("duplicate:".length).trim()}`;
    }
    return reviewReasonLabels[reason] ?? reason;
}

export function resolveQuestionType(question: ExamQuestionItem): ExamQuestionType {
    if (question.questionType) return question.questionType;
    if (question.statements?.length) return "tf";
    if (!question.optionA && !question.optionB && !question.optionC && !question.optionD) return "sa";
    return "mcq";
}