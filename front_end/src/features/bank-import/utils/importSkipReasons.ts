export interface SkipReasonInfo {
    label: string;
    hint: string;
}

// Lý do bỏ câu: docs/contract/enums.md mục 3 ("Nhập ngân hàng câu từ Word"). Thứ tự ở đây là thứ tự hiển thị.
const SKIP_REASONS: Record<string, SkipReasonInfo> = {
    has_image: {
        label: "Công thức dạng ảnh, chưa nhập được",
        hint: "Câu có công thức dán dạng ảnh, hình vẽ kiểu đối tượng OLE hoặc ảnh trong dòng Đáp số. Hãy gõ lại công thức trong Word rồi tải lại.",
    },
    formula_unsupported: {
        label: "Công thức chưa chuyển được",
        hint: "Câu có công thức MathType mà hệ thống chưa chuyển sang LaTeX được.",
    },
    has_table: { label: "Có bảng dữ liệu", hint: "Ngân hàng chưa lưu được bảng dữ liệu trong đề." },
    image_limit: {
        label: "Quá giới hạn hình",
        hint: "Câu có quá 6 hình, hoặc cả file quá 100 hình hay 20 MB hình, hoặc một hình quá lớn. Hãy tách file nhỏ hơn.",
    },
    image_convert_failed: {
        label: "Hình không chuyển được",
        hint: "Hình ở định dạng chưa hỗ trợ hoặc bị hỏng (ví dụ ảnh EMF không đổi được, ảnh WMF lớn). Hãy chèn lại hình dạng PNG hoặc JPEG.",
    },
};

const ORDER = Object.keys(SKIP_REASONS);

export function describeSkipReason(reason: string): SkipReasonInfo {
    return SKIP_REASONS[reason] ?? { label: reason, hint: "" };
}

export interface SkippedEntry extends SkipReasonInfo {
    reason: string;
    count: number;
}

/** Các lý do có ít nhất một câu bị bỏ, theo thứ tự cố định; mã lạ đứng cuối. */
export function skippedEntries(skipped: Record<string, number> | null | undefined): SkippedEntry[] {
    const entries = Object.entries(skipped ?? {}).filter(([, count]) => count > 0);
    const rank = (reason: string) => {
        const index = ORDER.indexOf(reason);
        return index === -1 ? ORDER.length : index;
    };
    return entries
        .sort((a, b) => rank(a[0]) - rank(b[0]))
        .map(([reason, count]) => ({ reason, count, ...describeSkipReason(reason) }));
}
