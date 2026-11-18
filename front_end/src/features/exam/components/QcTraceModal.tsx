import { useEffect, useRef, useState } from "react";
import type { FC } from "react";
import { Alert, Collapse, Empty, Modal, Space, Spin, Tag, Tooltip, Typography } from "antd";
import { ClockCircleOutlined, ExclamationCircleOutlined, HistoryOutlined, NodeIndexOutlined, UserOutlined } from "@ant-design/icons";

import { getQcTrace } from "../services/examApi";
import type { QcLineage, QcTraceResponse } from "../types/qcTrace.types";
import { QcVersionCard } from "./QcVersionCard";
import { currentVersion, describeAction, describeSlotState, formatQcTime, summarizeVersionQc, tagColor } from "../../../utils/qcTrace.utils";
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
                </Tag>
            )}
            {/* G22: không hiện số lần thử nội bộ; "N bản" = câu hệ thống soạn và các bản thầy/cô sửa */}
            <Text type="secondary">{lineage.versions.length} bản</Text>
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

// Thời gian antd mở panel Collapse (motionDurationMid 0,2 giây) cộng chút dư, để đo khi panel đã mở hết
const COLLAPSE_OPEN_MS = 190;

/**
 * Cuộn thân hộp để panel vừa mở hiện trọn: cạnh dưới panel không thấp hơn cạnh dưới vùng nhìn thấy của hộp. Panel cao
 * hơn vùng nhìn thấy thì đưa cạnh trên panel lên đầu (đọc được từ đầu). Chỉ cuộn xuống, không cuộn ngược.
 */
function revealPanel(scrollBox: HTMLElement, panel: HTMLElement) {
    const box = scrollBox.getBoundingClientRect();
    const rect = panel.getBoundingClientRect();
    const delta = rect.height > box.height ? rect.top - box.top : rect.bottom - box.bottom;
    if (delta > 0) scrollBox.scrollBy({ top: delta, behavior: "smooth" });
}

/**
 * onChange cho Collapse: panel nào vừa được mở (có trong keys mới, không có trong lần trước) thì cuộn cho nó hiện trọn.
 * `wrapper` là div bọc ngay ngoài Collapse; panel thứ i ứng với key thứ i trong `orderedKeys`.
 */
