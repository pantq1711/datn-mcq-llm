import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Checkbox, Form, Input, InputNumber, Modal, Radio, Space, Typography, message } from "antd";
import { DownloadOutlined } from "@ant-design/icons";

import type { ExamDetail } from "../types/exam.types";
import {
    getExamExportDocx,
    type ExportContent,
    type ExportEquationFormat,
    type ExportFontSize,
} from "../services/examApi";
import { isYoungMixShortAnswer, needsReviewNow } from "../../../utils/exam.utils";
import { stripDateSuffix } from "../utils/examName";

import "./ExportWordDialog.css";

const { Text } = Typography;

/** Thời gian làm bài chọn nhanh (phút); ô nhập nhận 5–180 như API (docs/api mục 7.6). */
const QUICK_DURATIONS = [45, 60, 90];
/** Thời gian chọn sẵn khi mở hộp (giáo viên xóa ô thì file in "……"). */
const DEFAULT_DURATION = 90;
const MIN_DURATION = 5;
const MAX_DURATION = 180;
const DEFAULT_EXAM_CODE = "101";

/** Tên file an toàn từ tên đề: bỏ ký tự Windows không cho phép trong tên file; rỗng thì dùng {@code fallback}. */
function toFileName(title: string | undefined, fallback: string): string {
    // eslint-disable-next-line no-control-regex
    const cleaned = (title ?? "").replace(/[\\/:*?"<>|\u0000-\u001f]/g, " ").replace(/\s+/g, " ").trim().replace(/[. ]+$/, "");
    return cleaned || fallback;
}

/** Điểm theo kiểu Việt Nam, ít nhất một chữ số thập phân: 7,0; 12,5; 2,25. */
function formatScore(value: number): string {
    return value.toLocaleString("vi-VN", { minimumFractionDigits: 1, maximumFractionDigits: 2 });
}

const SPEC_SUFFIX = "Bản đặc tả";
const YOUNGMIX_SUFFIX = "YoungMix";
const DEFAULT_FONT_SIZE: ExportFontSize = 12;

/** Câu giải thích dưới nhóm "Nội dung file", theo lựa chọn. */
const CONTENT_HINTS: Record<ExportContent, string> = {
    all: "File có đề, rồi sang trang mới là đáp án và lời giải chi tiết.",
    exam: "File chỉ có đề, dừng ở dòng HẾT.",
    spec: "Bảng ngang trang theo mẫu Phụ lục 2, Công văn 7991: số câu theo dạng và mức của từng nội dung, kèm câu số mấy trong đề.",
    youngmix:
        "Chỉ nội dung đề, đáp án đúng được gạch chân để YoungMix trộn và tạo đáp án, mã QR chấm. Không có đầu đề, đáp án, lời giải.",
};

/** Hậu tố tên file theo nội dung (bản đặc tả, bản YoungMix); đề thường không có. */
const CONTENT_SUFFIX: Partial<Record<ExportContent, string>> = { spec: SPEC_SUFFIX, youngmix: YOUNGMIX_SUFFIX };

/**
 * Tên file mặc định: tên đề (bỏ đuôi ngày `– DD/MM` của đợt 3 để không lặp ngày dạng "28 11 – 28-11-2026") + ngày xuất
 * ("Giữa kì I – Toán 8 – 16-11-2026"; tên file không chứa được "/"); bản đặc tả thêm " – Bản đặc tả" sau tên đề
 * ("Giữa kì I – Toán 8 – Bản đặc tả – 18-11-2026"), bản YoungMix (26/11) thêm " – YoungMix".
 */
function defaultFileName(title: string | undefined, content: ExportContent = "all"): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, "0");
    const date = `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()}`;
    const name = toFileName(title ? stripDateSuffix(title) : undefined, "");
    const suffix = CONTENT_SUFFIX[content];
    const base = suffix ? (name ? `${name} – ${suffix}` : suffix) : name;
    return base ? `${base} – ${date}` : date;
}

