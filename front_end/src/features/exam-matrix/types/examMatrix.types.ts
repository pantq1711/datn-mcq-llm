export type MatrixPart = "I" | "II" | "III";
export type MatrixQuestionType = "mcq" | "tf" | "sa";
export type MatrixDifficulty = "easy" | "medium" | "hard";

export interface ExamMatrixCellPayload {
    id?: number;
    position?: number;
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

export interface DeleteResponse {
    success: boolean;
}

/** Mẫu ma trận có sẵn (GET /api/exam-matrices/samples, SampleDto ở backend). */
export interface ExamMatrixSample {
    key: string;
    name: string;
    gradeLevel: number | null;
    source: string | null;
    notes: string[] | null;
}