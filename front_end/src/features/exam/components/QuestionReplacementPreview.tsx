import { useEffect, useState } from "react";
import { Alert, Button, Card, Divider, message, Modal, Space, Tag, Typography, } from "antd";
import { CheckCircleOutlined, CloseOutlined, SwapOutlined, } from "@ant-design/icons";

import type { ExamQuestionDifficulty, ExamQuestionItem, QuestionReplacementCandidate, } from "../types/exam.types";
import { replaceQuestion } from '../services/examApi';

import "./QuestionReplacementPreview.css";

const { Text, Paragraph, Title } = Typography;

const difficultyConfig: Record<
    ExamQuestionDifficulty,
    { label: string; color: string }
> = {
    easy: { label: "Biết", color: "green" },
    medium: { label: "Hiểu", color: "orange" },
    hard: { label: "Vận dụng", color: "red" },
};

const optionLabels = ["A", "B", "C", "D"] as const;

type NormalizedQuestionType =
    | "MULTIPLE_CHOICE"
    | "TRUE_FALSE"
    | "SHORT_ANSWER"
    | "UNKNOWN";

interface TrueFalseStatement {
    id?: string | number;
    statementText?: string;
    text?: string;
    correctAnswer?: boolean | "TRUE" | "FALSE" | "ĐÚNG" | "SAI";
    isTrue?: boolean;
}

type PreviewQuestionItem = (
    ExamQuestionItem | QuestionReplacementCandidate
) & {
    questionType?: string;
    trueFalseStatements?: TrueFalseStatement[];
    statements?: TrueFalseStatement[];
    expectedAnswer?: string;
    sampleAnswer?: string;
    correctAnswerText?: string;
};

interface QuestionReplacementPreviewProps {
    open: boolean;
    originalQuestion: PreviewQuestionItem | null;
    replacementQuestion: PreviewQuestionItem | null;
    onCancel: () => void;
    // Cho phép examId và slotId có thể là string, number hoặc undefined để tránh lỗi type mismatch
    examId?: number;
    slotId?: number;
    onConfirm?: () => Promise<void>;
    confirmLoading?: boolean;
    qualityStatus?: "passed" | "warning" | "failed" | "pending";
    qualityMessage?: string;
}

interface QuestionContentProps {
    question: PreviewQuestionItem;
    variant: "original" | "replacement";
}

function normalizeQuestionType(
    value?: string,
): NormalizedQuestionType {
    if (!value?.trim()) {
        return "MULTIPLE_CHOICE";
    }

    const type = value
        .trim()
        .toUpperCase()
        .replace(/[\s-]+/g, "_");

    if (
        [
            "MULTIPLE_CHOICE",
            "MULTIPLECHOICE",
            "MCQ",
            "TRAC_NGHIEM",
        ].includes(type)
    ) {
        return "MULTIPLE_CHOICE";
    }

    if (
        [
            "TRUE_FALSE",
            "TRUEFALSE",
            "DUNG_SAI",
        ].includes(type)
    ) {
        return "TRUE_FALSE";
    }

    if (
        [
            "SHORT_ANSWER",
            "SHORTANSWER",
            "TRA_LOI_NGAN",
        ].includes(type)
    ) {
        return "SHORT_ANSWER";
    }

    return "UNKNOWN";
}

function getQuestionTypeLabel(
    type: NormalizedQuestionType,
): string {
    switch (type) {
        case "MULTIPLE_CHOICE":
            return "Trắc nghiệm A–D";
        case "TRUE_FALSE":
            return "Đúng/Sai";
        case "SHORT_ANSWER":
            return "Trả lời ngắn";
        default:
            return "Chưa xác định dạng câu hỏi";
    }
}

function getBooleanAnswer(
    value: TrueFalseStatement["correctAnswer"] | undefined,
): boolean | null {
    if (typeof value === "boolean") {
        return value;
    }

    if (typeof value === "string") {
        const normalized = value.trim().toUpperCase();

        if (["TRUE", "ĐÚNG"].includes(normalized)) {
            return true;
        }

        if (["FALSE", "SAI"].includes(normalized)) {
            return false;
        }
    }

    return null;
}