interface FormValues {
    durationMinutes?: number | null;
    examCode?: string;
    fileName?: string;
    equation: ExportEquationFormat;
    content: ExportContent;
    /** Tờ phách (26/11), chỉ cho "all" và "exam". */
    phach?: boolean;
    /** Cỡ chữ (26/11); bản đặc tả không dùng. */
    fontSize?: ExportFontSize;
}

interface ExportWordDialogProps {
    open: boolean;
    examId: string;
    detail: ExamDetail;
    onClose: () => void;
    /** Bấm "Xem các câu này" ở dòng cảnh báo câu cần xem kỹ: trang đề lọc và cuộn tới các câu đó. */
    onShowFlagged?: () => void;
}

/**
 * Hộp xuất Word (16/11, NV-43/NV-44, rà soát giao diện G19/G23 và mục 7.5): bấm "Xuất file Word" luôn mở hộp này. Chọn thời
 * gian làm bài, mã đề, tên file, cách ghi công thức, nội dung file (đề kèm đáp án, chỉ đề, hoặc bản đặc tả từ 18/11); nếu đề
 * chưa hoàn chỉnh thì nhắc ngay trong hộp (nội dung cảnh báo như hộp xác nhận của NV-40/NV-41, không chặn). Bản đặc tả không
 * có ô mã đề nên ô Mã đề ẩn khi chọn.
 *
 * <p>26/11: thêm bản cho phần mềm trộn đề YoungMix (không dùng mã đề, thời gian, tờ phách; đáp án phần III không phải số hay
 * quá 4 ký tự thì cảnh báo, không chặn), ô tích tờ phách (chỉ "Đề kèm đáp án" và "Chỉ đề") và cỡ chữ 12 / 14 (trừ bản đặc
 * tả). Hộp không nhớ lựa chọn: mỗi lần mở điền lại mặc định như các lựa chọn khác.
 */
