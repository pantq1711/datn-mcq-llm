export type GenerationStatus = "IDLE" | "RUNNING" | "COMPLETED" | "FAILED";

export type GenerationResultStatus = "SUCCESS" | "FAILED";

export interface GenerationResultItem {
    id: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctOption: "A" | "B" | "C" | "D";
    status?: GenerationResultStatus;
    errorMessage?: string;
}

export interface GenerateQuestionPayload {
    lessonContent: string;
    selectedSegments: Array<{
        id: string;
        text: string;
        startOffset: number;
        endOffset: number;
    }>;
    questionCount: number;
    questionMatrix: Array<{
        topicId: string;
        topicName: string;
        cognitiveLevel: "BIET" | "HIEU" | "VAN_DUNG";
        questionCount: number;
    }>;
}

export interface GenerationResultResponse {
    taskId: string | number;
    status: GenerationStatus;
    questions: GenerationResultItem[];
}

export interface GenerateResponse {
    taskId: string | number;
    message?: string;
    status: string;
}

export interface GenerationStatusResponse {
    taskId: string | number;
    status: GenerationStatus;
    completedCount: number;
    totalCount: number;
    currentQuestion?: number;
    errorMessage?: string;
}

export interface Subject {
    id: number;
    code: string;
    name: string;
    description?: string;
}

export interface SubjectOption {
    label: string;
    value: string | number;
}

export type SourceType = "TEXT" | "WORD" | "PDF" | "IMAGE";

export interface GenerationHistoryItem {
    id: number;
    sourceName: string;
    sourceType: SourceType;
    selectedSegmentCount: number;
    subject: string;
    strategy: string;
    model: string;
    temperature: number;
    questionCount: number;
    status: GenerationStatus;
    startedAt: string;
    errorMessage?: string;
}

// export interface GenerationJob {
//     id: number;
//     status: "COMPLETED" | "RUNNING" | "FAILED";
//     run_id: number;
//     template_id: number;
//     question_id: number;
//     target_difficulty: string;
//     attempt: number;
//     last_error: string;
//     token_count: number;
//     latency_ms: number;
//     cost_usd: number;
//     created_at: string;
//     updated_at: string;
// }

export interface GenerateQuestionPayload {
    subjectId?: number;
    templateId?: number;
    lessonContent: string;
    selectedSegments: Array<{
        id: string;
        text: string;
        startOffset: number;
        endOffset: number;
    }>;
    questionCount: number;
    questionMatrix: Array<{
        topicId: string;
        topicName: string;
        cognitiveLevel: "BIET" | "HIEU" | "VAN_DUNG";
        questionCount: number;
    }>;
}


export interface QuestionTemplate {
    id: number;
    subjectId: number;
    subjectName?: string;
    sourceQuestion: string;
    version?: number;
    isActive?: boolean;
}

export interface TemplateOption {
    id: number;
    label: string;
    sourceQuestion: string;
    subject: string;
    version?: number;
}
