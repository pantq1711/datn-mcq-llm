import { Button, Card, Col, Empty, Input, InputNumber, Popconfirm, Row, Select, Space, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';

import type { ExamMatrixCellInput, MatrixDifficulty, MatrixQuestionType } from '../types/examMatrix.types';
import { MATRIX_LEVELS, MATRIX_LEVEL_LABELS, MATRIX_TYPE_LABELS, createEmptyCell, levelsUsed, withType } from '../../../utils/matrixCell.utils';
import './ExamMatrixCellEditor.css';

const { Text } = Typography;
const { TextArea } = Input;

const TYPE_OPTIONS = (Object.keys(MATRIX_TYPE_LABELS) as MatrixQuestionType[]).map((value) => ({
    value,
    label: MATRIX_TYPE_LABELS[value],
}));

const LEVEL_OPTIONS = MATRIX_LEVELS.map((value) => ({ value, label: MATRIX_LEVEL_LABELS[value] }));

export interface ExamMatrixCellEditorProps {
    value: ExamMatrixCellInput[];
    onChange: (cells: ExamMatrixCellInput[]) => void;
    /** Ma trận đã có đề: chỉ xem, không sửa ô (docs/api mục 7.1a). */
    readOnly?: boolean;
}

/**
 * Sửa ma trận theo từng ô (mỗi ô một Card): dạng câu, mức, số câu, chủ đề, nội dung, yêu cầu cần đạt theo mức.
 * Thứ tự Card là thứ tự ô gửi lên backend.
 */
export function ExamMatrixCellEditor({ value, onChange, readOnly = false }: ExamMatrixCellEditorProps) {
    const updateCell = (index: number, next: ExamMatrixCellInput) => {
        onChange(value.map((cell, i) => (i === index ? next : cell)));
    };

    const removeCell = (index: number) => {
        onChange(value.filter((_, i) => i !== index));
    };

    const addCell = () => {
        // Ô mới lấy dạng câu của ô cuối để nhập liên tiếp nhanh hơn
        onChange([...value, createEmptyCell(value[value.length - 1]?.questionType ?? 'mcq')]);
    };

    const setStatementLevel = (index: number, cell: ExamMatrixCellInput, statement: number, level: MatrixDifficulty) => {
        const levels = [...(cell.statementDifficulties ?? [])];
        levels[statement] = level;
        updateCell(index, { ...cell, statementDifficulties: levels });
    };

    return (
        <div className="exam-matrix-cell-editor">
            {value.length === 0 && (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có ô nào. Bấm “Thêm ô” để bắt đầu." />
            )}

            {value.map((cell, index) => (
                <Card
                    key={index}
                    size="small"
                    className="exam-matrix-cell-editor__card"
                    title={
                        <Space size={8}>
                            <Text strong>Ô {index + 1}</Text>
                            <Text type="secondary">Phần {cell.part}</Text>
                        </Space>
                    }
                    extra={
                        !readOnly && (
                            <Popconfirm
                                title={`Xóa ô ${index + 1}?`}
                                okText="Xóa"
                                cancelText="Hủy"
                                okButtonProps={{ danger: true }}
                                onConfirm={() => removeCell(index)}
                            >
                                <Button type="text" danger icon={<DeleteOutlined />} />
                            </Popconfirm>
                        )
                    }
                >
                    <Row gutter={[12, 12]}>
                        <Col xs={24} md={8}>
                            <Text className="exam-matrix-cell-editor__label">Dạng câu</Text>
                            <Select
                                value={cell.questionType}
                                options={TYPE_OPTIONS}
                                disabled={readOnly}
                                onChange={(type) => updateCell(index, withType(cell, type))}
                                style={{ width: '100%' }}
                            />
                        </Col>

                        <Col xs={24} md={11}>
                            <Text className="exam-matrix-cell-editor__label">
                                {cell.questionType === 'tf' ? 'Mức từng ý (a–d)' : 'Mức độ'}
                            </Text>
                            {cell.questionType === 'tf' ? (
                                <Row gutter={6}>
                                    {[0, 1, 2, 3].map((statement) => (
                                        <Col span={6} key={statement}>
                                            <Select
                                                value={cell.statementDifficulties?.[statement]}
                                                options={LEVEL_OPTIONS}
                                                disabled={readOnly}
                                                placeholder={`Ý ${String.fromCharCode(97 + statement)}`}
                                                onChange={(level) => setStatementLevel(index, cell, statement, level)}
                                                style={{ width: '100%' }}
                                            />
                                        </Col>
                                    ))}
                                </Row>
                            ) : (
                                <Select
                                    value={cell.difficulty}
                                    options={LEVEL_OPTIONS}
                                    disabled={readOnly}
                                    onChange={(difficulty) => updateCell(index, { ...cell, difficulty })}
                                    style={{ width: '100%' }}
                                />
                            )}
                        </Col>

                        <Col xs={24} md={5}>
                            <Text className="exam-matrix-cell-editor__label">Số câu</Text>
                            <InputNumber
                                min={1}
                                max={50}
                                precision={0}
                                value={cell.questionCount}
                                disabled={readOnly}
                                onChange={(count) => updateCell(index, { ...cell, questionCount: count ?? 0 })}
                                style={{ width: '100%' }}
                            />
                        </Col>

                        <Col xs={24} md={12}>
                            <Text className="exam-matrix-cell-editor__label">Chủ đề (tên bài)</Text>
                            <Input
                                value={cell.topic}
                                disabled={readOnly}
                                placeholder="Ví dụ: Đa thức"
                                status={!readOnly && !cell.topic.trim() ? 'warning' : undefined}
                                onChange={(event) => updateCell(index, { ...cell, topic: event.target.value })}
                            />
                        </Col>

                        <Col xs={24} md={12}>
                            <Text className="exam-matrix-cell-editor__label">Nội dung (không bắt buộc)</Text>
                            <Input
                                value={cell.content ?? ''}
                                disabled={readOnly}
                                placeholder="Ví dụ: Đơn thức, đa thức"
                                onChange={(event) => updateCell(index, { ...cell, content: event.target.value })}
                            />
                        </Col>

                        {/* Chỉ mức ô dùng mới cần yêu cầu cần đạt (backend bắt buộc đủ các mức này) */}
                        {levelsUsed(cell).map((level) => (
                            <Col xs={24} key={level}>
                                <Text className="exam-matrix-cell-editor__label">
                                    Yêu cầu cần đạt – {MATRIX_LEVEL_LABELS[level]}
                                </Text>
                                <TextArea
                                    value={cell.learningOutcomes[level] ?? ''}
                                    disabled={readOnly}
                                    autoSize={{ minRows: 1, maxRows: 4 }}
                                    status={!readOnly && !cell.learningOutcomes[level]?.trim() ? 'warning' : undefined}
                                    onChange={(event) =>
                                        updateCell(index, {
                                            ...cell,
                                            learningOutcomes: { ...cell.learningOutcomes, [level]: event.target.value },
                                        })
                                    }
                                />
                            </Col>
                        ))}
                    </Row>
                </Card>
            ))}

            {!readOnly && (
                <Button type="dashed" icon={<PlusOutlined />} onClick={addCell} block>
                    Thêm ô
                </Button>
            )}
        </div>
    );
}

export default ExamMatrixCellEditor;
