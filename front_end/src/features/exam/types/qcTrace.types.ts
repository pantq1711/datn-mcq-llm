export interface QcTraceStep {
    timestamp: string;          // ISO datetime
    version: number;
    action: string;             // ví dụ: "generate", "replace", "keep"
    details?: string;           // mô tả tùy chọn
}
export interface QcTraceResponse {
    slotId: string;
    steps: QcTraceStep[];
}
