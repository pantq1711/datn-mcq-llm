// Vết QC của một chỗ: GET /api/exams/{id}/slots/{slotId}/qc-trace (docs/api mục 7.7, 7.7a)

export interface QcUserRef {
    id: number | null;
    name: string;
}

export interface QcJob {
    jobId: number;
    createdAt?: string;
    status: string; // pending, running, done, failed...
    attempt?: number;
    error?: string | null;
}

export interface QcFormat {
    passed: boolean;
    errors: string[];
    warnings: string[];
    autoRepairs: string[];
    checkedAt?: string | null;
}

export interface QcDedup {
    passed: boolean;
    similarity: number | null;
    threshold: number | null;
    checkedAt?: string | null;
    comparedTo?: { questionId: number; inThisExam: boolean; inBank: boolean } | null;
}

export type QcAnswerCheckState = "consistent" | "inconsistent" | "error" | "pending" | "not_run";

export interface QcAnswerCheck {
    state: QcAnswerCheckState;
    /** Chỉ khi not_run: format_failed, duplicate, dedup_not_concluded, solver_not_run, superseded, not_recorded. */
    reason?: string | null;
    judgeModel?: string | null;
    judgeAnswer?: string | null;
    judgeSaKind?: string | null;
    judgeSaAnswer?: string | null;
    judgeLabels?: Record<string, string> | string[] | null;
    solverReasoning?: string | null;
    error?: string | null;
    checkedAt?: string | null;
    job?: QcJob | null;
}

export type QcGroundingState = "supported" | "not_supported" | "unknown" | "pending" | "error" | string;

export interface QcGrounding {
    state: QcGroundingState;
    reason?: string | null;
    evidenceQuote?: string | null;
    evidenceFound?: boolean | null;
    judgeModel?: string | null;
    checkedAt?: string | null;
    error?: string | null;
    job?: QcJob | null;
}

/** Mỗi bước là null khi câu chưa có dòng QC đó (ví dụ câu dựng tay). */
export interface QcSteps {
    format: QcFormat | null;
    dedup: QcDedup | null;
    answerCheck: QcAnswerCheck | null;
    /** null với câu không dùng tài liệu bài học. */
    grounding?: QcGrounding | null;
}

export interface QcAnswer {
    correctOption?: string | null;
    saAnswer?: string | null;
    labels?: Record<string, string> | string[] | null;
}

export interface QcVersion {
    questionId: number;
    kind: "llm" | "teacher_edit";
    createdAt?: string;
    status: string; // generated, format_failed, duplicate, qc_passed, rejected, in_bank
    current: boolean;
    editedBy?: QcUserRef | null;
    changedFields?: string[] | null;
    editDistance?: number | null;
    answer?: QcAnswer | null;
    qc?: QcSteps | null;
    reviewReasons?: string[];
}

/** Một lần sinh (job sinh câu của chỗ) hoặc chuỗi phiên bản của câu ngân hàng. */
export interface QcLineage {
    origin: "generated" | "bank";
    job: QcJob | null;
    versions: QcVersion[];
}

export interface QcAction {
    id: number;
    action: "edit" | "replace" | "keep" | string;
    at: string;
    actor?: QcUserRef | null;
    questionId?: number | null;
    bankItemId?: number | null;
    changedFields?: string[] | null;
    editDistance?: number | null;
    instruction?: string | null;
}

export interface QcTraceResponse {
    examId: number;
    slotId: number;
    cellId: number;
    slotNo: number;
    part: string;
    topic?: string | null;
    questionType: string;
    state: string;
    source?: string | null;
    currentQuestionId?: number | null;
    bankItemId?: number | null;
    attempts?: number;
    solverRetries?: number;
    lineages: QcLineage[];
    actions: QcAction[];
}
