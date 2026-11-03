export type MaterialKind = "docx" | "pdf" | "txt";

/**
 * Mã cảnh báo trích xuất (docs/api mục 9). MATHTYPE_FORMULAS_DROPPED không còn được sinh ra nhưng tài liệu tải lên trước
 * đó vẫn lưu mã này, nên giữ để hiển thị.
 */
export type MaterialWarningCode =
    "MATHTYPE_UNREADABLE" | "MATHTYPE_FORMULAS_DROPPED" | "FORMULA_LOSS_HIGH" | "FORMULA_IMAGES_NOT_READ"
    | "PDF_TEXT_QUALITY_LOW";

export interface MaterialWarning {
    code: string;
    message: string;
    count?: number;
    total?: number;
}

export interface MaterialDto {
    id: number;
    fileName: string;
    kind: MaterialKind;
    sizeBytes: number;
    charCount: number;
    pageCount?: number;
    sha256: string;
    extractMethod?: string;
    warnings?: MaterialWarning[];
    createdAt: string;
    textPreview?: string;
}

export interface MaterialPageDto {
    items: MaterialDto[];
    page: number;
    size: number;
    totalItems: number;
    totalPages: number;
}
