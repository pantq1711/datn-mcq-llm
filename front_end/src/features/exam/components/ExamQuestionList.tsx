import { Button, Empty, List, Skeleton, Space, Tag, Tooltip, Typography } from "antd";
import { DeleteOutlined, CheckCircleOutlined, SwapOutlined, EditOutlined, EyeOutlined, WarningOutlined } from "@ant-design/icons";

import "./ExamQuestionList.css";
import type { ExamQuestionItem } from "../types/exam.types";
import { difficultyConfig, describeReviewReason } from "../../../utils/exam.utils";
import QuestionContent from "./QuestionContent";

const { Text } = Typography;

interface ExamQuestionListProps {
    questions: ExamQuestionItem[];
    loading?: boolean;
    onRemove?: (question: ExamQuestionItem, index: number) => void;
    onKeep?: (question: ExamQuestionItem, index: number) => void;
    onChangeQuestion?: (question: ExamQuestionItem, index: number) => void;
    onEdit?: (question: ExamQuestionItem, index: number) => void;
    onViewQcTrace?: (question: ExamQuestionItem, index: number) => void;
}

function renderStateTags(question: ExamQuestionItem) {
    const tags = [];
    switch (question.state) {
        case "kept":
            tags.push(<Tag key="state" color="success">Đã giữ</Tag>);
            break;
        case "edited":
            tags.push(<Tag key="state" color="success">Đã giữ (bản sửa)</Tag>);
            break;
        case "pending":
        case "generating":
            tags.push(<Tag key="state" color="processing">Đang sinh câu</Tag>);
            break;
        case "failed":
            tags.push(<Tag key="state" color="error">Sinh câu lỗi</Tag>);
            break;
    }
    if (question.source === "bank") {
        tags.push(<Tag key="source" color="purple">Ngân hàng</Tag>);
    } else if (question.source === "generated") {
        tags.push(<Tag key="source" color="geekblue">AI sinh</Tag>);
    }
    if (question.edited && question.state === "ready") {
        tags.push(<Tag key="edited" color="gold">Đã sửa</Tag>);
    }
    if (question.qcState === "checking") {
        tags.push(<Tag key="qc" color="processing">Đang kiểm lại</Tag>);
    }
    return tags;
}

export default function ExamQuestionList({
    questions,
    loading = false,
    onRemove,
    onKeep,
    onChangeQuestion,
    onEdit,
    onViewQcTrace,
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
                    const state = question.state ?? "ready";
                    const hasContent = state === "ready" || state === "kept" || state === "edited";
                    const canKeep = state === "ready" && question.qcState !== "checking";
                    const canChange = state !== "pending" && state !== "generating";
                    const canEdit = state === "ready" && question.source !== "bank";
                    const reasons = question.reviewReasons ?? [];

                    return (
                        <List.Item
                            key={question.slotId ?? question.id}
                            className="exam-question-item"
                        >
                            <div className={`exam-question-item-content${question.needsReview ? " exam-question-item-needs-review" : ""}`}>
                                <div className="exam-question-item-header">
                                    {/* Số thứ tự, độ khó, cờ cần xem kỹ, nguồn và trạng thái */}
                                    <Space size={8} align="center" wrap>
                                        <div className="exam-question-item-number">
                                            Câu {index + 1}
                                        </div>

                                        {difficulty && (
                                            <Tag color={difficulty.color}>
                                                {difficulty.label}
                                            </Tag>
                                        )}

                                        {question.needsReview && (
                                            <Tooltip
                                                title={
                                                    <ul className="exam-question-review-tooltip">
                                                        {reasons.map((reason) => (
                                                            <li key={reason}>{describeReviewReason(reason)}</li>
                                                        ))}
                                                    </ul>
                                                }
                                            >
                                                <Tag color="volcano" icon={<WarningOutlined />}>
                                                    Cần xem kỹ
                                                </Tag>
                                            </Tooltip>
                                        )}

                                        {renderStateTags(question)}
                                    </Space>

                                    {/* Nhóm các nút hành động */}
                                    <Space size={4} align="center">
                                        <Tooltip title="Xem vết kiểm tra chất lượng (QC) của câu hỏi này">
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<EyeOutlined />}
                                                className="exam-action-btn-qc"
                                                onClick={() => onViewQcTrace?.(question, index)}
                                            >
                                                Xem vết QC
                                            </Button>
                                        </Tooltip>

                                        {canKeep && (
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
                                        )}

                                        {canChange && (
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
                                        )}

                                        {canEdit && (
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
                                        )}

                                        {onRemove && (
                                            <Tooltip title="Xóa câu hỏi khỏi đề">
                                                <Button
                                                    type="text"
                                                    size="small"
                                                    danger
                                                    icon={<DeleteOutlined />}
                                                    className="exam-action-btn-delete"
                                                    onClick={() => onRemove(question, index)}
                                                />
                                            </Tooltip>
                                        )}
                                    </Space>
                                </div>

                                {question.needsReview && reasons.length > 0 && (
                                    <div className="exam-question-review-reasons">
                                        <Text type="warning" strong>Lý do cần xem kỹ:</Text>
                                        <Space size={[4, 4]} wrap>
                                            {reasons.map((reason) => (
                                                <Tag key={reason} color="orange">{describeReviewReason(reason)}</Tag>
                                            ))}
                                        </Space>
                                    </div>
                                )}

                                {!hasContent ? (
                                    <Text type="secondary" italic>
                                        {state === "failed"
                                            ? "Hệ thống chưa sinh được câu cho chỗ này. Hãy bấm \"Đổi câu khác\" để thử lại."
                                            : "Hệ thống đang sinh câu cho chỗ này…"}
                                    </Text>
                                ) : (
                                    <QuestionContent question={question} showExplanation />
                                )}
                            </div>
                        </List.Item>
                    );
                }}
            />
        </div>
    );
}