export default function ExportWordDialog({ open, examId, detail, onClose, onShowFlagged }: ExportWordDialogProps) {
    const [form] = Form.useForm<FormValues>();
    const [exporting, setExporting] = useState(false);
    const duration = Form.useWatch("durationMinutes", form);
    const content = Form.useWatch("content", form) ?? "all";
    const isSpec = content === "spec";
    const isYoungMix = content === "youngmix";
    // Bản đặc tả và bản YoungMix không in ô mã đề; tờ phách chỉ cho đề (kèm hoặc không kèm đáp án)
    const usesExamCode = !isSpec && !isYoungMix;
    const allowsPhach = content === "all" || content === "exam";

    // Mở hộp: điền lại giá trị mặc định (tên file theo ngày hiện tại)
    useEffect(() => {
        if (!open) return;
        form.setFieldsValue({
            durationMinutes: DEFAULT_DURATION,
            examCode: DEFAULT_EXAM_CODE,
            fileName: defaultFileName(detail.title),
            equation: "mathtype",
            content: "all",
            phach: false,
            fontSize: DEFAULT_FONT_SIZE,
        });
    }, [open, detail.title, form]);

    // Đổi nội dung file: tên file còn là tên mặc định của lựa chọn cũ thì đổi theo (tên giáo viên tự gõ thì giữ)
    const handleValuesChange = (changed: Partial<FormValues>) => {
        if (!changed.content) return;
        const current = form.getFieldValue("fileName");
        const defaults = (["all", "exam", "spec", "youngmix"] as ExportContent[]).map((c) => defaultFileName(detail.title, c));
        if (!current || defaults.includes(current)) {
            form.setFieldValue("fileName", defaultFileName(detail.title, changed.content));
        }
    };

    // NV-40/NV-41: đề còn chỗ chưa soạn được, chỗ đang soạn, câu có cảnh báo chưa lưu, hoặc tổng điểm phần trắc nghiệm khác 10
    const warnings = useMemo(() => {
        const items = (detail.parts ?? []).flatMap((p) => p.items);
        const totalScore = detail.totalScore;
        return {
            failedCount: items.filter((i) => i.state === "failed").length,
            generatingCount: items.filter((i) => i.state === "pending" || i.state === "generating").length,
            // G14: cùng quy tắc với trang đề (bỏ câu chỉ còn lý do "đang chờ" hệ thống kiểm tra)
            flaggedCount: items.filter((i) => i.state === "ready" && needsReviewNow(i)).length,
            totalScore,
            scoreOff: typeof totalScore === "number" && Math.abs(totalScore - 10) > 1e-9,
        };
    }, [detail]);
    const { failedCount, generatingCount, flaggedCount, totalScore, scoreOff } = warnings;
    const hasWarning = failedCount + generatingCount + flaggedCount > 0 || scoreOff;

    // YoungMix (26/11): câu phần III đã có câu mà đáp án không phải số hoặc quá 4 ký tự ("Câu k phần III", k theo thứ tự
    // trong phần như file xuất). Chỗ chưa có câu đã được nhắc ở cảnh báo trên.
    const youngMixAnswerIssues = useMemo(() => {
        const part = (detail.parts ?? []).find((p) => p.part === "III");
        return (part?.items ?? []).flatMap((item, i) =>
            item.questionText && !isYoungMixShortAnswer(item.saAnswer) ? [`Câu ${i + 1} phần III`] : []
        );
    }, [detail]);

    const handleDownload = async () => {
        let values: FormValues;
        try {
            values = await form.validateFields();
        } catch {
            return; // antd đã hiện lỗi dưới ô
        }
        const equation = values.equation;
        const fileContent = values.content ?? "all";
        // Bản đặc tả và bản YoungMix không có ô mã đề: không gửi examCode; YoungMix không có đầu đề: không gửi thời gian
        const noExamCode = fileContent === "spec" || fileContent === "youngmix";
        const examCode = noExamCode ? DEFAULT_EXAM_CODE : values.examCode?.trim() || DEFAULT_EXAM_CODE;
        const withPhach = (fileContent === "all" || fileContent === "exam") && values.phach === true;
        const fontSize = fileContent !== "spec" && values.fontSize === 14 ? values.fontSize : undefined;
        try {
            setExporting(true);
            const { blob, keptOmml, scaled, failed, failedAt, filename } = await getExamExportDocx(examId, equation, {
                durationMinutes: fileContent === "youngmix" ? undefined : values.durationMinutes ?? undefined,
                examCode: examCode === DEFAULT_EXAM_CODE ? undefined : examCode,
                content: fileContent === "all" ? undefined : fileContent,
                phach: withPhach ? true : undefined,
                fontSize,
            });
            // Tên giáo viên nhập (bỏ đuôi .docx nếu có gõ); trống thì tên đề + ngày, rồi tên backend gửi, rồi mã hệ thống
            const typed = toFileName(values.fileName?.replace(/\.docx$/i, ""), "");
            const name = typed
                || toFileName(defaultFileName(detail.title, fileContent), "")
                || filename?.replace(/\.docx$/i, "")
                || toFileName(undefined, detail.examCode ?? `exam-${examId}`);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `${name}.docx`;
            a.click();
            window.URL.revokeObjectURL(url);
            message.success("Đã tải file Word về máy.");
            // Chỉ có khi xuất MathType (backend không gửi header ở chế độ Equation)
            if (keptOmml > 0) {
                message.warning(`${keptOmml} công thức không chuyển được sang MathType, vẫn ở dạng công thức Word`, 8);
            }
            if (scaled > 0) {
                message.info(`${scaled} công thức dài đã được thu nhỏ cho vừa trang`, 8);
            }
            if (failed > 0) {
                // failedAt: "I.8" = câu 8 phần I; ghi vị trí theo cách thống nhất "Phần I – Câu 8"
                const where = failedAt.map((p) => { const [part, no] = p.split("."); return `Phần ${part} – Câu ${no}`; });
                message.warning(`${failed} công thức không chuyển được${where.length > 0 ? `, ở ${where.join(", ")}` : ""}`, 8);
            }
            onClose();
        } catch (err: unknown) {
            // responseType 'blob': thân lỗi JSON cũng là Blob, phải đọc ra chữ mới thấy thông báo của backend
            let errorMsg = "Không xuất được file";
            const data = (err as { response?: { data?: unknown } })?.response?.data;
            try {
                const body = data instanceof Blob ? JSON.parse(await data.text()) : data;
                const serverMsg = (body as { error?: unknown } | null | undefined)?.error;
                if (serverMsg) errorMsg = String(serverMsg);
            } catch {
                // thân lỗi không phải JSON: giữ câu mặc định
            }
            message.error(errorMsg);
        } finally {
            setExporting(false);
        }
    };

    return (
        <Modal
            open={open}
            title="Xuất file Word"
            centered
            styles={{ body: { maxHeight: "calc(100dvh - 200px)", overflowY: "auto" } }}
            onCancel={exporting ? undefined : onClose}
            closable={!exporting}
            mask={{ closable: !exporting }}
            keyboard={!exporting}
            destroyOnHidden
            className="export-word-dialog"
            footer={[
                <Button key="back" onClick={onClose} disabled={exporting}>
                    Quay lại
                </Button>,
                <Button key="download" type="primary" icon={<DownloadOutlined />} loading={exporting} onClick={handleDownload}>
                    {exporting ? "Đang tạo file Word… (khoảng 10 giây)" : "Tải về"}
                </Button>,
            ]}
        >
            {hasWarning && (
                <Alert
                    type="warning"
                    showIcon
                    className="export-word-dialog-warning"
                    title="Xuất file Word khi đề chưa hoàn chỉnh?"
                    description={
                        <ul className="export-word-dialog-warning-list">
                            {/* Bản đặc tả không in câu: chỗ chưa có câu vẫn được đếm theo ma trận */}
                            {failedCount > 0 && (
                                <li>
                                    {failedCount} câu chưa soạn được
                                    {isSpec ? " (bản đặc tả vẫn đếm các chỗ này theo ma trận)." : ' (file ghi "Chưa có câu").'}
                                </li>
                            )}
                            {generatingCount > 0 && (
                                <li>
                                    {generatingCount} câu đang soạn
                                    {isSpec ? " (bản đặc tả vẫn đếm các chỗ này theo ma trận)." : ' (file ghi "Chưa có câu").'}
                                </li>
                            )}
                            {flaggedCount > 0 && (
                                <li>
                                    {flaggedCount} câu cần xem kỹ chưa được xử lý.
                                    {/* Đóng hộp, lọc "Chỉ hiện câu cần xem" và cuộn tới câu đầu (phác thảo 7.5) */}
                                    {onShowFlagged && (
                                        <>
                                            {" "}
                                            <Button
                                                type="link"
                                                className="export-word-dialog-show-flagged"
                                                disabled={exporting}
                                                onClick={() => {
                                                    onClose();
                                                    onShowFlagged();
                                                }}
                                            >
                                                Xem các câu này
                                            </Button>
                                        </>
                                    )}
                                </li>
                            )}
                            {scoreOff && (
                                <li>
                                    Tổng điểm phần trắc nghiệm là {formatScore(totalScore!)} điểm, khác 10 điểm.
                                    {totalScore! < 10 ? " File sẽ ghi chú phần tự luận (nếu có) do thầy/cô bổ sung." : ""}
                                </li>
                            )}
                        </ul>
                    }
                />
            )}

            <Form form={form} layout="vertical" disabled={exporting} requiredMark={false} onValuesChange={handleValuesChange}>
                {/* Bản YoungMix không có đầu đề: bỏ thời gian làm bài (giữ giá trị trong form) */}
                {!isYoungMix && (
                <Form.Item
                    label={
                        <span>
                            Thời gian làm bài (phút){" "}
                            <Text type="secondary" className="export-word-dialog-label-note">
                                (để trống thì file in "……" để thầy/cô điền tay)
                            </Text>
                        </span>
                    }
                >
                    <Space wrap>
                        {QUICK_DURATIONS.map((m) => (
                            <Button
                                key={m}
                                type={duration === m ? "primary" : "default"}
                                onClick={() => form.setFieldValue("durationMinutes", m)}
                                aria-pressed={duration === m}
                            >
                                {m}
                            </Button>
                        ))}
                        <Form.Item
                            name="durationMinutes"
                            noStyle
                            rules={[{
                                type: "integer",
                                min: MIN_DURATION,
                                max: MAX_DURATION,
                                message: `Thời gian làm bài là số phút từ ${MIN_DURATION} đến ${MAX_DURATION}.`,
                            }]}
                        >
                            <InputNumber
                                min={MIN_DURATION}
                                max={MAX_DURATION}
                                precision={0}
                                placeholder="Số phút khác"
                                aria-label="Thời gian làm bài (phút)"
                                className="export-word-dialog-duration"
                            />
                        </Form.Item>
                    </Space>
                </Form.Item>
                )}

                {/* Bản đặc tả và bản YoungMix không có ô mã đề: bỏ ô (form giữ giá trị, không kiểm) */}
                {usesExamCode && (
                    <Form.Item
                        name="examCode"
                        label={
                            <span>
                                Mã đề{" "}
                                <Text type="secondary" className="export-word-dialog-label-note">
                                    (in ở đầu đề, chân trang và trang đáp án)
                                </Text>
                            </span>
                        }
                        rules={[{
                            pattern: /^\s*[1-9]\d{2}\s*$/,
                            message: "Mã đề là số có 3 chữ số, từ 100 đến 999.",
                        }]}
                    >
                        <Input inputMode="numeric" maxLength={3} className="export-word-dialog-code" />
                    </Form.Item>
                )}

                <Form.Item
                    name="fileName"
                    label={
                        <span>
                            Tên file{" "}
                            <Text type="secondary" className="export-word-dialog-label-note">
                                (đuôi .docx được thêm tự động)
                            </Text>
                        </span>
                    }
                >
                    <Input maxLength={150} />
                </Form.Item>

                <Form.Item name="equation" label="Công thức">
                    <Radio.Group>
                        <Space orientation="vertical" size={4}>
                            <Radio value="mathtype">MathType (mặc định)</Radio>
                            <Radio value="omml">Công thức Word (Equation)</Radio>
                        </Space>
                    </Radio.Group>
                </Form.Item>

                <Form.Item name="content" label="Nội dung file" className="export-word-dialog-last">
                    <Radio.Group>
                        <Space orientation="vertical" size={4}>
                            <Radio value="all">Đề kèm đáp án và lời giải (mặc định)</Radio>
                            <Radio value="exam">Chỉ đề (không kèm đáp án)</Radio>
                            <Radio value="spec">Bản đặc tả đề</Radio>
                            <Radio value="youngmix">Cho phần mềm trộn đề (YoungMix)</Radio>
                        </Space>
                    </Radio.Group>
                </Form.Item>
                <Text type="secondary">{CONTENT_HINTS[content]}</Text>

                {/* YoungMix chỉ nhận đáp án phần III là số tối đa 4 ký tự: nhắc trước khi tải, không chặn */}
                {isYoungMix && youngMixAnswerIssues.length > 0 && (
                    <Alert
                        type="warning"
                        showIcon
                        className="export-word-dialog-youngmix-warning"
                        title="Có đáp án phần III YoungMix có thể không nhận"
                        description={
                            <div>
                                Đáp án phần III phải là số, tối đa 4 ký tự (kể cả dấu "-" và dấu ","). Thầy/cô sửa đáp án
                                của: {youngMixAnswerIssues.join(", ")} trước khi trộn (file vẫn được tải về).
                            </div>
                        }
                    />
                )}

                {/* 26/11: cỡ chữ (trừ bản đặc tả) và tờ phách (chỉ đề kèm đáp án, chỉ đề) */}
                {!isSpec && (
                    <div className="export-word-dialog-extra">
                        <Form.Item name="fontSize" label="Cỡ chữ" className={allowsPhach ? undefined : "export-word-dialog-last"}>
                            <Radio.Group>
                                <Radio value={12}>12</Radio>
                                <Radio value={14}>14</Radio>
                            </Radio.Group>
                        </Form.Item>
                        {allowsPhach && (
                            <Form.Item name="phach" valuePropName="checked" className="export-word-dialog-last">
                                <Checkbox>Có tờ phách (thường dùng cho đề cuối kì)</Checkbox>
                            </Form.Item>
                        )}
                    </div>
                )}
            </Form>
        </Modal>
    );
}
