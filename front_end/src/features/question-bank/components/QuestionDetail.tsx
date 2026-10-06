import {
    Card,
    Descriptions,
    Divider,
    Drawer,
    Space,
    Tag,
    Typography,
} from "antd";
import "./QuestionDetail.css";

const { Paragraph, Text, Title } = Typography;

export type QuestionStatus = "APPROVED" | "EDITED";

export interface QuestionDetailData {
    id: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctOption: "A" | "B" | "C" | "D";
    subject: string;
    difficulty: "EASY" | "MEDIUM" | "HARD";
    status: QuestionStatus;
    sourceQuestion?: string;
    createdAt: string;
}

interface QuestionDetailProps {
    open: boolean;
    question: QuestionDetailData | null;
    onClose: () => void;
}

const difficultyMap = {
    EASY: {
        label: "Dễ",
        color: "green",
    },
    MEDIUM: {
        label: "Trung bình",
        color: "orange",
    },
    HARD: {
        label: "Khó",
        color: "red",
    },
} as const;

const statusMap = {
    APPROVED: {
        label: "Đã duyệt",
        color: "green",
    },
    EDITED: {
        label: "Đã chỉnh sửa",
        color: "blue",
    },
} as const;

export default function QuestionDetail({
    open,
    question,
    onClose,
}: QuestionDetailProps) {
    if (!question) {
        return (
            <Drawer
                title="Chi tiết câu hỏi"
                open={open}
                onClose={onClose}
                width={640}
            />
        );
    }

    const difficulty = difficultyMap[question.difficulty];
    const status = statusMap[question.status];

    const options = [
        {
            key: "A",
            label: "A",
            content: question.optionA,
        },
        {
            key: "B",
            label: "B",
            content: question.optionB,
        },
        {
            key: "C",
            label: "C",
            content: question.optionC,
        },
        {
            key: "D",
            label: "D",
            content: question.optionD,
        },
    ];

    return (
        <Drawer
            title="Chi tiết câu hỏi"
            open={open}
            onClose={onClose}
            width={640}
            className="question-detail"
        >
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

                    <Descriptions.Item label="Độ khó">
                        <Tag color={difficulty.color}>
                            {difficulty.label}
                        </Tag>
                    </Descriptions.Item>

                    <Descriptions.Item label="Trạng thái">
                        <Tag color={status.color}>
                            {status.label}
                        </Tag>
                    </Descriptions.Item>

                    <Descriptions.Item label="Ngày tạo" span={2}>
                        {question.createdAt}
                    </Descriptions.Item>
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

                <div className="question-detail-section">
                    <Title level={5} className="question-detail-section-title">
                        Nội dung câu hỏi
                    </Title>

                    <Paragraph className="question-detail-question">
                        {question.questionText}
                    </Paragraph>
                </div>

                <Divider className="question-detail-divider" />

                <div className="question-detail-section">
                    <Title level={5} className="question-detail-section-title">
                        Các phương án trả lời
                    </Title>

                    <Space
                        direction="vertical"
                        size={12}
                        className="question-detail-options"
                    >
                        {options.map((option) => {
                            const isCorrect =
                                option.key === question.correctOption;

                            return (
                                <div
                                    key={option.key}
                                    className={`question-detail-option ${isCorrect
                                            ? "question-detail-option-correct"
                                            : ""
                                        }`}
                                >
                                    <div className="question-detail-option-label">
                                        <Text strong>{option.label}</Text>

                                        {isCorrect && (
                                            <Tag color="green">
                                                Đáp án đúng
                                            </Tag>
                                        )}
                                    </div>

                                    <Paragraph
                                        className="question-detail-option-content"
                                    >
                                        {option.content}
                                    </Paragraph>
                                </div>
                            );
                        })}
                    </Space>
                </div>
            </Space>
        </Drawer>
    );
}