export type QuestionDifficulty = "easy" | "medium" | "hard";
export type QuestionStatus = "approved" | "edited";

export interface QuestionTableItem {
    id: number;
    questionText: string;
    subject: string;
    difficulty: QuestionDifficulty;
    status: QuestionStatus;
    createdAt: string;
}

export interface QuestionFilterValues {
    keyword: string;
    subjectId: number | null;
    difficulty: QuestionDifficulty | null;
    status: QuestionStatus | null;
}

export interface Subject {
    id: number;
    code: string;
    name: string;
    description?: string;
}

export interface SubjectOption {
    label: string;
    value: number;
}

export interface BankItemParams extends Partial<QuestionFilterValues> {
    page?: number;
    pageSize?: number;
}

export interface BankItemsResponse {
    items: QuestionTableItem[];
    total: number;
    page: number;
    pageSize: number;
}

export interface BankItemDetail {
    id: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctOption: "A" | "B" | "C" | "D";
    subject: string;
    difficulty: "easy" | "medium" | "hard";
    status: "approved" | "edited";
    sourceQuestion?: string;
    createdAt: string;
}
