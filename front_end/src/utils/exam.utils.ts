import type { ExamKind, ExamProgress, ExamQuestionDifficulty, ExamQuestionItem, ExamQuestionType, ExamStatus, ReplaceReason } from "../features/exam/types/exam.types";

export const difficultyConfig: Record<
    ExamQuestionDifficulty,
    {
        label: string;
        color: string;
    }
> = {
    easy: {
        label: "Biết",
        color: "green",
    },
    medium: {
        label: "Hiểu",
        color: "orange",
    },
    hard: {
        label: "Vận dụng",
        color: "red",
    },
};

const EXPLANATION_DRAFT_REASON = "Lời giải có thể còn phần nháp, thầy/cô xem lại lời giải.";

const UNCLASSIFIED_LEVEL = { label: "Chưa phân loại", color: "default" };

/**
 * Mức độ để hiển thị. Backend chỉ trả easy / medium / hard (enum Difficulty); thiếu hoặc giá trị lạ
 * (dữ liệu cũ) thì hiện "Chưa phân loại", không bao giờ hiện mã thô hay để trống.
 */
export function describeLevel(level?: string | null): { label: string; color: string } {
    if (level && Object.prototype.hasOwnProperty.call(difficultyConfig, level)) {
        return difficultyConfig[level as ExamQuestionDifficulty];
    }
    return UNCLASSIFIED_LEVEL;
}

// Lý do "cần xem kỹ": đủ danh sách mã ở docs/contract/enums.md (mục 3) và docs/api mục 7.4, 7.5a, 7.7a
const reviewReasonLabels: Record<string, string> = {
    solver_inconsistent: "Hệ thống tự giải lại và ra đáp án khác. Thầy/cô kiểm tra lại đáp án.",
    solver_multiple_correct: "Có thể có hơn một phương án đúng. Thầy/cô kiểm tra lại.",
    solver_no_correct: "Hệ thống không tìm được phương án đúng. Thầy/cô kiểm tra lại.",
    solver_ambiguous: "Đề có thể hiểu theo nhiều cách. Thầy/cô kiểm tra lại đề.",
    solver_unparseable: "Hệ thống tự giải ra đáp số không so sánh được. Thầy/cô kiểm tra lại đáp số.",
    statement_undecidable: "Có ý hệ thống không xác định được đúng hay sai. Thầy/cô kiểm tra lại các ý.",
    solver_pending: "Đang tự kiểm tra lại đáp án…",
    solver_failed: "Chưa tự kiểm tra được đáp án, thầy/cô tự kiểm tra.",
    solver_not_run: "Chưa tự kiểm tra được đáp án, thầy/cô tự kiểm tra.",
    dedup_not_run: "Chưa kiểm tra được câu này có trùng câu khác không.",
    dedup_not_concluded: "Chưa kiểm tra được câu này có trùng câu khác không.",
    explanation_draft_suspect: EXPLANATION_DRAFT_REASON,
    grounding_not_supported: "Đáp án hoặc lời giải không khớp tài liệu đính kèm. Thầy/cô kiểm tra lại.",
    grounding_unknown: "Nội dung câu không có trong tài liệu đính kèm. Kiểm tra câu có đúng chương trình không.",
    grounding_pending: "Đang đối chiếu câu với tài liệu đính kèm…",
    grounding_failed: "Chưa đối chiếu được câu với tài liệu, thầy/cô tự kiểm tra.",
};

/** Lý do Đổi câu theo thứ tự hiển thị; nhãn theo docs/contract/enums.md (exam_slot_action.replace_reason). */
export const REPLACE_REASON_OPTIONS: { value: ReplaceReason; label: string }[] = [
    { value: "wrong_answer", label: "Đáp án sai" },
    { value: "ambiguous_options", label: "Phương án mơ hồ / nhiều đáp án" },
    { value: "off_matrix", label: "Lệch ma trận" },
    { value: "duplicate_pattern", label: "Trùng khuôn với câu khác" },
    { value: "latex_format", label: "Lỗi công thức / định dạng" },
    { value: "too_hard", label: "Quá khó" },
    { value: "too_easy", label: "Quá dễ" },
    { value: "other", label: "Lý do khác" },
];

