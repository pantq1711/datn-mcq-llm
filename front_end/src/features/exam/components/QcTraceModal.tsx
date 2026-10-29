import { useEffect, useState } from "react";
import type { FC } from "react";
import {
    Alert,
    Button,
    Empty,
    Modal,
    Space,
    Spin,
    Tag,
    Typography,
} from "antd";
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseOutlined,
    ExclamationCircleOutlined,
    FileSearchOutlined,
    HistoryOutlined,
    NodeIndexOutlined,
} from "@ant-design/icons";

import { getQcTrace } from "../services/examApi";
import type { QcTraceResponse } from "../types/qcTrace.types";

import "./QcTraceModal.css";

const { Text, Paragraph } = Typography;

interface Props {
    examId: string;
    slotId: string;
    visible: boolean;
    onClose: () => void;
}

type ActionTone = "success" | "error" | "warning" | "default";

function getActionTone(action: string): ActionTone {
    const normalized = action.trim().toLowerCase();

    if (
        /fail|error|rejected|failed|exception|bug|lỗi|thất bại|không đạt|chưa đạt|từ chối/.test(
            normalized,
        )
    ) {
        return "error";
    }

    if (
        /warning|warn|review|needreview|cảnh báo|xem lại|cần kiểm tra/.test(
            normalized,
        )
    ) {
        return "warning";
    }

    if (
        /success|passed|approved|completed|done|finished|generate|replace|keep|tạo|đổi|giữ|hoàn thành|đạt/.test(
            normalized,
        )
    ) {
        return "success";
    }

    return "default";
}

function getActionLabel(action: string): string {
    return action.trim() || "Hoạt động QC";
}

function formatTimestamp(timestamp: string | number): string {
    const date = new Date(timestamp);

    if (Number.isNaN(date.getTime())) {
        return String(timestamp || "Không có thời gian");
    }

    return new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
    }).format(date);
}

