import { useState } from "react";
import { Button, Empty, List, Skeleton, Space, Tag, Tooltip, Typography } from "antd";
import { DeleteOutlined, CheckCircleOutlined, SwapOutlined, EditOutlined, WarningOutlined } from "@ant-design/icons";

import "./ExamQuestionList.css";
import type { ExamQuestionItem } from "../types/exam.types";
import { describeFailureReason, describeLevel, describeReviewReason, resolveQuestionType } from "../../../utils/exam.utils";
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
            tags.push(<Tag key="state" color="success">Đã lưu vào ngân hàng</Tag>);
            break;
        case "edited":
            tags.push(<Tag key="state" color="success">Đã lưu vào ngân hàng (bản sửa)</Tag>);
            break;
        case "pending":
        case "generating":
            tags.push(<Text key="state" type="secondary">Đang soạn câu</Text>);
            break;
        case "failed":
            tags.push(<Text key="state" type="danger">Chưa soạn được</Text>);
            break;
    }
    // Chỉ giữ 3 tag màu: Cần xem kỹ, Đã lưu vào ngân hàng, Đã sửa. Còn lại là chữ thường.
    if (question.source === "bank") {
        // Câu lấy từ ngân hàng không có nút Sửa: ghi rõ để giáo viên hiểu vì sao
        tags.push(<Text key="source" type="secondary">Câu từ ngân hàng</Text>);
    } else if (question.source === "generated") {
        tags.push(<Text key="source" type="secondary">Câu mới</Text>);
    }
    if (question.edited && question.state === "ready") {
        tags.push(<Tag key="edited" color="gold">Đã sửa</Tag>);
    }
    if (question.qcState === "checking") {
        tags.push(<Text key="qc" type="secondary">Đang tự kiểm tra lại</Text>);
    }
    return tags;
}

/** Nội dung câu; lời giải thu gọn, giáo viên bấm "Xem lời giải" mới mở. */
function QuestionBody({ question }: { question: ExamQuestionItem }) {
    const [showExplanation, setShowExplanation] = useState(false);

    return (
        <>
            <QuestionContent question={question} showExplanation={showExplanation} />
            {question.explanation && (
                <Button
                    className="exam-explanation-toggle"
                    onClick={() => setShowExplanation((prev) => !prev)}
                >
                    {showExplanation ? "Ẩn lời giải" : "Xem lời giải"}
                </Button>
            )}
        </>
    );
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
                    const state = question.state ?? "ready";
                    const hasContent = state === "ready" || state === "kept" || state === "edited";
                    // Câu đúng/sai có mức ở từng ý; chỗ chưa có câu thì chưa có mức. Câu còn lại thiếu mức hoặc mức lạ: "Chưa phân loại"
                    const difficulty =
                        hasContent && resolveQuestionType(question) !== "tf"
                            ? describeLevel(question.difficulty)
                            : null;
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
                                            <Text type="secondary">{difficulty.label}</Text>
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
                                        {canKeep && (
                                            <Tooltip title="Câu này sẽ được lưu vào ngân hàng chung để dùng cho các đề sau.">
                                                <Button
                                                    type="text"
                                                    icon={<CheckCircleOutlined />}
                                                    className="exam-action-btn-keep"
                                                    onClick={() => onKeep?.(question, index)}
                                                >
                                                    Lưu vào ngân hàng
                                                </Button>
                                            </Tooltip>
                                        )}

                                        {canChange && (
                                            <Tooltip title="Đổi sang câu hỏi khác cùng mức độ">
                                                <Button
                                                    type="text"
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
                                        <Space size={[12, 4]} wrap>
                                            {reasons.map((reason) => (
                                                <Text key={reason}>{describeReviewReason(reason)}</Text>
                                            ))}
                                        </Space>
                                    </div>
                                )}

                                {!hasContent ? (
                                    <Text type="secondary" italic>
                                        {state === "failed"
                                            ? describeFailureReason(question.failureReason)
                                            : "Hệ thống đang soạn câu này…"}
                                    </Text>
                                ) : (
                                    <QuestionBody question={question} />
                                )}

                                {/* Kết quả tự kiểm tra: link nhỏ, không nằm trong hàng nút chính */}
                                {onViewQcTrace && (
                                    <div className="exam-question-qc-link">
                                        <Button
                                            type="link"
                                            size="small"
                                            onClick={() => onViewQcTrace(question, index)}
                                        >
                                            Xem kết quả tự kiểm tra
                                        </Button>
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