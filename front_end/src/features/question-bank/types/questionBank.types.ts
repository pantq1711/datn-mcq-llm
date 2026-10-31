import type { ExamItemStatement } from "../../exam/types/exam.types";

export type QuestionDifficulty = "easy" | "medium" | "hard";
export type QuestionType = "mcq" | "tf" | "sa";
export type BankSort = "newest" | "oldest" | "least_used" | "recently_used";
export type BankUsage = "used" | "unused";

export interface QuestionTableItem {
    id: number;
    questionText: string;
    subject?: string;
    subjectName?: string;
    gradeLevel?: number;
    topic?: string;
    questionType?: QuestionType;
    difficulty?: QuestionDifficulty | null
    origin?: {
        type: string;
        reviewMode: string;
        edited: boolean;
    };
    usageCount?: number;
    createdAt: string;
    content?: string | null;
    lastUsedAt?: string | null
}

export interface QuestionFilterValues {
    keyword: string;
    subjectId: number | null;
    gradeLevel: number | null;
    questionType: QuestionType | null;
    topic: string | null;
    difficulty: QuestionDifficulty | null;
    usage: BankUsage | null;
    levels: (QuestionDifficulty | null)[] | null; // 4 phần tử a-d, chỉ khi questionType = "tf"
    content: string | null;
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
    // Sắp xếp nằm cạnh bảng, áp dụng ngay, không thuộc bộ lọc
    sort?: BankSort;
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
    optionA?: string | null;
    optionB?: string | null;
    optionC?: string | null;
    optionD?: string | null;
    correctOption?: "A" | "B" | "C" | "D" | null;
    explanation?: string | null;
    subject: string;
    subjectName?: string;
    gradeLevel?: number;
    topic?: string;
    questionType?: QuestionType;
    saAnswer?: string | null;
    statements?: ExamItemStatement[];
    difficulty?: QuestionDifficulty | null;
    sourceQuestion?: string;
    origin?: {
        type: string;
        reviewMode: string;
        edited: boolean;
    };
    usageCount?: number;
    createdAt: string;
    content?: string | null;
    lastUsedAt?: string | null;
}