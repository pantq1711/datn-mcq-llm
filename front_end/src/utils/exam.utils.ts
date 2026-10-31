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

const EXPLANATION_DRAFT_REASON = "Lời giải có thể còn phần nháp, thầy/cô xem lại lời giải.";

// Lý do "cần xem kỹ": đủ danh sách mã ở docs/contract/enums.md (mục 3) và docs/api mục 7.4, 7.5a, 7.7a
const reviewReasonLabels: Record<string, string> = {
    solver_inconsistent: "Hệ thống tự giải lại và ra đáp án khác. Thầy/cô kiểm tra lại đáp án.",
    solver_multiple_correct: "Có thể có hơn một phương án đúng. Thầy/cô kiểm tra lại.",
    solver_no_correct: "Hệ thống không tìm được phương án đúng. Thầy/cô kiểm tra lại.",
    solver_ambiguous: "Đề có thể hiểu theo nhiều cách. Thầy/cô kiểm tra lại đề.",
    solver_unparseable: "Hệ thống tự giải ra đáp số không so sánh được. Thầy/cô kiểm tra lại đáp số.",
    statement_undecidable: "Có ý hệ thống không xác định được đúng hay sai. Thầy/cô kiểm tra lại các ý.",
    solver_pending: "Đang tự kiểm tra lại đáp án…",
    solver_failed: "Chưa tự kiểm tra được đáp án, thầy/cô tự kiểm tra.",
    solver_not_run: "Chưa tự kiểm tra được đáp án, thầy/cô tự kiểm tra.",
    dedup_not_run: "Chưa kiểm tra được câu này có trùng câu khác không.",
    dedup_not_concluded: "Chưa kiểm tra được câu này có trùng câu khác không.",
    explanation_draft_suspect: EXPLANATION_DRAFT_REASON,
    grounding_not_supported: "Đáp án hoặc lời giải không khớp tài liệu đính kèm. Thầy/cô kiểm tra lại.",
    grounding_unknown: "Nội dung câu không có trong tài liệu đính kèm. Kiểm tra câu có đúng chương trình không.",
    grounding_pending: "Đang đối chiếu câu với tài liệu đính kèm…",
    grounding_failed: "Chưa đối chiếu được câu với tài liệu, thầy/cô tự kiểm tra.",
};

const GENERIC_REVIEW_REASON = "Hệ thống đề nghị thầy/cô xem kỹ câu này";

/** Lý do chỗ không soạn được câu (state = "failed"); mã lạ thì chỉ dặn bấm Đổi câu. */
export function describeFailureReason(reason?: string | null): string {
    switch (reason) {
        case "llm_error":
            return "Lỗi kết nối AI. Bấm Đổi câu để thử lại.";
        case "qc_exhausted":
            return "Không soạn được câu đạt yêu cầu sau 3 lần. Bấm Đổi câu (có thể ghi thêm yêu cầu).";
        default:
            return "Chưa soạn được câu cho chỗ này. Bấm Đổi câu để thử lại.";
    }
}

export const examKindConfig: Record<ExamKind, { label: string; color: string }> = {
    matrix: { label: "Theo ma trận", color: "blue" },
    lesson: { label: "Theo bài", color: "purple" },
    manual: { label: "Đề cũ", color: "default" },
};

export const examStatusConfig: Record<ExamStatus, { label: string; color: string }> = {
    draft: { label: "Bản nháp", color: "default" },
    confirmed: { label: "Đã xác nhận", color: "success" },
};

const GENERIC_FORMAT_WARNING = "Có điểm trình bày cần xem lại";

// Tên trường backend nhắc tới trong cảnh báo định dạng → chỗ trong câu, nói theo giáo viên
const warningFieldLabels: Record<string, string> = {
    question_text: "câu dẫn",
    option_a: "phương án A",
    option_b: "phương án B",
    option_c: "phương án C",
    option_d: "phương án D",
    statement_a: "ý a)",
    statement_b: "ý b)",
    statement_c: "ý c)",
    statement_d: "ý d)",
    explanation: "lời giải",
    answer: "đáp số",
};

/**
 * Lỗi, cảnh báo và việc tự sửa của bước định dạng do backend ghi (nguồn: FormatChecker, LatexValidationRules,
 * ShortAnswerValue, LlmOutputRepair, TfJsonParser, TfExplanationLeak). Chỉ dịch các dạng đã biết; không bao giờ ghép
 * chuỗi thô của backend ra giao diện.
 */
