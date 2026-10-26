import { useEffect, useState } from "react";
import { Alert, Button, Form, Input, message, Modal, Select, Space, Typography } from "antd";
import { EditOutlined } from "@ant-design/icons";

import type { ExamQuestionItem, ExamQuestionDifficulty } from "../types/exam.types";
import { updateQuestion } from "../services/examApi";

import "./EditQuestionModal.css";

const { Text } = Typography;
const { TextArea } = Input;

interface EditQuestionFormValues {
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctOption: "A" | "B" | "C" | "D";
    difficulty: ExamQuestionDifficulty;
    explanation?: string;
}

interface EditQuestionModalProps {
    open: boolean;
    question: ExamQuestionItem | null;
    examId: number;
    slotId: number;
    onCancel: () => void;
    onSave: (updatedQuestion: ExamQuestionItem) => void | Promise<void>;
    loading?: boolean;
}

export default function EditQuestionModal({
    open,
    question,
    examId,
    slotId,
    onCancel,
    onSave,
    loading = false,
}: EditQuestionModalProps) {
    const [form] = Form.useForm<EditQuestionFormValues>();
    const [submitting, setSubmitting] = useState(false);

    const isBusy = loading || submitting;

    // Khi Modal mở hoặc đổi câu hỏi, đổ dữ liệu cũ vào form
    useEffect(() => {
        if (open && question) {
            form.setFieldsValue({
                questionText: question.questionText || "",
                optionA: question.optionA || "",
                optionB: question.optionB || "",
                optionC: question.optionC || "",
                optionD: question.optionD || "",
                correctOption: (question.correctOption as "A" | "B" | "C" | "D") || "A",
                difficulty: question.difficulty || "medium",
                explanation: question.explanation || "",
            });
            setSubmitting(false);
        }
    }, [open, question, form]);

    const handleSubmit = async () => {
        if (!question || isBusy) return;

        let values: EditQuestionFormValues;
        try {
            values = await form.validateFields();
        } catch {
            return;
        }

        setSubmitting(true);
        try {
            // 1️⃣ Tạo đối tượng câu hỏi mới
            const updated: ExamQuestionItem = {
                ...question,
                ...values,
            };

            // 2️⃣ Gọi API
            const saved = await updateQuestion(examId, slotId, updated);

            // 3️⃣ Thông báo UI
            message.success("Cập nhật câu hỏi thành công");

            // 4️⃣ Truyền kết quả lên component cha (ExamDetailPage)
            await onSave(saved);
        } catch (e) {
            console.error(e);
            message.error(e instanceof Error ? e.message : "Gặp lỗi khi cập nhật câu hỏi");
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
                    <div className="edit-question-row-grid">
                        <Form.Item
                            name="difficulty"
                            label={<Text strong>Mức độ nhận thức</Text>}
                            rules={[{ required: true, message: "Vui lòng chọn mức độ." }]}
                        >
                            <Select
                                disabled={isBusy}
                                options={[
                                    { value: "easy", label: "Biết (Easy)" },
                                    { value: "medium", label: "Hiểu (Medium)" },
                                    { value: "hard", label: "Vận dụng (Hard)" },
                                ]}
                            />
                        </Form.Item>

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
                    </div>

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

                    <div className="edit-question-options-grid">
                        <Form.Item
                            name="optionA"
                            label={<Text strong>Phương án A</Text>}
                            rules={[{ required: true, whitespace: true, message: "Nhập đáp án A." }]}
                        >
                            <Input placeholder="Nội dung đáp án A" disabled={isBusy} />
                        </Form.Item>

                        <Form.Item
                            name="optionB"
                            label={<Text strong>Phương án B</Text>}
                            rules={[{ required: true, whitespace: true, message: "Nhập đáp án B." }]}
                        >
                            <Input placeholder="Nội dung đáp án B" disabled={isBusy} />
                        </Form.Item>

                        <Form.Item
                            name="optionC"
                            label={<Text strong>Phương án C</Text>}
                            rules={[{ required: true, whitespace: true, message: "Nhập đáp án C." }]}
                        >
                            <Input placeholder="Nội dung đáp án C" disabled={isBusy} />
                        </Form.Item>

                        <Form.Item
                            name="optionD"
                            label={<Text strong>Phương án D</Text>}
                            rules={[{ required: true, whitespace: true, message: "Nhập đáp án D." }]}
                        >
                            <Input placeholder="Nội dung đáp án D" disabled={isBusy} />
                        </Form.Item>
                    </div>

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