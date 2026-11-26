import { Button, Card, Descriptions, Divider, Drawer, Modal, Space, Tag, Typography, Spin } from "antd";
import { DeleteOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import "./QuestionDetail.css";
import QuestionContent from "../../exam/components/QuestionContent";
import MathText from "../../../components/common/MathText/MathText";
import { describeLevel, QUESTION_TYPE_LABELS } from "../../../utils/exam.utils";
import type { BankItemDetail, QuestionType } from "../types/questionBank.types";
import type { ExamQuestionItem } from "../../exam/types/exam.types";

const { Paragraph, Title } = Typography;

interface QuestionDetailProps {
    open: boolean;
    loading?: boolean;
    question: BankItemDetail | null;
    onClose: () => void;
    /** NV-21: gỡ câu khỏi ngân hàng; chỉ hiện nút khi backend báo canRemove. Trả Promise để nút xác nhận hiện đang xử lý. */
    onRemove?: (question: BankItemDetail) => Promise<void>;
}

const questionTypeMap: Record<QuestionType, { label: string; color: string }> = {
    mcq: { label: QUESTION_TYPE_LABELS.mcq, color: "blue" },
    tf: { label: QUESTION_TYPE_LABELS.tf, color: "cyan" },
    sa: { label: QUESTION_TYPE_LABELS.sa, color: "purple" },
};

/** Đưa câu ngân hàng về dạng QuestionContent dùng (null → undefined). */
function toQuestionItem(question: BankItemDetail): ExamQuestionItem {
    return {
        id: question.id,
        questionText: question.questionText,
        optionA: question.optionA ?? undefined,
        optionB: question.optionB ?? undefined,
        optionC: question.optionC ?? undefined,
        optionD: question.optionD ?? undefined,
        correctOption: question.correctOption ?? undefined,
        explanation: question.explanation ?? undefined,
        difficulty: question.difficulty ?? undefined,
        questionType: question.questionType,
        saAnswer: question.saAnswer ?? undefined,
        statements: question.statements,
        images: question.images,
    };
}

export default function QuestionDetail({
    open,
    loading = false,
    question,
    onClose,
    onRemove,
}: QuestionDetailProps) {
    const questionType = question?.questionType ? questionTypeMap[question.questionType] : null;

    // NV-21: gỡ là ẩn mềm; đề cũ đã có câu vẫn giữ câu, chỉ đề mới không lấy câu này nữa
    const confirmRemove = () => {
        if (!question || !onRemove) return;
        Modal.confirm({
            title: "Gỡ câu này khỏi ngân hàng?",
            content: (
                <div>
                    <p style={{ margin: 0 }}>Câu sẽ không còn trong ngân hàng và không được chọn cho các đề tạo sau.</p>
                    <p style={{ margin: "8px 0 0" }}>Các đề đã có câu này vẫn giữ nguyên câu.</p>
                </div>
            ),
            okText: "Gỡ khỏi ngân hàng",
            okButtonProps: { danger: true },
            cancelText: "Quay lại",
            onOk: () => onRemove(question),
        });
    };

    return (
        <Drawer
            title="Chi tiết câu hỏi"
            open={open}
            onClose={onClose}
            width={640}
            className="question-detail"
            extra={question?.canRemove && onRemove ? (
                <Button danger icon={<DeleteOutlined />} onClick={confirmRemove}>
                    Gỡ khỏi ngân hàng
                </Button>
            ) : undefined}
        >
            {loading ? (
                <div style={{ textAlign: "center", padding: "60px 0" }}>
                    <Spin size="large" description="Đang tải chi tiết câu hỏi..." />
                </div>
            ) : !question ? null : (
                <Space
                    direction="vertical"
                    size={20}
                    className="question-detail-content"
                >
                    <Descriptions
                        column={2}
                        size="small"
                        bordered
                        className="question-detail-info"
                    >
                        <Descriptions.Item label="Mã câu hỏi">
                            #{question.id}
                        </Descriptions.Item>

                        <Descriptions.Item label="Môn học">
                            {question.subject}
                        </Descriptions.Item>

                        <Descriptions.Item label="Phạm vi" span={2}>
                            {/* NV-30: câu riêng chỉ người lưu thấy và dùng lại */}
                            {question.visibility === "private" ? (
                                <Tag color="purple">Riêng: chỉ thầy/cô thấy và dùng lại</Tag>
                            ) : question.visibility === "shared" ? (
                                <Tag>Chung: mọi giáo viên thấy và dùng lại</Tag>
                            ) : "—"}
                        </Descriptions.Item>

                        <Descriptions.Item label="Dạng câu">
                            {questionType ? (
                                questionType.label
                            ) : (
                                "—"
                            )}
                        </Descriptions.Item>

                        <Descriptions.Item label="Mức độ">
                            {/* Câu tf có difficulty null: mức nằm ở từng ý (docs/api "Ngân hàng câu hỏi") */}
                            {question.questionType === "tf" && !question.difficulty ? (
                                "Theo từng ý"
                            ) : (
                                describeLevel(question.difficulty).label
                            )}
                        </Descriptions.Item>

                        <Descriptions.Item label="Lớp – Chủ đề" span={2}>
                            {[question.gradeLevel ? `Lớp ${question.gradeLevel}` : null, question.topic]
                                .filter(Boolean)
                                .join(" – ") || "—"}
                        </Descriptions.Item>

                        <Descriptions.Item label="Số đề đã dùng">
                            {question.usageCount ?? 0}
                        </Descriptions.Item>

                        <Descriptions.Item label="Ngày tạo">
                            {question.createdAt ? dayjs(question.createdAt).format("DD/MM/YYYY HH:mm") : "—"}
                        </Descriptions.Item>

                        {question.origin?.edited && (
                            <Descriptions.Item label="Nguồn" span={2}>
                                <Tag color="gold">Đã sửa</Tag>
                            </Descriptions.Item>
                        )}
                    </Descriptions>

                    {question.sourceQuestion && (
                        <Card
                            size="small"
                            title="Câu hỏi nguồn"
                            className="question-detail-source"
                        >
                            <Paragraph className="question-detail-source-text">
                                <MathText text={question.sourceQuestion} images={question.images} />
                            </Paragraph>
                        </Card>
                    )}

                    <Divider className="question-detail-divider" />

                    <div className="question-detail-section">
                        <Title level={5} className="question-detail-section-title">
                            Nội dung câu hỏi
                        </Title>

                        {/* Dùng chung với trang đề: đủ mcq / tf / sa, đáp án, lời giải và KaTeX */}
                        <QuestionContent question={toQuestionItem(question)} />
                    </div>
                </Space>
            )}
        </Drawer>
    );
}