export const QcTraceModal: FC<Props> = ({
    examId,
    slotId,
    visible,
    onClose,
}) => {
    const [trace, setTrace] = useState<QcTraceResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!visible || !examId || !slotId) {
            return;
        }

        let active = true;

        const fetchTrace = async () => {
            setLoading(true);
            setTrace(null);
            setError(null);

            try {
                const data = await getQcTrace(examId, slotId);

                if (active) {
                    setTrace(data);
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
                    "Không thể lấy vết kiểm soát chất lượng.";

                if (active) {
                    setError(errorMessage);
                }
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        };

        void fetchTrace();

        return () => {
            active = false;
        };
    }, [examId, slotId, visible]);

    const steps = trace?.steps ?? [];

    return (
        <Modal
            open={visible}
            onCancel={onClose}
            width={720}
            centered
            destroyOnClose
            className="qc-trace-modal"
            title={
                <div className="qc-trace-modal__title">
                    <div className="qc-trace-modal__title-icon">
                        <HistoryOutlined />
                    </div>

                    <div className="qc-trace-modal__title-content">
                        <Text className="qc-trace-modal__title-text">
                            Nhật ký kiểm soát chất lượng
                        </Text>

                        <Text className="qc-trace-modal__subtitle">
                            Theo dõi lịch sử xử lý của ô câu hỏi
                        </Text>
                    </div>
                </div>
            }
            footer={
                <div className="qc-trace-modal__footer">
                    <Text
                        type="secondary"
                        className="qc-trace-modal__footer-note"
                    >
                        {loading
                            ? "Đang tải dữ liệu..."
                            : `${steps.length} bước xử lý`}
                    </Text>

                    <Button
                        icon={<CloseOutlined />}
                        onClick={onClose}
                    >
                        Đóng
                    </Button>
                </div>
            }
        >
            <div className="qc-trace-modal__body">
                <div className="qc-trace-modal__context">
                    <div className="qc-trace-modal__context-item">
                        <span className="qc-trace-modal__context-label">
                            Mã đề thi
                        </span>
                        <Text strong copyable>
                            {examId}
                        </Text>
                    </div>

                    <div className="qc-trace-modal__context-divider" />

                    <div className="qc-trace-modal__context-item">
                        <span className="qc-trace-modal__context-label">
                            Ô câu hỏi
                        </span>
                        <Text strong copyable>
                            {slotId}
                        </Text>
                    </div>
                </div>

                {loading ? (
                    <div className="qc-trace-modal__loading">
                        <Spin size="large" />

                        <Text type="secondary">
                            Đang tải lịch sử kiểm soát chất lượng...
                        </Text>
                    </div>
                ) : error ? (
                    <Alert
                        className="qc-trace-modal__error"
                        type="error"
                        showIcon
                        icon={<ExclamationCircleOutlined />}
                        message="Không thể tải nhật ký QC"
                        description={error}
                    />
                ) : steps.length === 0 ? (
                    <Empty
                        className="qc-trace-modal__empty"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            <Space
                                direction="vertical"
                                size={4}
                            >
                                <Text strong>
                                    Chưa có lịch sử kiểm soát
                                </Text>

                                <Text type="secondary">
                                    Ô câu hỏi này chưa có bước QC nào
                                    được ghi nhận.
                                </Text>
                            </Space>
                        }
                    />
                ) : (
                    <div className="qc-trace-modal__timeline">
                        <div className="qc-trace-modal__section-heading">
                            <div>
                                <Text strong>
                                    Lịch sử xử lý
                                </Text>

                                <Paragraph type="secondary">
                                    Các bước được ghi nhận trong quá trình
                                    kiểm soát chất lượng.
                                </Paragraph>
                            </div>

                            <Tag
                                color="blue"
                                icon={<NodeIndexOutlined />}
                            >
                                {steps.length} bước
                            </Tag>
                        </div>

                        <div className="qc-trace-modal__steps">
                            {steps.map((step, index) => {
                                const tone = getActionTone(step.action);

                                return (
                                    <div
                                        key={`${step.version}-${step.timestamp}-${index}`}
                                        className={`qc-trace-modal__step qc-trace-modal__step--${tone}`}
                                    >
                                        <div className="qc-trace-modal__step-rail">
                                            <div className="qc-trace-modal__step-marker">
                                                {tone === "success" ? (
                                                    <CheckCircleOutlined />
                                                ) : tone === "error" ? (
                                                    <ExclamationCircleOutlined />
                                                ) : tone === "warning" ? (
                                                    <ExclamationCircleOutlined />
                                                ) : (
                                                    <FileSearchOutlined />
                                                )}
                                            </div>

                                            {index < steps.length - 1 && (
                                                <div className="qc-trace-modal__step-line" />
                                            )}
                                        </div>

                                        <div className="qc-trace-modal__step-card">
                                            <div className="qc-trace-modal__step-header">
                                                <div className="qc-trace-modal__step-heading">
                                                    <Text
                                                        strong
                                                        className="qc-trace-modal__step-action"
                                                    >
                                                        {getActionLabel(
                                                            step.action,
                                                        )}
                                                    </Text>

                                                    <Tag
                                                        color={
                                                            tone === "default"
                                                                ? "blue"
                                                                : tone
                                                        }
                                                    >
                                                        Phiên bản {step.version}
                                                    </Tag>
                                                </div>

                                                <div className="qc-trace-modal__step-time">
                                                    <ClockCircleOutlined />

                                                    <Text type="secondary">
                                                        {formatTimestamp(
                                                            step.timestamp,
                                                        )}
                                                    </Text>
                                                </div>
                                            </div>

                                            {step.details?.trim() ? (
                                                <div className="qc-trace-modal__step-details">
                                                    {step.details}
                                                </div>
                                            ) : (
                                                <Text
                                                    type="secondary"
                                                    className="qc-trace-modal__no-details"
                                                >
                                                    Không có thông tin chi tiết.
                                                </Text>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}
            </div>
        </Modal>
    );
};