import { useState, type ReactNode } from "react";
import { Button, Empty, List, Skeleton, Space, Tag, Tooltip, Typography } from "antd";
import { DeleteOutlined, CheckCircleOutlined, SwapOutlined, EditOutlined, WarningOutlined, ReloadOutlined, InfoCircleOutlined, PauseCircleOutlined, SyncOutlined } from "@ant-design/icons";

import "./ExamQuestionList.css";
import type { BankVisibility, ExamQuestionItem } from "../types/exam.types";
import { describeInfoReason, teacherRequestLabel } from "../types/exam.types";
import {
    contentReviewReasons, describeFailureReason, describeLevel, describeReviewReasonList, examSlotAnchorId, hasContentFlag,
    isKeepBlockedByCheck, needsReviewNow, parseSimilarInExam, pendingCheckOf, resolveQuestionType, summarizeQc,
} from "../../../utils/exam.utils";
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
    /** Cấu hình hệ thống (API xem đề): câu không cờ khi lưu vào ngân hàng chung hay riêng. Mặc định chung. */
    bankKeepVisibility?: BankVisibility;
    /** Số "Câu k" của câu; danh sách có thể đã lọc nên không dùng vị trí trong danh sách. Mặc định index + 1. */
    getNumber?: (question: ExamQuestionItem, index: number) => number;
    /** Công tắc "Hiện tất cả lời giải" ở đầu trang. */
    showAllExplanations?: boolean;
    /** Bấm "Phần P – Câu k" trong lý do "gần giống" thì nhảy tới câu đó (G5); không truyền thì hiện chữ thường. */
    onJumpTo?: (part: string, number: number) => void;
    /** Câu vừa được nhảy tới (mục lục hoặc liên kết trong lý do): viền nổi trong chốc lát. */
    flashSlotId?: number;
    /** Chỗ đang sửa ngay trên thẻ (CT-2): thẻ đó hiện form sửa thay cho nội dung câu và ẩn các nút của thẻ. */
    editingSlotId?: number;
    /** Vẽ form sửa cho câu đang sửa (trang giữ trạng thái và cách lưu). */
    renderEditor?: (question: ExamQuestionItem) => ReactNode;
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
    // "Đang tự kiểm tra lại" nay ở dòng trạng thái riêng dưới đầu thẻ (G14), không lặp ở đây
    return tags;
}

/**
 * Nội dung câu. Lời giải mở sẵn ở câu có cờ về nội dung hoặc khi bật "Hiện tất cả lời giải"; còn lại thu gọn. Giáo viên bấm nút
 * thì theo lựa chọn đó cho tới khi công tắc đầu trang đổi.
 */
