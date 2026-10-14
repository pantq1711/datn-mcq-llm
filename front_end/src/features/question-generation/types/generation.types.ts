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
    selectedTemplateId: number | null;
    strategy: string | null;
    model: string;
    questionCount: number;
    temperature: number;
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