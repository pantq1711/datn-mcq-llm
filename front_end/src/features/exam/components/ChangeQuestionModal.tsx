import { useEffect, useState } from "react";
import { Alert, Button, ConfigProvider, Form, Input, Modal, Radio, Space, Tag, Tooltip, Typography } from "antd";
import { SwapOutlined, DatabaseOutlined, EditOutlined, DownOutlined, UpOutlined } from "@ant-design/icons";

import type { ExamQuestionItem, ChangeQuestionRequest, ExamQuestionType, ReplaceReason, TeacherRequestCode } from "../types/exam.types";
import { teacherRequestOptionsFor } from "../types/exam.types";
import { REPLACE_REASON_OPTIONS, describeLevel, resolveQuestionType, describeFailureReason, QUESTION_TYPE_LABELS } from "../../../utils/exam.utils";
import MathText from "../../../components/common/MathText/MathText";
import QuestionContent from "../components/QuestionContent";
import { READABLE_MODAL_THEME } from "./readableModalTheme";

import "./ChangeQuestionModal.css";

const { Text } = Typography;
const { TextArea } = Input;

// Backend API spec mục 7.5: instruction tối đa 1000 ký tự (PR B, bằng giới hạn của đề và ô, để văn bản thừa hưởng điền đủ)
const MAX_INSTRUCTION_LENGTH = 1000;

// Lời của lý do đổi nói theo giáo viên (rà soát G16, bảng thuật ngữ mục 4); giá trị gửi backend giữ nguyên
const REASON_LABELS: Partial<Record<ReplaceReason, string>> = {
    ambiguous_options: "Phương án mơ hồ, có hơn một đáp án đúng",
    off_matrix: "Không đúng ma trận",
    duplicate_pattern: "Giống câu khác",
    latex_format: "Công thức hiện sai",
};
const REASON_OPTIONS = REPLACE_REASON_OPTIONS.map((o) => ({ ...o, label: REASON_LABELS[o.value] ?? o.label }));

/** Kết quả trang cha báo lại: "bank_exhausted" khi backend trả 409 BANK_EXHAUSTED (docs/api mục 7.5). */
export type ChangeQuestionResult = void | "bank_exhausted";

interface ChangeQuestionModalProps {
    open: boolean;
    question: ExamQuestionItem | null;
    /** Vị trí câu trong đề ("Phần I – Câu 3"), ghi ở tiêu đề hộp. */
    positionLabel?: string;
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
    positionLabel,
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
    // Chip yêu cầu của lượt Đổi (PR B): mở hộp thì chọn sẵn chip thừa hưởng của ô hoặc đề
    const [requestCodes, setRequestCodes] = useState<TeacherRequestCode[]>([]);
    // Câu đang có thu gọn một dòng để phần chọn cách đổi nằm ngay màn đầu (G15); bấm "Xem cả câu" mới mở
    const [showFullQuestion, setShowFullQuestion] = useState(false);

    const watchedSource = Form.useWatch("source", form) ?? "generate";
    const isBusy = loading || submitting;
    const isFailed = question?.state === "failed";

    // Xác định loại câu hỏi
    const qType: ExamQuestionType = question ? resolveQuestionType(question) : "mcq";

    // Lấy nhãn dạng câu (tên dùng chung, G25)
    const getQuestionTypeLabel = (type: ExamQuestionType) => QUESTION_TYPE_LABELS[type] ?? QUESTION_TYPE_LABELS.mcq;

    // Mức độ của câu đang có (tf hiện mức 4 ý); câu chưa soạn được thì không có mức nên không gọi
    const renderDifficultyTags = (item: ExamQuestionItem) => {
        const type = resolveQuestionType(item);

        if (type === "tf" && item.statements && item.statements.length > 0) {
            const levelsText = item.statements
                .map(s => `${s.key}) ${describeLevel(s.difficulty).label}`)
                .join(" · ");
            return <Tag color="blue">{levelsText}</Tag>;
        }

        const diffConfig = describeLevel(item.difficulty);
        return <Tag color={diffConfig.color}>{diffConfig.label}</Tag>;
    };

    // Yêu cầu thừa hưởng (docs/api mục 7.4): Đổi câu THAY HẲN yêu cầu của ô và đề, nên chọn sẵn chip và điền sẵn văn bản
    const inherited = question?.inheritedRequests;
    const requestOptions = teacherRequestOptionsFor(qType);
    // D-15: câu đang đổi do một lượt Đổi tự đặt yêu cầu sinh ra thì điền sẵn yêu cầu của chính lượt đó (Đổi lần hai không phải
    // gõ lại); không thì yêu cầu thừa hưởng của ô hoặc đề
    const prefill = question?.requests?.scope === "replace" ? question.requests : inherited;
    const applicableCodes = (codes?: TeacherRequestCode[]) =>
        (codes ?? []).filter(c => requestOptions.some(o => o.value === c));

