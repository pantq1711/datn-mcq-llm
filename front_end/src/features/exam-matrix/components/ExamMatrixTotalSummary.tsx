import { Divider, Empty, Progress, Statistic, Tooltip, Typography } from 'antd';
import {
    AppstoreOutlined,
    BarChartOutlined,
    CalculatorOutlined,
    InfoCircleOutlined,
} from '@ant-design/icons';
import { useMemo } from 'react';

import {
    EXAM_MATRIX_COGNITIVE_LEVELS,
    EXAM_MATRIX_QUESTION_TYPES,
    createEmptyExamMatrixCounts,
} from './ExamMatrixDistributionTable';
import type {
    ExamMatrixCounts,
    ExamMatrixCognitiveLevel,
    ExamMatrixQuestionType,
} from './ExamMatrixDistributionTable';
import './ExamMatrixTotalSummary.css';

const { Text } = Typography;

export interface ExamMatrixTotalSummaryProps {
    /** The same distribution value supplied to ExamMatrixDistributionTable. */
    value?: ExamMatrixCounts;
    /** Shows a loading skeleton/state while the matrix data is being loaded. */
    loading?: boolean;
}

function normalizeCount(value: number | null | undefined): number {
    if (value === null || value === undefined || !Number.isFinite(value)) return 0;
    return Math.max(0, Math.floor(value));
}

function getQuestionTypeTotal(
    counts: ExamMatrixCounts,
    questionType: ExamMatrixQuestionType,
): number {
    return EXAM_MATRIX_COGNITIVE_LEVELS.reduce(
        (total, level) => total + normalizeCount(counts[questionType]?.[level.value]),
        0,
    );
}

function getCognitiveLevelTotal(
    counts: ExamMatrixCounts,
    cognitiveLevel: ExamMatrixCognitiveLevel,
): number {
    return EXAM_MATRIX_QUESTION_TYPES.reduce(
        (total, type) => total + normalizeCount(counts[type.value]?.[cognitiveLevel]),
        0,
    );
}

/**
 * Read-only summary of the current exam matrix distribution.
 * Pass the same `value` used by ExamMatrixDistributionTable to keep both components in sync.
 */
export function ExamMatrixTotalSummary({
    value,
    loading = false,
}: ExamMatrixTotalSummaryProps) {
    const counts = value ?? createEmptyExamMatrixCounts();

    const summary = useMemo(() => {
        const byQuestionType = EXAM_MATRIX_QUESTION_TYPES.map((type) => ({
            ...type,
            total: getQuestionTypeTotal(counts, type.value),
        }));
        const byCognitiveLevel = EXAM_MATRIX_COGNITIVE_LEVELS.map((level) => ({
            ...level,
            total: getCognitiveLevelTotal(counts, level.value),
        }));
        const total = byQuestionType.reduce((sum, item) => sum + item.total, 0);

        return { byQuestionType, byCognitiveLevel, total };
    }, [counts]);

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
                    <Tooltip title="Tổng được tính tự động từ 9 ô phân bổ ở bảng câu hỏi.">
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
                    <Text strong>Số câu theo dạng</Text>
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

            <section className="exam-matrix-total-summary__section" aria-label="Số câu theo mức độ nhận thức">
                <div className="exam-matrix-total-summary__section-heading">
                    <BarChartOutlined />
                    <Text strong>Số câu theo mức độ</Text>
                </div>
                <div className="exam-matrix-total-summary__cognitive-grid">
                    {summary.byCognitiveLevel.map((item) => {
                        const percent = summary.total > 0
                            ? Math.round((item.total / summary.total) * 100)
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
                        description={<Text type="secondary">Nhập số câu ở bảng phân bổ để cập nhật tổng quan.</Text>}
                    />
                </div>
            )}
        </div>
    );
}

export default ExamMatrixTotalSummary;
