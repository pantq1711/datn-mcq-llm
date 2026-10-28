import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Form, Input, Modal, Space, Tag, Typography } from "antd";
import { InfoCircleOutlined, SwapOutlined, BulbOutlined } from "@ant-design/icons";

import type { ExamQuestionDifficulty, ExamQuestionItem, ChangeQuestionRequest } from "../types/exam.types";

import "./ChangeQuestionModal.css";

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const MAX_INSTRUCTION_LENGTH = 1000;

const difficultyConfig: Record<ExamQuestionDifficulty, { label: string; color: string }> = {
    easy: { label: "Biết", color: "green" },
    medium: { label: "Hiểu", color: "orange" },
    hard: { label: "Vận dụng", color: "red" },
};

interface ChangeQuestionModalProps {
    open: boolean;
    question: ExamQuestionItem | null;
    onCancel: () => void;
    onGenerate: (request: ChangeQuestionRequest) => void | Promise<void>;
    loading?: boolean;
}

export default function ChangeQuestionModal({
    open,
    question,
    onCancel,
    onGenerate,
    loading = false,
}: ChangeQuestionModalProps) {
    const [form] = Form.useForm<{ instruction: string }>();
    const [submitting, setSubmitting] = useState(false);

    const isBusy = loading || submitting;

    const options = useMemo(() => {
        if (!question) return [];

        return [
            { label: "A", value: question.optionA },
            { label: "B", value: question.optionB },
            { label: "C", value: question.optionC },
            { label: "D", value: question.optionD },
        ] as const;
    }, [question]);

    const difficulty = question?.difficulty
        ? difficultyConfig[question.difficulty]
        : null;

    useEffect(() => {
        if (open) {
            form.resetFields();
            setSubmitting(false);
        }
    }, [open, question?.id, form]);

    const handleSubmit = async () => {
        if (!question || isBusy) return;

        let values: { instruction: string };
        try {
            values = await form.validateFields();
        } catch {
            // Ant Design renders field validation errors next to the input.
            return;
        }

        const instruction = values.instruction.trim();
        if (!instruction) return;

        setSubmitting(true);
        try {
            // The parent owns API error handling, notifications, and deciding
            // whether the modal should close or stay open for a retry.
            await onGenerate({ question, instruction });
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
                    <SwapOutlined className="change-question-modal-title-icon" />
                    <span>Đổi câu hỏi</span>
                </Space>
            }
            width={720}
            style={{ top: 20 }}
            onCancel={handleCancel}
            closable={!isBusy}
            maskClosable={!isBusy}
            keyboard={!isBusy}
            destroyOnClose
            className="change-question-modal"
            footer={
                <div className="change-question-modal-footer">
                    <Button onClick={handleCancel} disabled={isBusy}>
                        Hủy
                    </Button>
                    <Button
                        type="primary"
                        icon={<SwapOutlined />}
                        loading={isBusy}
                        disabled={!question}
                        onClick={handleSubmit}
                    >
                        Sinh câu thay thế
                    </Button>
                </div>
            }
        >
            {!question ? (
                <Alert
                    type="warning"
                    showIcon
                    message="Chưa chọn câu hỏi"
                    description="Hãy chọn một câu hỏi trong đề thi trước khi yêu cầu đổi câu."
                />
            ) : (
                <div className="change-question-modal-content">
                    <section className="change-question-current">
                        <div className="change-question-section-heading">
                            <div>
                                <Text strong>Câu hỏi hiện tại</Text>
                            </div>
                            <Space size={6} wrap>
                                <Tag>Câu hỏi trắc nghiệm</Tag>
                                {difficulty && (
                                    <Tag color={difficulty.color}>{difficulty.label}</Tag>
                                )}
                            </Space>
                        </div>

                        <div className="change-question-current-question">
                            <Paragraph className="change-question-current-text">
                                {question.questionText}
                            </Paragraph>
                        </div>

                        <div className="change-question-options">
                            {options.map((option) => {
                                const isCorrect = option.label === question.correctOption;

                                return (
                                    <div
                                        key={option.label}
                                        className={`change-question-option${isCorrect ? " is-correct" : ""}`}
                                    >
                                        <span className="change-question-option-label">
                                            {option.label}.
                                        </span>
                                        <span className="change-question-option-value">
                                            {option.value || "—"}
                                        </span>
                                        {isCorrect && <Tag color="success">Đáp án đúng</Tag>}
                                    </div>
                                );
                            })}
                        </div>
                    </section>

                    <Alert
                        className="change-question-constraint-alert"
                        type="info"
                        showIcon
                        icon={<InfoCircleOutlined />}
                        message="Các ràng buộc mặc định"
                        description="Câu thay thế cần giữ nguyên dạng câu hỏi và mức độ nhận thức, bám sát phạm vi kiến thức của đề. Yêu cầu bên dưới được áp dụng bổ sung, không tự động thay đổi các ràng buộc này."
                    />

                    <section className="change-question-instruction-section">
                        <div className="change-question-instruction-heading">
                            <div className="change-question-instruction-title">
                                <BulbOutlined />
                                <Text strong>Yêu cầu đổi câu</Text>
                            </div>
                        </div>

                        <Form
                            form={form}
                            layout="vertical"
                            requiredMark={false}
                            initialValues={{ instruction: "" }}
                        >
                            <Form.Item
                                name="instruction"
                                rules={[
                                    {
                                        required: true,
                                        whitespace: true,
                                        message: "Vui lòng nhập yêu cầu đổi câu.",
                                    },
                                    {
                                        max: MAX_INSTRUCTION_LENGTH,
                                        message: `Yêu cầu không được vượt quá ${MAX_INSTRUCTION_LENGTH} ký tự.`,
                                    },
                                ]}
                            >
                                <TextArea
                                    placeholder="Ví dụ: Tạo câu hỏi bằng một tình huống thực tế khác, thay đổi các phương án nhiễu và không lặp lại cách diễn đạt của câu cũ."
                                    autoSize={{ minRows: 4, maxRows: 7 }}
                                    maxLength={MAX_INSTRUCTION_LENGTH}
                                    showCount
                                    disabled={isBusy}
                                />
                            </Form.Item>
                        </Form>

                        <div className="change-question-instruction-hint">
                            <InfoCircleOutlined />
                            <Text type="secondary">
                                Nêu rõ nội dung, bối cảnh hoặc cách diễn đạt mong muốn. Đáp án và lời giải cần được sinh lại phù hợp với câu hỏi mới.
                            </Text>
                        </div>
                    </section>
                </div>
            )}
        </Modal>
    );
}