// Kết luận đặc biệt của bước tự giải (judge_answer): docs/contract/enums.md, bảng "Số phương án đúng"
const judgeAnswerLabels: Record<string, string> = {
    multiple: "Nhiều đáp án đúng",
    none: "Không có đáp án đúng",
};

/** Đáp án hệ thống tự giải ra, nói bằng chữ của giáo viên; chữ cái A-D giữ nguyên. */
export function describeJudgeAnswerValue(value: string): string {
    return judgeAnswerLabels[value.trim().toLowerCase()] ?? value;
}

const GENERIC_REVIEW_REASON = "Hệ thống đề nghị thầy/cô xem kỹ câu này";

/** Lý do chỗ không soạn được câu (state = "failed"); mã lạ thì chỉ dặn bấm Đổi câu. */
export function describeFailureReason(reason?: string | null): string {
    switch (reason) {
        // G20: nói theo giáo viên, không nhắc "AI", số lần thử (G21) hay dấu nháy
        case "llm_error":
            return "Chưa soạn được câu này vì hệ thống soạn câu đang bận. Thầy/cô bấm Soạn lại.";
        case "qc_exhausted":
            return "Hệ thống chưa soạn được câu đạt yêu cầu. Thầy/cô bấm Soạn lại, có thể ghi thêm yêu cầu cho câu mới.";
        default:
            return "Chưa soạn được câu này. Thầy/cô bấm Soạn lại.";
    }
}

/**
 * Tên dạng câu và tên Phần dùng chung mọi trang (G25, theo cấu trúc đề 2025). Đổi tên thì chỉ sửa ở đây.
 */
export const QUESTION_TYPE_LABELS: Record<ExamQuestionType, string> = {
    mcq: "Trắc nghiệm nhiều phương án",
    tf: "Trắc nghiệm đúng/sai",
    sa: "Trả lời ngắn",
};

/** Dạng ngắn, viết thường, cho dòng đếm và cột hẹp: "12 trắc nghiệm · 2 đúng/sai · 4 trả lời ngắn". */
export const QUESTION_TYPE_SHORT_LABELS: Record<ExamQuestionType, string> = {
    mcq: "trắc nghiệm",
    tf: "đúng/sai",
    sa: "trả lời ngắn",
};

/** Tiêu đề từng Phần của đề, như file Word: "Phần II. Trắc nghiệm đúng/sai". */
export const PART_TITLES: Record<string, string> = {
    I: `Phần I. ${QUESTION_TYPE_LABELS.mcq}`,
    II: `Phần II. ${QUESTION_TYPE_LABELS.tf}`,
    III: `Phần III. ${QUESTION_TYPE_LABELS.sa}`,
};

/** Phần → dạng câu của Phần đó (Phần I trắc nghiệm, II đúng/sai, III trả lời ngắn). */
export const PART_QUESTION_TYPE: Record<string, ExamQuestionType> = { I: "mcq", II: "tf", III: "sa" };

export const examKindConfig: Record<ExamKind, { label: string; color: string }> = {
    matrix: { label: "Theo ma trận", color: "blue" },
    lesson: { label: "Theo bài", color: "purple" },
    manual: { label: "Đề cũ", color: "default" },
};

export const examStatusConfig: Record<ExamStatus, { label: string; color: string }> = {
    draft: { label: "Bản nháp", color: "default" },
    confirmed: { label: "Đã xác nhận", color: "success" },
};

const GENERIC_FORMAT_WARNING = "Có điểm trình bày cần xem lại";

// Đầu câu các lỗi đáp số (ShortAnswerValue) đã đủ rõ cho giáo viên, không cần dịch lại
const readableAnswerErrors = [
    "không phân tích được",
    "dấu chấm có thể",
    "có chữ số 0",
    "số 0 mang dấu",
    "chuỗi dài quá",
    "số dưới dấu căn",
    "mẫu số bằng 0",
];

