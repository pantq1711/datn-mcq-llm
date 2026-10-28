import React, { useEffect, useMemo, useState } from "react";
import {
    Alert,
    Button,
    Card,
    Empty,
    List,
    Progress,
    Segmented,
    Space,
    Spin,
    Statistic,
    Tag,
    Typography,
    message,
    Row,
    Col,
} from "antd";
import {
    CheckCircleFilled,
    ClockCircleOutlined,
    CloseOutlined,
    ExclamationCircleFilled,
    FileTextOutlined,
    SyncOutlined,
} from "@ant-design/icons";

import { getExamProgress } from "../services/examApi";
import type { ExamProgress } from "../types/exam.types";

import "./ExamProgressDetail.css";

const { Title, Text } = Typography;

interface Props {
    examId: string;
    /** Callback đóng modal hoặc drawer chứa component. */
    onClose?: () => void;
}

type CellFilter = "all" | "unanswered" | "review";

export const ExamProgressDetail: React.FC<Props> = ({
    examId,
    onClose,
}) => {
    const [progress, setProgress] = useState<ExamProgress | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [filter, setFilter] = useState<CellFilter>("all");

    useEffect(() => {
        let active = true;

        const fetchProgress = async () => {
            setLoading(true);
            setError(null);
            setProgress(null);
            setFilter("all");

            try {
                const data = await getExamProgress(examId);

                if (active) {
                    setProgress(data);
                }
            } catch (err: unknown) {
                const apiError = (
                    err as {
                        response?: {
                            data?: {
                                error?: string;
                                message?: string;
                            };
                        };
                    }
                )?.response?.data;

                const errorMessage =
                    apiError?.error ??
                    apiError?.message ??
                    "Không tải được tiến độ bài thi.";

                if (active) {
                    setError(errorMessage);
                    message.error(errorMessage);
                }
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        };

        void fetchProgress();

        return () => {
            active = false;
        };
    }, [examId]);

    const cells = progress?.cells ?? [];

    const summary = useMemo(() => {
        const total = cells.length;
        const answered = cells.filter((cell) => cell.answered).length;
        const unanswered = total - answered;
        const needReview = cells.filter((cell) => cell.needReview).length;

        const completionPercent =
            total > 0
                ? Math.round((answered / total) * 100)
                : 0;

        return {
            total,
            answered,
            unanswered,
            needReview,
            completionPercent,
        };
    }, [cells]);

    const filteredCells = useMemo(() => {
        switch (filter) {
            case "unanswered":
                return cells.filter((cell) => !cell.answered);

            case "review":
                return cells.filter((cell) => cell.needReview);

            default:
                return cells;
        }
    }, [cells, filter]);

    if (loading) {
        return (
            <div className="exam-progress-detail exam-progress-detail--loading">
                <Spin size="large" />
                <Text type="secondary">Đang tải tiến độ bài thi...</Text>
            </div>
        );
    }

    return (
        <div className="exam-progress-detail">
            <div className="exam-progress-detail__header">
                <div className="exam-progress-detail__heading">
                    <div className="exam-progress-detail__heading-icon">
                        <FileTextOutlined />
                    </div>

                    <div>
                        <Title level={4}>
                            Tiến độ chi tiết bài thi
                        </Title>
                        <Text type="secondary">
                            Theo dõi trạng thái trả lời của từng ô câu hỏi.
                        </Text>
                    </div>
                </div>

                {onClose && (
                    <Button
                        type="text"
                        icon={<CloseOutlined />}
                        onClick={onClose}
                        aria-label="Đóng chi tiết tiến độ"
                    >
                        Đóng
                    </Button>
                )}
            </div>

            {error ? (
                <Alert
                    className="exam-progress-detail__error"
                    type="error"
                    showIcon
                    message="Không thể tải tiến độ"
                    description={error}
                />
            ) : (
                <>
                    <div className="exam-progress-detail__summary">
                        <Row gutter={[12, 12]}>
                            <Col xs={12} sm={12} md={6}>
                                <Card
                                    bordered={false}
                                    className="exam-progress-detail__stat-card exam-progress-detail__stat-card--total"
                                >
                                    <Statistic
                                        title="Tổng số ô"
                                        value={summary.total}
                                        prefix={
                                            <FileTextOutlined className="exam-progress-detail__stat-icon" />
                                        }
                                    />
                                    <Text type="secondary">
                                        Tổng số câu hỏi
                                    </Text>
                                </Card>
                            </Col>

                            <Col xs={12} sm={12} md={6}>
                                <Card
                                    bordered={false}
                                    className="exam-progress-detail__stat-card exam-progress-detail__stat-card--answered"
                                >
                                    <Statistic
                                        title="Đã trả lời"
                                        value={summary.answered}
                                        prefix={
                                            <CheckCircleFilled className="exam-progress-detail__stat-icon" />
                                        }
                                    />
                                    <Text type="secondary">
                                        Đã hoàn thành
                                    </Text>
                                </Card>
                            </Col>

                            <Col xs={12} sm={12} md={6}>
                                <Card
                                    bordered={false}
                                    className="exam-progress-detail__stat-card exam-progress-detail__stat-card--unanswered"
                                >
                                    <Statistic
                                        title="Chưa trả lời"
                                        value={summary.unanswered}
                                        prefix={
                                            <ClockCircleOutlined className="exam-progress-detail__stat-icon" />
                                        }
                                    />
                                    <Text type="secondary">
                                        Cần hoàn thành
                                    </Text>
                                </Card>
                            </Col>

                            <Col xs={12} sm={12} md={6}>
                                <Card
                                    bordered={false}
                                    className="exam-progress-detail__stat-card exam-progress-detail__stat-card--review"
                                >
                                    <Statistic
                                        title="Cần xem lại"
                                        value={summary.needReview}
                                        prefix={
                                            <ExclamationCircleFilled className="exam-progress-detail__stat-icon" />
                                        }
                                    />
                                    <Text type="secondary">
                                        Được đánh dấu xem lại
                                    </Text>
                                </Card>
                            </Col>
                        </Row>
                    </div>

                    <Card
                        bordered={false}
                        className="exam-progress-detail__main-card"
                    >
                        <div className="exam-progress-detail__completion">
                            <div className="exam-progress-detail__completion-heading">
                                <div>
                                    <Text strong>
                                        Mức độ hoàn thành
                                    </Text>
                                    <div>
                                        <Text
                                            type="secondary"
                                            className="exam-progress-detail__completion-caption"
                                        >
                                            {summary.answered}/{summary.total} ô
                                            đã được trả lời
                                        </Text>
                                    </div>
                                </div>

                                <Text
                                    strong
                                    className="exam-progress-detail__completion-percent"
                                >
                                    {summary.completionPercent}%
                                </Text>
                            </div>

                            <Progress
                                percent={summary.completionPercent}
                                showInfo={false}
                                strokeWidth={10}
                                strokeColor="#1677ff"
                                trailColor="#edf0f5"
                            />
                        </div>

                        <div className="exam-progress-detail__list-header">
                            <div>
                                <Title level={5}>
                                    Danh sách ô câu hỏi
                                </Title>
                                <Text type="secondary">
                                    {filteredCells.length} kết quả
                                </Text>
                            </div>

                            <Segmented
                                value={filter}
                                onChange={(value) =>
                                    setFilter(value as CellFilter)
                                }
                                options={[
                                    {
                                        label: `Tất cả (${summary.total})`,
                                        value: "all",
                                    },
                                    {
                                        label: `Chưa trả lời (${summary.unanswered})`,
                                        value: "unanswered",
                                    },
                                    {
                                        label: `Cần xem lại (${summary.needReview})`,
                                        value: "review",
                                    },
                                ]}
                            />
                        </div>

                        {cells.length === 0 ? (
                            <Empty
                                className="exam-progress-detail__empty"
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description="Chưa có dữ liệu tiến độ cho bài thi này."
                            />
                        ) : filteredCells.length === 0 ? (
                            <Empty
                                className="exam-progress-detail__empty"
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description={
                                    filter === "unanswered"
                                        ? "Tất cả ô câu hỏi đã được trả lời."
                                        : "Không có ô câu hỏi nào cần xem lại."
                                }
                            />
                        ) : (
                            <List
                                className="exam-progress-detail__list"
                                dataSource={filteredCells}
                                rowKey={(cell) => String(cell.cellId)}
                                renderItem={(cell) => (
                                    <List.Item className="exam-progress-detail__list-item">
                                        <div className="exam-progress-detail__cell">
                                            <div className="exam-progress-detail__cell-main">
                                                <div
                                                    className={`exam-progress-detail__cell-icon ${cell.answered
                                                            ? "exam-progress-detail__cell-icon--answered"
                                                            : "exam-progress-detail__cell-icon--unanswered"
                                                        }`}
                                                >
                                                    {cell.answered ? (
                                                        <CheckCircleFilled />
                                                    ) : (
                                                        <ClockCircleOutlined />
                                                    )}
                                                </div>

                                                <div className="exam-progress-detail__cell-info">
                                                    <Text strong>
                                                        Ô {cell.cellId}
                                                    </Text>

                                                    <Text
                                                        type="secondary"
                                                        className="exam-progress-detail__cell-description"
                                                    >
                                                        {cell.answered
                                                            ? "Đã có câu trả lời"
                                                            : "Chưa có câu trả lời"}
                                                    </Text>
                                                </div>
                                            </div>

                                            <Space
                                                size={[6, 6]}
                                                wrap
                                                className="exam-progress-detail__cell-tags"
                                            >
                                                <Tag
                                                    color={
                                                        cell.answered
                                                            ? "success"
                                                            : "default"
                                                    }
                                                >
                                                    {cell.answered
                                                        ? "Đã trả lời"
                                                        : "Chưa trả lời"}
                                                </Tag>

                                                {cell.needReview && (
                                                    <Tag
                                                        color="warning"
                                                        icon={
                                                            <SyncOutlined />
                                                        }
                                                    >
                                                        Cần xem lại
                                                    </Tag>
                                                )}
                                            </Space>
                                        </div>
                                    </List.Item>
                                )}
                            />
                        )}
                    </Card>
                </>
            )}
        </div>
    );
};