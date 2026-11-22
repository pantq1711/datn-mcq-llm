import type { QuestionImageInfo } from "../../../components/common/MathText/questionImage.types";

export type { QuestionImageInfo };

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
/** Lý do chỗ ở trạng thái failed (docs/contract/enums.md, V23); chỉ có khi state = "failed". */
export type ExamSlotFailureReason = "llm_error" | "qc_exhausted";

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
    failureReason?: ExamSlotFailureReason;
    infoReasons?: string[];
    requests?: TeacherRequests;
    inheritedRequests?: TeacherRequests;
    /** Ảnh của chỗ lấy từ ngân hàng mà câu nguồn có hình (G0 H3); token ⟦img:id⟧ trong chữ trỏ tới `id`. Vắng khi không có hình. */
    images?: QuestionImageInfo[];
}

/** Chip yêu cầu của giáo viên (PR B, docs/contract/enums.md TeacherRequest). */
export type TeacherRequestCode =
    | "real_context"
    | "multi_step"
    | "common_trap"
    | "extremum"
    | "geometry_link"
    | "tf_shared_context";

/** Nhãn chip; tfOnly: chỉ áp cho câu Đúng/Sai (ẩn với dạng khác). */
export const TEACHER_REQUEST_OPTIONS: { value: TeacherRequestCode; label: string; tfOnly?: boolean }[] = [
    { value: "real_context", label: "Bối cảnh thực tế" },
    { value: "multi_step", label: "Nhiều bước biến đổi" },
    { value: "common_trap", label: "Có bẫy lỗi hay gặp" },
    { value: "extremum", label: "Tìm giá trị lớn nhất/nhỏ nhất" },
    { value: "geometry_link", label: "Liên hệ hình học" },
    { value: "tf_shared_context", label: "Đúng/Sai cùng một tình huống", tfOnly: true },
];

/** Các chip hợp dạng câu (ẩn chip chỉ cho Đúng/Sai với câu khác). */
export function teacherRequestOptionsFor(type?: ExamQuestionType) {
    return TEACHER_REQUEST_OPTIONS.filter(o => !o.tfOnly || type === undefined || type === "tf");
}

/** Nhãn chip của một mã; mã lạ hiện nguyên văn. */
export function teacherRequestLabel(code: string): string {
    return TEACHER_REQUEST_OPTIONS.find(o => o.value === code)?.label ?? code;
}

/** Chuỗi hiển thị một cờ thông tin (infoReasons); giá trị lạ hiện nguyên văn. */
export function describeInfoReason(reason: string): string {
    const at = reason.indexOf(": ");
    const code = at < 0 ? reason : reason.slice(0, at);
    const detail = at < 0 ? "" : reason.slice(at + 2);
    if (code === "request_partial") return `Đúng một phần yêu cầu${detail ? `: ${detail}` : ""}`;
    if (code === "request_not_met") return `Chưa đúng yêu cầu${detail ? `: ${detail}` : ""}`;
    // D-05: lượt kiểm yêu cầu của hệ thống bị lỗi, yêu cầu chưa được kiểm (không phải "cần xem kỹ")
    if (code === "request_unchecked") return "Chưa kiểm được yêu cầu";
    // 22/11 (góp ý giáo viên 21/11): câu cùng dạng trong đề là lưu ý, không phải "cần xem kỹ"; giáo viên tự chọn giữ
    // cả hai hay Đổi câu khác
    if (code === "similar_in_exam") {
        const position = /^(I{1,3})\.(\d+)$/.exec(detail.trim());
        return position ? `Cùng dạng với Phần ${position[1]} – Câu ${position[2]} trong đề` : "Cùng dạng với một câu khác trong đề";
    }
    return reason;
}