// Lỗi dạng đáp số (quá 4 ký tự, phân số, căn, đơn vị…): nhận ra theo phần đầu của thông báo backend ("Đáp số phải là"
// từ 2/11, "đáp án phải là" ở dữ liệu cũ), không theo phần còn lại, và hiện chữ của frontend để đổi câu chữ backend
// không làm mất chỗ nhận. Dùng chung cho lý do trên thẻ câu (describeFormatIssue) và lỗi dưới ô "Đáp số" của form sửa câu
// (EditQuestionForm), để hai chỗ nói cùng một câu.
export const ANSWER_FORMAT_START = /^đáp (?:số|án) phải là/i;
export const ANSWER_FORMAT_MESSAGE =
    "Đáp số phải là số nguyên hoặc số thập phân, tối đa 4 kí tự kể cả dấu trừ và dấu phẩy (ví dụ 12; -3,5; 0,25).";

/**
 * Đáp án câu trả lời ngắn mà YoungMix nhận được (26/11): số nguyên hoặc thập phân, tối đa 4 ký tự tính cả dấu "-" và ",",
 * như quy tắc STRICT của backend (docs/contract/enums.md, quyết định 27/10): bỏ cặp $...$ bao cả chuỗi, "−" thành "-",
 * "{,}" và dấu "." thành ","; không có chữ số 0 thừa ở đầu, không có "-0".
 */
export function isYoungMixShortAnswer(value: string | null | undefined): boolean {
    let s = (value ?? "").trim();
    if (s.length >= 4 && s.startsWith("$$") && s.endsWith("$$")) s = s.slice(2, -2);
    else if (s.length >= 2 && s.startsWith("$") && s.endsWith("$")) s = s.slice(1, -1);
    s = s.trim().replace(/−/g, "-").replace(/\{,\}/g, ",").replace(/\./g, ",");
    if (!/^-?[0-9]+(,[0-9]+)?$/.test(s) || s.length > 4) return false;
    const unsigned = s.startsWith("-") ? s.slice(1) : s;
    const integerPart = unsigned.split(",")[0];
    if (integerPart.length > 1 && integerPart.startsWith("0")) return false;
    return !(s.startsWith("-") && !/[1-9]/.test(unsigned));
}

// Tên trường backend nhắc tới trong cảnh báo định dạng → chỗ trong câu, nói theo giáo viên
const warningFieldLabels: Record<string, string> = {
    question_text: "câu dẫn",
    option_a: "phương án A",
    option_b: "phương án B",
    option_c: "phương án C",
    option_d: "phương án D",
    statement_a: "ý a)",
    statement_b: "ý b)",
    statement_c: "ý c)",
    statement_d: "ý d)",
    explanation: "lời giải",
    answer: "đáp số",
};

// Tên trường backend ghi ở "changedFields" của bản sửa → chữ cho giáo viên
const changedFieldLabels: Record<string, string> = {
    ...warningFieldLabels,
    correct_option: "đáp án đúng",
    sa_answer: "đáp số",
    label_a: "Đúng/Sai của ý a)",
    label_b: "Đúng/Sai của ý b)",
    label_c: "Đúng/Sai của ý c)",
    label_d: "Đúng/Sai của ý d)",
};

/** Các chỗ giáo viên đã sửa trong câu, viết cho dễ đọc; tên lạ thì gọi chung là "nội dung khác". */
export function describeChangedFields(fields: string[]): string {
    return [...new Set(fields.map((f) => changedFieldLabels[f] ?? "nội dung khác"))].join(", ");
}

/**
 * Một cảnh báo định dạng đã dịch: câu hoàn chỉnh, hoặc mẫu câu có chỗ trống cho tên chỗ trong câu (để gộp nhiều chỗ cùng
 * mẫu thành một câu, G4). `key` phân biệt mẫu; `field` là trường backend (question_text, option_a…).
 */
type FormatIssueParts =
    | { kind: "text"; text: string }
    | { kind: "place"; key: string; field: string; render: (place: string) => string };

/**
 * Lỗi, cảnh báo và việc tự sửa của bước định dạng do backend ghi (nguồn: FormatChecker, LatexValidationRules,
 * ShortAnswerValue, LlmOutputRepair, TfJsonParser, TfExplanationLeak). Chỉ dịch các dạng đã biết; không bao giờ ghép
 * chuỗi thô của backend ra giao diện.
 */
export function describeFormatIssue(issue: string): string {
    const parts = formatIssueParts(issue);
    return parts.kind === "text" ? parts.text : parts.render(warningFieldLabels[parts.field]);
}

