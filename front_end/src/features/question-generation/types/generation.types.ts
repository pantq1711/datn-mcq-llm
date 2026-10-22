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
