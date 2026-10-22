export type CorrectAnswer =
    | "A"
    | "B"
    | "C"
    | "D"
    | "NONE"
    | "MULTIPLE";

export type FormatStatus = "qc_passed" | "format_failed";

export type Difficulty = "easy" | "medium" | "hard";

export type ReviewDecisions = "approved_as_is" | "approved_with_edit" | "rejected";

export interface QuestionDraft {
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctAnswer: "A" | "B" | "C" | "D" | null;
    explanation?: string;
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
    qualityStatus: FormatStatus;
    reviewStatus: "pending" | ReviewDecisions;
    difficulty: "easy" | "medium" | "hard";
    createdAt: string;
}

export interface ReviewQuestionDetail {
    id: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    explanation?: string;

    // Kết quả kiểm tra tự động QC
    formatStatus?: FormatStatus;
    qcDetails?: {
        checkType: "format" | "dedup";
        passed: boolean;
        message?: string;
    }[];

    // Dự đoán độ khó ban đầu từ LLM
    llmDifficulty?: Difficulty;

    subject?: string;
    reviewStatus?: "pending" | ReviewDecisions;
}

export interface SubmitReviewPayload {
    reviewerAnswer: "A" | "B" | "C" | "D" | "multiple" | "none";
    formatOk: boolean;
    difficultyLabel: Difficulty;
    decision: ReviewDecisions;
    finalOption?: "A" | "B" | "C" | "D" | null;

    // Nếu decision === 'approved_with_edit'
    editedQuestionText?: string;
    editedOptionA?: string;
    editedOptionB?: string;
    editedOptionC?: string;
    editedOptionD?: string;

    // Nếu decision === 'rejected'
    reasons?: {
        reasonCode: string;
        detail?: string;
    }[];
}
