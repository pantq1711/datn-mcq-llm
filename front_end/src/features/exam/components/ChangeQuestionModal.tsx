import { useEffect, useState } from "react";
import { Alert, Button, Form, Input, Modal, Radio, Space, Tag, Tooltip, Typography } from "antd";
import { InfoCircleOutlined, SwapOutlined, BulbOutlined, DatabaseOutlined, RobotOutlined } from "@ant-design/icons";

import type { ExamQuestionItem, ChangeQuestionRequest, ExamQuestionType } from "../types/exam.types";
import { difficultyConfig, resolveQuestionType } from "../../../utils/exam.utils";
import QuestionContent from "../components/QuestionContent";

import "./ChangeQuestionModal.css";

const { Text } = Typography;
const { TextArea } = Input;

// Backend API spec mục 7.5: instruction tối đa 500 ký tự
const MAX_INSTRUCTION_LENGTH = 500;

/** Kết quả trang cha báo lại: "bank_exhausted" khi backend trả 409 BANK_EXHAUSTED (docs/api mục 7.5). */
export type ChangeQuestionResult = void | "bank_exhausted";

interface ChangeQuestionModalProps {
    open: boolean;
    question: ExamQuestionItem | null;
    onCancel: () => void;
    onGenerate: (request: ChangeQuestionRequest) => ChangeQuestionResult | Promise<ChangeQuestionResult>;
    loading?: boolean;
}

interface FormValues {
    source: "generate" | "bank";
    instruction?: string;
}

