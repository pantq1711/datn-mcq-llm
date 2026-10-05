import {
    Card,
    Descriptions,
    Radio,
    Space,
    Tag,
    Typography,
} from "antd";
import './ReviewQuestionForm.css';

import CorrectAnswerSelector from "./CorrectAnswerSelector";
import QuestionEditor from "./QuestionEditor";
import RejectionReasonSelector from "./RejectionReasonSelector";
import ReviewDecision from "./ReviewDecision";

import type {
    CorrectAnswer,
    Difficulty,
    FormatStatus,
    QuestionDraft,
    ReviewDecision as ReviewDecisionType,
} from "../types/review.types";

interface ReviewQuestionFormProps {
    question: QuestionDraft;

    correctAnswer: CorrectAnswer | null;
    formatStatus: FormatStatus | null;
    difficulty: Difficulty | null;
    decision: ReviewDecisionType | null;

    selectedReasons: string[];
    otherReason: string;

    onCorrectAnswerChange: (value: CorrectAnswer) => void;
    onFormatStatusChange: (value: FormatStatus) => void;
    onDifficultyChange: (value: Difficulty) => void;
    onDecisionChange: (value: ReviewDecisionType) => void;

    onQuestionChange: (value: QuestionDraft) => void;

    onReasonsChange: (value: string[]) => void;
    onOtherReasonChange: (value: string) => void;
}

const difficultyOptions = [
    { value: "EASY", label: "Dễ" },
    { value: "MEDIUM", label: "Trung bình" },
    { value: "HARD", label: "Khó" },
];

export default function ReviewQuestionForm({
    question,
    correctAnswer,
    formatStatus,
    difficulty,
    decision,
    selectedReasons,
    otherReason,
    onCorrectAnswerChange,
    onFormatStatusChange,
    onDifficultyChange,
    onDecisionChange,
    onQuestionChange,
    onReasonsChange,
    onOtherReasonChange,
}: ReviewQuestionFormProps) {
    const forceReject =
        correctAnswer === "NONE" ||
        correctAnswer === "MULTIPLE";

    return (
        <div className="review-form">
            {/* Question preview */}
            <Card className="review-section-card">
                <div className="question-header">
                    <div>
                        <Typography.Text type="secondary">
                            Môn học
                        </Typography.Text>

                        <Typography.Title level={4} style={{ marginTop: 4 }}>
                            Vật lý
                        </Typography.Title>
                    </div>

                    <Tag color="processing">
                        Chờ đánh giá
                    </Tag>
                </div>

                <Typography.Title
                    level={5}
                    style={{ marginTop: 24 }}
                >
                    Câu hỏi
                </Typography.Title>

                <Typography.Paragraph>
                    {question.questionText}
                </Typography.Paragraph>

                <Descriptions
                    bordered
                    size="small"
                    column={1}
                    items={[
                        {
                            key: "A",
                            label: "A",
                            children: question.optionA,
                        },
                        {
                            key: "B",
                            label: "B",
                            children: question.optionB,
                        },
                        {
                            key: "C",
                            label: "C",
                            children: question.optionC,
                        },
                        {
                            key: "D",
                            label: "D",
                            children: question.optionD,
                        },
                    ]}
                />
            </Card>

            {/* 1. Correct answer */}
            <CorrectAnswerSelector
                value={correctAnswer}
                onChange={onCorrectAnswerChange}
            />

            {/* 2. Format */}
            <Card className="review-section-card">
                <div className="review-section-title">
                    <Typography.Title level={5} style={{ margin: 0 }}>
                        2. Định dạng
                    </Typography.Title>

                    <Typography.Text type="secondary">
                        Kiểm tra câu hỏi có đúng định dạng yêu cầu hay không
                    </Typography.Text>
                </div>

                <RadioGroupWrapper
                    value={formatStatus}
                    onChange={onFormatStatusChange}
                    options={[
                        {
                            value: "PASS",
                            label: "Đạt chuẩn",
                        },
                        {
                            value: "FAIL",
                            label: "Không đạt chuẩn",
                        },
                    ]}
                />
            </Card>

            {/* 3. Difficulty */}
            <Card className="review-section-card">
                <div className="review-section-title">
                    <Typography.Title level={5} style={{ margin: 0 }}>
                        3. Độ khó
                    </Typography.Title>

                    <Typography.Text type="secondary">
                        Đánh giá độ khó phù hợp với người học
                    </Typography.Text>
                </div>

                <RadioGroupWrapper
                    value={difficulty}
                    onChange={onDifficultyChange}
                    options={difficultyOptions}
                />
            </Card>

            {/* 4. Decision */}
            <ReviewDecision
                value={decision}
                correctAnswer={correctAnswer}
                onChange={onDecisionChange}
            />

            {/* 5. Edit */}
            {decision === "EDIT" && !forceReject && (
                <QuestionEditor
                    value={question}
                    onChange={onQuestionChange}
                />
            )}

            {/* 6. Rejection */}
            {(decision === "REJECT" || forceReject) && (
                <RejectionReasonSelector
                    selectedReasons={selectedReasons}
                    otherReason={otherReason}
                    onReasonsChange={onReasonsChange}
                    onOtherReasonChange={onOtherReasonChange}
                />
            )}
        </div>
    );
}

interface RadioGroupWrapperProps<T extends string> {
    value: T | null;
    onChange: (value: T) => void;
    options: {
        value: T;
        label: string;
    }[];
}

function RadioGroupWrapper<T extends string>({
    value,
    onChange,
    options,
}: RadioGroupWrapperProps<T>) {
    return (
        <Radio.Group
            value={value ?? undefined}
            onChange={(e) => onChange(e.target.value)}
        >
            <Space size={24} wrap>
                {options.map((option) => (
                    <Radio
                        key={option.value}
                        value={option.value}
                    >
                        {option.label}
                    </Radio>
                ))}
            </Space>
        </Radio.Group>
    );
}