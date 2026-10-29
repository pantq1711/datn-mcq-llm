export type ExamQuestionDifficulty = "easy" | "medium" | "hard";
export type ExamKind = "matrix" | "lesson" | "manual";
export type ExamStatus = "draft" | "confirmed";
export type ExamMode = "teacher" | "manual";

export type ExamQuestionType = "mcq" | "tf" | "sa";
export type ExamOption = "A" | "B" | "C" | "D";
export type StatementKey = "a" | "b" | "c" | "d";
export type TruthLabel = "T" | "F";
/** Trạng thái chỗ trong đề (docs/api mục 7.4, 7.5). */
export type ExamSlotState = "pending" | "generating" | "ready" | "kept" | "edited" | "failed";
export type ExamItemSource = "bank" | "generated";
export type ExamQcState = "checking" | "passed" | "needs_review";

export interface ExamPreviewData {
    title: string;
    subject?: string;
    questionCount: number;
    duration?: number;
    easyCount: number;
    mediumCount: number;
    hardCount: number;
    instructions?: string;
}

export interface ExamItemStatement {
    key: StatementKey;
    text: string;
    label: TruthLabel;
    difficulty?: ExamQuestionDifficulty | null;
}

export interface ExamQuestionItem {
    id: number;
    questionText: string;
    optionA?: string;
    optionB?: string;
    optionC?: string;
    optionD?: string;
    correctOption?: ExamOption;
    explanation?: string;
    difficulty?: ExamQuestionDifficulty;
    message?: string;
    // Các trường dưới có khi câu lấy từ GET /api/exams/{id} (ExamItemDto)
    slotId?: number;
    questionId?: number;
    questionType?: ExamQuestionType;
    part?: string;
    topic?: string;
    state?: ExamSlotState;
    source?: ExamItemSource;
    bankItemId?: number;
    saAnswer?: string;
    statements?: ExamItemStatement[];
    needsReview?: boolean;
    reviewReasons?: string[];
    qcState?: ExamQcState;
    edited?: boolean;
    originalQuestionId?: number;
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
    /** Nguồn đổi: 'generate' (mặc định) sinh mới qua AI, hoặc 'bank' lấy từ ngân hàng */
    source?: "generate" | "bank";
}

export interface ExamListItem {
    examId: number;
    examCode: string;
    title: string;
    mode: ExamMode;
    kind: ExamKind;
    status: ExamStatus;
    matrixId: number | null;
    createdAt: string;
    totalSlots: number;
    readySlots: number;
    keptSlots: number;
    needsReviewSlots: number;
    generatingSlots: number;
    failedSlots: number;
}

export interface ExamListResponse {
    items: ExamListItem[];
    page: number;         // 0-indexed theo Spring / Backend
    size: number;
    totalItems: number;
    totalPages: number;
}
export interface GetMyExamsParams {
    page?: number;
    size?: number;
    kind?: ExamKind;
}

/** Một chỗ (câu) của đề, khớp ExamViewDtos.ExamItemDto. Chỗ chưa có câu chỉ có phần định danh và state. */
export interface ExamItemDto {
    slotId: number;
    cellId: number;
    slotNo: number;
    part: string;
    topic: string | null;
    questionType: ExamQuestionType;
    state: ExamSlotState;
    source?: ExamItemSource;
    bankItemId?: number;
    questionId?: number;
    questionText?: string;
    optionA?: string;
    optionB?: string;
    optionC?: string;
    optionD?: string;
    correctOption?: ExamOption;
    saAnswer?: string;
    statements?: ExamItemStatement[];
    explanation?: string;
    difficulty?: ExamQuestionDifficulty;
    needsReview: boolean;
    reviewReasons: string[];
    qcState?: ExamQcState;
    edited: boolean;
    originalQuestionId?: number;
}

export interface ExamPartDto {
    part: string;      // "I" | "II" | "III"
    required: number;
    ready: number;
    items: ExamItemDto[];
}

/** Response của GET /api/exams/{id}, khớp ExamViewDtos.ExamViewDto. */
export interface ExamDetail {
    examId: number;
    examCode: string;
    title: string;
    mode: ExamMode;
    status: ExamStatus;
    matrixId: number | null;
    createdAt: string;
    buildStatus: string;
    needsReviewCount: number;
    parts: ExamPartDto[];
    kind: ExamKind;
}

/** Body của PUT /api/exams/{id}/slots/{slotId}/question (docs/api mục 7.5a). */
export interface UpdateSlotQuestionPayload {
    baseQuestionId: number;
    questionText: string;
    explanation?: string;
    optionA?: string;
    optionB?: string;
    optionC?: string;
    optionD?: string;
    correctOption?: ExamOption;
    saAnswer?: string;
    statements?: { key: StatementKey; text: string; label: TruthLabel }[];
}

export interface ExamProgressCell {
    cellId: string;
    answered: boolean;
    needReview?: boolean;
}

export interface ExamProgress {
    examId: string;
    cells: ExamProgressCell[];
}

export interface LessonCellInput {
    questionType: "mcq" | "tf" | "sa";
    difficulty?: ExamQuestionDifficulty;
    statementDifficulties?: ExamQuestionDifficulty[];
    questionCount: number;
}

export interface BuildByLessonPayload {
    subjectId: number;
    gradeLevel?: number;
    lesson: string;
    content?: string;
    title?: string;
    learningOutcomes?: Record<string, string>;
    cells: LessonCellInput[];
    materialIds?: number[];
    extraInstruction?: string;
}

export interface BuildResultDto {
    examId: number;
    examCode: string;
    title: string;
    matrixId: number | null;
    slots: number;
    fromBank: number;
    jobsQueued: number;
    kind: ExamKind;
}

export interface ReplaceQuestionParams {
    source?: "generate" | "bank";
    instruction?: string;
}

export interface CellContextInput {
    cellId: number;
    materialIds?: number[];
    extraInstruction?: string;
}

export interface BuildExamRequest {
    matrixId: number;
    title?: string;
    materialIds?: number[];
    cells?: CellContextInput[];
}
