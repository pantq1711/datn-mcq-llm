export type MatrixPart = "I" | "II" | "III";
export type MatrixQuestionType = "mcq" | "tf" | "sa";
export type MatrixDifficulty = "easy" | "medium" | "hard";

export type MatrixLearningOutcomes = Partial<Record<MatrixDifficulty, string>>;

/** Một ô gửi lên khi tạo/sửa ma trận (CellInput ở backend, docs/api mục 7.1). */
export interface ExamMatrixCellInput {
    part: MatrixPart; // I: mcq, II: tf, III: sa
    questionType: MatrixQuestionType;
    difficulty?: MatrixDifficulty; // bắt buộc cho mcq/sa, không gửi với tf
    statementDifficulties?: MatrixDifficulty[]; // đúng 4 mức theo ý a-d, chỉ tf
    questionCount: number; // 1 - 50
    topic: string;
    content?: string | null;
    learningOutcomes: MatrixLearningOutcomes; // phải có mọi mức ô dùng
}

/** Một ô đọc về (CellDto): thêm id, vị trí, template. */
export interface ExamMatrixCellPayload extends ExamMatrixCellInput {
    id?: number;
    position?: number;
    templateId?: number;
}

/** Body của POST /api/exam-matrices và PUT /api/exam-matrices/{id} (CreateMatrixRequest). */
export interface CreateExamMatrixPayload {
    name: string;
    subjectId: number;
    gradeLevel: number; // 6 - 9
    source?: string | null;
    notes?: string[] | null;
    cells: ExamMatrixCellInput[];
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
    source?: string | null;
    sampleKey?: string;
    /** Số đề đã tạo từ ma trận: lớn hơn 0 thì chỉ sửa được tên, nguồn, ghi chú (docs/api mục 7.1a). */
    examCount?: number;
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

/** Một dòng của GET /api/exam-matrices (MatrixSummaryDto): không có ô, không có tổng câu. */
export interface ExamMatrixSummary extends ExamMatrix {
    /** Số ô; mỗi ô có ít nhất 1 câu nên lớn hơn 0 là ma trận đã có câu. */
    cellCount: number;
    /** Số đề đã tạo từ ma trận: lớn hơn 0 thì không xóa được (409 MATRIX_IN_USE). */
    examCount: number;
}