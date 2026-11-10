import { useEffect, useState } from "react";
import { Alert, Button, Form, Input, Modal, Radio, Space, Tag, Tooltip, Typography } from "antd";
import { InfoCircleOutlined, SwapOutlined, BulbOutlined, DatabaseOutlined, RobotOutlined } from "@ant-design/icons";

import type { ExamQuestionItem, ChangeQuestionRequest, ExamQuestionType, ReplaceReason } from "../types/exam.types";
import { REPLACE_REASON_OPTIONS, describeLevel, resolveQuestionType, describeFailureReason } from "../../../utils/exam.utils";
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
    // Lý do đổi (tùy chọn); undefined = không chọn, không gửi lên backend
    const [reason, setReason] = useState<ReplaceReason | undefined>(undefined);

    const watchedSource = Form.useWatch("source", form) ?? "generate";
    const isBusy = loading || submitting;
    const isFailed = question?.state === "failed";

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

    // Mức độ của câu đang có (tf hiện mức 4 ý); câu chưa soạn được thì không có mức nên không gọi
    const renderDifficultyTags = (item: ExamQuestionItem) => {
        const type = resolveQuestionType(item);

        if (type === "tf" && item.statements && item.statements.length > 0) {
            const levelsText = item.statements
                .map(s => describeLevel(s.difficulty).label)
                .join(" · ");
            return <Tag color="blue">{levelsText}</Tag>;
        }

        const diffConfig = describeLevel(item.difficulty);
        return <Tag color={diffConfig.color}>{diffConfig.label}</Tag>;
    };

    useEffect(() => {
        if (open) {
            form.resetFields();
            form.setFieldsValue({ source: "generate", instruction: "" });
            setSubmitting(false);
            setBankExhausted(false);
            setReason(undefined);
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
        const finalReason = isFailed ? undefined : reason;

        setSubmitting(true);
        try {
            const result = await onGenerate({
                question,
                instruction,
                source,
                ...(finalReason ? { reason: finalReason } : {}),
            });
            if (result === "bank_exhausted") {
                // Không tự soạn: chỉ chuyển sang "Soạn câu mới" để giáo viên tự bấm (mỗi lần soạn tốn lượt gọi AI)
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
                    <span>{isFailed ? "Soạn lại câu" : "Đổi câu hỏi"}</span>
                </Space>
            }
            width={720}
            style={{ top: 20 }}
            // Nội dung dài thì cuộn trong hộp; nút hành động ở đáy luôn thấy được (kể cả màn 1366×768)
            styles={{ body: { maxHeight: "calc(100vh - 210px)", overflowY: "auto" } }}
            onCancel={handleCancel}
            closable={!isBusy}
            maskClosable={!isBusy}
            keyboard={!isBusy}
            destroyOnClose
            className="change-question-modal"
            footer={
                <div
                    className="change-question-modal-footer"
                    style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 12 }}
                >
                    {isFailed ? (
                        <Text type="secondary">Hệ thống sẽ soạn lại từ đầu (tính lại 3 lần thử)</Text>
                    ) : (
                        <Text type="danger">Câu hiện tại sẽ bị thay. Không hoàn tác được.</Text>
                    )}
                    <Space size={8}>
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
                            {watchedSource === "bank"
                                ? "Lấy câu từ ngân hàng"
                                : isFailed ? "Soạn lại" : "Soạn câu mới"}
                        </Button>
                    </Space>
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
                                {question.topic && <Tag color="cyan">{question.topic}</Tag>}
                                {!isFailed && renderDifficultyTags(question)}
                            </Space>
                        </div>

                        {/* Hiển thị Alert lỗi hoặc QuestionContent tùy theo trạng thái isFailed */}
                        <div className="change-question-current-question-content">
                            {isFailed ? (
                                <Alert
                                    type="error"
                                    showIcon
                                    message="Hệ thống chưa soạn được câu này"
                                    description={describeFailureReason(question.failureReason)}
                                />
                            ) : (
                                <QuestionContent question={question} showExplanation={false} />
                            )}
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
                                            <span>Soạn câu mới</span>
                                        </Space>
                                    </Radio.Button>
                                    <Tooltip title={bankExhausted ? "Ngân hàng đã hết câu phù hợp để thay câu này" : undefined}>
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
                                            message="Ngân hàng không còn câu phù hợp để thay câu này"
                                            description="Bạn có thể soạn câu mới (có thể ghi thêm yêu cầu bên dưới) rồi bấm &quot;Soạn câu mới&quot;."
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
                                            Nêu rõ bối cảnh hoặc cách diễn đạt mong muốn. Hệ thống sẽ soạn câu mới và tự kiểm tra đáp án.
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

                    {/* Lý do đổi: ẩn hoàn toàn khi lỗi (isFailed) */}
                    {!isFailed && (
                        <section className="change-question-reason-section">
                            <Text strong>Lý do đổi câu (tùy chọn)</Text>
                            <div className="change-question-reason-chips" role="group" aria-label="Lý do đổi câu">
                                {REPLACE_REASON_OPTIONS.map((option) => (
                                    <Tag.CheckableTag
                                        key={option.value}
                                        className="change-question-reason-chip"
                                        checked={reason === option.value}
                                        onChange={(checked) => {
                                            if (!isBusy) setReason(checked ? option.value : undefined);
                                        }}
                                    >
                                        {option.label}
                                    </Tag.CheckableTag>
                                ))}
                            </div>
                            <Text type="secondary" className="change-question-reason-hint">
                                Giúp hệ thống biết câu cũ chưa tốt ở đâu. Không chọn cũng được.
                            </Text>
                        </section>
                    )}
                </div>
            )}
        </Modal>
    );
}