function formatIssueParts(issue: string): FormatIssueParts {
    const done = (value: string): FormatIssueParts => ({ kind: "text", text: value });
    const text = issue.trim();

    // "label: 4 ý cùng nhãn T|F": cả 4 ý đúng/sai cùng một đáp án
    const sameLabel = /^label:\s*4 ý cùng nhãn\s*([TF])\s*$/i.exec(text);
    if (sameLabel) {
        const same = sameLabel[1].toUpperCase() === "T" ? "Đúng" : "Sai";
        return done(`Cả 4 ý đúng/sai đều cùng là ${same} — nên kiểm lại đáp án từng ý.`);
    }

    // Lời giải còn phần nháp: cảnh báo không có tên trường đứng trước (bản giáo viên sửa)
    if (text.startsWith("Lời giải còn chứa phần nháp")) {
        return done(EXPLANATION_DRAFT_REASON);
    }

    // Hệ thống tự sửa lỗi gõ công thức ở đầu ra của AI (không nói chi tiết kỹ thuật)
    if (text.startsWith("json: đã sửa")) {
        return done("Hệ thống đã tự sửa lỗi gõ công thức trong câu.");
    }
    if (text.startsWith("Không giải mã được JSON")) {
        return done("Hệ thống không đọc được câu do AI soạn.");
    }
    if (text.startsWith("4 phương án")) {
        return done("Các phương án A, B, C, D có nội dung trùng nhau.");
    }
    if (text.startsWith("4 ý a, b, c, d")) {
        return done("Các ý a), b), c), d) có nội dung trùng nhau.");
    }

    const parts = /^([a-z_]+):\s*([\s\S]*)$/.exec(text);
    const field = parts?.[1];
    const detail = parts?.[2] ?? "";

    // Nhãn đúng/sai của từng ý bị trống hoặc không đọc được
    const labelField = field ? /^label_([a-d])$/.exec(field) : null;
    if (labelField) {
        return done(`Chưa rõ ý ${labelField[1]}) đúng hay sai.`);
    }

    const place = field ? warningFieldLabels[field] : undefined;
    if (!field || !place) {
        return done(GENERIC_FORMAT_WARNING);
    }
    // Mẫu câu có tên chỗ: gộp được khi nhiều chỗ cùng mẫu (G4)
    const placed = (key: string, render: (p: string) => string): FormatIssueParts => ({ kind: "place", key, field, render });

    if (field === "explanation" && detail.startsWith("lời giải nhắc tới mẫu nhãn")) {
        return done("Lời giải nhắc tới việc gán đáp án theo mẫu. Cần viết lại cho đúng từng ý.");
    }
    if (field === "explanation" && detail.startsWith("lời giải tự sửa lại")) {
        return done("Lời giải có phần tự sửa lại đề. Cần viết lại lời giải cuối cùng.");
    }
    if (detail.startsWith("nội dung trống")) {
        return placed("empty", (p) => `Chưa có nội dung ở ${p}.`);
    }

    if (field === "explanation" && detail.startsWith("lời giải có thể còn phần nháp")) {
        return done(EXPLANATION_DRAFT_REASON);
    }
    if (field === "answer" && detail.includes("phân số chưa tối giản")) {
        return done("Đáp số là phân số chưa tối giản. Thầy/cô kiểm tra lại đáp số.");
    }
    if (field.startsWith("statement_") && detail.startsWith("đã bỏ tiền tố")) {
        return done(`Ý ${field.slice("statement_".length)}) có ký hiệu đánh số thừa ở đầu nên hệ thống đã tự bỏ. Thầy/cô xem lại ý này.`);
    }
    // Chuỗi có từ 3 dấu $ trở lên: vị trí hệ thống tự đóng có thể sai chỗ (khác trường hợp thiếu đúng một dấu $)
    if (detail.startsWith("đã tự đóng $ khi chuỗi có")) {
        return placed("many-dollars", (p) => `Ở ${p} có nhiều dấu $ và hệ thống đã tự thêm một dấu để đóng công thức, có thể đóng sai chỗ. Thầy/cô kiểm tra lại công thức.`);
    }
    if (detail.startsWith("đã tự đóng")) {
        return placed("auto-closed", (p) => `Công thức ở ${p} chưa đóng đủ nên hệ thống đã tự sửa. Thầy/cô xem lại công thức.`);
    }
    if (detail.startsWith("đã tự bọc")) {
        return placed("auto-wrapped", (p) => `Hệ thống đã tự sửa cách gõ công thức ở ${p}. Thầy/cô xem lại.`);
    }
    if (detail.startsWith("có từ tiếng Anh")) {
        return done("Câu có từ tiếng Anh lẫn vào, thầy/cô xem lại câu chữ.");
    }
    if (detail.startsWith("Lệnh ")) {
        return placed("unknown-command", (p) => `Công thức ở ${p} có ký hiệu lạ, có thể gõ sai. Thầy/cô xem lại công thức.`);
    }
    if (field === "answer") {
        if (ANSWER_FORMAT_START.test(detail)) {
            return done(ANSWER_FORMAT_MESSAGE);
        }
        // Lỗi đáp số do backend viết sẵn bằng tiếng Việt dễ hiểu: bỏ tiền tố "answer:" rồi giữ nguyên câu
        if (readableAnswerErrors.some((start) => detail.startsWith(start))) {
            return done(detail.charAt(0).toUpperCase() + detail.slice(1));
        }
        return done("Đáp số chưa đúng dạng. Thầy/cô kiểm tra lại đáp số.");
    }
    // Còn lại của một trường đã biết là lỗi công thức (môi trường không hỗ trợ, ngoặc lệch, thiếu đối số…)
    return placed("bad-formula", (p) => `Công thức ở ${p} có phần trình bày chưa đúng. Thầy/cô xem lại công thức.`);
}

