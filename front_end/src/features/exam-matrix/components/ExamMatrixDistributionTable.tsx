import { useMemo, useState } from 'react';
import { InputNumber, Table, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';

import './ExamMatrixDistributionTable.css';

const { Text } = Typography;

export type ExamMatrixQuestionType =
    | 'MULTIPLE_CHOICE'
    | 'TRUE_FALSE'
    | 'SHORT_ANSWER';

export type ExamMatrixCognitiveLevel = 'KNOW' | 'UNDERSTAND' | 'APPLY';

export interface ExamMatrixCounts {
    MULTIPLE_CHOICE: Record<ExamMatrixCognitiveLevel, number>;
    TRUE_FALSE: Record<ExamMatrixCognitiveLevel, number>;
    SHORT_ANSWER: Record<ExamMatrixCognitiveLevel, number>;
}

export interface ExamMatrixDistributionTableProps {
    /** Current matrix distribution. If omitted, the component maintains its own state. */
    value?: ExamMatrixCounts;
    /** Called whenever any cell's question count changes. */
    onChange?: (value: ExamMatrixCounts) => void;
    /** Disables all count inputs, for example while saving. */
    disabled?: boolean;
}

export const EXAM_MATRIX_QUESTION_TYPES: Array<{
    value: ExamMatrixQuestionType;
    label: string;
}> = [
        { value: 'MULTIPLE_CHOICE', label: 'Trắc nghiệm nhiều lựa chọn' },
        { value: 'TRUE_FALSE', label: 'Đúng / Sai' },
        { value: 'SHORT_ANSWER', label: 'Trả lời ngắn' },
    ];

export const EXAM_MATRIX_COGNITIVE_LEVELS: Array<{
    value: ExamMatrixCognitiveLevel;
    label: string;
}> = [
        { value: 'KNOW', label: 'Biết' },
        { value: 'UNDERSTAND', label: 'Hiểu' },
        { value: 'APPLY', label: 'Vận dụng' },
    ];

export function createEmptyExamMatrixCounts(): ExamMatrixCounts {
    return {
        MULTIPLE_CHOICE: { KNOW: 0, UNDERSTAND: 0, APPLY: 0 },
        TRUE_FALSE: { KNOW: 0, UNDERSTAND: 0, APPLY: 0 },
        SHORT_ANSWER: { KNOW: 0, UNDERSTAND: 0, APPLY: 0 },
    };
}

interface DistributionRow {
    key: string;
    questionType: ExamMatrixQuestionType;
    cognitiveLevel: ExamMatrixCognitiveLevel;
    questionCount: number;
}

function normalizeCount(value: number | null | undefined): number {
    if (value === null || value === undefined || !Number.isFinite(value)) return 0;
    return Math.max(0, Math.floor(value));
}

function getTypeSubtotal(
    counts: ExamMatrixCounts,
    questionType: ExamMatrixQuestionType,
): number {
    return EXAM_MATRIX_COGNITIVE_LEVELS.reduce(
        (total, level) => total + normalizeCount(counts[questionType][level.value]),
        0,
    );
}

function getGrandTotal(counts: ExamMatrixCounts): number {
    return EXAM_MATRIX_QUESTION_TYPES.reduce(
        (total, type) => total + getTypeSubtotal(counts, type.value),
        0,
    );
}

/**
 * Matrix distribution editor. The table contains the three in-scope question types
 * and the three cognitive levels currently used by the project: Biết, Hiểu, Vận dụng.
 */
export function ExamMatrixDistributionTable({
    value,
    onChange,
    disabled = false,
}: ExamMatrixDistributionTableProps) {
    const [internalValue, setInternalValue] = useState<ExamMatrixCounts>(createEmptyExamMatrixCounts);
    const [focusedEmptyCell, setFocusedEmptyCell] = useState<string | null>(null);

    const counts = value ?? internalValue;

    const rows = useMemo<DistributionRow[]>(() => {
        return EXAM_MATRIX_QUESTION_TYPES.flatMap((type) =>
            EXAM_MATRIX_COGNITIVE_LEVELS.map((level) => ({
                key: `${type.value}-${level.value}`,
                questionType: type.value,
                cognitiveLevel: level.value,
                questionCount: normalizeCount(counts[type.value][level.value]),
            })),
        );
    }, [counts]);

    const grandTotal = useMemo(() => getGrandTotal(counts), [counts]);

    const handleCountChange = (
        questionType: ExamMatrixQuestionType,
        cognitiveLevel: ExamMatrixCognitiveLevel,
        nextValue: number | null,
    ) => {
        const nextCounts: ExamMatrixCounts = {
            MULTIPLE_CHOICE: { ...counts.MULTIPLE_CHOICE },
            TRUE_FALSE: { ...counts.TRUE_FALSE },
            SHORT_ANSWER: { ...counts.SHORT_ANSWER },
        };

        nextCounts[questionType][cognitiveLevel] = normalizeCount(nextValue);

        if (value === undefined) {
            setInternalValue(nextCounts);
        }
        onChange?.(nextCounts);
    };

    const columns: TableColumnsType<DistributionRow> = [
        {
            title: 'Dạng câu hỏi',
            dataIndex: 'questionType',
            key: 'questionType',
            width: '52%',
            render: (_: ExamMatrixQuestionType, record, index) => {
                if (index % EXAM_MATRIX_COGNITIVE_LEVELS.length !== 0) return null;

                const type = EXAM_MATRIX_QUESTION_TYPES.find(
                    (item) => item.value === record.questionType,
                );
                const subtotal = getTypeSubtotal(counts, record.questionType);

                return (
                    <div className="exam-matrix-distribution-table__question-type">
                        <Text strong>{type?.label}</Text>
                        <Text className="exam-matrix-distribution-table__subtotal" type="secondary">
                            {subtotal} câu
                        </Text>
                    </div>
                );
            },
            onCell: (_record, index) => ({
                rowSpan:
                    (index ?? 0) % EXAM_MATRIX_COGNITIVE_LEVELS.length === 0
                        ? EXAM_MATRIX_COGNITIVE_LEVELS.length
                        : 0,
            }),
        },
        {
            title: 'Mức độ nhận thức',
            dataIndex: 'cognitiveLevel',
            key: 'cognitiveLevel',
            width: '28%',
            render: (level: ExamMatrixCognitiveLevel) => {
                const cognitiveLevel = EXAM_MATRIX_COGNITIVE_LEVELS.find(
                    (item) => item.value === level,
                );
                return <span>{cognitiveLevel?.label}</span>;
            },
        },
        {
            title: (
                <span className="exam-matrix-distribution-table__count-heading">
                    Số câu
                    <Tooltip title="Nhập số câu cho từng cặp dạng câu hỏi và mức độ nhận thức. Giá trị không nhập được tính là 0.">
                        <InfoCircleOutlined aria-label="Thông tin về số câu" />
                    </Tooltip>
                </span>
            ),
            dataIndex: 'questionCount',
            key: 'questionCount',
            width: '20%',
            align: 'center',
            render: (questionCount: number, record) => {
                const isFocusedEmpty = focusedEmptyCell === record.key && questionCount === 0;

                return (
                    <InputNumber
                        className="exam-matrix-distribution-table__count-input"
                        min={0}
                        precision={0}
                        step={1}
                        controls={false}
                        value={isFocusedEmpty ? null : questionCount}
                        disabled={disabled}
                        aria-label={`Số câu ${record.questionType}, mức ${record.cognitiveLevel}`}
                        onFocus={() => {
                            if (questionCount === 0) setFocusedEmptyCell(record.key);
                        }}
                        onBlur={() => {
                            setFocusedEmptyCell((current) => current === record.key ? null : current);
                        }}
                        onChange={(nextValue) =>
                            handleCountChange(record.questionType, record.cognitiveLevel, nextValue)
                        }
                    />
                );
            },
        },
    ];

    return (
        <div className="exam-matrix-distribution-table">
            <div className="exam-matrix-distribution-table__intro">
                <Text type="secondary">
                    Phân bổ số câu theo dạng câu hỏi và mức độ nhận thức.
                </Text>
                <Tooltip title="Hiện hỗ trợ ba dạng câu hỏi: trắc nghiệm nhiều lựa chọn, đúng/sai và trả lời ngắn; cùng ba mức độ Biết, Hiểu và Vận dụng.">
                    <InfoCircleOutlined className="exam-matrix-distribution-table__info-icon" />
                </Tooltip>
            </div>

            <Table<DistributionRow>
                className="exam-matrix-distribution-table__table"
                columns={columns}
                dataSource={rows}
                rowKey="key"
                pagination={false}
                bordered
                size="middle"
                tableLayout="fixed"
                scroll={{ x: 540 }}
                summary={() => (
                    <Table.Summary.Row className="exam-matrix-distribution-table__total-row">
                        <Table.Summary.Cell index={0} colSpan={2}>
                            <Text strong>Tổng số câu</Text>
                        </Table.Summary.Cell>
                        <Table.Summary.Cell index={2} align="center">
                            <Text strong>{grandTotal}</Text>
                        </Table.Summary.Cell>
                    </Table.Summary.Row>
                )}
            />
        </div>
    );
}

export default ExamMatrixDistributionTable;
