// Kiểu dữ liệu bài giao trực tuyến, theo docs/api mục 11. Thời gian backend trả là giờ máy chủ, không kèm múi giờ.

import type { QuestionImageView } from "../../../components/common/MathText/questionImage.types";

/**
 * Một hình của câu cho học sinh (S1, docs/api mục 11.2): không có `role`; `url` ký theo lượt làm
 * ("/api/public/question-images/{id}?exp&a&sig"), dùng nguyên.
 */
export type PublicImage = QuestionImageView;

export type AssignmentStatus = "open" | "closed";
export type AttemptState = "in_progress" | "submitted" | "expired";
export type PartCode = "I" | "II" | "III";
/** Học sinh nhập họ và tên (`name`, mặc định từ 25/11) hay số thứ tự (`seat`, bài giao cũ). */
export type IdentityMode = "name" | "seat";

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

/** Một dòng bảng điểm của giáo viên. Đúng một trong `seatNo` / `studentName` có giá trị, theo chế độ của bài giao. */
export interface AttemptRow {
    attemptId: number;
    classLabel: string;
    seatNo?: number;
    studentName?: string;
    state: AttemptState;
    /** Có lượt khác cùng lớp + số thứ tự, hoặc cùng lớp + họ tên (cả hai lượt đều được giữ). */
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
    /** Máy chủ trước 25/11 không gửi: coi là số thứ tự. */
    identityMode?: IdentityMode;
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
    identityMode: IdentityMode;
}

/** Mã lỗi backend dùng khi xóa đề còn bài giao. */
export const EXAM_HAS_ASSIGNMENTS = "EXAM_HAS_ASSIGNMENTS";

/** Mã lỗi 409 của P2 (25/11): đã có lượt cùng lớp + tên / số, học sinh chưa xác nhận; chưa tạo lượt. */
export const ATTEMPT_IDENTITY_TAKEN = "ATTEMPT_IDENTITY_TAKEN";

// ---------- Phân tích câu (docs/api mục 11.3, B1/B2) ----------

/** Mã lỗi 409 của B1/B2: bài còn mở; bài đã đóng nhưng còn ân hạn nộp (errors[0] là lúc có thống kê). */
export const ASSIGNMENT_OPEN = "ASSIGNMENT_OPEN";
export const STATS_NOT_READY = "STATS_NOT_READY";

/** Một câu của bảng phân tích. `choiceCounts` có dạng riêng theo `questionType` (xem utils/itemStats). */
export interface ItemStat {
    slotId: number | null;
    part: PartCode;
    /** Số câu trong phần (đánh số lại từ 1 mỗi phần, khớp file Word). */
    number: number;
    questionType: QuestionType;
    bankItemId: number | null;
    /** mcq "B"; tf nhãn bốn ý "TFFT"; sa đáp số. */
    answerKey: string | null;
    /** Tỉ lệ làm đúng, 0..1. */
    p: number | null;
    /** Độ phân biệt, -1..1; null khi nhóm cao/thấp rỗng. */
    d: number | null;
    choiceCounts: Record<string, unknown>;
    flags: string[];
    /** Chỗ này trên đề đã Đổi câu sau lúc giao. */
    slotChanged: boolean;
}

export interface ItemStats {
    assignmentId: number;
    /** Số lượt hợp lệ được tính. */
    n: number;
    /** Số lượt mỗi nhóm cao/thấp (27%). */
    groupSize: number;
    computedAt: string;
    sampleSufficient: boolean;
    minSampleSize: number;
    sampleNote?: string | null;
    excluded: { duplicate: number; notSubmitted: number };
    items: ItemStat[];
}

// ---------- Học sinh (công khai) ----------

export interface PublicAssignmentInfo {
    title: string;
    questionCount: number;
    durationMinutes: number;
    closesAt: string;
    status: AssignmentStatus;
    showScore: boolean;
    showSolutions: boolean;
    /** Máy chủ trước 25/11 không gửi: coi là số thứ tự. */
    identityMode?: IdentityMode;
    classes: AssignmentClass[];
}

/** Thân P2: chế độ số thứ tự gửi `seatNo`, chế độ họ tên gửi `studentName`. */
export interface StartAttemptPayload {
    classLabel: string;
    seatNo?: number;
    studentName?: string;
    /** true khi em đã thấy báo trùng và bấm "Đúng là em, vẫn làm". */
    confirmDuplicate: boolean;
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
    /** Hình của đề, phương án, ý (không bao giờ có hình lời giải); vắng khi câu không có hình. */
    images?: PublicImage[];
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
    /** Mọi hình của câu, kể cả hình lời giải; vắng khi câu không có hình. */
    images?: PublicImage[];
}

export interface ReviewPart {
    part: PartCode;
    items: ReviewItem[];
}

/**
 * Trả lời P2 (có attemptToken, chỉ một lần) và P3. Đúng một trong `seatNo` / `studentName` có giá trị; `studentName` là
 * tên của chính lượt này.
 */
export interface AttemptView {
    attemptToken?: string;
    identityMode?: IdentityMode;
    classLabel: string;
    seatNo?: number;
    studentName?: string;
    /** Có lượt khác cùng lớp + số, hoặc cùng lớp + tên. */
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