// Thứ tự các chỗ trong câu khi gộp: câu dẫn → phương án → ý → lời giải → đáp số
const FIELD_ORDER = [
    "question_text", "option_a", "option_b", "option_c", "option_d",
    "statement_a", "statement_b", "statement_c", "statement_d", "explanation", "answer",
];

/** Tên các chỗ: đủ A–D thì "cả 4 phương án", đủ 4 ý thì "cả 4 ý"; nối bằng dấu phẩy, chỗ cuối dùng "và". */
function joinPlaces(fields: string[]): string {
    const unique = [...new Set(fields)].sort((a, b) => FIELD_ORDER.indexOf(a) - FIELD_ORDER.indexOf(b));
    const names: string[] = [];
    const allOf = (group: string[]) => group.every((f) => unique.includes(f));
    const options = ["option_a", "option_b", "option_c", "option_d"];
    const statements = ["statement_a", "statement_b", "statement_c", "statement_d"];
    for (const field of unique) {
        if (options.includes(field) && allOf(options)) {
            if (field === "option_a") names.push("cả 4 phương án");
            continue;
        }
        if (statements.includes(field) && allOf(statements)) {
            if (field === "statement_a") names.push("cả 4 ý");
            continue;
        }
        names.push(warningFieldLabels[field]);
    }
    return names.length <= 1 ? (names[0] ?? "") : `${names.slice(0, -1).join(", ")} và ${names[names.length - 1]}`;
}

/**
 * Dịch nhiều cảnh báo định dạng, gộp các chỗ cùng mẫu câu thành một câu (G4): sáu cảnh báo "ký hiệu lạ" ở câu dẫn, 4
 * phương án và lời giải thành "Công thức ở câu dẫn, cả 4 phương án và lời giải có ký hiệu lạ…". Giữ thứ tự xuất hiện,
 * bỏ câu trùng chữ.
 */
export function describeFormatIssueList(issues: string[]): string[] {
    type Group = { render: (p: string) => string; fields: string[] };
    const slots: (string | Group)[] = [];
    const groups = new Map<string, Group>();
    for (const issue of issues) {
        const parts = formatIssueParts(issue);
        if (parts.kind === "text") {
            slots.push(parts.text);
            continue;
        }
        const group = groups.get(parts.key);
        if (group) {
            group.fields.push(parts.field);
        } else {
            const created = { render: parts.render, fields: [parts.field] };
            groups.set(parts.key, created);
            slots.push(created);
        }
    }
    const texts = slots.map((slot) => (typeof slot === "string" ? slot : slot.render(joinPlaces(slot.fields))));
    return [...new Set(texts)];
}