function revealOnOpen(
    wrapper: HTMLDivElement | null,
    previousKeys: { current: string[] },
    orderedKeys: string[],
    keys: string | string[]
) {
    const next = Array.isArray(keys) ? keys : [keys];
    const opened = next.find((key) => !previousKeys.current.includes(key));
    previousKeys.current = next;
    if (opened === undefined || !wrapper) return;
    const index = orderedKeys.indexOf(opened);
    window.setTimeout(() => {
        const panel = wrapper.querySelectorAll<HTMLElement>(":scope > .ant-collapse > .ant-collapse-item")[index];
        const scrollBox = wrapper.closest<HTMLElement>(".ant-modal-body");
        if (panel && scrollBox) revealPanel(scrollBox, panel);
    }, COLLAPSE_OPEN_MS);
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

    // Mở panel nào thì cuộn hộp cho panel đó hiện trọn (mục "Chi tiết kỹ thuật" và từng lần soạn bên trong)
    const detailsWrapperRef = useRef<HTMLDivElement>(null);
    const lineagesWrapperRef = useRef<HTMLDivElement>(null);
    const detailsOpenRef = useRef<string[]>([]);
    const lineagesOpenRef = useRef<string[]>([]);

    useEffect(() => {
        if (!visible || !examId || !slotId) {
            return;
        }

        let active = true;
        // Hộp mở lại thì mọi mục về đóng (destroyOnClose): quên các panel đã mở lần trước
        detailsOpenRef.current = [];
        lineagesOpenRef.current = [];

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
                    "Không tải được kết quả kiểm tra tự động.";

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

    const slotState = trace ? describeSlotState(trace.state) : null;
    // Chỉ chỗ đã có câu mới có tóm tắt (đang soạn, chưa soạn được thì không)
    const hasQuestion = trace ? ["ready", "kept", "edited"].includes(trace.state) : false;
    const version = trace && hasQuestion ? currentVersion(trace) : null;
    const summaryRows = version ? summarizeVersionQc(version) : null;

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
                            Kết quả kiểm tra tự động
                        </Text>
                    </div>
                </div>
            }
            // Số lần soạn và số thao tác hiện bằng thẻ ở đầu mục "Các lần soạn", không lặp ở chân hộp
            footer={null}
        >
            <div className="qc-trace-modal__body">
                {trace && (
                    <div className="qc-trace-modal__context">
                        <div className="qc-trace-modal__context-item">
                            <span className="qc-trace-modal__context-label">Câu</span>
                            <Text className="qc-trace-modal__context-value">
                                Phần {trace.part} – Câu {questionNo ?? trace.slotNo}
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
                            <span className="qc-trace-modal__context-label">Số lần soạn</span>
                            <Text className="qc-trace-modal__context-value">{trace.attempts ?? 0}</Text>
                        </div>
                    </div>
                )}

                {/* G22: ba dòng tóm tắt của bản đang dùng, chữ 16px; lịch sử đầy đủ ở "Chi tiết kỹ thuật" bên dưới */}
                {summaryRows && (
                    <ul className="qc-trace-modal__summary">
                        {summaryRows.map((row) => (
                            <li key={row.label}>
                                <span className="qc-trace-modal__summary-label">{row.label}:</span>{" "}
                                <span
                                    className={
                                        row.ok === true
                                            ? "qc-trace-modal__summary-ok"
                                            : row.ok === false
                                                ? "qc-trace-modal__summary-bad"
                                                : "qc-trace-modal__summary-none"
                                    }
                                >
                                    {row.value}
                                </span>
                            </li>
                        ))}
                    </ul>
                )}

                {/* Chỗ failed: lý do từ failureReason (V23), cùng câu với danh sách câu của đề */}
                {trace?.state === "failed" && (
                    <Alert
                        type="error"
                        showIcon
                        message={describeFailureReason(trace.failureReason)}
                        description={
                            // Lịch sử các lần soạn nằm trong mục "Chi tiết kỹ thuật" (đóng sẵn, G22)
                            trace.failureReason === "llm_error"
                                ? "Mở Chi tiết kỹ thuật bên dưới để xem lần soạn cuối."
                                : trace.failureReason === "qc_exhausted"
                                    ? "Mở Chi tiết kỹ thuật bên dưới để xem lỗi trình bày hoặc câu trùng ở các lần soạn."
                                    : undefined
                        }
                    />
                )}

                {loading ? (
                    <div className="qc-trace-modal__loading">
                        <Spin size="large" />

                        <Text type="secondary">
                            Đang tải kết quả kiểm tra tự động...
                        </Text>
                    </div>
                ) : error ? (
                    <Alert
                        className="qc-trace-modal__error"
                        type="error"
                        showIcon
                        icon={<ExclamationCircleOutlined />}
                        message="Không tải được kết quả kiểm tra tự động"
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
                                    Chưa có kết quả kiểm tra tự động
                                </Text>

                                <Text type="secondary">
                                    Câu này chưa có lần soạn hay thao tác nào được ghi nhận.
                                </Text>
                            </Space>
                        }
                    />
                ) : (
                    // G22: lịch sử các lần soạn và thao tác gom vào "Chi tiết kỹ thuật", đóng sẵn khi mở hộp
                    <div ref={detailsWrapperRef}>
                        <Collapse
                            className="qc-trace-modal__details"
                            defaultActiveKey={[]}
                            onChange={(keys) => revealOnOpen(detailsWrapperRef.current, detailsOpenRef, ["details"], keys)}
                            items={[{
                                key: "details",
                                label: (
                                    <div className="qc-trace-modal__section-heading">
                                        <Text strong>Chi tiết kỹ thuật</Text>
                                        {/* Số lần soạn và số thao tác: thẻ cạnh tiêu đề mục (trước nằm ở tiêu đề "Các lần soạn") */}
                                        <div className="qc-trace-modal__section-tags">
                                            <Tag color="blue" icon={<NodeIndexOutlined />}>
                                                {lineages.length} lần soạn
                                            </Tag>
                                            <Tag color="cyan" icon={<UserOutlined />}>
                                                {actions.length} thao tác
                                            </Tag>
                                        </div>
                                    </div>
                                ),
                                children: (
                                    <div className="qc-trace-modal__details-body">
                                        {lineages.length > 0 && (
                                            <div className="qc-trace-modal__timeline">
                                                {/* Hàng tiêu đề (chữ + hai thẻ) và dòng chú thích tách riêng, chú thích rộng hết khung */}
                                                <div className="qc-trace-modal__section-header">
                                                    <div className="qc-trace-modal__section-heading">
                                                        <Text strong>Các lần soạn</Text>
                                                    </div>
                                                    <Paragraph type="secondary" className="qc-trace-modal__section-desc">
                                                        Mỗi lần soạn gồm câu hệ thống soạn ra và các bản thầy/cô sửa kèm kết quả từng bước kiểm tra tự động.
                                                    </Paragraph>
                                                </div>

                                                <div ref={lineagesWrapperRef}>
                                                    <Collapse
                                                        // Mọi lần soạn thu gọn khi mở hộp; giáo viên bấm tiêu đề lần soạn để xem chi tiết
                                                        defaultActiveKey={[]}
                                                        onChange={(keys) =>
                                                            revealOnOpen(
                                                                lineagesWrapperRef.current,
                                                                lineagesOpenRef,
                                                                lineages.map((_, index) => String(index)),
                                                                keys
                                                            )
                                                        }
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
                                            </div>
                                        )}

                                        {actions.length > 0 && (
                                            <div className="qc-trace-modal__timeline">
                                                <div className="qc-trace-modal__section-header">
                                                    <div className="qc-trace-modal__section-heading">
                                                        <Text strong>Thao tác của giáo viên</Text>
                                                    </div>
                                                    <Paragraph type="secondary" className="qc-trace-modal__section-desc">
                                                        Sửa, Đổi, Lưu vào ngân hàng theo thứ tự thời gian.
                                                    </Paragraph>
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
                                    </div>
                                ),
                            }]}
                        />
                    </div>
                )}
            </div>
        </Modal>
    );
};
