export type MaterialKind = "docx" | "pdf" | "txt";

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