/** Một lý do cần xem kỹ đã dịch; `position` có với lý do "gần giống Phần P – Câu k" (để hiện liên kết, G5). */
export interface ReviewReasonEntry {
    text: string;
    position: ExamQuestionPosition | null;
}

/**
 * Dịch danh sách lý do cần xem kỹ: cảnh báo định dạng (`warning: …`) gộp theo mẫu câu (G4), lý do khác dịch từng cái;
 * bỏ câu trùng chữ (G3), giữ thứ tự xuất hiện.
 */
export function describeReviewReasonList(reasons: string[]): ReviewReasonEntry[] {
    const warnings = reasons.filter((r) => r.startsWith("warning:")).map((r) => r.slice("warning:".length));
    const mergedWarnings = describeFormatIssueList(warnings);
    const entries: ReviewReasonEntry[] = [];
    let warningsPlaced = false;
    for (const reason of reasons) {
        if (reason.startsWith("warning:")) {
            // Cảnh báo định dạng đứng ở chỗ cảnh báo đầu tiên
            if (!warningsPlaced) {
                entries.push(...mergedWarnings.map((text) => ({ text, position: null })));
                warningsPlaced = true;
            }
            continue;
        }
        entries.push({ text: describeReviewReason(reason), position: parseSimilarInExam(reason) });
    }
    const seen = new Set<string>();
    return entries.filter((entry) => (seen.has(entry.text) ? false : (seen.add(entry.text), true)));
}

/**
 * Câu có cờ về nội dung thì khi Giữ chỉ vào ngân hàng riêng; lý do `similar_in_exam: …` là về đề, không tính
 * (docs/api mục 7.5, làm rõ 11/11). Có cờ mà không kèm lý do thì coi như cờ nội dung cho an toàn.
 */
export function hasContentFlag(needsReview: boolean | undefined, reasons: string[] | undefined): boolean {
    if (!needsReview) return false;
    const list = reasons ?? [];
    if (list.length === 0) return true;
    return list.some((reason) => !reason.startsWith("similar_in_exam:"));
}

/**
 * Câu được "Lưu hàng loạt" (CT-7): đã có câu, chưa lưu, không đang tự kiểm tra lại bản sửa, và không có lý do cần xem kỹ
 * nào. Cờ thông tin (`infoReasons`) không tính là cờ; từ 22/11 `similar_in_exam` là cờ thông tin nên câu chỉ cùng dạng với
 * câu khác trong đề vẫn được lưu hàng loạt (góp ý giáo viên 21/11). Dữ liệu cũ còn `similar_in_exam` trong `reviewReasons`
 * thì vẫn không lưu hàng loạt.
 */
export function isBulkKeepCandidate(question: Pick<ExamQuestionItem, "state" | "qcState" | "needsReview" | "reviewReasons">): boolean {
    return (question.state ?? "ready") === "ready"
        && question.qcState !== "checking"
        && !question.needsReview
        && (question.reviewReasons ?? []).length === 0;
}

/**
 * Câu chữ và màu của dòng tiến độ ở đầu trang đề (docs/api mục 7.3). G18: không ước lượng "khoảng N phút" (tính theo giờ
 * tạo đề thì sai cả hai chiều, và chưa có số đo thời gian soạn một câu); chỉ nói số câu và lời cố định. Khi AI hết lượt
 * trong ngày thì kèm `notice` của backend (giờ chạy tiếp).
 */
export function describeBuildProgress(
    progress: ExamProgress
): { type: "info" | "success" | "warning"; text: string } {
    const done = `${progress.ready}/${progress.total}`;
    const stayNote = "Thầy/cô có thể rời trang, đề vẫn tiếp tục được soạn.";
    if (progress.status === "generating") {
        if (progress.notice) {
            return { type: "warning", text: `Đang soạn ${done} câu · ${progress.notice}. ${stayNote}` };
        }
        return { type: "info", text: `Đang soạn ${done} câu. ${stayNote}` };
    }
    if (progress.status === "partial" && progress.failed > 0) {
        return {
            type: "warning",
            text: `Đã soạn xong ${done} câu · ${progress.failed} câu chưa soạn được (xem bên dưới).`,
        };
    }
    return { type: "success", text: `Đã soạn xong ${done} câu.` };
}

