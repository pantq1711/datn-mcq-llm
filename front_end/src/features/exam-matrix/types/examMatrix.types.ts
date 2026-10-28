export type MatrixPart = "I" | "II" | "III";
export type MatrixQuestionType = "mcq" | "tf" | "sa";
export type MatrixDifficulty = "easy" | "medium" | "hard";

export interface ExamMatrixCellPayload {
    part: MatrixPart; // I: mcq, II: tf, III: sa
    questionType: MatrixQuestionType;
    difficulty?: MatrixDifficulty; // bắt buộc cho mcq/sa
    statementDifficulties?: MatrixDifficulty[]; // đúng 4 mức theo ý a-d cho tf
    questionCount: number; // 1 - 50
    topic: string;
    content?: string;
    learningOutcomes: {
        easy?: string;
        medium?: string;
        hard?: string;
    };
}

export interface CreateExamMatrixPayload {
    name: string;
    subjectId: number;
    gradeLevel: number; // 6 - 9
    description?: string;
    cells: ExamMatrixCellPayload[];
}

export interface ExamMatrix {
    id: string;
    name: string;
    subjectId: number;
    gradeLevel: number;
    notes?: string[];
    createdAt?: string;
    updatedAt?: string;
}

export interface ExamMatrixDetail extends ExamMatrix {
    cells: ExamMatrixCellPayload[];
}

export interface ExamMatrixTemplate {
    id: string;
    name: string;
    subjectId?: number;
    subjectName?: string;
    gradeLevel?: number;
    description?: string;
    cells: ExamMatrixCellPayload[];
}

export interface DeleteResponse {
    success: boolean;
}