export type CorrectAnswer =
    | "A"
    | "B"
    | "C"
    | "D"
    | "NONE"
    | "MULTIPLE";

export type FormatStatus = "PASS" | "FAIL";

export type Difficulty = "EASY" | "MEDIUM" | "HARD";

export type ReviewDecisions = "EDIT" | "APPROVE" | "REJECT";

export interface QuestionDraft {
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctAnswer: "A" | "B" | "C" | "D" | null;
}

export interface RejectionReasonGroup {
    title: string;
    reasons: {
        code: string;
        label: string;
    }[];
}

export interface ReviewQuestion {
    id: number;
    question: string;
    subject: string;
    qualityStatus: "PASSED" | "FAILED";
    reviewStatus: "PENDING" | "APPROVED" | "EDITED" | "REJECTED";
    difficulty: "EASY" | "MEDIUM" | "HARD";
    createdAt: string;
}