// G14: lý do "đang chờ" (hệ thống chưa kiểm xong), không phải lỗi của câu: hiện thành dòng trạng thái, không là "cần xem kỹ"
const PENDING_REASONS = new Set(["solver_pending", "grounding_pending"]);

/** Lý do cần xem kỹ thật (bỏ các lý do "đang chờ" solver_pending, grounding_pending). */
export function contentReviewReasons(reasons: string[] | undefined): string[] {
    return (reasons ?? []).filter((reason) => !PENDING_REASONS.has(reason));
}

/**
 * Câu đang cần xem kỹ theo cách giao diện hiển thị (G14): có cờ và còn ít nhất một lý do không phải "đang chờ". Có cờ mà
 * không kèm lý do nào thì vẫn coi là cần xem kỹ cho an toàn. Dùng chung cho thẻ câu, mục lục và ô tóm tắt.
 */
export function needsReviewNow(question: Pick<ExamQuestionItem, "needsReview" | "reviewReasons">): boolean {
    if (!question.needsReview) return false;
    const reasons = question.reviewReasons ?? [];
    return reasons.length === 0 || contentReviewReasons(reasons).length > 0;
}

/**
 * Trạng thái "hệ thống đang kiểm tra" của câu (G14): `checking` đang tự giải lại bản sửa, `grounding` đang đối chiếu tài
 * liệu (hai trường hợp này chưa Lưu được, backend trả 409 QC_CHECKING), `deferred` kiểm lại bị hoãn vì hết lượt AI trong
 * ngày (vẫn Lưu được, vào ngân hàng riêng). Không có thì null.
 */
export type PendingCheck = "checking" | "grounding" | "deferred";

export function pendingCheckOf(question: Pick<ExamQuestionItem, "qcState" | "reviewReasons">): PendingCheck | null {
    const reasons = question.reviewReasons ?? [];
    if (question.qcState === "checking") return "checking";
    if (reasons.includes("grounding_pending")) return "grounding";
    if (reasons.includes("solver_pending")) return "deferred";
    return null;
}

/** Chưa Lưu được vì hệ thống đang kiểm tra (backend trả 409 QC_CHECKING). */
export function isKeepBlockedByCheck(question: Pick<ExamQuestionItem, "qcState" | "reviewReasons">): boolean {
    const pending = pendingCheckOf(question);
    return pending === "checking" || pending === "grounding";
}

/** id của thẻ câu trên trang đề để mục lục cuộn tới. */
export function examSlotAnchorId(slotId: string | number): string {
    return `exam-slot-${slotId}`;
}

/** Trạng thái một ô của mục lục trang đề. */
export type ExamNavStatus = "kept" | "review" | "failed" | "generating" | "unreviewed";

/** Thứ tự ưu tiên: đã lưu, chưa soạn được, đang soạn, cần xem kỹ, chưa lưu. */
export function navStatusOf(question: ExamQuestionItem): ExamNavStatus {
    switch (question.state) {
        case "kept":
        case "edited":
            return "kept";
        case "failed":
            return "failed";
        case "pending":
        case "generating":
            return "generating";
    }
    // G14: chỉ còn lý do "đang chờ" thì không tính là cần xem kỹ
    return needsReviewNow(question) ? "review" : "unreviewed";
}

/** Một dòng tóm tắt kết quả tự kiểm tra trên thẻ câu. */
export interface QcSummaryLine {
    label: string;
    value: string;
    ok: boolean;
}

/**
 * Ba dòng tóm tắt kết quả tự kiểm tra (trình bày, trùng câu, tự giải lại), suy từ `reviewReasons`/`qcState` của API xem đề
 * (docs/api mục 7.4) nên không phải gọi vết QC từng câu. Chỉ có với câu hệ thống soạn chưa lưu (`source = generated`,
 * `state = ready`); câu ngân hàng, câu đã lưu, chỗ chưa có câu trả null.
 */
