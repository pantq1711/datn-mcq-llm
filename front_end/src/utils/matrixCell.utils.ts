import type { ExamMatrixCellInput, ExamMatrixCellPayload, MatrixDifficulty, MatrixPart, MatrixQuestionType } from "../features/exam-matrix/types/examMatrix.types";
import { QUESTION_TYPE_LABELS } from "./exam.utils";

// Phần I chỉ mcq, II chỉ tf, III chỉ sa (docs/api mục 7.1)
export const PART_BY_TYPE: Record<MatrixQuestionType, MatrixPart> = { mcq: "I", tf: "II", sa: "III" };

export const MATRIX_TYPE_LABELS: Record<MatrixQuestionType, string> = {
    // Tên ba Phần theo cấu trúc đề 2025, dùng giống nhau ở mọi trang
    mcq: `${QUESTION_TYPE_LABELS.mcq} (Phần I)`,
    tf: `${QUESTION_TYPE_LABELS.tf} (Phần II)`,
    sa: `${QUESTION_TYPE_LABELS.sa} (Phần III)`,
};

export const MATRIX_LEVEL_LABELS: Record<MatrixDifficulty, string> = {
    easy: "Biết",
    medium: "Hiểu",
    hard: "Vận dụng",
};

export const MATRIX_LEVELS: MatrixDifficulty[] = ["easy", "medium", "hard"];

// Mặc định của backend khi tf không có statementDifficulties (docs/api mục 7.2a)
export const DEFAULT_TF_LEVELS: MatrixDifficulty[] = ["easy", "easy", "medium", "hard"];

export function createEmptyCell(questionType: MatrixQuestionType = "mcq"): ExamMatrixCellInput {
    return withType({ part: "I", questionType, questionCount: 1, topic: "", content: "", learningOutcomes: {} }, questionType);
}

/** Đổi dạng câu: đặt lại phần, mức (tf dùng 4 mức ý a-d, mcq/sa dùng một mức). */
export function withType(cell: ExamMatrixCellInput, questionType: MatrixQuestionType): ExamMatrixCellInput {
    const { difficulty, statementDifficulties, ...rest } = cell;
    if (questionType === "tf") {
        return { ...rest, questionType, part: PART_BY_TYPE.tf, statementDifficulties: statementDifficulties ?? [...DEFAULT_TF_LEVELS] };
    }
    return { ...rest, questionType, part: PART_BY_TYPE[questionType], difficulty: difficulty ?? "easy" };
}

/** Các mức ô dùng, theo thứ tự Biết → Vận dụng: cần yêu cầu cần đạt cho từng mức này. */
export function levelsUsed(cell: ExamMatrixCellInput): MatrixDifficulty[] {
    const used = cell.questionType === "tf" ? cell.statementDifficulties ?? [] : cell.difficulty ? [cell.difficulty] : [];
    return MATRIX_LEVELS.filter((level) => used.includes(level));
}

/** Ô đọc về → ô gửi lên: bỏ id, position, templateId. */
export function toCellInput(cell: ExamMatrixCellPayload): ExamMatrixCellInput {
    const { id: _id, position: _position, templateId: _templateId, ...input } = cell;
    return input;
}

/** Chuẩn hóa trước khi gửi: trim, chỉ giữ yêu cầu cần đạt của mức ô dùng, đúng trường theo dạng câu. */
export function normalizeCell(cell: ExamMatrixCellInput): ExamMatrixCellInput {
    const learningOutcomes: ExamMatrixCellInput["learningOutcomes"] = {};
    levelsUsed(cell).forEach((level) => {
        learningOutcomes[level] = cell.learningOutcomes[level]?.trim() ?? "";
    });
    const base = {
        part: PART_BY_TYPE[cell.questionType],
        questionType: cell.questionType,
        questionCount: cell.questionCount,
        topic: cell.topic.trim(),
        content: cell.content?.trim() || null,
        learningOutcomes,
    };
    return cell.questionType === "tf"
        ? { ...base, statementDifficulties: cell.statementDifficulties }
        : { ...base, difficulty: cell.difficulty };
}

