export type ExamQuestionDifficulty = "easy" | "medium" | "hard";

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