/** Yêu cầu của câu (requests) hoặc yêu cầu thừa hưởng của chỗ (inheritedRequests), docs/api mục 7.4. */
export interface TeacherRequests {
    codes: TeacherRequestCode[];
    text?: string | null;
    scope: "replace" | "cell" | "exam";
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

export interface ChangeQuestionRequest {
    /** Câu hỏi cần được thay thế. Backend nên xác minh lại câu hỏi này. */
    question: ExamQuestionItem;
    /** Yêu cầu bổ sung do giáo viên nhập. */
    instruction: string;
    /** Nguồn đổi: 'generate' (mặc định) sinh mới qua AI, hoặc 'bank' lấy từ ngân hàng */
    source?: "generate" | "bank";
    /** Lý do giáo viên chọn khi Đổi (tùy chọn); không chọn thì không gửi. */
    reason?: ReplaceReason;
    /**
     * Chip yêu cầu của lượt Đổi; chỉ có khi giáo viên đặt yêu cầu KHÁC yêu cầu thừa hưởng của ô/đề (kể cả [] khi bỏ hết chip),
     * để backend coi là tự đặt yêu cầu. Không có thì câu mới theo yêu cầu thừa hưởng (D-08).
     */
    requestCodes?: TeacherRequestCode[];
}

/** Lý do Đổi câu, khớp CHECK chk_esa_replace_reason (V25) và docs/contract/enums.md. */
export type ReplaceReason =
    | "wrong_answer"
    | "ambiguous_options"
    | "off_matrix"
    | "duplicate_pattern"
    | "latex_format"
    | "too_hard"
    | "too_easy"
    | "other";

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
    failureReason?: ExamSlotFailureReason;
    /** Cờ thông tin (không phải "cần xem kỹ"), luôn có: request_partial: …, request_not_met: …, request_unchecked */
    infoReasons?: string[];
    requests?: TeacherRequests;
    inheritedRequests?: TeacherRequests;
    /** Chỉ có trong kết quả Giữ câu (docs/api mục 7.5): câu vừa lưu vào ngân hàng chung hay riêng của thầy/cô. */
    bankVisibility?: BankVisibility;
    /** Ảnh có URL ký ngắn hạn cho người xem đề; chỉ chỗ `source = "bank"` có hình (G0 H3). Vắng khi rỗng. */
    images?: QuestionImageInfo[];
}

/** Ngân hàng chung (mọi giáo viên dùng lại được) hoặc riêng (chỉ người lưu thấy). */
export type BankVisibility = "shared" | "private";

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
    challenge?: ExamChallenge;
    /** Cấu hình hệ thống: câu không cờ khi Giữ vào ngân hàng chung ("shared") hay riêng ("private"). */
    bankKeepVisibility?: BankVisibility;
    /** Tổng điểm phần trắc nghiệm theo thang điểm của hệ thống (tính cả chỗ chưa có câu). */
    totalScore?: number;
}

/** Mức thử thách của đề (12/11): "advanced" thêm yêu cầu nâng cao cho câu mức Vận dụng. */
export type ExamChallenge = "standard" | "advanced";

export const EXAM_CHALLENGE_OPTIONS: { label: string; value: ExamChallenge }[] = [
    { label: "Chuẩn", value: "standard" },
    { label: "Nâng cao (câu Vận dụng khó hơn)", value: "advanced" },
];

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

/** Trạng thái soạn đề (docs/api mục 7.3): còn chỗ đang soạn, đủ câu, hoặc hết việc nhưng có chỗ lỗi. */
export type ExamBuildStatus = "generating" | "ready" | "partial";

/** Response của GET /api/exams/{id}/progress (docs/api mục 7.3). */
export interface ExamProgress {
    examId: number;
    status: ExamBuildStatus;
    total: number;
    ready: number;
    generating: number;
    failed: number;
    fromBank: number;
    /** Có khi các chỗ đang soạn bị hoãn vì AI hết lượt trong ngày: "Hết lượt AI hôm nay, hệ thống chạy tiếp lúc HH:mm". */
    notice?: string;
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
    challenge?: ExamChallenge;
    requestCodes?: TeacherRequestCode[];
    /** false: mọi câu soạn mới, không lấy câu đã lưu trong ngân hàng (mặc định true). */
    useBank?: boolean;
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
    challenge?: ExamChallenge;
}

export interface ReplaceQuestionParams {
    source?: "generate" | "bank";
    instruction?: string;
    reason?: ReplaceReason;
    requestCodes?: TeacherRequestCode[];
}

export interface CellContextInput {
    cellId: number;
    materialIds?: number[];
    extraInstruction?: string;
    requestCodes?: TeacherRequestCode[];
}

export interface BuildExamRequest {
    matrixId: number;
    title?: string;
    materialIds?: number[];
    cells?: CellContextInput[];
    challenge?: ExamChallenge;
    requestCodes?: TeacherRequestCode[];
    /** false: mọi câu soạn mới, không lấy câu đã lưu trong ngân hàng (mặc định true). */
    useBank?: boolean;
}
