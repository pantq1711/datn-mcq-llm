import type { ExamMatrixSummary } from "../types/examMatrix.types";

/** Dòng bảng ma trận: kèm tên môn đã tra từ danh mục. */
export type MatrixRow = ExamMatrixSummary & { subjectName: string };

/** Bỏ dấu tiếng Việt để tìm kiếm linh hoạt hơn (ví dụ: "ma tran" tìm được "Ma trận"). */
function normalizeSearchValue(value: string): string {
    return value
        .normalize('NFD')
        .replace(/[̀-ͯ]/g, '')
        .replace(/đ/g, 'd')
        .replace(/Đ/g, 'D')
        .toLocaleLowerCase('vi-VN')
        .trim();
}

/** Gắn tên môn cho từng ma trận; chưa có tên thì ghi "Môn #id". */
export function toMatrixRows(matrices: ExamMatrixSummary[], subjectNames: Record<number, string>): MatrixRow[] {
    return matrices.map((m) => ({
        ...m,
        subjectName: subjectNames[m.subjectId] ?? `Môn #${m.subjectId}`,
    }));
}

/**
 * Lọc ma trận theo từ khóa (tên, môn, lớp; không phân biệt dấu). Dùng chung cho bảng và số đếm trên tiêu đề card
 * của trang Quản lý ma trận để hai chỗ luôn khớp nhau.
 */
export function filterMatrixRows(rows: MatrixRow[], searchText: string): MatrixRow[] {
    const keyword = normalizeSearchValue(searchText);
    if (!keyword) return rows;

    return rows.filter((matrix) => {
        const searchableText = normalizeSearchValue(
            [
                matrix.name,
                matrix.subjectName,
                // Bảng ghi "Lớp 8"; vẫn tìm được theo cách gõ cũ "khối 8"
                matrix.gradeLevel == null ? '' : `lop ${matrix.gradeLevel} khoi ${matrix.gradeLevel}`,
            ].join(' ')
        );
        return searchableText.includes(keyword);
    });
}
