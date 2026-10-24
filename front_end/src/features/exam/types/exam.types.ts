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