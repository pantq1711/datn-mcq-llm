import { Divider, Empty, Progress, Statistic, Tooltip, Typography } from 'antd';
import { AppstoreOutlined, BarChartOutlined, CalculatorOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useMemo } from 'react';

import type { ExamMatrixCellInput, MatrixQuestionType } from '../types/examMatrix.types';
import { MATRIX_LEVELS, MATRIX_LEVEL_LABELS, MATRIX_TYPE_LABELS, PART_BY_TYPE, summarizeCells } from '../../../utils/matrixCell.utils';
import './ExamMatrixTotalSummary.css';

const { Text } = Typography;

export interface ExamMatrixTotalSummaryProps {
    /** Các ô của ma trận (cùng giá trị với ExamMatrixCellEditor). */
    value?: ExamMatrixCellInput[];
    /** Shows a loading skeleton/state while the matrix data is being loaded. */
    loading?: boolean;
}

const TYPES: MatrixQuestionType[] = ['mcq', 'tf', 'sa'];

/**
 * Tổng quan chỉ đọc của ma trận, cách tính như `totals` của backend (docs/api mục 7.1):
 * số câu theo phần I-III và số lệnh hỏi theo mức (mỗi câu đúng/sai là 4 lệnh hỏi).
 */
export function ExamMatrixTotalSummary({
    value,
    loading = false,
}: ExamMatrixTotalSummaryProps) {
    const summary = useMemo(() => {
        const totals = summarizeCells(value ?? []);
        const byQuestionType = TYPES.map((type) => ({
            value: type,
            label: MATRIX_TYPE_LABELS[type],
            total: totals.questionsByPart[PART_BY_TYPE[type]],
        }));
        const byCognitiveLevel = MATRIX_LEVELS.map((level) => ({
            value: level,
            label: MATRIX_LEVEL_LABELS[level],
            total: totals.commandsByLevel[level],
        }));

        return { byQuestionType, byCognitiveLevel, total: totals.totalQuestions, totalCommands: totals.totalCommands };
    }, [value]);

    if (loading) {
        return (
            <div className="exam-matrix-total-summary exam-matrix-total-summary--loading" aria-busy="true">
                <div className="exam-matrix-total-summary__skeleton-total" />
                <Divider />
                <div className="exam-matrix-total-summary__skeleton-line" />
                <div className="exam-matrix-total-summary__skeleton-line" />
                <div className="exam-matrix-total-summary__skeleton-line" />
            </div>
        );
    }

    const hasQuestions = summary.total > 0;

    return (
        <div className="exam-matrix-total-summary">
            <section className="exam-matrix-total-summary__total-section" aria-label="Tổng số câu hỏi">
                <div className="exam-matrix-total-summary__total-heading">
                    <span className="exam-matrix-total-summary__icon exam-matrix-total-summary__icon--primary">
                        <CalculatorOutlined />
                    </span>
                    <Text type="secondary">Tổng số câu</Text>
                    <Tooltip title="Tổng được tính tự động từ các ô của ma trận.">
                        <InfoCircleOutlined className="exam-matrix-total-summary__info-icon" />
                    </Tooltip>
                </div>
                <Statistic
                    className="exam-matrix-total-summary__statistic"
                    value={summary.total}
                    suffix={<span className="exam-matrix-total-summary__statistic-suffix">câu</span>}
                    valueStyle={{ fontSize: 32, fontWeight: 650, lineHeight: 1.2 }}
                />
                <Text type="secondary" className="exam-matrix-total-summary__total-caption">
                    {hasQuestions ? 'Số câu theo cơ cấu hiện tại' : 'Chưa phân bổ câu hỏi'}
                </Text>
            </section>

            <Divider className="exam-matrix-total-summary__divider" />

            <section className="exam-matrix-total-summary__section" aria-label="Số câu theo dạng câu hỏi">
                <div className="exam-matrix-total-summary__section-heading">
                    <AppstoreOutlined />
                    <Text strong>Số câu theo phần</Text>
                </div>
                <div className="exam-matrix-total-summary__rows">
                    {summary.byQuestionType.map((item) => {
                        const percent = summary.total > 0
                            ? Math.round((item.total / summary.total) * 100)
                            : 0;

                        return (
                            <div className="exam-matrix-total-summary__row" key={item.value}>
                                <div className="exam-matrix-total-summary__row-topline">
                                    <Text className="exam-matrix-total-summary__row-label">{item.label}</Text>
                                    <Text strong className="exam-matrix-total-summary__row-value">
                                        {item.total}
                                    </Text>
                                </div>
                                <Progress
                                    className="exam-matrix-total-summary__progress"
                                    percent={percent}
                                    showInfo={false}
                                    size="small"
                                    strokeColor="#1677ff"
                                    trailColor="#f0f0f0"
                                />
                            </div>
                        );
                    })}
                </div>
            </section>

            <Divider className="exam-matrix-total-summary__divider" />

            <section className="exam-matrix-total-summary__section" aria-label="Số lệnh hỏi theo mức độ nhận thức">
                <div className="exam-matrix-total-summary__section-heading">
                    <BarChartOutlined />
                    <Text strong>Lệnh hỏi theo mức độ ({summary.totalCommands})</Text>
                    <Tooltip title="Mỗi câu trắc nghiệm hoặc trả lời ngắn là 1 lệnh hỏi; mỗi câu đúng/sai là 4 lệnh hỏi theo mức của 4 ý.">
                        <InfoCircleOutlined className="exam-matrix-total-summary__info-icon" />
                    </Tooltip>
                </div>
                <div className="exam-matrix-total-summary__cognitive-grid">
                    {summary.byCognitiveLevel.map((item) => {
                        const percent = summary.totalCommands > 0
                            ? Math.round((item.total / summary.totalCommands) * 100)
                            : 0;

                        return (
                            <div className="exam-matrix-total-summary__cognitive-item" key={item.value}>
                                <Text type="secondary" className="exam-matrix-total-summary__cognitive-label">
                                    {item.label}
                                </Text>
                                <Text strong className="exam-matrix-total-summary__cognitive-value">
                                    {item.total}
                                </Text>
                                <Text type="secondary" className="exam-matrix-total-summary__cognitive-percent">
                                    {percent}%
                                </Text>
                            </div>
                        );
                    })}
                </div>
            </section>

            {!hasQuestions && (
                <div className="exam-matrix-total-summary__empty-hint">
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={<Text type="secondary">Thêm ô và nhập số câu để cập nhật tổng quan.</Text>}
                    />
                </div>
            )}
        </div>
    );
}

export default ExamMatrixTotalSummary;