export function summarizeQc(question: ExamQuestionItem): QcSummaryLine[] | null {
    if (question.source !== "generated" || (question.state ?? "ready") !== "ready") return null;
    const reasons = question.reviewReasons ?? [];
    const has = (code: string) => reasons.includes(code);

    const formatIssue = reasons.some((r) => r.startsWith("warning:"));
    const duplicate = reasons.some((r) => r.startsWith("duplicate:") || r.startsWith("similar_in_exam:"));
    // 22/11: câu cùng dạng trong đề là cờ thông tin (infoReasons), chỉ ghi chú, không tô màu lỗi
    const sameTypeInExam = (question.infoReasons ?? []).some((r) => r.startsWith("similar_in_exam:"));

    let solver: { value: string; ok: boolean };
    if (question.qcState === "checking" || has("solver_pending")) {
        solver = { value: "đang tự giải lại", ok: true };
    } else if (has("solver_inconsistent")) {
        solver = { value: "ra đáp án khác", ok: false };
    } else if (has("solver_multiple_correct")) {
        solver = { value: "thấy nhiều phương án cùng đúng", ok: false };
    } else if (has("solver_no_correct")) {
        solver = { value: "không thấy phương án nào đúng", ok: false };
    } else if (has("solver_ambiguous") || has("solver_unparseable") || has("statement_undecidable")) {
        solver = { value: "chưa kết luận được", ok: false };
    } else if (has("solver_not_run") || has("solver_failed")) {
        solver = { value: "chưa giải lại được", ok: false };
    } else {
        solver = { value: "khớp đáp án", ok: true };
    }

    return [
        { label: "Trình bày", value: formatIssue ? "cần xem lại" : "đạt", ok: !formatIssue },
        {
            label: "Trùng câu khác",
            value: duplicate ? "có câu gần giống" : sameTypeInExam ? "không (có câu cùng dạng trong đề)" : "không",
            ok: !duplicate,
        },
        { label: "Tự giải lại", ...solver },
    ];
}

/** Vị trí câu trong đề theo Phần, như trang đề và file Word: Phần II – Câu 3. */
export interface ExamQuestionPosition {
    part: string;
    number: number;
}

/**
 * Vị trí câu gần giống trong lý do `similar_in_exam: II.3` (dạng từ 2/11). Lý do khác, hoặc dạng cũ chỉ có số thứ tự cả đề
 * (không biết Phần) thì null.
 */
export function parseSimilarInExam(reason: string): ExamQuestionPosition | null {
    if (!reason.startsWith("similar_in_exam:")) return null;
    const byPart = /^(I{1,3})\.(\d+)$/.exec(reason.slice("similar_in_exam:".length).trim());
    return byPart ? { part: byPart[1], number: Number(byPart[2]) } : null;
}

/**
 * Câu chữ cho một lý do cần xem kỹ. Vị trí câu ghi theo Phần ("Phần II – Câu 3"), đếm lại từ 1 ở mỗi Phần như trang đề
 * và file Word.
 */
export function describeReviewReason(reason: string): string {
    if (reason.startsWith("warning:")) {
        return describeFormatIssue(reason.slice("warning:".length));
    }
    if (reason.startsWith("duplicate:")) {
        return "Câu này trùng với một câu khác. Thầy/cô sửa lại hoặc đổi câu.";
    }
    if (reason.startsWith("similar_in_exam:")) {
        const position = reason.slice("similar_in_exam:".length).trim();
        // Từ 2/11: "II.3" = câu 3 của Phần II (đánh số như trang đề)
        const byPart = parseSimilarInExam(reason);
        if (byPart) {
            return `Gần giống Phần ${byPart.part} – Câu ${byPart.number} trong đề, nên đổi một trong hai câu.`;
        }
        // Dữ liệu cũ: n là số thứ tự câu trong cả đề; không đọc được số thì không đoán
        if (/^\d+$/.test(position)) {
            return `Gần giống câu ${position} trong đề, nên đổi một trong hai câu.`;
        }
        return GENERIC_REVIEW_REASON;
    }
    // Mã lạ: không hiện mã thô cho giáo viên
    return reviewReasonLabels[reason] ?? GENERIC_REVIEW_REASON;
}

export function resolveQuestionType(question: ExamQuestionItem): ExamQuestionType {
    if (question.questionType) return question.questionType;
    if (question.statements?.length) return "tf";
    if (!question.optionA && !question.optionB && !question.optionC && !question.optionD) return "sa";
    return "mcq";
}