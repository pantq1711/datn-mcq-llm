import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Checkbox, Form, Input, InputNumber, Modal, Radio, Space, Typography, message } from "antd";
import { DownloadOutlined } from "@ant-design/icons";

import type { ExamDetail } from "../types/exam.types";
import { getExamExportDocx, type ExportEquationFormat } from "../services/examApi";

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

/** Tên file mặc định: tên đề + ngày xuất ("Giữa kì I – Toán 8 – 16-11-2026"; tên file không chứa được "/"). */
function defaultFileName(title: string | undefined): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, "0");
    const date = `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()}`;
    const name = toFileName(title, "");
    return name ? `${name} – ${date}` : date;
}

interface FormValues {
    durationMinutes?: number | null;
    examCode?: string;
    fileName?: string;
    equation: ExportEquationFormat;
    examOnly: boolean;
}

interface ExportWordDialogProps {
    open: boolean;
    examId: string;
    detail: ExamDetail;
    onClose: () => void;
}

/**
 * Hộp xuất Word (16/11, NV-43/NV-44, rà soát giao diện G19/G23 và mục 7.5): bấm "Xuất file Word" luôn mở hộp này. Chọn thời
 * gian làm bài, mã đề, tên file, cách ghi công thức, chỉ đề hay đề kèm đáp án; nếu đề chưa hoàn chỉnh thì nhắc ngay trong
 * hộp (nội dung cảnh báo như hộp xác nhận của NV-40/NV-41, không chặn).
 */
export default function ExportWordDialog({ open, examId, detail, onClose }: ExportWordDialogProps) {
    const [form] = Form.useForm<FormValues>();
    const [exporting, setExporting] = useState(false);
    const duration = Form.useWatch("durationMinutes", form);

    // Mở hộp: điền lại giá trị mặc định (tên file theo ngày hiện tại)
    useEffect(() => {
        if (!open) return;
        form.setFieldsValue({
            durationMinutes: DEFAULT_DURATION,
            examCode: DEFAULT_EXAM_CODE,
            fileName: defaultFileName(detail.title),
            equation: "mathtype",
            examOnly: false,
        });
    }, [open, detail.title, form]);

    // NV-40/NV-41: đề còn chỗ chưa soạn được, chỗ đang soạn, câu có cảnh báo chưa lưu, hoặc tổng điểm phần trắc nghiệm khác 10
    const warnings = useMemo(() => {
        const items = (detail.parts ?? []).flatMap((p) => p.items);
        const totalScore = detail.totalScore;
        return {
            failedCount: items.filter((i) => i.state === "failed").length,
            generatingCount: items.filter((i) => i.state === "pending" || i.state === "generating").length,
            flaggedCount: items.filter((i) => i.state === "ready" && i.needsReview).length,
            totalScore,
            scoreOff: typeof totalScore === "number" && Math.abs(totalScore - 10) > 1e-9,
        };
    }, [detail]);
    const { failedCount, generatingCount, flaggedCount, totalScore, scoreOff } = warnings;
    const hasWarning = failedCount + generatingCount + flaggedCount > 0 || scoreOff;

    const handleDownload = async () => {
        let values: FormValues;
        try {
            values = await form.validateFields();
        } catch {
            return; // antd đã hiện lỗi dưới ô
        }
        const equation = values.equation;
        const examCode = values.examCode?.trim() || DEFAULT_EXAM_CODE;
        try {
            setExporting(true);
            const { blob, keptOmml, scaled, failed, failedAt, filename } = await getExamExportDocx(examId, equation, {
                durationMinutes: values.durationMinutes ?? undefined,
                examCode: examCode === DEFAULT_EXAM_CODE ? undefined : examCode,
                content: values.examOnly ? "exam" : undefined,
            });
            // Tên giáo viên nhập (bỏ đuôi .docx nếu có gõ); trống thì tên đề + ngày, rồi tên backend gửi, rồi mã hệ thống
            const typed = toFileName(values.fileName?.replace(/\.docx$/i, ""), "");
            const name = typed
                || toFileName(defaultFileName(detail.title), "")
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
                // failedAt: "I.8" = câu 8 phần I
                const where = failedAt.map((p) => { const [part, no] = p.split("."); return `${no} (phần ${part})`; });
                message.warning(`${failed} công thức không chuyển được${where.length > 0 ? `, ở câu ${where.join(", ")}` : ""}`, 8);
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
                            {failedCount > 0 && <li>{failedCount} câu chưa soạn được (file ghi "Chưa có câu").</li>}
                            {generatingCount > 0 && <li>{generatingCount} câu đang soạn (file ghi "Chưa có câu").</li>}
                            {flaggedCount > 0 && <li>{flaggedCount} câu cần xem kỹ chưa được xử lý.</li>}
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

            <Form form={form} layout="vertical" disabled={exporting} requiredMark={false}>
                <Form.Item label="Thời gian làm bài (phút)" extra='Để trống thì file in "……" để thầy/cô điền tay.'>
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

                <Form.Item
                    name="examCode"
                    label="Mã đề"
                    extra="In ở đầu đề, chân trang và trang đáp án."
                    rules={[{
                        pattern: /^\s*[1-9]\d{2}\s*$/,
                        message: "Mã đề là số có 3 chữ số, từ 100 đến 999.",
                    }]}
                >
                    <Input inputMode="numeric" maxLength={3} className="export-word-dialog-code" />
                </Form.Item>

                <Form.Item name="fileName" label="Tên file" extra="Đuôi .docx được thêm tự động.">
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

                <Form.Item name="examOnly" valuePropName="checked" className="export-word-dialog-last">
                    <Checkbox>Chỉ đề (không kèm đáp án)</Checkbox>
                </Form.Item>
                <Text type="secondary">
                    Không chọn thì file có đề, rồi sang trang mới là đáp án và lời giải chi tiết.
                </Text>
            </Form>
        </Modal>
    );
}
