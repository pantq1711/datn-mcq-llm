import { useState } from "react";
import { Alert, Button, Card, Col, Radio, Row, Space, Tag, Typography } from "antd";
import { ArrowLeftOutlined, SaveOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import type { CorrectAnswer, Difficulty, FormatStatus, QuestionDraft, ReviewDecisions } from "../types/review.types";

import CorrectAnswerSelector from "../components/CorrectAnswerSelector";
import QuestionEditor from "../components/QuestionEditor";
import RejectionReasonSelector from "../components/RejectionReasonSelector";
import ReviewDecision from "../components/ReviewDecision";
import QuestionExplanationCard from "../components/QuestionExplanationCard";

import "./ReviewQuestionDetailPage.css";

const INITIAL_QUESTION: QuestionDraft = {
    questionText:
        "Một vật chuyển động thẳng đều với vận tốc 5 m/s. Trong 4 giây, vật đi được quãng đường bao nhiêu?",
    optionA: "10 m",
    optionB: "20 m",
    optionC: "25 m",
    optionD: "40 m",
    correctAnswer: null,
    explanation: "Áp dụng công thức chuyển động thẳng đều: s = v * t = 5 * 4 = 20 (mét). Do đó đáp án chính xác là phương án B.",
};

export default function ReviewQuestionDetailPage() {
    const navigate = useNavigate();

    const handleBackToList = () => {
        navigate("/review");
    };

    const [question, setQuestion] =
        useState<QuestionDraft>(INITIAL_QUESTION);

    const [correctAnswer, setCorrectAnswer] =
        useState<CorrectAnswer | null>(null);

    const [formatStatus, setFormatStatus] =
        useState<FormatStatus | null>(null);

    const [difficulty, setDifficulty] =
        useState<Difficulty | null>(null);

    const [decision, setDecision] =
        useState<ReviewDecisions | null>(null);

    const [selectedReasons, setSelectedReasons] =
        useState<string[]>([]);

    const [otherReason, setOtherReason] =
        useState("");

    const forceReject =
        correctAnswer === "NONE" ||
        correctAnswer === "MULTIPLE";

    const isBasicInfoComplete =
        correctAnswer !== null &&
        formatStatus !== null &&
        difficulty !== null &&
        decision !== null;

    const isRejectionValid =
        decision === "REJECT" || forceReject
            ? selectedReasons.length > 0 || otherReason.trim().length > 0
            : true;

    const hasQuestionChanged =
        question.questionText !== INITIAL_QUESTION.questionText ||
        question.optionA !== INITIAL_QUESTION.optionA ||
        question.optionB !== INITIAL_QUESTION.optionB ||
        question.optionC !== INITIAL_QUESTION.optionC ||
        question.optionD !== INITIAL_QUESTION.optionD ||
        question.correctAnswer !== INITIAL_QUESTION.correctAnswer;

    const isEditValid =
        decision === "EDIT" && !forceReject ? hasQuestionChanged : true;

    const isSaveDisabled = !isBasicInfoComplete || !isRejectionValid || !isEditValid;

    const handleCorrectAnswerChange = (
        value: CorrectAnswer,
    ) => {
        setCorrectAnswer(value);

        if (
            value === "NONE" ||
            value === "MULTIPLE"
        ) {
            setDecision("REJECT");
            return;
        }

        setDecision(null);
    };

    const handleDecisionChange = (
        value: ReviewDecisions,
    ) => {
        if (forceReject) {
            setDecision("REJECT");
            return;
        }

        setDecision(value);
    };

    const handleFormatStatusChange = (
        value: FormatStatus,
    ) => {
        setFormatStatus(value);
    };

    const handleDifficultyChange = (
        value: Difficulty,
    ) => {
        setDifficulty(value);
    };

    const handleSubmit = () => {
        if (isSaveDisabled) return;

        const payload = {
            question,
            correctAnswer,
            formatStatus,
            difficulty,
            decision,
            selectedReasons,
            otherReason,
        };

        console.log("Review result:", payload);
    };

    return (
        <div className="review-question-detail-page">
            <div className="review-page-header">
                <Button
                    type="text"
                    icon={<ArrowLeftOutlined />}
                    className="review-back-button"
                    onClick={handleBackToList}
                >
                    Quay lại danh sách
                </Button>

                <div className="review-page-heading">
                    <div>
                        <Typography.Title
                            level={2}
                            className="review-page-title"
                        >
                            Đánh giá câu hỏi
                        </Typography.Title>
                    </div>

                    <Tag color="processing">
                        Chờ đánh giá
                    </Tag>
                </div>
            </div>

            <Row className="review-page-content">
                <Col xs={24}>
                    <Space
                        direction="vertical"
                        size={24}
                        className="review-section-list"
                    >
                        <Card className="review-card">
                            <div className="section-heading">
                                <Typography.Title
                                    level={4}
                                    className="section-title"
                                >
                                    Câu hỏi
                                </Typography.Title>

                                <Typography.Text type="secondary">
                                    Nội dung do hệ thống sinh ra
                                </Typography.Text>
                            </div>

                            <Typography.Paragraph className="question-text">
                                {question.questionText}
                            </Typography.Paragraph>

                            <div className="question-options">
                                <div className="question-option">
                                    <span className="option-label">A.</span>
                                    <span className="option-content">{question.optionA}</span>
                                </div>
                                <div className="question-option">
                                    <span className="option-label">B.</span>
                                    <span className="option-content">{question.optionB}</span>
                                </div>
                                <div className="question-option">
                                    <span className="option-label">C.</span>
                                    <span className="option-content">{question.optionC}</span>
                                </div>
                                <div className="question-option">
                                    <span className="option-label">D.</span>
                                    <span className="option-content">{question.optionD}</span>
                                </div>
                            </div>

                            <QuestionExplanationCard
                                correctAnswer={question.correctAnswer}
                                explanation={question.explanation}
                            />
                        </Card>

                        <Card className="review-card">
                            <div className="section-heading">
                                <Typography.Title
                                    level={5}
                                    className="section-title"
                                >
                                    1. Đáp án
                                </Typography.Title>

                                <Typography.Text type="secondary">
                                    Xác định đáp án đúng của câu hỏi.
                                </Typography.Text>
                            </div>

                            <CorrectAnswerSelector
                                value={correctAnswer}
                                onChange={handleCorrectAnswerChange}
                            />
                        </Card>

                        <Card className="review-card">
                            <div className="section-heading">
                                <Typography.Title
                                    level={5}
                                    className="section-title"
                                >
                                    2. Định dạng
                                </Typography.Title>

                                <Typography.Text type="secondary">
                                    Kiểm tra định dạng của câu hỏi và các phương án.
                                </Typography.Text>
                            </div>

                            <Radio.Group
                                value={formatStatus ?? undefined}
                                onChange={(event) =>
                                    handleFormatStatusChange(
                                        event.target.value as FormatStatus,
                                    )
                                }
                            >
                                <Space direction="vertical" size={12}>
                                    <Radio value="VALID">Đạt chuẩn</Radio>
                                    <Radio value="INVALID">Không đạt chuẩn</Radio>
                                </Space>
                            </Radio.Group>
                        </Card>

                        <Card className="review-card">
                            <div className="section-heading">
                                <Typography.Title
                                    level={5}
                                    className="section-title"
                                >
                                    3. Mức độ nhận thức
                                </Typography.Title>

                                <Typography.Text type="secondary">
                                    Đánh giá mức độ khó của câu hỏi.
                                </Typography.Text>
                            </div>

                            <Radio.Group
                                value={difficulty ?? undefined}
                                onChange={(event) =>
                                    handleDifficultyChange(
                                        event.target.value as Difficulty,
                                    )
                                }
                            >
                                <Space direction="vertical" size={12}>
                                    <Radio value="EASY">Biết</Radio>
                                    <Radio value="MEDIUM">Hiểu</Radio>
                                    <Radio value="HARD">Vận dụng</Radio>
                                </Space>
                            </Radio.Group>
                        </Card>

                        <Card className="review-card">
                            <div className="section-heading">
                                <Typography.Title
                                    level={5}
                                    className="section-title"
                                >
                                    4. Kết luận
                                </Typography.Title>

                                <Typography.Text type="secondary">
                                    Quyết định xử lý câu hỏi.
                                </Typography.Text>
                            </div>

                            {forceReject && (
                                <Alert
                                    type="error"
                                    showIcon
                                    className="forced-reject-alert"
                                    message="Câu hỏi sẽ bị từ chối"
                                    description={
                                        correctAnswer === "NONE"
                                            ? "Câu hỏi không có phương án đúng."
                                            : "Câu hỏi có nhiều phương án đúng."
                                    }
                                />
                            )}

                            <ReviewDecision
                                value={decision}
                                correctAnswer={correctAnswer}
                                onChange={handleDecisionChange}
                            />
                        </Card>

                        {decision === "EDIT" && !forceReject && (
                            <Card className="review-card">
                                <div className="section-heading">
                                    <Typography.Title
                                        level={5}
                                        className="section-title"
                                    >
                                        5. Chỉnh sửa câu hỏi
                                    </Typography.Title>

                                    <Typography.Text type="secondary">
                                        Chỉnh sửa nội dung câu hỏi và các phương án trước khi chấp thuận.
                                    </Typography.Text>
                                </div>

                                <QuestionEditor
                                    value={question}
                                    onChange={setQuestion}
                                />
                            </Card>
                        )}

                        {(decision === "REJECT" || forceReject) && (
                            <Card className="review-card">
                                <div className="section-heading">
                                    <Typography.Title
                                        level={5}
                                        className="section-title"
                                    >
                                        6. Lí do từ chối
                                    </Typography.Title>

                                    <Typography.Text type="secondary">
                                        Chọn một hoặc nhiều lí do phù hợp với lỗi của câu hỏi.
                                    </Typography.Text>
                                </div>

                                <RejectionReasonSelector
                                    selectedReasons={selectedReasons}
                                    otherReason={otherReason}
                                    onReasonsChange={setSelectedReasons}
                                    onOtherReasonChange={setOtherReason}
                                />
                            </Card>
                        )}
                    </Space>
                </Col>
            </Row>

            <div className="review-page-footer">
                <Button onClick={() => window.history.back()}>
                    Hủy
                </Button>

                <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    disabled={isSaveDisabled}
                    onClick={handleSubmit}
                >
                    Lưu đánh giá
                </Button>
            </div>
        </div>
    );
}