    useEffect(() => {
        if (open) {
            form.resetFields();
            form.setFieldsValue({ source: "generate", instruction: prefill?.text ?? "" });
            setSubmitting(false);
            setBankExhausted(false);
            setReason(undefined);
            setRequestCodes(applicableCodes(prefill?.codes));
            setShowFullQuestion(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, question?.id, form]);

    /**
     * D-08: chip và văn bản cuối có đúng bằng yêu cầu thừa hưởng (thứ tự chip không tính, văn bản đã trim)? Bằng thì không gửi,
     * backend giữ mức của ô/đề; khác (kể cả bỏ hết chip, hoặc giữ yêu cầu của lần Đổi trước) mới là lượt Đổi tự đặt yêu cầu.
     */
    const sameAsInherited = (codes: TeacherRequestCode[], text: string) => {
        const base = new Set(applicableCodes(inherited?.codes));
        return codes.length === base.size && codes.every(c => base.has(c)) && text === (inherited?.text ?? "").trim();
    };

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
        const typed = source === "generate" ? (values.instruction || "").trim() : "";
        // D-08: giáo viên không đổi gì so với yêu cầu thừa hưởng thì không gửi chip lẫn văn bản
        const ownRequest = source === "generate" && !sameAsInherited(requestCodes, typed);
        const instruction = ownRequest ? typed : "";
        const finalReason = isFailed ? undefined : reason;

        setSubmitting(true);
        try {
            const result = await onGenerate({
                question,
                instruction,
                source,
                ...(finalReason ? { reason: finalReason } : {}),
                // Yêu cầu khác thừa hưởng: gửi chip (kể cả [] khi đã bỏ hết) để backend coi là tự đặt yêu cầu
                ...(ownRequest ? { requestCodes } : {}),
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
        <ConfigProvider theme={READABLE_MODAL_THEME}>
        <Modal
            open={open}
            title={
                <Space size={10}>
                    <SwapOutlined className="change-question-modal-title-icon" />
                    <span>
                        {isFailed ? "Soạn lại câu" : "Đổi câu khác"}
                        {positionLabel && `: ${positionLabel}`}
                    </span>
                </Space>
            }
            width={760}
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
                        <Text type="secondary">Hệ thống sẽ soạn lại câu này từ đầu.</Text>
                    ) : (
                        <Text type="danger">Câu đang có sẽ bị thay, không lấy lại được.</Text>
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
                    description="Thầy/cô chọn một câu trong đề rồi bấm Đổi câu khác."
                />
            ) : (
                <Form
                    form={form}
                    layout="vertical"
                    requiredMark={false}
                    initialValues={{ source: "generate", instruction: "" }}
                    className="change-question-modal-content"
                >
                    {/* Câu đang có: một dòng, bấm "Xem cả câu" để mở (G15) */}
                    <section className="change-question-current">
                        <div className="change-question-section-heading">
                            <Text strong>{isFailed ? "Câu chưa soạn được" : "Câu đang có"}</Text>
                            <Space size={6} wrap>
                                <Tag>{getQuestionTypeLabel(qType)}</Tag>
                                {question.topic && <Tag color="cyan">{question.topic}</Tag>}
                                {!isFailed && renderDifficultyTags(question)}
                            </Space>
                        </div>

                        {isFailed ? (
                            <Text className="change-question-failed-reason">
                                {describeFailureReason(question.failureReason)}
                            </Text>
                        ) : showFullQuestion ? (
                            <div className="change-question-current-question-content">
                                <QuestionContent question={question} showExplanation={false} />
                            </div>
                        ) : (
                            <div className="change-question-current-line">
                                <MathText text={question.questionText} images={question.images} compact />
                            </div>
                        )}
                        {!isFailed && (
                            <Button
                                type="link"
                                className="change-question-toggle"
                                icon={showFullQuestion ? <UpOutlined /> : <DownOutlined />}
                                aria-expanded={showFullQuestion}
                                onClick={() => setShowFullQuestion(v => !v)}
                            >
                                {showFullQuestion ? "Thu gọn" : "Xem cả câu và các phương án"}
                            </Button>
                        )}
                    </section>

                    <section className="change-question-block">
                        <Form.Item
                            name="source"
                            label={<Text strong>Lấy câu mới từ đâu?</Text>}
                            className="change-question-source"
                        >
                            <Radio.Group buttonStyle="solid" disabled={isBusy}>
                                <Radio.Button value="generate">
                                    <Space size={6}>
                                        <EditOutlined />
                                        <span>Soạn câu mới</span>
                                    </Space>
                                </Radio.Button>
                                <Tooltip title={bankExhausted ? "Ngân hàng đã hết câu phù hợp để thay câu này" : undefined}>
                                    <Radio.Button value="bank" disabled={bankExhausted}>
                                        <Space size={6}>
                                            <DatabaseOutlined />
                                            <span>Lấy từ ngân hàng</span>
                                        </Space>
                                    </Radio.Button>
                                </Tooltip>
                            </Radio.Group>
                        </Form.Item>
                        <Text className="change-question-note">
                            {watchedSource === "bank"
                                ? "Hệ thống chọn một câu phù hợp có sẵn trong ngân hàng. Nếu ngân hàng hết câu, hệ thống sẽ báo để thầy/cô chọn soạn câu mới."
                                : "Câu mới sẽ cùng dạng, cùng chủ đề và cùng mức độ với câu đang có. Hệ thống soạn xong sẽ tự kiểm tra lại đáp án."}
                        </Text>
                        {watchedSource === "generate" && bankExhausted && (
                            <Alert
                                className="change-question-bank-exhausted-alert"
                                type="warning"
                                showIcon
                                message="Ngân hàng không còn câu phù hợp để thay câu này"
                                description="Thầy/cô có thể ghi thêm yêu cầu bên dưới rồi bấm &quot;Soạn câu mới&quot;."
                            />
                        )}
                    </section>

                    {/* Lý do đổi: ẩn hoàn toàn khi lỗi (isFailed) */}
                    {!isFailed && (
                        <section className="change-question-reason-section">
                            <Text strong>
                                Câu đang có chưa tốt ở đâu? <span className="change-question-optional">(không bắt buộc)</span>
                            </Text>
                            <div className="change-question-reason-chips" role="group" aria-label="Câu đang có chưa tốt ở đâu">
                                {REASON_OPTIONS.map((option) => (
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
                        </section>
                    )}

                    {/* Yêu cầu cho câu mới: chip (PR B) và lời ghi thêm gộp một mục (G16); chỉ khi soạn mới */}
                    {watchedSource === "generate" && (
                        <section className="change-question-reason-section">
                            <Text strong>
                                Thầy/cô muốn câu mới thế nào? <span className="change-question-optional">(không bắt buộc)</span>
                            </Text>
                            <div className="change-question-reason-chips" role="group" aria-label="Thầy/cô muốn câu mới thế nào">
                                {requestOptions.map((option) => (
                                    <Tag.CheckableTag
                                        key={option.value}
                                        className="change-question-reason-chip"
                                        checked={requestCodes.includes(option.value)}
                                        onChange={(checked) => {
                                            if (isBusy) return;
                                            setRequestCodes(prev => checked
                                                ? [...prev, option.value]
                                                : prev.filter(c => c !== option.value));
                                        }}
                                    >
                                        {option.label}
                                    </Tag.CheckableTag>
                                ))}
                            </div>
                            <Form.Item
                                name="instruction"
                                label={<span className="change-question-instruction-label">Ghi thêm cho hệ thống</span>}
                                className="change-question-instruction"
                                rules={[
                                    {
                                        max: MAX_INSTRUCTION_LENGTH,
                                        message: `Lời ghi thêm dài quá ${MAX_INSTRUCTION_LENGTH} kí tự.`,
                                    },
                                ]}
                            >
                                <TextArea
                                    placeholder="Ví dụ: dùng tình huống đi chợ mua rau; đổi số liệu cho gọn hơn."
                                    autoSize={{ minRows: 2, maxRows: 6 }}
                                    maxLength={MAX_INSTRUCTION_LENGTH}
                                    showCount
                                    disabled={isBusy}
                                />
                            </Form.Item>
                            {prefill && (
                                <Text type="secondary" className="change-question-reason-hint">
                                    Đã chọn và điền sẵn {prefill.scope === "replace"
                                        ? "yêu cầu của lần đổi trước"
                                        : prefill.scope === "cell" ? "yêu cầu của nhóm câu này trong ma trận" : "yêu cầu chung của đề"}.
                                    Thầy/cô bỏ chọn hoặc sửa nếu muốn khác.
                                </Text>
                            )}
                        </section>
                    )}
                </Form>
            )}
        </Modal>
        </ConfigProvider>
    );
}