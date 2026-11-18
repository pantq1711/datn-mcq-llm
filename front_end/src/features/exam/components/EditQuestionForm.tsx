import { useEffect, useState } from "react";
import { Alert, Button, ConfigProvider, Form, Input, message, Modal, Radio, Space, Tag, Typography } from "antd";
import type { FormInstance } from "antd";
import { CloseOutlined, SaveOutlined } from "@ant-design/icons";

import type {
    ExamItemDto, ExamOption, ExamQuestionItem, StatementKey, TruthLabel, UpdateSlotQuestionPayload,
} from "../types/exam.types";
import { updateQuestion } from "../services/examApi";
import { describeFormatIssue, describeLevel } from "../../../utils/exam.utils";
import { MathField, MathInputProvider, MathSymbolBar } from "./MathInput";
import { READABLE_MODAL_THEME } from "./readableModalTheme";
import { clearEditDraft, loadEditDraft, saveEditDraft } from "../utils/editDraft";

import "./EditQuestionForm.css";

const { Text } = Typography;

const STATEMENT_KEYS: StatementKey[] = ["a", "b", "c", "d"];
const OPTION_KEYS = ["A", "B", "C", "D"] as const;

type FieldName = string | (string | number)[];

/** Tên trường trong lỗi FORMAT_INVALID của backend ("answer: …") → ô tương ứng trong form (rà soát G11). */
const FIELD_OF_ERROR: Record<string, FieldName> = {
    question_text: "questionText",
    option_a: "optionA",
    option_b: "optionB",
    option_c: "optionC",
    option_d: "optionD",
    answer: "saAnswer",
    explanation: "explanation",
    ...Object.fromEntries(STATEMENT_KEYS.flatMap((key, i) => [
        [`statement_${key}`, ["statements", i, "text"]],
        [`label_${key}`, ["statements", i, "label"]],
    ])),
};

// Lỗi dạng đáp số: nói kèm ví dụ, bỏ cụm "vừa khung" khó hiểu (rà soát G11)
const ANSWER_FORMAT_START = /^đáp (?:số|án) phải là/i;
const ANSWER_FORMAT_MESSAGE =
    "Đáp số phải là số nguyên hoặc số thập phân, tối đa 4 kí tự kể cả dấu trừ và dấu phẩy (ví dụ 12; -3,5; 0,25).";

/**
 * Đặt lỗi của backend xuống dưới đúng ô và cuộn tới ô đầu tiên. Trả về các lỗi không gắn được vào ô nào
 * (ví dụ "4 ý cùng nhãn") để báo bằng thông báo chung.
 */
function showFieldErrors(form: FormInstance, errors: string[]): { placed: boolean; rest: string[] } {
    const byField = new Map<string, { name: FieldName; messages: string[] }>();
    const rest: string[] = [];
    for (const raw of errors) {
        const match = /^([a-z_]+):\s*([\s\S]*)$/.exec(raw.trim());
        const name = match ? FIELD_OF_ERROR[match[1]] : undefined;
        if (!match || !name) {
            rest.push(describeFormatIssue(raw));
            continue;
        }
        const text = match[1] === "answer" && ANSWER_FORMAT_START.test(match[2])
            ? ANSWER_FORMAT_MESSAGE
            : describeFormatIssue(raw);
        const key = JSON.stringify(name);
        const entry = byField.get(key) ?? { name, messages: [] };
        if (!entry.messages.includes(text)) entry.messages.push(text);
        byField.set(key, entry);
    }
    const fields = [...byField.values()];
    if (fields.length > 0) {
        form.setFields(fields.map(({ name, messages }) => ({ name, errors: messages })));
        form.scrollToField(fields[0].name, { block: "center", behavior: "smooth" });
    }
    return { placed: fields.length > 0, rest: [...new Set(rest)] };
}

interface EditQuestionFormValues {
    questionText: string;
    optionA?: string;
    optionB?: string;
    optionC?: string;
    optionD?: string;
    correctOption?: ExamOption;
    saAnswer?: string;
    statements?: { text: string; label: TruthLabel }[];
    explanation?: string;
}

interface SaveErrorBody {
    error?: string;
    message?: string;
    code?: string;
    errors?: unknown;
}

/**
 * Thông báo lỗi theo docs/api mục 7.5a: FORMAT_INVALID có thêm danh sách errors. Lỗi gắn được vào ô thì hiện đỏ dưới ô
 * đó; thông báo chung chỉ nhắc xem chỗ báo đỏ và nêu các lỗi còn lại.
 */
