import { Button, Empty, List, Skeleton, Space, Tag, Tooltip, Typography } from "antd";
import { DeleteOutlined } from "@ant-design/icons";

import "./ExamQuestionList.css";
import type { ExamQuestionDifficulty, ExamQuestionItem } from "../types/exam.types";

const { Text, Paragraph } = Typography;

interface ExamQuestionListProps {
    questions: ExamQuestionItem[];
    loading?: boolean;
    onRemove?: (question: ExamQuestionItem, index: number) => void;
}

const difficultyConfig: Record<
    ExamQuestionDifficulty,
    {
        label: string;
        color: string;
    }
> = {
    easy: {
        label: "Biết",
        color: "green",
    },
    medium: {
        label: "Hiểu",
        color: "orange",
    },
    hard: {
        label: "Vận dụng",
        color: "red",
    },
};

const optionLabels = ["A", "B", "C", "D"] as const;

export default function ExamQuestionList({
    questions,
    loading = false,
    onRemove,
}: ExamQuestionListProps) {
    if (loading) {
        return (
            <div className="exam-question-list">
                <Skeleton
                    active
                    paragraph={{
                        rows: 12,
                    }}
                />
            </div>
        );
    }

    if (questions.length === 0) {
        return (
            <div className="exam-question-list exam-question-list-empty">
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="Chưa có câu hỏi trong đề thi"
                />
            </div>
        );
    }

    return (
        <div className="exam-question-list">
            <List
                dataSource={questions}
                split={false}
                className="exam-question-list-content"
                renderItem={(question, index) => {
                    const difficulty = question.difficulty
                        ? difficultyConfig[question.difficulty]
                        : null;

                    const options = {
                        A: question.optionA,
                        B: question.optionB,
                        C: question.optionC,
                        D: question.optionD,
                    };

                    return (
                        <List.Item
                            key={question.id}
                            className="exam-question-item"
                        >
                            <div className="exam-question-item-content">
                                <div className="exam-question-item-header">
                                    <div className="exam-question-item-number">
                                        Câu {index + 1}
                                    </div>

                                    <Space
                                        size={8}
                                        wrap
                                    >
                                        {difficulty && (
                                            <Tag color={difficulty.color}>
                                                {difficulty.label}
                                            </Tag>
                                        )}

                                        <Tooltip title="Xóa câu hỏi khỏi đề">
                                            <Button
                                                type="text"
                                                danger
                                                icon={<DeleteOutlined />}
                                                onClick={() =>
                                                    onRemove?.(
                                                        question,
                                                        index,
                                                    )
                                                }
                                            />
                                        </Tooltip>
                                    </Space>
                                </div>

                                <Paragraph className="exam-question-item-text">
                                    {question.questionText}
                                </Paragraph>

                                <div className="exam-question-item-options">
                                    {optionLabels.map((label) => {
                                        const isCorrect =
                                            label ===
                                            question.correctOption;

                                        return (
                                            <div
                                                key={label}
                                                className={`exam-question-option ${isCorrect
                                                    ? "exam-question-option-correct"
                                                    : ""
                                                    }`}
                                            >
                                                <div className="exam-question-option-label">
                                                    <Text strong>
                                                        {label}.
                                                    </Text>
                                                </div>

                                                <Text className="exam-question-option-text">
                                                    {options[label]}
                                                </Text>

                                                {isCorrect && (
                                                    <Tag
                                                        color="success"
                                                        className="exam-question-option-answer"
                                                    >
                                                        Đáp án đúng
                                                    </Tag>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        </List.Item>
                    );
                }}
            />
        </div>
    );
}