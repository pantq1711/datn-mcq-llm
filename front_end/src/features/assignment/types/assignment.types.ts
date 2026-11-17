// Kiểu dữ liệu bài giao trực tuyến, theo docs/api mục 11. Thời gian backend trả là giờ máy chủ, không kèm múi giờ.

export type AssignmentStatus = "open" | "closed";
export type AttemptState = "in_progress" | "submitted" | "expired";
export type PartCode = "I" | "II" | "III";

export interface AssignmentClass {
    classLabel: string;
    size: number;
}

export interface PartScore {
    part: PartCode;
    correct: number;
    total: number;
    score: number;
}

/** Một dòng bảng điểm của giáo viên (không có tên học sinh). */
export interface AttemptRow {
    attemptId: number;
    classLabel: string;
    seatNo: number;
    state: AttemptState;
    /** Có lượt khác cùng lớp + số thứ tự (cả hai lượt đều được giữ). */
    duplicate: boolean;
    startedAt: string;
    submittedAt?: string;
    score?: number;
    score10?: number;
    parts?: PartScore[];
}

export interface Assignment {
    assignmentId: number;
    publicCode: string;
    title: string;
    status: AssignmentStatus;
    durationMinutes: number;
    closesAt: string;
    closedAt?: string;
    showScore: boolean;
    showSolutions: boolean;
    createdAt: string;
    classes: AssignmentClass[];
    questionCount: number;
    maxScore: number;
    maxAttempts: number;
    startedCount: number;
    submittedCount: number;
    duplicateCount: number;
    /** Chỉ có khi xem một bài giao; danh sách bài giao không có. */
    attempts?: AttemptRow[];
}

export interface CreateAssignmentPayload {
    durationMinutes: number;
    /** ISO có múi giờ (Instant), ví dụ 2026-11-20T10:00:00.000Z. */
    closesAt: string;
    showScore: boolean;
    showSolutions: boolean;
    consentConfirmed: true;
    classes: AssignmentClass[];
}

/** Mã lỗi backend dùng khi xóa đề còn bài giao. */
export const EXAM_HAS_ASSIGNMENTS = "EXAM_HAS_ASSIGNMENTS";

// ---------- Học sinh (công khai) ----------

export interface PublicAssignmentInfo {
    title: string;
    questionCount: number;
    durationMinutes: number;
    closesAt: string;
    status: AssignmentStatus;
    showScore: boolean;
    showSolutions: boolean;
    classes: AssignmentClass[];
}

export interface PublicStatement {
    key: string;
    text: string;
}

export type QuestionType = "mcq" | "tf" | "sa";

export interface PublicQuestion {
    slotId: number;
    number: number;
    questionType: QuestionType;
    questionText: string;
    options?: Record<string, string>;
    statements?: PublicStatement[];
}

export interface PublicPart {
    part: PartCode;
    items: PublicQuestion[];
}

export interface AttemptResult {
    score: number;
    maxScore: number;
    score10: number;
    parts: PartScore[];
}

export interface ReviewStatement {
    key: string;
    text: string;
    /** Nhãn đúng: T hoặc F. */
    label: string;
    /** Lựa chọn của em; không có là bỏ trống. */
    yourAnswer?: string;
    correct: boolean;
}

export interface ReviewItem {
    slotId: number;
    number: number;
    questionType: QuestionType;
    questionText: string;
    options?: Record<string, string>;
    yourChoice?: string;
    correctOption?: string;
    statements?: ReviewStatement[];
    yourText?: string;
    saAnswer?: string;
    correctCount: number;
    score: number;
    explanation?: string;
}

export interface ReviewPart {
    part: PartCode;
    items: ReviewItem[];
}

/** Trả lời P2 (có attemptToken, chỉ một lần) và P3. */
export interface AttemptView {
    attemptToken?: string;
    classLabel: string;
    seatNo: number;
    duplicateSeat: boolean;
    startedAt: string;
    deadlineAt: string;
    serverNow: string;
    remainingSeconds: number;
    submitted: boolean;
    submittedAt?: string;
    resultsReleased: boolean;
    resultsAt?: string;
    parts?: PublicPart[];
    result?: AttemptResult;
    review?: ReviewPart[];
}

export interface SubmitResult {
    submittedAt: string;
    resultsAt?: string;
}

/** Câu trả lời đang làm dở của học sinh, khóa theo slotId. */
export interface DraftAnswers {
    /** slotId → A..D */
    mcq: Record<string, string>;
    /** slotId → { a: "T" | "F", ... } */
    tf: Record<string, Record<string, "T" | "F">>;
    /** slotId → đáp số học sinh gõ */
    sa: Record<string, string>;
}

export interface SubmitAnswerPayload {
    slotId: number;
    choice?: string;
    tf?: Record<string, string>;
    text?: string;
}