export function describeFormatIssue(issue: string): string {
    const text = issue.trim();

    // "label: 4 ý cùng nhãn T|F": cả 4 ý đúng/sai cùng một đáp án
    const sameLabel = /^label:\s*4 ý cùng nhãn\s*([TF])\s*$/i.exec(text);
    if (sameLabel) {
        const same = sameLabel[1].toUpperCase() === "T" ? "Đúng" : "Sai";
        return `Cả 4 ý đúng/sai đều cùng là ${same} — nên kiểm lại đáp án từng ý.`;
    }

    // Lời giải còn phần nháp: cảnh báo không có tên trường đứng trước (bản giáo viên sửa)
    if (text.startsWith("Lời giải còn chứa phần nháp")) {
        return EXPLANATION_DRAFT_REASON;
    }

    // Hệ thống tự sửa lỗi gõ công thức ở đầu ra của AI (không nói chi tiết kỹ thuật)
    if (text.startsWith("json: đã sửa")) {
        return "Hệ thống đã tự sửa lỗi gõ công thức trong câu.";
    }
    if (text.startsWith("Không giải mã được JSON")) {
        return "Hệ thống không đọc được câu do AI soạn.";
    }
    if (text.startsWith("4 phương án")) {
        return "Các phương án A, B, C, D có nội dung trùng nhau.";
    }
    if (text.startsWith("4 ý a, b, c, d")) {
        return "Các ý a), b), c), d) có nội dung trùng nhau.";
    }

    const parts = /^([a-z_]+):\s*([\s\S]*)$/.exec(text);
    const field = parts?.[1];
    const detail = parts?.[2] ?? "";

    // Nhãn đúng/sai của từng ý bị trống hoặc không đọc được
    const labelField = field ? /^label_([a-d])$/.exec(field) : null;
    if (labelField) {
        return `Chưa rõ ý ${labelField[1]}) đúng hay sai.`;
    }

    const place = field ? warningFieldLabels[field] : undefined;
    if (!field || !place) {
        return GENERIC_FORMAT_WARNING;
    }

    if (field === "explanation" && detail.startsWith("lời giải nhắc tới mẫu nhãn")) {
        return "Lời giải nhắc tới việc gán đáp án theo mẫu. Cần viết lại cho đúng từng ý.";
    }
    if (field === "explanation" && detail.startsWith("lời giải tự sửa lại")) {
        return "Lời giải có phần tự sửa lại đề. Cần viết lại lời giải cuối cùng.";
    }
    if (detail.startsWith("nội dung trống")) {
        return `Chưa có nội dung ở ${place}.`;
    }

    if (field === "explanation" && detail.startsWith("lời giải có thể còn phần nháp")) {
        return EXPLANATION_DRAFT_REASON;
    }
    if (field === "answer" && detail.includes("phân số chưa tối giản")) {
        return "Đáp số là phân số chưa tối giản. Thầy/cô kiểm tra lại đáp số.";
    }
    if (field.startsWith("statement_") && detail.startsWith("đã bỏ tiền tố")) {
        return `Ý ${field.slice("statement_".length)}) có ký hiệu đánh số thừa ở đầu nên hệ thống đã tự bỏ. Thầy/cô xem lại ý này.`;
    }
    if (detail.startsWith("đã tự đóng")) {
        return `Công thức ở ${place} chưa đóng đủ nên hệ thống đã tự sửa. Thầy/cô xem lại công thức.`;
    }
    if (detail.startsWith("Lệnh ")) {
        return `Công thức ở ${place} có ký hiệu lạ, có thể gõ sai. Thầy/cô xem lại công thức.`;
    }
    if (field === "answer") {
        return "Đáp số chưa đúng dạng. Thầy/cô kiểm tra lại đáp số.";
    }
    // Còn lại của một trường đã biết là lỗi công thức (môi trường không hỗ trợ, ngoặc lệch, thiếu đối số…)
    return `Công thức ở ${place} có chỗ trình bày chưa đúng. Thầy/cô xem lại công thức.`;
}

export function describeReviewReason(reason: string): string {
    if (reason.startsWith("warning:")) {
        return describeFormatIssue(reason.slice("warning:".length));
    }
    if (reason.startsWith("duplicate:")) {
        return "Câu này trùng với một câu khác. Thầy/cô sửa lại hoặc đổi câu.";
    }
    if (reason.startsWith("similar_in_exam:")) {
        const position = reason.slice("similar_in_exam:".length).trim();
        // n là số thứ tự câu trong đề; không đọc được số thì không đoán
        if (/^\d+$/.test(position)) {
            return `Gần giống câu ${position} trong đề, nên đổi một trong hai câu.`;
        }
        return GENERIC_REVIEW_REASON;
    }
    // Mã lạ: không hiện mã thô cho giáo viên
    return reviewReasonLabels[reason] ?? GENERIC_REVIEW_REASON;
}

export function resolveQuestionType(question: ExamQuestionItem): ExamQuestionType {
    if (question.questionType) return question.questionType;
    if (question.statements?.length) return "tf";
    if (!question.optionA && !question.optionB && !question.optionC && !question.optionD) return "sa";
    return "mcq";
}