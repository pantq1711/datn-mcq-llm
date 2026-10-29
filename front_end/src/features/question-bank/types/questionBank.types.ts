export type QuestionDifficulty = "easy" | "medium" | "hard";
export type QuestionStatus = "approved" | "edited";
export type QuestionType = "mcq" | "tf" | "sa";

export interface QuestionTableItem {
    id: number;
    questionText: string;
    subject?: string;
    subjectName?: string;
    gradeLevel?: number;
    topic?: string;
    questionType?: QuestionType;
    difficulty: QuestionDifficulty;
    status?: QuestionStatus;
    origin?: {
        type: string;
        reviewMode: string;
        edited: boolean;
    };
    usageCount?: number;
    createdAt: string;
}

export interface QuestionFilterValues {
    keyword: string;
    subjectId: number | null;
    gradeLevel: number | null;
    questionType: QuestionType | null;
    topic: string | null;
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

export interface BankTopic {
    subjectId: number;
    subjectName?: string;
    gradeLevel?: number | null;
    topic: string;
    count: number;
}

export interface BankItemParams extends Partial<QuestionFilterValues> {
    page?: number;
    pageSize?: number;
    sort?: string;
}

export interface BankItemsResponse {
    items: QuestionTableItem[];
    total?: number;
    totalItems?: number;
    page: number;
    pageSize?: number;
    size?: number;
    totalPages?: number;
}

export interface BankItemDetail {
    id: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correct_option: "A" | "B" | "C" | "D";
    explanation?: string;
    subject: string;
    subjectName?: string;
    gradeLevel?: number;
    topic?: string;
    questionType?: QuestionType;
    saAnswer?: string;
    statements?: {
        key: string;
        statementText: string;
        correctLabel: boolean;
        difficulty: QuestionDifficulty;
    }[];
    difficulty: QuestionDifficulty;
    status: QuestionStatus;
    sourceQuestion?: string;
    origin?: {
        type: string;
        reviewMode: string;
        edited: boolean;
    };
    usageCount?: number;
    createdAt: string;
}
