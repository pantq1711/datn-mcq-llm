import { Button, Progress, Space, Spin, Switch, Typography } from "antd";
import { CheckCircleFilled, FileWordOutlined, ReloadOutlined, SaveOutlined, SyncOutlined } from "@ant-design/icons";

import "./ExamSummaryBar.css";

const { Text } = Typography;

export interface ExamSummaryCounts {
    /** Tổng số câu theo ma trận (cộng required các Phần). */
    total: number;
    /** Số chỗ đã có câu. */
    ready: number;
    kept: number;
    needsReview: number;
    failed: number;
    generating: number;
}

interface ExamSummaryBarProps {
    counts: ExamSummaryCounts;
    /** "12 trắc nghiệm · 2 đúng/sai · 4 trả lời ngắn", hiện trong ngoặc sau số câu */
    partsBreakdown: string;
    /** Dòng tiến độ khi đề đang soạn (describeBuildProgress); vắng khi đã soạn xong. */
    progressText?: string;
    progressPercent?: number;
    /** "3 câu đang soạn, 1 câu đang tự kiểm tra lại" khi trang đang tự cập nhật; vắng thì ẩn. */
    pendingText?: string;
    /** Lời nhắc xuất Word khi đề còn câu đang soạn (G19); vắng thì ẩn. */
    exportNote?: string;
    onlyNeedsAttention: boolean;
    onOnlyNeedsAttentionChange: (value: boolean) => void;
    showAllExplanations: boolean;
    onShowAllExplanationsChange: (value: boolean) => void;
    onExport: () => void;
    exportLoading: boolean;
    /** Lưu hàng loạt (CT-7): số câu không có cảnh báo, chưa lưu; 0 thì ẩn nút. */
    bulkKeepCount?: number;
    /** Đang chạy lưu hàng loạt hoặc soạn lại hàng loạt: khóa cả hai nút. */
    bulkSaving?: boolean;
    onBulkKeep?: () => void;
    /** Soạn lại hàng loạt (G20): số câu chưa soạn được vì hệ thống bận; 0 thì ẩn nút. */
    bulkRegenerateCount?: number;
    onBulkRegenerate?: () => void;
}

/**
 * Ô tóm tắt đầu trang đề (CT-1, CT-4): số câu đã soạn, cần xem kỹ, đã lưu; hai công tắc lọc và hiện lời giải. Đề đủ câu,
 * không còn câu cần xem kỹ hay câu lỗi thì đổi thành "Đề sẵn sàng in" kèm nút xuất Word.
 */
export default function ExamSummaryBar({
    counts,
    partsBreakdown,
    progressText,
    progressPercent,
    pendingText,
    exportNote,
    onlyNeedsAttention,
    onOnlyNeedsAttentionChange,
    showAllExplanations,
    onShowAllExplanationsChange,
    onExport,
    exportLoading,
    bulkKeepCount = 0,
    bulkSaving = false,
    onBulkKeep,
    bulkRegenerateCount = 0,
    onBulkRegenerate,
}: ExamSummaryBarProps) {
    const readyToPrint =
        counts.total > 0 && counts.generating === 0 && counts.failed === 0 && counts.needsReview === 0
        && counts.ready >= counts.total;

    // Công tắc và nút: cùng hàng với số liệu trên màn rộng (G1: bớt một hàng ở đầu trang), hẹp thì tự xuống dòng
    const controls = (
        <div className="exam-summary-toggles">
            <label className="exam-summary-toggle">
                <Switch checked={onlyNeedsAttention} onChange={onOnlyNeedsAttentionChange} />
                <span>Chỉ hiện câu cần xem</span>
            </label>
            <label className="exam-summary-toggle">
                <Switch checked={showAllExplanations} onChange={onShowAllExplanationsChange} />
                <span>Hiện tất cả lời giải</span>
            </label>
            {onBulkRegenerate && bulkRegenerateCount > 0 && (
                <Button
                    icon={<ReloadOutlined />}
                    disabled={bulkSaving}
                    onClick={onBulkRegenerate}
                >
                    Soạn lại {bulkRegenerateCount} câu (hệ thống bận)
                </Button>
            )}
            {onBulkKeep && (bulkKeepCount > 0 || bulkSaving) && (
                <Button
                    icon={<SaveOutlined />}
                    loading={bulkSaving}
                    onClick={onBulkKeep}
                >
                    {bulkSaving ? "Đang xử lý…" : `Lưu ${bulkKeepCount} câu không có cảnh báo`}
                </Button>
            )}
            {readyToPrint && (
                <Button type="primary" icon={<FileWordOutlined />} loading={exportLoading} onClick={onExport}>
                    Xuất file Word
                </Button>
            )}
        </div>
    );

    return (
        <div className={`exam-summary-bar${readyToPrint ? " exam-summary-bar-ready" : ""}`}>
            <div className="exam-summary-main">
            {readyToPrint ? (
                <div className="exam-summary-ready-row">
                    <Space size={10} align="center">
                        <CheckCircleFilled className="exam-summary-ready-icon" />
                        <span>
                            <Text strong className="exam-summary-ready-title">Đề sẵn sàng in</Text>
                            <br />
                            <Text type="secondary">
                                Đủ {counts.total} câu{partsBreakdown ? ` (${partsBreakdown})` : ""}, không còn câu cần xem kỹ · Đã lưu {counts.kept}/{counts.total} câu vào ngân hàng
                            </Text>
                        </span>
                    </Space>
                </div>
            ) : (
                <div className="exam-summary-stats">
                    <Text strong className="exam-summary-stat">
                        Đã soạn {counts.ready}/{counts.total} câu
                        {partsBreakdown && (
                            <Text type="secondary" className="exam-summary-parts"> ({partsBreakdown})</Text>
                        )}
                    </Text>
                    <span className="exam-summary-sep">·</span>
                    <Text strong className={counts.needsReview > 0 ? "exam-summary-stat-warn" : "exam-summary-stat"}>
                        {counts.needsReview > 0 ? `Còn ${counts.needsReview} câu cần xem kỹ` : "Không có câu cần xem kỹ"}
                    </Text>
                    {counts.failed > 0 && (
                        <>
                            <span className="exam-summary-sep">·</span>
                            <Text strong className="exam-summary-stat-danger">{counts.failed} câu chưa soạn được</Text>
                        </>
                    )}
                    <span className="exam-summary-sep">·</span>
                    <Text strong className="exam-summary-stat">Đã lưu {counts.kept}/{counts.total}</Text>
                </div>
            )}
            {controls}
            </div>

            {progressText && (
                <div className="exam-summary-progress">
                    <Space size={8} align="start">
                        <SyncOutlined spin className="exam-summary-progress-icon" />
                        <Text>{progressText}</Text>
                    </Space>
                    {progressPercent !== undefined && <Progress percent={progressPercent} showInfo={false} />}
                </div>
            )}

            {exportNote && (
                <Space size={6} className="exam-summary-export-note">
                    <FileWordOutlined />
                    <Text type="secondary">{exportNote}</Text>
                </Space>
            )}

            {/* Trang đang tự cập nhật (câu đang soạn / đang tự kiểm tra lại) */}
            {pendingText && (
                <Space size={6} className="exam-summary-pending">
                    <Spin size="small" />
                    <Text type="secondary">Đang cập nhật: {pendingText}</Text>
                </Space>
            )}
        </div>
    );
}