function QuestionBody({ question, showAll, actions }: { question: ExamQuestionItem; showAll: boolean; actions?: ReactNode }) {
    // Lựa chọn tay chỉ có hiệu lực khi công tắc đầu trang chưa đổi kể từ lúc bấm
    const [manual, setManual] = useState<{ open: boolean; showAll: boolean } | null>(null);
    const manualOpen = manual && manual.showAll === showAll ? manual.open : null;
    // Chỉ câu có cờ về nội dung mới mở sẵn lời giải; cờ "gần giống câu khác" là về đề, xem lời giải không giúp gì
    // Lý do "đang chờ" không tính (G14): câu vừa sửa đang kiểm lại không mở sẵn lời giải
    const showExplanation = manualOpen
        ?? (showAll || hasContentFlag(needsReviewNow(question), contentReviewReasons(question.reviewReasons)));

    return (
        <>
            <QuestionContent question={question} showExplanation={showExplanation} />
            {/* Nút lời giải (trái) và phần kết quả tự kiểm tra (phải) cùng một hàng */}
            {(question.explanation || actions) && (
                <div className="exam-question-body-actions">
                    {question.explanation && (
                        <Button
                            className="exam-explanation-toggle"
                            onClick={() => setManual({ open: !showExplanation, showAll })}
                        >
                            {showExplanation ? "Ẩn lời giải" : "Xem lời giải"}
                        </Button>
                    )}
                    {actions}
                </div>
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
    bankKeepVisibility = "shared",
    getNumber = (_question, index) => index + 1,
    showAllExplanations = false,
    onJumpTo,
    flashSlotId,
    editingSlotId,
    renderEditor,
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
                    const isFailed = state === "failed";
                    // Câu đúng/sai có mức ở từng ý; chỗ chưa có câu thì chưa có mức. Câu còn lại thiếu mức hoặc mức lạ: "Chưa phân loại"
                    const difficulty =
                        hasContent && resolveQuestionType(question) !== "tf"
                            ? describeLevel(question.difficulty)
                            : null;
                    // CT-2: câu đang sửa trên thẻ (chỉ khi câu vẫn đang ở trạng thái sửa được)
                    const isEditing = Boolean(renderEditor) && editingSlotId !== undefined
                        && question.slotId === editingSlotId && state === "ready";
                    // G14: đang kiểm tra thì nút Lưu vẫn hiện nhưng khóa, kèm lời giải thích
                    const canKeep = !isEditing && state === "ready";
                    const keepBlocked = isKeepBlockedByCheck(question);
                    const pendingCheck = state === "ready" ? pendingCheckOf(question) : null;
                    // Chỉ còn lý do "đang chờ" thì không tô cam, không hiện "Cần xem kỹ"
                    const showReview = needsReviewNow(question);
                    const canChange = !isEditing && state !== "pending" && state !== "generating";
                    const canEdit = !isEditing && state === "ready" && question.source !== "bank";
                    const hasActions = canKeep || canChange || canEdit || Boolean(onRemove);
                    const allReasons = question.reviewReasons ?? [];
                    // Lý do "đang chờ" (solver_pending, grounding_pending) không nằm trong nhóm lý do cần xem kỹ (G14)
                    const reasons = contentReviewReasons(allReasons);
                    // Câu chữ lý do cần xem kỹ: gộp cảnh báo cùng mẫu (G4), bỏ câu trùng (G3); lý do "gần giống
                    // Phần P – Câu k" giữ vị trí để hiện thành liên kết nhảy tới câu đó (G5)
                    const reasonEntries = describeReviewReasonList(reasons);
                    const reasonTexts = reasonEntries.map((entry) => entry.text);
                    const qcSummary = summarizeQc(question);
                    const qcBlock = renderQcBlock(question, index, qcSummary, onViewQcTrace);

                    return (
                        <List.Item
                            key={question.slotId ?? question.id}
                            id={question.slotId !== undefined ? examSlotAnchorId(question.slotId) : undefined}
                            className="exam-question-item"
                        >
                            <div
                                className={`exam-question-item-content${showReview ? " exam-question-item-needs-review" : ""}${flashSlotId !== undefined && question.slotId === flashSlotId ? " exam-question-item-flash" : ""}${isEditing ? " exam-question-item-editing" : ""}`}
                            >
                                <div className="exam-question-item-header">
                                    {/* Số thứ tự, độ khó, cờ cần xem kỹ, nguồn và trạng thái */}
                                    <Space size={8} align="center" wrap>
                                        <div className="exam-question-item-number">
                                            Câu {getNumber(question, index)}
                                            {isEditing && <span className="exam-question-editing-note"> (đang sửa)</span>}
                                        </div>

                                        {difficulty && (
                                            <Text type="secondary">{difficulty.label}</Text>
                                        )}

                                        {showReview && (
                                            <Tooltip
                                                title={
                                                    <ul className="exam-question-review-tooltip">
                                                        {reasonTexts.map((text) => (
                                                            <li key={text}>{text}</li>
                                                        ))}
                                                    </ul>
                                                }
                                            >
                                                {/* Chữ cam nâu #ad4e00 trên nền cam nhạt (đạt tương phản 4,5:1, G3) */}
                                                <Tag icon={<WarningOutlined />} className="exam-question-review-tag">
                                                    Cần xem kỹ
                                                </Tag>
                                            </Tooltip>
                                        )}

                                        {renderStateTags(question)}
                                    </Space>
                                </div>

                                {/* Nhóm các nút hành động: màn rộng nằm bên phải dòng đầu thẻ, màn ≤768px xuống cuối thẻ (G26) */}
                                {hasActions && (
                                    <div className="exam-question-item-actions">
                                        {canKeep && (
                                            <Tooltip
                                                title={
                                                    keepBlocked
                                                        ? "Lưu được sau khi hệ thống kiểm tra xong."
                                                        // Câu có cờ nội dung vẫn Giữ được nhưng vào ngân hàng riêng (docs/api mục 7.5, làm rõ 11/11);
                                                        // tính cả solver_pending (hoãn kiểm lại vẫn vào ngân hàng riêng)
                                                        // NV-31: hệ thống cấu hình lưu riêng thì mọi câu vào ngân hàng riêng
                                                        : hasContentFlag(question.needsReview, allReasons)
                                                            ? "Câu cần xem kỹ sẽ chỉ được lưu vào ngân hàng riêng của thầy/cô."
                                                            : bankKeepVisibility === "private"
                                                                ? "Câu này sẽ được lưu vào ngân hàng riêng của thầy/cô để dùng cho các đề sau."
                                                                : "Câu này sẽ được lưu vào ngân hàng chung để dùng cho các đề sau."
                                                }
                                            >
                                                {/* span để tooltip vẫn hiện khi nút bị khóa */}
                                                <span className="exam-action-keep-wrap">
                                                    <Button
                                                        type="primary"
                                                        icon={<CheckCircleOutlined />}
                                                        className="exam-action-btn-keep"
                                                        disabled={keepBlocked}
                                                        onClick={() => onKeep?.(question, index)}
                                                    >
                                                        Lưu vào ngân hàng
                                                    </Button>
                                                </span>
                                            </Tooltip>
                                        )}

                                        {canChange && (
                                            <Tooltip title={isFailed ? "Soạn lại câu này" : "Đổi sang câu hỏi khác cùng mức độ"}>
                                                <Button
                                                    icon={isFailed ? <ReloadOutlined /> : <SwapOutlined />}
                                                    className="exam-action-btn-change"
                                                    onClick={() => onChangeQuestion?.(question, index)}
                                                >
                                                    {isFailed ? "Soạn lại" : "Đổi câu khác"}
                                                </Button>
                                            </Tooltip>
                                        )}

                                        {canEdit && (
                                            <Tooltip title="Chỉnh sửa nội dung câu hỏi">
                                                <Button
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
                                    </div>
                                )}

                                <div className="exam-question-item-body">
                                {/* G14: hệ thống đang kiểm tra (hoặc tạm hoãn): dòng trạng thái riêng, tách khỏi nhóm lý do */}
                                {pendingCheck && !isEditing && (
                                    <div className={`exam-question-pending exam-question-pending-${pendingCheck}`}>
                                        {pendingCheck === "deferred" ? <PauseCircleOutlined /> : <SyncOutlined spin />}
                                        <span>
                                            {pendingCheck === "checking"
                                                ? "Hệ thống đang tự kiểm tra lại đáp án của bản sửa. Lưu vào ngân hàng được sau khi kiểm tra xong; trang tự cập nhật."
                                                : pendingCheck === "grounding"
                                                    ? "Hệ thống đang đối chiếu câu với tài liệu đính kèm. Lưu vào ngân hàng được sau khi đối chiếu xong; trang tự cập nhật."
                                                    : "Hệ thống tạm hoãn kiểm tra lại vì hết lượt trong ngày. Thầy/cô vẫn lưu được; câu sẽ vào ngân hàng riêng."}
                                        </span>
                                    </div>
                                )}

                                {showReview && reasons.length > 0 && (
                                    <div className="exam-question-review-reasons">
                                        <Text strong className="exam-question-review-label">Lý do cần xem kỹ:</Text>
                                        {/* Mỗi lý do một dòng có gạch đầu dòng (G3) */}
                                        <ul className="exam-question-review-list">
                                            {reasonEntries.map(({ text, position }) => (
                                                <li key={text}>
                                                    {position && onJumpTo ? (
                                                        <>
                                                            Gần giống{" "}
                                                            <Button
                                                                type="link"
                                                                className="exam-question-review-jump"
                                                                onClick={() => onJumpTo(position.part, position.number)}
                                                            >
                                                                Phần {position.part} – Câu {position.number}
                                                            </Button>{" "}
                                                            trong đề, nên đổi một trong hai câu.
                                                        </>
                                                    ) : (
                                                        text
                                                    )}
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                )}

                                {/* PR B: yêu cầu của câu (chữ xám) và cờ thông tin (thẻ xanh, không phải "cần xem kỹ") */}
                                {question.requests && (
                                    <Text type="secondary">
                                        Yêu cầu: {[
                                            ...question.requests.codes.map(teacherRequestLabel),
                                            ...(question.requests.text ? [question.requests.text] : []),
                                        ].join("; ")}
                                    </Text>
                                )}
                                {(question.infoReasons ?? []).length > 0 && (
                                    <Space size={[6, 4]} wrap>
                                        {(question.infoReasons ?? []).map((info) => {
                                            // 22/11: câu cùng dạng trong đề là cờ thông tin; vị trí bấm được để xem câu kia
                                            const similarTo = parseSimilarInExam(info);
                                            return (
                                                <Tag key={info} color="blue" icon={<InfoCircleOutlined />}>
                                                    {similarTo && onJumpTo ? (
                                                        <>
                                                            Cùng dạng với{" "}
                                                            <Button
                                                                type="link"
                                                                size="small"
                                                                className="exam-question-review-jump"
                                                                onClick={() => onJumpTo(similarTo.part, similarTo.number)}
                                                            >
                                                                Phần {similarTo.part} – Câu {similarTo.number}
                                                            </Button>{" "}
                                                            trong đề
                                                        </>
                                                    ) : (
                                                        describeInfoReason(info)
                                                    )}
                                                </Tag>
                                            );
                                        })}
                                    </Space>
                                )}

                                {isEditing ? (
                                    // CT-2: sửa ngay trên thẻ; lý do cần xem kỹ vẫn hiện ở trên để giáo viên sửa theo
                                    renderEditor?.(question)
                                ) : !hasContent ? (
                                    <>
                                        <Text type="secondary" italic>
                                            {state === "failed"
                                                ? describeFailureReason(question.failureReason)
                                                : "Hệ thống đang soạn câu này…"}
                                        </Text>
                                        {qcBlock && <div className="exam-question-body-actions">{qcBlock}</div>}
                                    </>
                                ) : (
                                    <QuestionBody question={question} showAll={showAllExplanations} actions={qcBlock} />
                                )}
                                </div>
                            </div>
                        </List.Item>
                    );
                }}
            />
        </div>
    );
}

/** Kết quả tự kiểm tra: tóm tắt ngay trên thẻ (câu hệ thống soạn chưa lưu), lịch sử đầy đủ ở "Chi tiết". */
function renderQcBlock(
    question: ExamQuestionItem,
    index: number,
    qcSummary: ReturnType<typeof summarizeQc>,
    onViewQcTrace?: (question: ExamQuestionItem, index: number) => void
): ReactNode {
    if (!onViewQcTrace) return null;
    return (
        <div className="exam-question-qc-summary">
            {qcSummary && (
                <span className="exam-question-qc-summary-text">
                    Hệ thống đã kiểm tra:{" "}
                    {qcSummary.map((line, i) => (
                        <span key={line.label}>
                            {i > 0 && " · "}
                            {line.label}:{" "}
                            <span className={line.ok ? "exam-qc-ok" : "exam-qc-bad"}>{line.value}</span>
                        </span>
                    ))}
                </span>
            )}
            <Button
                type="link"
                size="small"
                onClick={() => onViewQcTrace(question, index)}
            >
                {qcSummary ? "Chi tiết" : "Xem kết quả kiểm tra tự động"}
            </Button>
        </div>
    );
}