/** id của thẻ ô thứ `index` (từ 0) trong trang sửa ma trận, để cuộn tới thẻ ô lỗi. */
export const matrixCellAnchorId = (index: number) => `matrix-cell-${index}`;

export interface CellError {
    /** Vị trí ô lỗi đầu tiên (từ 0); null khi lỗi không thuộc ô nào ("cần ít nhất một ô"). */
    index: number | null;
    /** Câu báo đầy đủ kèm số ô, như backend: "Ô 2: chưa nhập chủ đề." */
    message: string;
    /** Lý do không kèm số ô, hiện ngay trong thẻ ô lỗi: "Chưa nhập chủ đề." */
    reason: string;
}

/** Lý do ô không hợp lệ (viết thường đầu câu để ghép sau "Ô k: "); null nếu ô hợp lệ. Kiểm như backend. */
function cellIssue(cell: ExamMatrixCellInput): string | null {
    if (!cell.topic?.trim()) return "chưa nhập chủ đề.";
    if (!Number.isInteger(cell.questionCount) || cell.questionCount < 1 || cell.questionCount > 50) {
        return "số câu phải từ 1 đến 50.";
    }
    if (cell.questionType === "tf") {
        if (cell.statementDifficulties?.length !== 4 || cell.statementDifficulties.some((d) => !d)) {
            return "câu đúng/sai phải chọn đủ mức cho 4 ý a-d.";
        }
    } else if (!cell.difficulty) {
        return "chưa chọn mức độ.";
    }
    const missing = levelsUsed(cell).filter((level) => !cell.learningOutcomes[level]?.trim());
    if (missing.length > 0) {
        return `thiếu yêu cầu cần đạt mức ${missing.map((l) => MATRIX_LEVEL_LABELS[l]).join(", ")}.`;
    }
    return null;
}

/** Ô lỗi đầu tiên kèm vị trí, để trang cuộn tới thẻ ô và đánh dấu lỗi trong thẻ (rà soát D9); null nếu mọi ô hợp lệ. */
export function findCellError(cells: ExamMatrixCellInput[]): CellError | null {
    if (cells.length === 0) {
        const message = "Ma trận cần ít nhất một ô.";
        return { index: null, message, reason: message };
    }
    for (let i = 0; i < cells.length; i++) {
        const issue = cellIssue(cells[i]);
        if (issue) {
            return { index: i, message: `Ô ${i + 1}: ${issue}`, reason: issue.charAt(0).toUpperCase() + issue.slice(1) };
        }
    }
    return null;
}

/** Kiểm tra như backend, báo theo số ô ("Ô 2: ..."); trả null nếu hợp lệ. */
export function validateCells(cells: ExamMatrixCellInput[]): string | null {
    return findCellError(cells)?.message ?? null;
}

/** Tổng như backend: số câu theo phần, lệnh hỏi theo mức (mỗi câu tf là 4 lệnh hỏi). */
export function summarizeCells(cells: ExamMatrixCellInput[]) {
    const questionsByPart: Record<MatrixPart, number> = { I: 0, II: 0, III: 0 };
    const commandsByLevel: Record<MatrixDifficulty, number> = { easy: 0, medium: 0, hard: 0 };
    cells.forEach((cell) => {
        const count = Number.isFinite(cell.questionCount) ? Math.max(0, cell.questionCount) : 0;
        questionsByPart[PART_BY_TYPE[cell.questionType]] += count;
        if (cell.questionType === "tf") {
            (cell.statementDifficulties ?? []).forEach((level) => {
                if (level) commandsByLevel[level] += count;
            });
        } else if (cell.difficulty) {
            commandsByLevel[cell.difficulty] += count;
        }
    });
    const totalQuestions = questionsByPart.I + questionsByPart.II + questionsByPart.III;
    const totalCommands = commandsByLevel.easy + commandsByLevel.medium + commandsByLevel.hard;
    return { questionsByPart, commandsByLevel, totalQuestions, totalCommands };
}
