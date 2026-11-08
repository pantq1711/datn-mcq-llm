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
    Tooltip,
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
import { describeChangedFields, describeFailureReason } from "../../../utils/exam.utils";

import "./QcTraceModal.css";

const { Text, Paragraph } = Typography;

interface Props {
    examId: string;
    slotId: string;
    /** Số câu như thẻ câu ở trang đề (thứ tự trong Phần); thiếu thì dùng số thứ tự trong ô ma trận của backend. */
    questionNo?: number;
    visible: boolean;
    onClose: () => void;
}

/** Tình trạng một lần soạn, nói bằng chữ của giáo viên (không hiện mã công việc). */
function describeJobStatus(status: string): string {
    if (status === "done") return "Đã xong";
    if (status === "failed") return "Bị lỗi";
    return "Đang soạn";
}

function lineageHeader(lineage: QcLineage, index: number) {
    const job = lineage.job;
    return (
        <Space size={6} wrap>
            <Text strong>{lineage.origin === "bank" ? "Câu từ ngân hàng" : `Lần soạn ${index + 1}`}</Text>
            {lineage.versions.some((v) => v.current) && <Tag color="purple">Đang dùng</Tag>}
            {job && (
                <Tag color={job.status === "failed" ? "error" : job.status === "done" ? "default" : "warning"}>
                    {describeJobStatus(job.status)}
                    {job.attempt ? ` · lần thử ${job.attempt}` : ""}
                </Tag>
            )}
            <Text type="secondary">{lineage.versions.length} phiên bản</Text>
            {/* Lỗi kỹ thuật của backend chỉ để trong tooltip, không hiện thẳng cho giáo viên */}
            {job?.error && (
                <Tooltip title={job.error}>
                    <Text type="danger">
                        Lỗi khi soạn câu <Text type="secondary" underline>(Chi tiết)</Text>
                    </Text>
                </Tooltip>
            )}
        </Space>
    );
}

export const QcTraceModal: FC<Props> = ({
    examId,
    slotId,
    questionNo,
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
                    "Không tải được kết quả tự kiểm tra.";

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
                            Kết quả tự kiểm tra
                        </Text>

                        <Text className="qc-trace-modal__subtitle">
                            Các lần soạn, phiên bản, từng bước tự kiểm tra và thao tác của câu này
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
                            : `${lineages.length} lần soạn · ${actions.length} thao tác`}
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
                            <span className="qc-trace-modal__context-label">Câu</span>
                            <Text strong>
                                Câu {questionNo ?? trace.slotNo} · Phần {trace.part}
                                {trace.topic ? ` · ${trace.topic}` : ""}
                            </Text>
                        </div>

                        <div className="qc-trace-modal__context-divider" />

                        <div className="qc-trace-modal__context-item">
                            <span className="qc-trace-modal__context-label">Tình trạng</span>
                            <Space size={4} wrap>
                                {slotState && <Tag color={tagColor(slotState.tone)}>{slotState.label}</Tag>}
                                {trace.source && (
                                    <Tag>{trace.source === "bank" ? "Câu từ ngân hàng" : "Câu mới"}</Tag>
                                )}
                            </Space>
                        </div>

                        <div className="qc-trace-modal__context-divider" />

                        <div className="qc-trace-modal__context-item">
                            <span className="qc-trace-modal__context-label">Lần thử</span>
                            <Text strong>
                                {trace.attempts ?? 0}
                                {trace.solverRetries ? ` · tự kiểm tra lại ${trace.solverRetries} lần` : ""}
                            </Text>
                        </div>
                    </div>
                )}

                {/* Chỗ failed: lý do từ failureReason (V23), cùng câu với danh sách câu của đề */}
                {trace?.state === "failed" && (
                    <Alert
                        type="error"
                        showIcon
                        message={describeFailureReason(trace.failureReason)}
                        description={
                            trace.failureReason === "llm_error"
                                ? "Xem lỗi ở lần soạn cuối bên dưới."
                                : trace.failureReason === "qc_exhausted"
                                    ? "Xem lỗi trình bày hoặc câu trùng ở các lần soạn bên dưới."
                                    : undefined
                        }
                    />
                )}

                {loading ? (
                    <div className="qc-trace-modal__loading">
                        <Spin size="large" />

                        <Text type="secondary">
                            Đang tải kết quả tự kiểm tra...
                        </Text>
                    </div>
                ) : error ? (
                    <Alert
                        className="qc-trace-modal__error"
                        type="error"
                        showIcon
                        icon={<ExclamationCircleOutlined />}
                        message="Không tải được kết quả tự kiểm tra"
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
                                    Chưa có kết quả tự kiểm tra
                                </Text>

                                <Text type="secondary">
                                    Câu này chưa có lần soạn hay thao tác nào được ghi nhận.
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
                                        <Text strong>Các lần soạn</Text>
                                        <Paragraph type="secondary">
                                            Mỗi lần soạn gồm câu hệ thống soạn ra, rồi các bản thầy/cô sửa, kèm kết quả từng bước tự kiểm tra.
                                        </Paragraph>
                                    </div>

                                    <Tag color="blue" icon={<NodeIndexOutlined />}>
                                        {lineages.length} lần soạn
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
                                        <Paragraph type="secondary">Sửa, Đổi, Lưu vào ngân hàng theo thứ tự thời gian.</Paragraph>
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
                                                                    Đã sửa: {describeChangedFields(action.changedFields)}
                                                                </div>
                                                            )}
                                                            {action.bankItemId != null && <div>Câu từ ngân hàng</div>}
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
