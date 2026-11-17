import { Button, Progress, Space, Switch, Typography } from "antd";
import { CheckCircleFilled, FileWordOutlined, SyncOutlined } from "@ant-design/icons";

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
    onlyNeedsAttention: boolean;
    onOnlyNeedsAttentionChange: (value: boolean) => void;
    showAllExplanations: boolean;
    onShowAllExplanationsChange: (value: boolean) => void;
    onExport: () => void;
    exportLoading: boolean;
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
    onlyNeedsAttention,
    onOnlyNeedsAttentionChange,
    showAllExplanations,
    onShowAllExplanationsChange,
    onExport,
    exportLoading,
}: ExamSummaryBarProps) {
    const readyToPrint =
        counts.total > 0 && counts.generating === 0 && counts.failed === 0 && counts.needsReview === 0
        && counts.ready >= counts.total;

    return (
        <div className={`exam-summary-bar${readyToPrint ? " exam-summary-bar-ready" : ""}`}>
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
                    <Button type="primary" icon={<FileWordOutlined />} loading={exportLoading} onClick={onExport}>
                        Xuất file Word
                    </Button>
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

            {progressText && (
                <div className="exam-summary-progress">
                    <Space size={8} align="start">
                        <SyncOutlined spin className="exam-summary-progress-icon" />
                        <Text>{progressText}</Text>
                    </Space>
                    {progressPercent !== undefined && <Progress percent={progressPercent} showInfo={false} />}
                </div>
            )}

            <div className="exam-summary-toggles">
                <label className="exam-summary-toggle">
                    <Switch checked={onlyNeedsAttention} onChange={onOnlyNeedsAttentionChange} />
                    <span>Chỉ hiện câu cần xem</span>
                </label>
                <label className="exam-summary-toggle">
                    <Switch checked={showAllExplanations} onChange={onShowAllExplanationsChange} />
                    <span>Hiện tất cả lời giải</span>
                </label>
            </div>
        </div>
    );
}
