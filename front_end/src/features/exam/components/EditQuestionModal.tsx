import { useEffect, useState } from "react";
import { Alert, Button, Form, Input, message, Modal, Select, Space, Tag, Typography } from "antd";
import { EditOutlined } from "@ant-design/icons";

import type {
    ExamItemDto, ExamOption, ExamQuestionDifficulty, ExamQuestionItem, StatementKey, TruthLabel, UpdateSlotQuestionPayload,
} from "../types/exam.types";
import { updateQuestion } from "../services/examApi";

import "./EditQuestionModal.css";

const { Text } = Typography;
const { TextArea } = Input;

const STATEMENT_KEYS: StatementKey[] = ["a", "b", "c", "d"];

const difficultyLabels: Record<ExamQuestionDifficulty, string> = {
    easy: "Biết",
    medium: "Hiểu",
    hard: "Vận dụng",
};

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

interface EditQuestionModalProps {
    open: boolean;
    question: ExamQuestionItem | null;
    examId: number;
    onCancel: () => void;
    onSave: (saved: ExamItemDto) => void | Promise<void>;
    loading?: boolean;
}

/** Thông báo lỗi theo docs/api mục 7.5a: FORMAT_INVALID có thêm danh sách errors. */
function extractErrorMessage(e: any): string {
    const data = e?.response?.data;
    const base = data?.error || data?.message || e?.message || "Gặp lỗi khi cập nhật câu hỏi";
    if (Array.isArray(data?.errors) && data.errors.length > 0) {
        return `${base}: ${data.errors.join("; ")}`;
    }
    if (data?.code === "VERSION_CONFLICT") {
        return `${base}. Hãy tải lại trang rồi sửa tiếp.`;
    }
    return base;
}

