export interface ContentSource {
    /** Tên hoặc tiêu đề bài học/tài liệu */
    title: string;

    /** ID tham chiếu đến bài học, nếu có */
    lessonId?: number;

    /** Toàn bộ nội dung gốc để tái lập thực nghiệm */
    fullContent: string;

    /** Hash của nội dung để kiểm tra tính nhất quán */
    contentHash: string;
}

export interface SelectedSegment {
    id: string;
    /** Nội dung văn bản của đoạn */
    text: string;

    /** Vị trí bắt đầu trong fullContent */
    startOffset: number;

    /** Vị trí kết thúc trong fullContent */
    endOffset: number;
}

export interface ExperimentRun {
    id: number;
    subject: string;
    modelName: string;
    strategy: string;
    promptVersion: string;
    questionCount: number;
    status: "COMPLETED" | "RUNNING" | "FAILED";
    startedAt: string;

    /** Thời điểm kết thúc thực nghiệm */
    endedAt?: string;

    /** Nhiệt độ sinh câu hỏi */
    temperature?: number;

    /** Nội dung prompt được sử dụng */
    promptText?: string;

    /** Nguồn nội dung được sử dụng */
    contentSource: ContentSource;

    /** Các đoạn được chọn làm phạm vi sinh câu hỏi */
    selectedSegments: SelectedSegment[];
}

export interface CreateExperimentPayload {
    subjectId: number;
    modelName: string;
    strategy: string;
    promptVersion: string;
    temperature: number;
    questionCount: number;
    // Nguồn nội dung & phạm vi
    contentSource: ContentSource;
    selectedSegments: SelectedSegment[];
}
