export type ExamQuestionDifficulty = "easy" | "medium" | "hard";
export type ExamKind = "matrix" | "lesson" | "manual";
export type ExamStatus = "draft" | "confirmed";
export type ExamMode = "teacher" | "manual";

export interface ExamPreviewData {
    title: string;
    subject: string;
    questionCount: number;
    duration: number;
    easyCount: number;
    mediumCount: number;
    hardCount: number;
    instructions?: string;
}

export interface ExamQuestionItem {
    id: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctOption: "A" | "B" | "C" | "D";
    explanation?: string;
    difficulty?: ExamQuestionDifficulty;
    message?: string;
}

export interface ExamGenerationValues {
    title: string;
    subjectId?: number;
    questionCount?: number;
    duration?: number;
    examCodeCount?: number;
    easyCount?: number;
    mediumCount?: number;
    hardCount?: number;
    shuffleQuestions: boolean;
    shuffleOptions: boolean;
}

export interface Subject {
    id: number;
    code: string;
    name: string;
    description?: string;
}

export interface ExamSubjectOption {
    label: string;
    value: number;
}

export interface GeneratedExamCode {
    id: string;
    code: string;
    title: string;
    preview: ExamPreviewData;
    questions: ExamQuestionItem[];
}

export type GenerateExamPayload = ExamGenerationValues;

export interface ChangeQuestionRequest {
    /** Câu hỏi cần được thay thế. Backend nên xác minh lại câu hỏi này. */
    question: ExamQuestionItem;
    /** Yêu cầu bổ sung do giáo viên nhập. */
    instruction: string;
}

/**
 * Câu ứng viên có thể chưa có ID trong hệ thống.
 */
export type QuestionReplacementCandidate = Omit<
    ExamQuestionItem,
    "id"
> & {
    id?: ExamQuestionItem["id"];
};

export interface ExamListItem {
    examId: number;
    examCode: string;
    title: string;
    mode: ExamMode;
    kind: ExamKind;
    status: ExamStatus;
    matrixId: number | null;
    createdAt: string;
    totalSlots: number;
    readySlots: number;
    keptSlots: number;
    needsReviewSlots: number;
    generatingSlots: number;
    failedSlots: number;
}

export interface ExamListResponse {
    items: ExamListItem[];
    page: number;         // 0-indexed theo Spring / Backend
    size: number;
    totalItems: number;
    totalPages: number;
}
export interface GetMyExamsParams {
    page?: number;
    size?: number;
    kind?: ExamKind;
}