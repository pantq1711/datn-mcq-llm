import { useEffect, useState } from "react";
import type { FC } from "react";
import {
    Alert,
    Button,
    Collapse,
    Empty,
    Modal,
    Space,
    Spin,
    Tag,
    Typography,
} from "antd";
import {
    ClockCircleOutlined,
    CloseOutlined,
    ExclamationCircleOutlined,
    HistoryOutlined,
    NodeIndexOutlined,
    UserOutlined,
} from "@ant-design/icons";

import { getQcTrace } from "../services/examApi";
import type { QcLineage, QcTraceResponse } from "../types/qcTrace.types";
import { QcVersionCard } from "./QcVersionCard";
import { describeAction, describeSlotState, formatQcTime, tagColor } from "../../../utils/qcTrace.utils";

import "./QcTraceModal.css";

const { Text, Paragraph } = Typography;

interface Props {
    examId: string;
    slotId: string;
    visible: boolean;
    onClose: () => void;
}

function lineageHeader(lineage: QcLineage, index: number) {
    const job = lineage.job;
    return (
        <Space size={6} wrap>
            <Text strong>{lineage.origin === "bank" ? "Câu ngân hàng" : `Lần sinh ${index + 1}`}</Text>
            {lineage.versions.some((v) => v.current) && <Tag color="purple">Đang dùng</Tag>}
            {job && (
                <Tag color={job.status === "failed" ? "error" : job.status === "done" ? "default" : "warning"}>
                    Job #{job.jobId} · {job.status}
                    {job.attempt ? ` · lần thử ${job.attempt}` : ""}
                </Tag>
            )}
            <Text type="secondary">{lineage.versions.length} phiên bản</Text>
            {job?.error && <Text type="danger">{job.error}</Text>}
        </Space>
    );
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

    const lineages = trace?.lineages ?? [];
    const actions = trace?.actions ?? [];
    const isEmpty = lineages.length === 0 && actions.length === 0;

    // Chỉ mở sẵn lần sinh chứa phiên bản đang dùng; không có thì mở lần cuối
    const currentIndex = lineages.findIndex((l) => l.versions.some((v) => v.current));
    const defaultOpen = String(currentIndex >= 0 ? currentIndex : lineages.length - 1);

    const slotState = trace ? describeSlotState(trace.state) : null;

    return (
        <Modal
            open={visible}
            onCancel={onClose}
            width={760}
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
                            Các lần sinh, phiên bản, từng bước QC và thao tác của chỗ
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
                            : `${lineages.length} lần sinh · ${actions.length} thao tác`}
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
                {trace && (
                    <div className="qc-trace-modal__context">
                        <div className="qc-trace-modal__context-item">
                            <span className="qc-trace-modal__context-label">Chỗ</span>
                            <Text strong>
                                Số {trace.slotNo} · Phần {trace.part}
                                {trace.topic ? ` · ${trace.topic}` : ""}
                            </Text>
                        </div>

                        <div className="qc-trace-modal__context-divider" />

                        <div className="qc-trace-modal__context-item">
                            <span className="qc-trace-modal__context-label">Trạng thái</span>
                            <Space size={4} wrap>
                                {slotState && <Tag color={tagColor(slotState.tone)}>{slotState.label}</Tag>}
                                {trace.source && (
                                    <Tag>{trace.source === "bank" ? "Từ ngân hàng" : "Hệ thống sinh"}</Tag>
                                )}
                            </Space>
                        </div>

                        <div className="qc-trace-modal__context-divider" />

                        <div className="qc-trace-modal__context-item">
                            <span className="qc-trace-modal__context-label">Lần thử</span>
                            <Text strong>
                                {trace.attempts ?? 0}
                                {trace.solverRetries ? ` · Solver thử lại ${trace.solverRetries}` : ""}
                            </Text>
                        </div>
                    </div>
                )}

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
                ) : isEmpty ? (
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
                                    Chỗ này chưa có lần sinh hay thao tác nào được ghi nhận.
                                </Text>
                            </Space>
                        }
                    />
                ) : (
                    <>
                        {lineages.length > 0 && (
                            <div className="qc-trace-modal__timeline">
                                <div className="qc-trace-modal__section-heading">
                                    <div>
                                        <Text strong>Các lần sinh</Text>
                                        <Paragraph type="secondary">
                                            Mỗi lần sinh gồm câu LLM gốc rồi các bản giáo viên sửa, kèm kết quả từng bước QC.
                                        </Paragraph>
                                    </div>

                                    <Tag color="blue" icon={<NodeIndexOutlined />}>
                                        {lineages.length} lần sinh
                                    </Tag>
                                </div>

                                <Collapse
                                    defaultActiveKey={[defaultOpen]}
                                    items={lineages.map((lineage, index) => ({
                                        key: String(index),
                                        label: lineageHeader(lineage, index),
                                        children: (
                                            <div className="qc-trace-modal__steps">
                                                {lineage.versions.map((version, vIndex) => (
                                                    <QcVersionCard
                                                        key={version.questionId}
                                                        version={version}
                                                        isLast={vIndex === lineage.versions.length - 1}
                                                    />
                                                ))}
                                            </div>
                                        ),
                                    }))}
                                />
                            </div>
                        )}

                        {actions.length > 0 && (
                            <div className="qc-trace-modal__timeline">
                                <div className="qc-trace-modal__section-heading">
                                    <div>
                                        <Text strong>Thao tác của giáo viên</Text>
                                        <Paragraph type="secondary">Sửa, Đổi, Giữ theo thứ tự thời gian.</Paragraph>
                                    </div>
                                </div>

                                <div className="qc-trace-modal__steps">
                                    {actions.map((action, index) => {
                                        const info = describeAction(action.action);
                                        return (
                                            <div
                                                key={action.id}
                                                className={`qc-trace-modal__step qc-trace-modal__step--${info.tone}`}
                                            >
                                                <div className="qc-trace-modal__step-rail">
                                                    <div className="qc-trace-modal__step-marker">
                                                        <UserOutlined />
                                                    </div>
                                                    {index < actions.length - 1 && (
                                                        <div className="qc-trace-modal__step-line" />
                                                    )}
                                                </div>

                                                <div className="qc-trace-modal__step-card">
                                                    <div className="qc-trace-modal__step-header">
                                                        <Space size={6} wrap className="qc-trace-modal__step-heading">
                                                            <Text strong className="qc-trace-modal__step-action">
                                                                {info.label}
                                                            </Text>
                                                            {action.actor?.name && <Tag>{action.actor.name}</Tag>}
                                                            {action.questionId != null && (
                                                                <Text type="secondary">câu #{action.questionId}</Text>
                                                            )}
                                                        </Space>

                                                        <div className="qc-trace-modal__step-time">
                                                            <ClockCircleOutlined />
                                                            <Text type="secondary">{formatQcTime(action.at)}</Text>
                                                        </div>
                                                    </div>

                                                    {action.instruction || (action.changedFields && action.changedFields.length > 0) || action.bankItemId != null ? (
                                                        <div className="qc-trace-modal__step-details">
                                                            {action.instruction && <div>Yêu cầu: {action.instruction}</div>}
                                                            {action.changedFields && action.changedFields.length > 0 && (
                                                                <div>
                                                                    Trường đã sửa: {action.changedFields.join(", ")}
                                                                    {action.editDistance != null && ` (khoảng cách sửa ${action.editDistance})`}
                                                                </div>
                                                            )}
                                                            {action.bankItemId != null && <div>Câu ngân hàng #{action.bankItemId}</div>}
                                                        </div>
                                                    ) : (
                                                        <Text type="secondary" className="qc-trace-modal__no-details">
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
                    </>
                )}
            </div>
        </Modal>
    );
};
