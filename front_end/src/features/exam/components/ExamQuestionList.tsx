import { Button, Empty, List, Skeleton, Space, Tag, Tooltip, Typography } from "antd";
import { DeleteOutlined, BulbOutlined, CheckCircleOutlined, SwapOutlined, EditOutlined } from "@ant-design/icons";

import "./ExamQuestionList.css";
import type { ExamQuestionDifficulty, ExamQuestionItem, ExamDetail } from "../types/exam.types";

const { Text, Paragraph } = Typography;

interface ExamQuestionListProps {
    questions: ExamQuestionItem[];
    loading?: boolean;
    onRemove?: (question: ExamQuestionItem, index: number) => void;
    onKeep?: (question: ExamQuestionItem, index: number) => void;
    onChangeQuestion?: (question: ExamQuestionItem, index: number) => void;
    onEdit?: (question: ExamQuestionItem, index: number) => void; // Thêm prop onEdit
    examDetail?: ExamDetail; // Add optional exam detail prop
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
    onKeep,
    onChangeQuestion,
    onEdit, // Nhận prop onEdit
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
                    const optionKeys = (question.optionC && question.optionD) ? optionLabels : ['A', 'B'];

                    return (
                        <List.Item
                            key={question.id}
                            className="exam-question-item"
                        >
                            <div className="exam-question-item-content">
                                <div className="exam-question-item-header">
                                    {/* Số thứ tự và Tag độ khó */}
                                    <Space size={8} align="center" wrap>
                                        <div className="exam-question-item-number">
                                            Câu {index + 1}
                                        </div>

                                        {difficulty && (
                                            <Tag color={difficulty.color}>
                                                {difficulty.label}
                                            </Tag>
                                        )}
                                    </Space>

                                    {/* Nhóm các nút hành động: Giữ, Đổi câu khác, Sửa, Xóa */}
                                    <Space size={4} align="center">
                                        <Tooltip title="Giữ lại câu hỏi này">
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<CheckCircleOutlined />}
                                                className="exam-action-btn-keep"
                                                onClick={() => onKeep?.(question, index)}
                                            >
                                                Giữ
                                            </Button>
                                        </Tooltip>

                                        <Tooltip title="Đổi sang câu hỏi khác cùng mức độ">
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<SwapOutlined />}
                                                className="exam-action-btn-change"
                                                onClick={() => onChangeQuestion?.(question, index)}
                                            >
                                                Đổi câu khác
                                            </Button>
                                        </Tooltip>

                                        <Tooltip title="Chỉnh sửa nội dung câu hỏi">
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<EditOutlined />}
                                                className="exam-action-btn-edit"
                                                onClick={() => onEdit?.(question, index)}
                                            >
                                                Sửa
                                            </Button>
                                        </Tooltip>

                                        <Tooltip title="Xóa câu hỏi khỏi đề">
                                            <Button
                                                type="text"
                                                size="small"
                                                danger
                                                icon={<DeleteOutlined />}
                                                className="exam-action-btn-delete"
                                                onClick={() => onRemove?.(question, index)}
                                            />
                                        </Tooltip>
                                    </Space>
                                </div>

                                <Paragraph className="exam-question-item-text">
                                    {question.questionText}
                                </Paragraph>

                                {/* Render options based on question type */}
                                {(() => {
                                    const isShortAnswer = !question.optionA && !question.optionB && !question.optionC && !question.optionD;
                                    const isTrueFalse = !!question.optionA && !!question.optionB && !question.optionC && !question.optionD;
                                    const optionsMap = {
                                        A: question.optionA,
                                        B: question.optionB,
                                        C: question.optionC,
                                        D: question.optionD,
                                    };
                                    if (isShortAnswer) {
                                        return (
                                            <div className="exam-question-short-answer">
                                                <Text strong>Đáp án:</Text> {question.optionA}
                                            </div>
                                        );
                                    }
                                    const keys = isTrueFalse ? ['A', 'B'] : ['A', 'B', 'C', 'D'];
                                    return (
                                        <div className="exam-question-item-options">
                                            {keys.map(label => {
                                                const isCorrect = label === question.correctOption;
                                                return (
                                                    <div
                                                        key={label}
                                                        className={`exam-question-option ${isCorrect ? 'exam-question-option-correct' : ''}`}
                                                    >
                                                        <div className="exam-question-option-label"><Text strong>{label}.</Text></div>
                                                        <Text className="exam-question-option-text">{optionsMap[label]}</Text>
                                                        {isCorrect && (
                                                            <Tag color="success" className="exam-question-option-answer">Đáp án đúng</Tag>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    );
                                })()}

                                {/* Hiển thị lời giải chi tiết nếu có */}
                                {question.explanation && (
                                    <div className="exam-question-explanation">
                                        <div className="exam-question-explanation-header">
                                            <BulbOutlined className="exam-question-explanation-icon" />
                                            <Text strong>Lời giải chi tiết:</Text>
                                        </div>
                                        <Paragraph className="exam-question-explanation-text">
                                            {question.explanation}
                                        </Paragraph>
                                    </div>
                                )}
                            </div>
                        </List.Item>
                    );
                }}
            />
        </div>
    );
}