export default function ChangeQuestionModal({
    open,
    question,
    onCancel,
    onGenerate,
    loading = false,
}: ChangeQuestionModalProps) {
    const [form] = Form.useForm<FormValues>();
    const [submitting, setSubmitting] = useState(false);
    // Ngân hàng đã hết câu phù hợp cho chỗ này trong lần mở modal hiện tại
    const [bankExhausted, setBankExhausted] = useState(false);

    const watchedSource = Form.useWatch("source", form) ?? "generate";
    const isBusy = loading || submitting;

    // Xác định loại câu hỏi
    const qType: ExamQuestionType = question ? resolveQuestionType(question) : "mcq";

    // Lấy nhãn dạng câu
    const getQuestionTypeLabel = (type: ExamQuestionType) => {
        switch (type) {
            case "tf": return "Đúng sai";
            case "sa": return "Trả lời ngắn";
            default: return "Trắc nghiệm nhiều phương án";
        }
    };

    // Lấy thông tin hiển thị mức độ (hỗ trợ riêng cho tf hiện mẫu mức 4 ý)
    const renderDifficultyTags = (item: ExamQuestionItem) => {
        const type = resolveQuestionType(item);

        if (type === "tf" && item.statements && item.statements.length > 0) {
            const levelsText = item.statements
                .map(s => (s.difficulty ? difficultyConfig[s.difficulty]?.label : "Biết"))
                .filter(Boolean)
                .join(" · ");
            return <Tag color="blue">{levelsText}</Tag>;
        }

        const diffConfig = item.difficulty ? difficultyConfig[item.difficulty] : null;
        if (!diffConfig) return null;

        return <Tag color={diffConfig.color}>{diffConfig.label}</Tag>;
    };

    useEffect(() => {
        if (open) {
            form.resetFields();
            form.setFieldsValue({ source: "generate", instruction: "" });
            setSubmitting(false);
            setBankExhausted(false);
        }
    }, [open, question?.id, form]);

    const handleSubmit = async () => {
        if (!question || isBusy) return;

        let values: FormValues;
        try {
            values = await form.validateFields();
        } catch {
            return;
        }

        const source = values.source || "generate";
        // Chỉ gửi instruction khi nguồn là sinh mới (generate)
        const instruction = source === "generate" ? (values.instruction || "").trim() : "";

        setSubmitting(true);
        try {
            const result = await onGenerate({ question, instruction, source });
            if (result === "bank_exhausted") {
                // Không tự sinh: chỉ chuyển sang "Sinh mới qua AI" để giáo viên tự bấm (lần sinh tốn lượt gọi AI)
                setBankExhausted(true);
                form.setFieldsValue({ source: "generate" });
            }
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
                        {watchedSource === "bank" ? "Đổi câu từ ngân hàng" : "Sinh câu mới qua AI"}
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
                                <Tag>{getQuestionTypeLabel(qType)}</Tag>
                                {renderDifficultyTags(question)}
                            </Space>
                        </div>

                        {/* Thay khối options cứng bằng QuestionContent dùng chung (không hiện lời giải) */}
                        <div className="change-question-current-question-content">
                            <QuestionContent question={question} showExplanation={false} />
                        </div>
                    </section>

                    <Alert
                        className="change-question-constraint-alert"
                        type="info"
                        showIcon
                        icon={<InfoCircleOutlined />}
                        message="Quy tắc đổi câu"
                        description="Câu thay thế sẽ giữ nguyên dạng câu, chủ đề, mức từng ý và mẫu nhãn tương ứng của vị trí này trong đề thi."
                    />

                    <section className="change-question-instruction-section">
                        <div className="change-question-instruction-heading">
                            <div className="change-question-instruction-title">
                                <BulbOutlined />
                                <Text strong>Phương thức đổi câu hỏi</Text>
                            </div>
                        </div>

                        <Form
                            form={form}
                            layout="vertical"
                            requiredMark={false}
                            initialValues={{ source: "generate", instruction: "" }}
                        >
                            <Form.Item name="source" label="Chọn nguồn câu thay thế">
                                <Radio.Group buttonStyle="solid" disabled={isBusy}>
                                    <Radio.Button value="generate">
                                        <Space size={6}>
                                            <RobotOutlined />
                                            <span>Sinh mới qua AI</span>
                                        </Space>
                                    </Radio.Button>
                                    <Tooltip title={bankExhausted ? "Ngân hàng đã hết câu phù hợp cho chỗ này" : undefined}>
                                        <Radio.Button value="bank" disabled={bankExhausted}>
                                            <Space size={6}>
                                                <DatabaseOutlined />
                                                <span>Lấy từ ngân hàng câu hỏi</span>
                                            </Space>
                                        </Radio.Button>
                                    </Tooltip>
                                </Radio.Group>
                            </Form.Item>

                            {watchedSource === "generate" ? (
                                <>
                                    {bankExhausted && (
                                        <Alert
                                            className="change-question-bank-exhausted-alert"
                                            type="warning"
                                            showIcon
                                            message="Ngân hàng không còn câu phù hợp cho chỗ này"
                                            description="Bạn có thể sinh câu mới qua AI (có thể nhập yêu cầu bổ sung bên dưới) rồi bấm &quot;Sinh câu mới qua AI&quot;."
                                        />
                                    )}
                                    <Form.Item
                                        name="instruction"
                                        label="Yêu cầu bổ sung cho AI (tùy chọn)"
                                        rules={[
                                            {
                                                max: MAX_INSTRUCTION_LENGTH,
                                                message: `Yêu cầu không được vượt quá ${MAX_INSTRUCTION_LENGTH} ký tự.`,
                                            },
                                        ]}
                                    >
                                        <TextArea
                                            placeholder="Ví dụ: Tạo câu hỏi bằng một tình huống thực tế khác, thay đổi các phương án nhiễu..."
                                            autoSize={{ minRows: 3, maxRows: 6 }}
                                            maxLength={MAX_INSTRUCTION_LENGTH}
                                            showCount
                                            disabled={isBusy}
                                        />
                                    </Form.Item>
                                    <div className="change-question-instruction-hint">
                                        <InfoCircleOutlined />
                                        <Text type="secondary">
                                            Nêu rõ bối cảnh hoặc cách diễn đạt mong muốn. Hệ thống sẽ sinh câu mới và chạy kiểm định QC.
                                        </Text>
                                    </div>
                                </>
                            ) : (
                                <Alert
                                    type="success"
                                    showIcon
                                    icon={<DatabaseOutlined />}
                                    message="Đổi từ ngân hàng câu hỏi"
                                    description="Hệ thống sẽ tự động tìm kiếm và thay thế bằng câu hỏi phù hợp nhất đã có trong ngân hàng của bạn. Nếu hết câu khả dụng, hệ thống sẽ thông báo."
                                />
                            )}
                        </Form>
                    </section>
                </div>
            )}
        </Modal>
    );
}