function handleSaveError(e: unknown, form: FormInstance): string {
    const err = e as { message?: string; response?: { data?: SaveErrorBody } } | undefined;
    const data = err?.response?.data;
    const base = data?.error || data?.message || err?.message || "Chưa lưu được bản sửa. Thầy/cô thử lại sau ít phút.";
    if (Array.isArray(data?.errors) && data.errors.length > 0) {
        const { placed, rest } = showFieldErrors(form, data.errors as string[]);
        if (!placed) {
            return `Bản sửa chưa lưu được: ${rest.join(" ")}`;
        }
        return rest.length > 0
            ? `Bản sửa chưa lưu được. Thầy/cô xem chỗ báo đỏ trên câu. ${rest.join(" ")}`
            : "Bản sửa chưa lưu được. Thầy/cô xem chỗ báo đỏ trên câu.";
    }
    if (data?.code === "VERSION_CONFLICT") {
        return `${base}. Thầy/cô tải lại trang rồi sửa tiếp.`;
    }
    return base;
}

/** Giá trị ban đầu của form lấy từ câu đang có trong đề. */
function toFormValues(question: ExamQuestionItem): EditQuestionFormValues {
    return {
        questionText: question.questionText || "",
        optionA: question.optionA || "",
        optionB: question.optionB || "",
        optionC: question.optionC || "",
        optionD: question.optionD || "",
        correctOption: question.correctOption || "A",
        saAnswer: question.saAnswer || "",
        statements: STATEMENT_KEYS.map((key) => {
            const statement = question.statements?.find((s) => s.key === key);
            return { text: statement?.text || "", label: statement?.label || "T" };
        }),
        explanation: question.explanation || "",
    };
}

interface EditQuestionFormProps {
    question: ExamQuestionItem;
    examId: number;
    /** Lưu xong (backend trả câu của chỗ sau khi sửa). */
    onSaved: (saved: ExamItemDto) => void | Promise<void>;
    /** Bấm Hủy (đã hỏi lại nếu có thay đổi chưa lưu). */
    onCancel: () => void;
    /** Báo trang khi form bắt đầu có thay đổi chưa lưu (để hỏi lại khi chuyển câu, đóng tab…). */
    onDirtyChange?: (dirty: boolean) => void;
}

/**
 * Form sửa câu ngay trên thẻ câu (CT-2, mục 7.2): ô có công thức với dòng "Sẽ hiện là", hàng nút kí hiệu, lỗi dưới đúng
 * ô; thanh nút Hủy/Lưu dính đáy màn hình. Giá trị ban đầu chỉ đổ một lần khi mở: trang tự tải lại đề không xóa chữ đang
 * gõ (trang gắn `key` theo chỗ để đổi câu thì mở form mới).
 */