export default function EditQuestionModal({
    open,
    question,
    examId,
    onCancel,
    onSave,
    loading = false,
}: EditQuestionModalProps) {
    const [form] = Form.useForm<EditQuestionFormValues>();
    const [submitting, setSubmitting] = useState(false);

    const isBusy = loading || submitting;
    const questionType = question?.questionType ?? "mcq";

    // Khi Modal mở hoặc đổi câu hỏi, đổ dữ liệu cũ vào form
    useEffect(() => {
        if (open && question) {
            form.setFieldsValue({
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
            });
            setSubmitting(false);
        }
    }, [open, question, form]);

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
        if (!question || isBusy) return;
        if (question.slotId === undefined || question.questionId === undefined) {
            message.error("Không xác định được chỗ của câu hỏi trong đề");
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
            const saved = await updateQuestion(
                examId,
                question.slotId,
                buildPayload(values, question.questionId)
            );
            message.success(
                saved.qcState === "checking"
                    ? "Đã lưu bản sửa. Solver đang kiểm lại câu hỏi."
                    : "Cập nhật câu hỏi thành công"
            );
            await onSave(saved);
        } catch (e) {
            console.error(e);
            message.error(extractErrorMessage(e));
        } finally {
            setSubmitting(false);
        }
    };

    const handleCancel = () => {
        if (isBusy) return;
        form.resetFields();
        onCancel();
    };

    return (
        <Modal
            open={open}
            title={
                <Space size={10}>
                    <EditOutlined className="edit-question-modal-title-icon" />
                    <span>Chỉnh sửa câu hỏi</span>
                </Space>
            }
            width={720}
            style={{ top: 20 }}
            onCancel={handleCancel}
            closable={!isBusy}
            maskClosable={!isBusy}
            keyboard={!isBusy}
            destroyOnClose
            className="edit-question-modal"
            footer={
                <div className="edit-question-modal-footer">
                    <Button onClick={handleCancel} disabled={isBusy}>
                        Hủy
                    </Button>
                    <Button
                        type="primary"
                        icon={<EditOutlined />}
                        loading={isBusy}
                        disabled={!question}
                        onClick={handleSubmit}
                    >
                        Lưu thay đổi
                    </Button>
                </div>
            }
        >
            {!question ? (
                <Alert
                    type="warning"
                    showIcon
                    message="Chưa chọn câu hỏi"
                    description="Hãy chọn một câu hỏi để chỉnh sửa."
                />
            ) : (
                <Form
                    form={form}
                    layout="vertical"
                    requiredMark={false}
                    className="edit-question-form"
                >
                    {/* Mức độ do ô ma trận quyết định, không sửa được (docs/api mục 7.5a) */}
                    {question.difficulty && (
                        <div style={{ marginBottom: 16 }}>
                            <Text strong>Mức độ nhận thức: </Text>
                            <Tag>{difficultyLabels[question.difficulty]}</Tag>
                        </div>
                    )}

                    <Form.Item
                        name="questionText"
                        label={<Text strong>Nội dung câu hỏi</Text>}
                        rules={[
                            { required: true, whitespace: true, message: "Vui lòng nhập nội dung câu hỏi." },
                        ]}
                    >
                        <TextArea
                            placeholder="Nhập nội dung câu hỏi..."
                            autoSize={{ minRows: 3, maxRows: 6 }}
                            disabled={isBusy}
                        />
                    </Form.Item>

                    {questionType === "mcq" && (
                        <>
                            <div className="edit-question-options-grid">
                                {(["A", "B", "C", "D"] as const).map((label) => (
                                    <Form.Item
                                        key={label}
                                        name={`option${label}`}
                                        label={<Text strong>Phương án {label}</Text>}
                                        rules={[{ required: true, whitespace: true, message: `Nhập đáp án ${label}.` }]}
                                    >
                                        <Input placeholder={`Nội dung đáp án ${label}`} disabled={isBusy} />
                                    </Form.Item>
                                ))}
                            </div>

                            <Form.Item
                                name="correctOption"
                                label={<Text strong>Đáp án đúng</Text>}
                                rules={[{ required: true, message: "Vui lòng chọn đáp án đúng." }]}
                            >
                                <Select
                                    disabled={isBusy}
                                    options={[
                                        { value: "A", label: "Phương án A" },
                                        { value: "B", label: "Phương án B" },
                                        { value: "C", label: "Phương án C" },
                                        { value: "D", label: "Phương án D" },
                                    ]}
                                />
                            </Form.Item>
                        </>
                    )}

                    {questionType === "sa" && (
                        <Form.Item
                            name="saAnswer"
                            label={<Text strong>Đáp số</Text>}
                            extra="Một giá trị số, ví dụ 3,5 hoặc \frac{1}{2}."
                            rules={[{ required: true, whitespace: true, message: "Vui lòng nhập đáp số." }]}
                        >
                            <Input placeholder="Nhập đáp số" disabled={isBusy} />
                        </Form.Item>
                    )}

                    {questionType === "tf" &&
                        STATEMENT_KEYS.map((key, i) => (
                            <div key={key} className="edit-question-statement-grid">
                                <Form.Item
                                    name={["statements", i, "text"]}
                                    label={<Text strong>Ý {key})</Text>}
                                    rules={[{ required: true, whitespace: true, message: `Nhập nội dung ý ${key}.` }]}
                                >
                                    <TextArea autoSize={{ minRows: 1, maxRows: 4 }} disabled={isBusy} />
                                </Form.Item>
                                <Form.Item
                                    name={["statements", i, "label"]}
                                    label={<Text strong>Nhãn</Text>}
                                    rules={[{ required: true, message: "Chọn nhãn." }]}
                                >
                                    <Select
                                        disabled={isBusy}
                                        options={[
                                            { value: "T", label: "Đúng" },
                                            { value: "F", label: "Sai" },
                                        ]}
                                    />
                                </Form.Item>
                            </div>
                        ))}

                    <Form.Item
                        name="explanation"
                        label={
                            <Space size={4}>
                                <Text strong>Lời giải chi tiết</Text>
                                <Text type="secondary" style={{ fontSize: "12px" }}>(Không bắt buộc)</Text>
                            </Space>
                        }
                    >
                        <TextArea
                            placeholder="Nhập giải thích chi tiết cho đáp án..."
                            autoSize={{ minRows: 2, maxRows: 5 }}
                            disabled={isBusy}
                        />
                    </Form.Item>
                </Form>
            )}
        </Modal>
    );
}
