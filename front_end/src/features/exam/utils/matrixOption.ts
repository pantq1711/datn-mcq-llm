import dayjs from "dayjs";

import type { ExamMatrixSummary } from "../../exam-matrix/types/examMatrix.types";

export interface MatrixOptionText {
    /** Chữ hiện trong ô sau khi chọn (một dòng): tên · Lớp · (môn) · ngày tạo, đủ phân biệt các ma trận trùng tên. */
    label: string;
    /** Dòng phụ trong danh sách thả xuống: giờ tạo · số ô · số đề đã dùng. */
    meta: string;
    /** Chuỗi chữ thường để tìm trong ô chọn (tên, lớp, ngày tạo). */
    searchText: string;
}

/**
 * Chữ của một ma trận trong ô "Chọn ma trận đề thi" (rà soát E1). Ngày giờ theo một dạng chung "DD/MM/YYYY HH:mm" (A6);
 * thiếu lớp hoặc ngày tạo thì bỏ hẳn phần đó (E2). `subjectName` chỉ truyền khi hệ thống có từ 2 môn trở lên.
 * Danh sách ma trận không có tổng số câu (MatrixSummaryDto chỉ có cellCount), nên ghi số ô.
 */
export function describeMatrixOption(matrix: ExamMatrixSummary, subjectName?: string): MatrixOptionText {
    const created = matrix.createdAt ? dayjs(matrix.createdAt) : null;
    const createdValid = created !== null && created.isValid();
    const grade = matrix.gradeLevel != null ? `Lớp ${matrix.gradeLevel}` : null;

    const label = [
        matrix.name,
        grade,
        subjectName || null,
        createdValid ? created.format("DD/MM/YYYY") : null,
    ].filter(Boolean).join(" · ");

    const meta = [
        createdValid ? `Tạo lúc ${created.format("DD/MM/YYYY HH:mm")}` : null,
        `${matrix.cellCount ?? 0} ô`,
        (matrix.examCount ?? 0) > 0 ? `Đã dùng cho ${matrix.examCount} đề` : "Chưa dùng cho đề nào",
    ].filter(Boolean).join(" · ");

    const searchText = [matrix.name, grade, subjectName, createdValid ? created.format("DD/MM/YYYY HH:mm") : null]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase("vi-VN");

    return { label, meta, searchText };
}