export default function EditQuestionForm({ question, examId, onSaved, onCancel, onDirtyChange }: EditQuestionFormProps) {
    const [form] = Form.useForm<EditQuestionFormValues>();
    const slotId = question.slotId;
    const questionId = question.questionId;
    // Bản đang sửa dở lưu tạm trong tab (tải lại trang không mất chữ đang gõ); không có thì lấy nội dung câu
    const [draft] = useState(() =>
        slotId !== undefined && questionId !== undefined
            ? loadEditDraft<EditQuestionFormValues>(examId, slotId, questionId)
            : null
    );
    const [restored, setRestored] = useState(draft !== null);
    const [initialValues] = useState(() => draft ?? toFormValues(question));
    const [submitting, setSubmitting] = useState(false);

    // Mở lại bản sửa dở: báo trang là đang có thay đổi chưa lưu (hỏi lại khi đóng tab, khi chuyển câu)
    useEffect(() => {
        if (draft) onDirtyChange?.(true);
        // chỉ chạy một lần khi mở form
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const forgetDraft = () => {
        if (slotId !== undefined) clearEditDraft(examId, slotId);
    };

    // Bỏ bản khôi phục, quay về nội dung hiện tại của câu
    const useCurrentQuestion = () => {
        form.setFieldsValue(toFormValues(question));
        forgetDraft();
        setRestored(false);
        onDirtyChange?.(false);
    };
    // Hộp hỏi lại dùng chung cỡ chữ 16px (Modal.confirm tĩnh không nhận ConfigProvider)
    const [modal, modalContextHolder] = Modal.useModal();

    const questionType = question.questionType ?? "mcq";

    const buildPayload = (values: EditQuestionFormValues, baseQuestionId: number): UpdateSlotQuestionPayload => {
        const common = {
            baseQuestionId,
            questionText: values.questionText,
            explanation: values.explanation || undefined,
        };
        if (questionType === "sa") {
            return { ...common, saAnswer: values.saAnswer };
        }
        if (questionType === "tf") {
            return {
                ...common,
                statements: STATEMENT_KEYS.map((key, i) => ({
                    key,
                    text: values.statements?.[i]?.text ?? "",
                    label: values.statements?.[i]?.label ?? "T",
                })),
            };
        }
        return {
            ...common,
            optionA: values.optionA,
            optionB: values.optionB,
            optionC: values.optionC,
            optionD: values.optionD,
            correctOption: values.correctOption,
        };
    };

    const handleSubmit = async () => {
        if (submitting) return;
        if (question.slotId === undefined || question.questionId === undefined) {
            message.error("Không xác định được câu này trong đề. Hãy tải lại trang.");
            return;
        }

        let values: EditQuestionFormValues;
        try {
            values = await form.validateFields();
        } catch {
            return;
        }

        setSubmitting(true);
        try {
            const saved = await updateQuestion(examId, question.slotId, buildPayload(values, question.questionId));
            message.success(
                saved.qcState === "checking"
                    ? "Đã lưu bản sửa. Hệ thống đang tự kiểm tra lại đáp án."
                    : "Đã lưu bản sửa."
            );
            forgetDraft();
            onDirtyChange?.(false);
            await onSaved(saved);
        } catch (e) {
            console.error(e);
            message.error({ content: handleSaveError(e, form), duration: 8 });
        } finally {
            setSubmitting(false);
        }
    };

    const handleCancel = () => {
        if (submitting) return;
        // Đã gõ gì đó (hoặc đang mở bản sửa dở khôi phục) mà chưa lưu thì hỏi lại, tránh mất công sửa vì lỡ bấm
        if (restored || form.isFieldsTouched()) {
            modal.confirm({
                title: "Bỏ các thay đổi chưa lưu?",
                okText: "Bỏ thay đổi",
                cancelText: "Tiếp tục sửa",
                okButtonProps: { danger: true },
                autoFocusButton: "cancel",
                onOk: () => {
                    forgetDraft();
                    onDirtyChange?.(false);
                    onCancel();
                },
            });
            return;
        }
        forgetDraft();
        onCancel();
    };

    return (
        <ConfigProvider theme={READABLE_MODAL_THEME}>
            {modalContextHolder}
            <MathInputProvider>
                <Form
                    form={form}
                    layout="vertical"
                    requiredMark={false}
                    initialValues={initialValues}
                    onValuesChange={(_, allValues) => {
                        onDirtyChange?.(true);
                        // Lưu tạm bản đang gõ trong tab để tải lại trang không mất
                        if (slotId !== undefined && questionId !== undefined) {
                            saveEditDraft(examId, slotId, questionId, allValues);
                        }
                    }}
                    className="edit-question-form"
                    // G13: Esc ở bất kỳ ô nào = bấm Hủy (đã gõ thì hỏi lại). Bỏ qua khi đang gõ dấu bằng bộ gõ tiếng Việt
                    // hoặc phím đã được xử lý; hộp hỏi lại vẽ ngoài Form nên Esc đóng hộp đó không lan tới đây
                    onKeyDown={(e) => {
                        if (e.key !== "Escape" || e.defaultPrevented || e.nativeEvent.isComposing) return;
                        e.preventDefault();
                        handleCancel();
                    }}
                >
                    {restored && (
                        <Alert
                            type="info"
                            showIcon
                            className="edit-question-restored"
                            message="Đã mở lại bản thầy/cô đang sửa dở (chưa lưu)."
                            action={
                                <Button size="small" onClick={useCurrentQuestion} disabled={submitting}>
                                    Dùng nội dung hiện tại của câu
                                </Button>
                            }
                        />
                    )}

                    {/* Mức độ do ô ma trận quyết định, không sửa được (docs/api mục 7.5a) */}
                    {questionType !== "tf" && (
                        <div className="edit-question-level">
                            <Text strong>Mức độ:</Text>
                            <Tag className="edit-question-level-tag">{describeLevel(question.difficulty).label}</Tag>
                            <Text type="secondary">(theo ma trận, không đổi ở đây)</Text>
                        </div>
                    )}

                    <MathSymbolBar disabled={submitting} />

                    <MathField
                        name="questionText"
                        label={<Text strong>Câu dẫn</Text>}
                        fieldLabel="Câu dẫn"
                        requiredMessage="Thầy/cô chưa nhập câu dẫn."
                        placeholder="Nhập câu dẫn. Công thức đặt giữa hai dấu $, ví dụ $x^2 + 1$"
                        minRows={3}
                        maxRows={8}
                        disabled={submitting}
                    />

                    {questionType === "mcq" && (
                        <>
                            <div className="edit-question-options-grid">
                                {OPTION_KEYS.map((label) => (
                                    <MathField
                                        key={label}
                                        name={`option${label}`}
                                        label={<Text strong>Phương án {label}</Text>}
                                        fieldLabel={`Phương án ${label}`}
                                        requiredMessage={`Thầy/cô chưa nhập phương án ${label}.`}
                                        maxRows={4}
                                        disabled={submitting}
                                    />
                                ))}
                            </div>

                            <Form.Item
                                name="correctOption"
                                label={<Text strong>Đáp án đúng</Text>}
                                rules={[{ required: true, message: "Thầy/cô chưa chọn đáp án đúng." }]}
                            >
                                <Radio.Group
                                    disabled={submitting}
                                    optionType="button"
                                    buttonStyle="solid"
                                    className="edit-question-answer-choice"
                                    options={OPTION_KEYS.map((key) => ({ value: key, label: key }))}
                                />
                            </Form.Item>
                        </>
                    )}

                    {questionType === "sa" && (
                        <Form.Item
                            name="saAnswer"
                            label={<Text strong>Đáp số</Text>}
                            extra="Số nguyên hoặc số thập phân, tối đa 4 kí tự kể cả dấu trừ và dấu phẩy. Ví dụ: 12; -3,5; 0,25"
                            rules={[{ required: true, whitespace: true, message: "Thầy/cô chưa nhập đáp số." }]}
                            className="edit-question-sa-answer"
                        >
                            <Input placeholder="Ví dụ: 12" disabled={submitting} />
                        </Form.Item>
                    )}

                    {questionType === "tf" &&
                        STATEMENT_KEYS.map((key, i) => {
                            const level = question.statements?.find((s) => s.key === key)?.difficulty;
                            return (
                                <div key={key} className="edit-question-statement-grid">
                                    <MathField
                                        name={["statements", i, "text"]}
                                        label={
                                            <Space size={8}>
                                                <Text strong>Ý {key})</Text>
                                                {level && <Text type="secondary">Mức độ: {describeLevel(level).label}</Text>}
                                            </Space>
                                        }
                                        fieldLabel={`Ý ${key})`}
                                        requiredMessage={`Thầy/cô chưa nhập ý ${key}).`}
                                        maxRows={4}
                                        disabled={submitting}
                                    />
                                    <Form.Item
                                        name={["statements", i, "label"]}
                                        label={<Text strong>Ý {key}) là</Text>}
                                        rules={[{ required: true, message: "Thầy/cô chọn Đúng hoặc Sai." }]}
                                    >
                                        <Radio.Group
                                            disabled={submitting}
                                            optionType="button"
                                            buttonStyle="solid"
                                            className="edit-question-truth-choice"
                                            options={[
                                                { value: "T", label: "Đúng" },
                                                { value: "F", label: "Sai" },
                                            ]}
                                        />
                                    </Form.Item>
                                </div>
                            );
                        })}

                    <MathField
                        name="explanation"
                        label={
                            <Space size={6}>
                                <Text strong>Lời giải</Text>
                                <Text type="secondary">(không bắt buộc)</Text>
                            </Space>
                        }
                        fieldLabel="Lời giải"
                        placeholder="Nhập lời giải để đối chiếu khi chấm"
                        minRows={2}
                        maxRows={8}
                        disabled={submitting}
                    />

                    {/* Thanh nút dính đáy màn hình khi câu dài (G12) */}
                    <div className="edit-question-form-footer">
                        <Button icon={<CloseOutlined />} onClick={handleCancel} disabled={submitting}>
                            Hủy
                        </Button>
                        <Button type="primary" icon={<SaveOutlined />} loading={submitting} onClick={handleSubmit}>
                            Lưu thay đổi
                        </Button>
                    </div>
                </Form>
            </MathInputProvider>
        </ConfigProvider>
    );
}