function QuestionContent({
    question,
    variant,
}: QuestionContentProps) {
    const questionType = normalizeQuestionType(
        question.questionType,
    );

    const difficulty = question.difficulty
        ? difficultyConfig[question.difficulty]
        : null;

    const correctOption = String(
        question.correctOption ?? "",
    ).toUpperCase();

    const statements =
        question.trueFalseStatements ?? question.statements ?? [];

    const expectedAnswer =
        question.expectedAnswer ??
        question.sampleAnswer ??
        question.correctAnswerText ??
        "";

    return (
        <div
            className={`replacement-preview-question replacement-preview-question--${variant}`}
        >
            <div className="replacement-preview-question-heading">
                <Space size={6} wrap>
                    <Tag color="blue">
                        {getQuestionTypeLabel(questionType)}
                    </Tag>

                    {difficulty && (
                        <Tag color={difficulty.color}>
                            {difficulty.label}
                        </Tag>
                    )}
                </Space>
            </div>

            <Paragraph className="replacement-preview-question-text">
                {question.questionText || "Chưa có nội dung câu hỏi."}
            </Paragraph>

            {questionType === "MULTIPLE_CHOICE" && (
                <div
                    className="replacement-preview-options"
                    aria-label="Các phương án trả lời"
                >
                    {optionLabels.map((label) => {
                        const value =
                            question[
                            `option${label}` as
                            | "optionA"
                            | "optionB"
                            | "optionC"
                            | "optionD"
                            ];

                        const isCorrect =
                            label === correctOption;

                        return (
                            <div
                                key={label}
                                className={`replacement-preview-option${isCorrect ? " is-correct" : ""
                                    }`}
                            >
                                <span className="replacement-preview-option-label">
                                    {label}.
                                </span>

                                <span className="replacement-preview-option-value">
                                    {value || "—"}
                                </span>

                                {isCorrect && (
                                    <Tag color="success">
                                        Đáp án đúng
                                    </Tag>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            {questionType === "TRUE_FALSE" && (
                <div className="replacement-preview-statements">
                    {statements.length > 0 ? (
                        statements.map((statement, index) => {
                            const answer = getBooleanAnswer(
                                statement.correctAnswer ??
                                statement.isTrue,
                            );

                            const statementText =
                                statement.statementText ??
                                statement.text ??
                                "";

                            return (
                                <div
                                    key={
                                        statement.id ??
                                        `statement-${index}`
                                    }
                                    className={`replacement-preview-statement${answer === null
                                        ? ""
                                        : answer
                                            ? " is-true"
                                            : " is-false"
                                        }`}
                                >
                                    <div className="replacement-preview-statement-content">
                                        <Text
                                            className="replacement-preview-statement-label"
                                            strong
                                        >
                                            {String.fromCharCode(
                                                97 + index,
                                            )}.
                                        </Text>

                                        <span className="replacement-preview-statement-text">
                                            {statementText || "—"}
                                        </span>
                                    </div>

                                    <div className="replacement-preview-statement-answer">
                                        {answer === null ? (
                                            <Tag>
                                                Chưa có đáp án
                                            </Tag>
                                        ) : (
                                            <Tag
                                                color={
                                                    answer
                                                        ? "success"
                                                        : "error"
                                                }
                                            >
                                                {answer
                                                    ? "Đúng"
                                                    : "Sai"}
                                            </Tag>
                                        )}
                                    </div>
                                </div>
                            );
                        })
                    ) : (
                        <Alert
                            type="warning"
                            showIcon
                            message="Chưa có dữ liệu mệnh đề"
                            description="Cần cung cấp danh sách mệnh đề và đáp án Đúng/Sai để hiển thị câu hỏi."
                        />
                    )}
                </div>
            )}

            {questionType === "SHORT_ANSWER" && (
                <div className="replacement-preview-short-answer">
                    <Text
                        className="replacement-preview-short-answer-label"
                        strong
                    >
                        Đáp án tham khảo
                    </Text>

                    {expectedAnswer.trim() ? (
                        <Paragraph className="replacement-preview-short-answer-value">
                            {expectedAnswer}
                        </Paragraph>
                    ) : (
                        <Text type="secondary">
                            Chưa có đáp án tham khảo.
                        </Text>
                    )}
                </div>
            )}

            {questionType === "UNKNOWN" && (
                <Alert
                    type="warning"
                    showIcon
                    message="Chưa hỗ trợ dạng câu hỏi này"
                    description={`Giá trị questionType nhận được: ${question.questionType}`}
                />
            )}

            {question.explanation?.trim() && (
                <>
                    <Divider className="replacement-preview-inner-divider" />

                    <div className="replacement-preview-explanation">
                        <Text strong>Lời giải</Text>

                        <Paragraph>
                            {question.explanation}
                        </Paragraph>
                    </div>
                </>
            )}
        </div>
    );
}

export default function QuestionReplacementPreview({
    open,
    originalQuestion,
    replacementQuestion,
    onCancel,
    examId,
    slotId,
    onConfirm,
    confirmLoading = false,
    qualityStatus,
    qualityMessage,
}: QuestionReplacementPreviewProps) {
    const [submitting, setSubmitting] = useState(false);

    const isBusy = confirmLoading || submitting;

    useEffect(() => {
        if (open) {
            setSubmitting(false);
        }
    }, [open, replacementQuestion]);

    const handleConfirm = async () => {
        if (!originalQuestion || !replacementQuestion || isBusy) {
            return;
        }

        setSubmitting(true);
        try {
            // Kiểm tra tồn tại examId và slotId trước khi gọi API
            if (examId !== undefined && slotId !== undefined) {
                await replaceQuestion(
                    examId,
                    slotId,
                    originalQuestion.id,
                    replacementQuestion.id
                );
                message.success('Thay thế câu hỏi thành công');
            }

            await onConfirm?.();
        } catch (e) {
            console.error(e);
            message.error(e instanceof Error ? e.message : 'Gặp lỗi khi thay thế câu hỏi');
        } finally {
            setSubmitting(false);
        }
    };

    const handleCancel = () => {
        if (!isBusy) {
            onCancel();
        }
    };

    const qualityAlert = (() => {
        if (qualityStatus === "passed") {
            return {
                type: "success" as const,
                message: "Kiểm soát chất lượng đạt",
                description:
                    qualityMessage ||
                    "Ứng viên đã vượt qua bước kiểm soát chất lượng.",
            };
        }

        if (qualityStatus === "warning") {
            return {
                type: "warning" as const,
                message: "Cần xem xét kết quả kiểm soát",
                description:
                    qualityMessage ||
                    "Có cảnh báo cần được kiểm tra trước khi thay thế.",
            };
        }

        if (qualityStatus === "failed") {
            return {
                type: "error" as const,
                message: "Ứng viên chưa đạt kiểm soát chất lượng",
                description:
                    qualityMessage ||
                    "Không thể xác nhận thay thế khi ứng viên chưa đạt kiểm soát chất lượng.",
            };
        }

        if (qualityStatus === "pending") {
            return {
                type: "info" as const,
                message: "Đang chờ kết quả kiểm soát chất lượng",
                description:
                    qualityMessage ||
                    "Chỉ xác nhận thay thế sau khi hệ thống hoàn tất kiểm soát chất lượng.",
            };
        }

        return null;
    })();

    const qualityBlocksConfirmation =
        qualityStatus === "failed" ||
        qualityStatus === "pending";

    return (
        <Modal
            open={open}
            title={
                <Space size={10}>
                    <SwapOutlined className="replacement-preview-title-icon" />
                    <span>Xem trước câu thay thế</span>
                </Space>
            }
            width={1000}
            style={{ top: 20 }}
            onCancel={handleCancel}
            closable={!isBusy}
            maskClosable={!isBusy}
            keyboard={!isBusy}
            destroyOnClose
            className="question-replacement-preview-modal"
            footer={
                <div className="replacement-preview-footer">
                    <Button
                        icon={<CloseOutlined />}
                        onClick={handleCancel}
                        disabled={isBusy}
                    >
                        Hủy thay thế
                    </Button>

                    <Button
                        type="primary"
                        icon={<CheckCircleOutlined />}
                        loading={isBusy}
                        disabled={
                            !originalQuestion ||
                            !replacementQuestion ||
                            qualityBlocksConfirmation
                        }
                        onClick={handleConfirm}
                    >
                        Xác nhận thay câu hỏi
                    </Button>
                </div>
            }
        >
            {!originalQuestion || !replacementQuestion ? (
                <Alert
                    type="warning"
                    showIcon
                    message="Chưa đủ dữ liệu để xem trước"
                    description="Cần có cả câu hỏi hiện tại và câu hỏi được sinh thay thế."
                />
            ) : (
                <div className="replacement-preview-content">
                    <Alert
                        showIcon
                        type="info"
                        message="Câu hỏi trong đề chưa bị thay đổi"
                        description="Hãy so sánh nội dung, đáp án và lời giải. Câu mới chỉ được áp dụng sau khi bạn xác nhận."
                    />

                    {qualityAlert && (
                        <Alert
                            showIcon
                            type={qualityAlert.type}
                            message={qualityAlert.message}
                            description={qualityAlert.description}
                        />
                    )}

                    <div className="replacement-preview-comparison-heading">
                        <Title level={5}>So sánh câu hỏi</Title>
                    </div>

                    <div className="replacement-preview-comparison-grid">
                        <Card
                            size="small"
                            className="replacement-preview-card replacement-preview-card--original"
                            title="Câu hỏi hiện tại"
                        >
                            <QuestionContent
                                question={originalQuestion}
                                variant="original"
                            />
                        </Card>

                        <Card
                            size="small"
                            className="replacement-preview-card replacement-preview-card--replacement"
                            title="Câu hỏi thay thế"
                        >
                            <QuestionContent
                                question={replacementQuestion}
                                variant="replacement"
                            />
                        </Card>
                    </div>
                </div>
            )}
        </Modal>
    );
}