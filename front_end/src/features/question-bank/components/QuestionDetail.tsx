import { Card, Descriptions, Divider, Drawer, Space, Tag, Typography, Spin } from "antd";
import dayjs from "dayjs";
import "./QuestionDetail.css";
import QuestionContent from "../../exam/components/QuestionContent";
import type { BankItemDetail, QuestionType } from "../types/questionBank.types";
import type { ExamQuestionItem } from "../../exam/types/exam.types";

const { Paragraph, Title } = Typography;

interface QuestionDetailProps {
    open: boolean;
    loading?: boolean;
    question: BankItemDetail | null;
    onClose: () => void;
}

const difficultyMap = {
    easy: {
        label: "Dễ",
        color: "green",
    },
    medium: {
        label: "Trung bình",
        color: "orange",
    },
    hard: {
        label: "Khó",
        color: "red",
    },
} as const;

const questionTypeMap: Record<QuestionType, { label: string; color: string }> = {
    mcq: { label: "Trắc nghiệm", color: "blue" },
    tf: { label: "Đúng / Sai", color: "cyan" },
    sa: { label: "Trả lời ngắn", color: "purple" },
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
    };
}

export default function QuestionDetail({
    open,
    loading = false,
    question,
    onClose,
}: QuestionDetailProps) {
    const questionType = question?.questionType ? questionTypeMap[question.questionType] : null;

    return (
        <Drawer
            title="Chi tiết câu hỏi"
            open={open}
            onClose={onClose}
            width={640}
            className="question-detail"
        >
            {loading ? (
                <div style={{ textAlign: "center", padding: "60px 0" }}>
                    <Spin size="large" tip="Đang tải chi tiết câu hỏi..." />
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

                        <Descriptions.Item label="Dạng câu">
                            {questionType ? (
                                <Tag color={questionType.color}>{questionType.label}</Tag>
                            ) : (
                                "—"
                            )}
                        </Descriptions.Item>

                        <Descriptions.Item label="Độ khó">
                            {/* Câu tf có difficulty null: mức nằm ở từng ý (docs/api "Ngân hàng câu hỏi") */}
                            {question.difficulty ? (
                                <Tag color={difficultyMap[question.difficulty].color}>
                                    {difficultyMap[question.difficulty].label}
                                </Tag>
                            ) : question.questionType === "tf" ? (
                                "Theo từng ý"
                            ) : (
                                "—"
                            )}
                        </Descriptions.Item>

                        <Descriptions.Item label="Khối – Chủ đề" span={2}>
                            {[question.gradeLevel ? `Khối ${question.gradeLevel}` : null, question.topic]
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
                                <Tag color="blue">Đã qua giáo viên sửa</Tag>
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
                                {question.sourceQuestion}
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
