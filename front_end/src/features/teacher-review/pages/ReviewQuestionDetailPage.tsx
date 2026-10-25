import { useEffect, useState, useCallback } from "react";
import { Spin, message, Alert, Button, Card, Col, Radio, Row, Space, Tag, Typography } from "antd";
import { ArrowLeftOutlined, SaveOutlined } from "@ant-design/icons";
import { useParams, useNavigate } from "react-router-dom";

import { getReviewQuestionDetail, submitQuestionReview } from "../services/teacherReviewApi";
import type { ReviewQuestionDetail, CorrectAnswer, Difficulty, FormatStatus, QuestionDraft, ReviewDecisions, SubmitReviewPayload } from "../types/review.types";

import CorrectAnswerSelector from "../components/CorrectAnswerSelector";
import QuestionEditor from "../components/QuestionEditor";
import RejectionReasonSelector from "../components/RejectionReasonSelector";
import ReviewDecision from "../components/ReviewDecision";
import QuestionExplanationCard from "../components/QuestionExplanationCard";

import "./ReviewQuestionDetailPage.css";

export default function ReviewQuestionDetailPage() {

    // Move handleBackToList before usage to avoid TS18047
    const handleBackToList = () => {
        navigate("/review");
    };
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [initialQuestion, setInitialQuestion] = useState<QuestionDraft | null>(null);
    const [question, setQuestion] = useState<QuestionDraft | null>(null);
    const [questionDetail, setQuestionDetail] = useState<ReviewQuestionDetail | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const [submitting, setSubmitting] = useState<boolean>(false);

    useEffect(() => {
        if (!id) return;

        const fetchDetail = async () => {
            setCorrectAnswer(null);
            setDecision(null);
            setSelectedReasons([]);
            setOtherReason("");
            setLoading(true);
            try {
                const data = await getReviewQuestionDetail(id);
                setQuestionDetail(data);

                const draft: QuestionDraft = {
                    questionText: data.questionText,
                    optionA: data.optionA,
                    optionB: data.optionB,
                    optionC: data.optionC,
                    optionD: data.optionD,
                    correctAnswer: null, // Đáp án do giáo viên tự chọn
                    explanation: data.explanation || "",
                };

                setInitialQuestion(draft);
                setQuestion({
                    questionText: data.questionText,
                    optionA: data.optionA,
                    optionB: data.optionB,
                    optionC: data.optionC,
                    optionD: data.optionD,
                    correctAnswer: null, // Giữ null để giáo viên tự chọn
                    explanation: data.explanation || "",
                });


                // Nếu có sẵn trạng thái format hoặc gợi ý độ khó từ QC/LLM:
                if (data.formatStatus) setFormatStatus(data.formatStatus);
                if (data.llmDifficulty) {
                    const diffUpper = data.llmDifficulty as Difficulty;
                    setDifficulty(diffUpper);
                }

            } catch (err) {
                console.error("Lỗi khi tải chi tiết câu hỏi:", err);
                message.error("Không thể tải thông tin câu hỏi cần duyệt.");
                setError(err instanceof Error ? err.message : String(err));
            } finally {
                setLoading(false);
            }

            if (error) {
                return (
                    <div style={{ padding: 40, textAlign: "center" }}>
                        <Alert
                            type="error"
                            message="Lỗi tải dữ liệu"
                            description={error}
                            showIcon
                            style={{ marginBottom: 16, display: "inline-block" }}
                        />
                        <div>
                            <Button type="primary" onClick={handleBackToList}>
                                Quay lại danh sách
                            </Button>
                        </div>
                    </div>
                );
            }

        };

        fetchDetail();
    }, [id, handleBackToList]);

    // Removed duplicated handleBackToList moved above

    // State declarations moved above useEffect to avoid using setters before they are defined
    const [correctAnswer, setCorrectAnswer] = useState<CorrectAnswer | null>(null);
    const [formatStatus, setFormatStatus] = useState<FormatStatus | null>(null);
    const [difficulty, setDifficulty] = useState<Difficulty | null>(null);
    const [decision, setDecision] = useState<ReviewDecisions | null>(null);
    const [selectedReasons, setSelectedReasons] = useState<string[]>([]);
    const [otherReason, setOtherReason] = useState("");

    const forceReject =
        correctAnswer === "NONE" ||
        correctAnswer === "MULTIPLE";

    const isBasicInfoComplete =
        correctAnswer !== null &&
        formatStatus !== null &&
        difficulty !== null &&
        decision !== null;

    const isRejectionValid =
        decision === "rejected" || forceReject
            ? selectedReasons.length > 0 || otherReason.trim().length > 0
            : true;

    const hasQuestionChanged = initialQuestion && question
        ? question.questionText !== initialQuestion.questionText ||
        question.optionA !== initialQuestion.optionA ||
        question.optionB !== initialQuestion.optionB ||
        question.optionC !== initialQuestion.optionC ||
        question.optionD !== initialQuestion.optionD ||
        question.correctAnswer !== initialQuestion.correctAnswer
        : false;

    if (loading || !question) {
        return (
            <div style={{ textAlign: "center", padding: "80px 0" }}>
                <Spin size="large" tip="Đang tải câu hỏi..." />
            </div>
        );
    }

    const isEditValid =
        decision === "approved_with_edit" && !forceReject ? hasQuestionChanged : true;

    const isSaveDisabled = !isBasicInfoComplete || !isRejectionValid || !isEditValid;

    const handleCorrectAnswerChange = (
        value: CorrectAnswer,
    ) => {
        setCorrectAnswer(value);

        if (
            value === "NONE" ||
            value === "MULTIPLE"
        ) {
            setDecision("rejected");
            return;
        }

        setDecision(null);
    };

    const handleDecisionChange = (
        value: ReviewDecisions,
    ) => {
        if (forceReject) {
            setDecision("rejected");
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

    const handleSubmit = async () => {
        if (isSaveDisabled || !id || !question) return;

        // 1. Map decision sang giá trị DB
        let backendDecision: SubmitReviewPayload["decision"] = "approved_as_is";
        if (decision === "rejected" || forceReject) {
            backendDecision = "rejected";
        } else if (decision === "approved_with_edit") {
            backendDecision = "approved_with_edit";
        }

        // 2. Map reviewerAnswer ('NONE' -> 'none', 'MULTIPLE' -> 'multiple')
        const backendReviewerAnswer = (correctAnswer?.toLowerCase() ?? "none") as SubmitReviewPayload["reviewerAnswer"];

        // 3. Chuẩn bị payload
        const payload: SubmitReviewPayload = {
            reviewerAnswer: backendReviewerAnswer,
            formatOk: formatStatus === "qc_passed",
            difficultyLabel: (difficulty?.toLowerCase() ?? "medium") as "easy" | "medium" | "hard",
            decision: backendDecision,
            finalOption: backendDecision === "rejected" ? null : (correctAnswer as "A" | "B" | "C" | "D"),
        };

        // Nếu là sửa câu hỏi
        if (backendDecision === "approved_with_edit") {
            payload.editedQuestionText = question.questionText;
            payload.editedOptionA = question.optionA;
            payload.editedOptionB = question.optionB;
            payload.editedOptionC = question.optionC;
            payload.editedOptionD = question.optionD;
        }

        // Nếu từ chối
        if (backendDecision === "rejected") {
            payload.reasons = selectedReasons.map((code) => ({
                reasonCode: code,
                detail: code === "other" ? otherReason : undefined,
            }));
        }

        // 4. Gọi API
        setSubmitting(true);
        try {
            await submitQuestionReview(id, payload);
            message.success("Đã lưu đánh giá câu hỏi thành công!");
            navigate("/review"); // Quay lại danh sách câu hỏi cần duyệt
        } catch (err) {
            console.error("Lỗi khi lưu đánh giá:", err);
            message.error("Không thể lưu đánh giá. Vui lòng thử lại.");
        } finally {
            setSubmitting(false);
        }
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
                        <Typography.Title level={2} className="review-page-title">
                            Đánh giá câu hỏi #{id} {questionDetail?.subject && `- Môn ${questionDetail.subject}`}
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
                                    {questionDetail?.formatStatus && (
                                        <Tag color={questionDetail.formatStatus === "qc_passed" ? "success" : "error"} style={{ marginLeft: 8 }}>
                                            QC tự động: {questionDetail.formatStatus === "qc_passed" ? "Đạt chuẩn" : "Chưa đạt"}
                                        </Tag>
                                    )}
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
                                {questionDetail?.llmDifficulty && (
                                    <Tag color="blue" style={{ marginLeft: 8 }}>
                                        AI gợi ý: {questionDetail.llmDifficulty}
                                    </Tag>
                                )}
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
                                    <Radio value="easy">Biết</Radio>
                                    <Radio value="medium">Hiểu</Radio>
                                    <Radio value="hard">Vận dụng</Radio>
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

                        {decision === "approved_with_edit" && !forceReject && (
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

                        {(decision === "rejected" || forceReject) && (
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
                    loading={submitting}
                    disabled={isSaveDisabled || submitting}
                    onClick={handleSubmit}
                >
                    Lưu đánh giá
                </Button>
            </div>
        </div>
    );
}