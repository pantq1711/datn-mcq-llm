import { Alert, Button, Card, Space, Typography, message, Form, Spin } from 'antd';
import { ArrowLeftOutlined, CopyOutlined, SaveOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { useEffect, useState } from 'react';

import { ExamMatrixBasicInfoForm, type ExamMatrixBasicInfoValues, type ExamMatrixSubjectOption } from '../components/ExamMatrixBasicInfoForm';
import { ExamMatrixCellEditor } from '../components/ExamMatrixCellEditor';
import { ExamMatrixTotalSummary } from '../components/ExamMatrixTotalSummary';
import { createExamMatrix, updateExamMatrix, getExamMatrix, duplicateExamMatrix } from '../services/examMatrixService';
import { getCatalogSubjects } from '../../exam/services/examApi';
import type { CreateExamMatrixPayload, ExamMatrixCellInput } from '../types/examMatrix.types';
import { createEmptyCell, normalizeCell, toCellInput, validateCells } from '../../../utils/matrixCell.utils';
import './ExamMatrixFormPage.css';

const { Title, Text } = Typography;

/**
 * Trang tạo/chỉnh sửa ma trận: thông tin chung + danh sách ô (docs/api mục 7.1, 7.1a).
 */
export default function ExamMatrixFormPage() {
    const navigate = useNavigate();
    const { id } = useParams<{ id: string }>();
    const isEditMode = Boolean(id);

    const [basicForm] = Form.useForm<ExamMatrixBasicInfoValues>();
    const [saving, setSaving] = useState(false);
    const [loading, setLoading] = useState(false);
    const [duplicating, setDuplicating] = useState(false);

    const [cells, setCells] = useState<ExamMatrixCellInput[]>(() => [createEmptyCell()]);
    // Ma trận đã có đề: giữ nguyên ô gốc để gửi lại đúng cấu trúc cũ (backend trả 200, không đổi gì)
    const [originalCells, setOriginalCells] = useState<ExamMatrixCellInput[]>([]);
    const [examCount, setExamCount] = useState(0);
    const isLocked = isEditMode && examCount > 0;

    const [subjects, setSubjects] = useState<ExamMatrixSubjectOption[]>([]);
    const [loadingSubjects, setLoadingSubjects] = useState(false);

    const pageTitle = isEditMode ? 'Chỉnh sửa ma trận' : 'Tạo ma trận';

    const handleCancel = () => {
        navigate('/exam-matrices');
    };

    useEffect(() => {
        setLoadingSubjects(true);
        getCatalogSubjects()
            .then((list) => setSubjects(list.map((s) => ({ label: s.name, value: Number(s.id) }))))
            .catch(() => message.error('Không thể tải danh sách môn học'))
            .finally(() => setLoadingSubjects(false));
    }, []);

    // Dựng body đúng CreateMatrixRequest; lỗi thì ném Error với thông báo cho giáo viên
    const buildPayload = async (): Promise<CreateExamMatrixPayload> => {
        const basicValues = await basicForm.validateFields();

        const payloadCells = isLocked ? originalCells : cells;
        if (!isLocked) {
            const cellError = validateCells(payloadCells);
            if (cellError) throw new Error(cellError);
        }

        const notes = (basicValues.notesText ?? '')
            .split('\n')
            .map((line) => line.trim())
            .filter(Boolean);

        return {
            name: basicValues.name.trim(),
            subjectId: Number(basicValues.subjectId),
            gradeLevel: Number(basicValues.gradeLevel),
            source: basicValues.source?.trim() || null,
            notes,
            cells: isLocked ? payloadCells : payloadCells.map(normalizeCell),
        };
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            const payload = await buildPayload();
            if (isEditMode) {
                await updateExamMatrix(id!, payload);
                message.success(`Đã cập nhật ma trận "${payload.name}" thành công!`);
            } else {
                await createExamMatrix(payload);
                message.success(`Đã tạo ma trận "${payload.name}" thành công!`);
            }
            navigate('/exam-matrices');
        } catch (error: any) {
            if (error?.errorFields) {
                message.error("Vui lòng điền đầy đủ các thông tin bắt buộc!");
                return;
            }
            // 400 nêu số ô ("Ô 2: ..."), 409 MATRIX_IN_USE nêu số đề: hiện nguyên thông báo backend
            const errMsg = error?.response?.data?.error || error?.response?.data?.message || error?.message || "Lưu ma trận thất bại!";
            message.error(errMsg);
        } finally {
            setSaving(false);
        }
    };

    // Ma trận đã có đề không đổi cấu trúc được: nhân bản rồi sửa bản sao
    const handleDuplicate = async () => {
        if (!id) return;
        try {
            setDuplicating(true);
            const copy = await duplicateExamMatrix(id);
            message.success(`Đã tạo bản sao "${copy.name}". Bạn có thể sửa cấu trúc ở bản này.`);
            navigate(`/exam-matrices/${copy.id}/edit`);
        } catch (err: any) {
            message.error(err?.response?.data?.error || 'Không thể nhân bản ma trận');
        } finally {
            setDuplicating(false);
        }
    };

    useEffect(() => {
        if (!isEditMode) return;
        setLoading(true);
        (async () => {
            try {
                const data = await getExamMatrix(id!);
                basicForm.setFieldsValue({
                    name: data.name,
                    subjectId: data.subjectId,
                    gradeLevel: data.gradeLevel,
                    source: data.source ?? '',
                    notesText: data.notes?.join('\n') ?? '',
                });
                // Giữ đủ từng ô theo thứ tự (không gộp theo dạng × mức như trước)
                const loadedCells = (data.cells ?? []).map(toCellInput);
                setCells(loadedCells);
                setOriginalCells(loadedCells);
                setExamCount(data.examCount ?? 0);
            } catch (err: any) {
                const msg = err?.response?.data?.error || err?.response?.data?.message || err?.message;
                message.error(msg ?? 'Không thể tải ma trận để sửa. Vui lòng thử lại.');
            } finally {
                setLoading(false);
            }
        })();
    }, [isEditMode, id, basicForm]);

    return (
        <main className="exam-matrix-form-page">
            {loading ? (
                <div className="profile-loading" style={{ textAlign: 'center', padding: '80px 0' }}>
                    <Spin size="large" />
                    <p className="profile-loading-text" style={{ marginTop: '16px' }}>
                        Đang tải ma trận…
                    </p>
                </div>
            ) : (
                <>
                    <header className="exam-matrix-form-page__header">
                        <div className="exam-matrix-form-page__heading">
                            <Button
                                type="text"
                                icon={<ArrowLeftOutlined />}
                                className="exam-matrix-form-page__back-button"
                                onClick={handleCancel}
                            >
                                Quay lại danh sách
                            </Button>
                            <Title level={2} className="exam-matrix-form-page__title">
                                {pageTitle}
                            </Title>
                        </div>

                        <Space className="exam-matrix-form-page__header-actions" wrap>
                            <Button onClick={handleCancel}>Hủy</Button>
                            <Button
                                type="primary"
                                icon={<SaveOutlined />}
                                loading={saving}
                                onClick={handleSave}
                            >
                                Lưu ma trận
                            </Button>
                        </Space>
                    </header>

                    {isLocked && (
                        <Alert
                            type="warning"
                            showIcon
                            message={`Ma trận đã dùng cho ${examCount} đề: chỉ sửa được tên, nguồn, ghi chú.`}
                            description="Muốn đổi môn, khối hoặc các ô, hãy nhân bản ma trận rồi sửa bản sao."
                            action={
                                <Button icon={<CopyOutlined />} loading={duplicating} onClick={handleDuplicate}>
                                    Nhân bản để sửa
                                </Button>
                            }
                        />
                    )}

                    <div className="exam-matrix-form-page__content">
                        <div className="exam-matrix-form-page__main-column">
                            <Card
                                className="exam-matrix-form-page__section-card"
                                title="Thông tin chung"
                                bordered
                            >
                                <ExamMatrixBasicInfoForm
                                    form={basicForm}
                                    subjects={subjects}
                                    loadingSubjects={loadingSubjects}
                                    lockStructure={isLocked}
                                />
                            </Card>

                            <Card
                                className="exam-matrix-form-page__section-card"
                                title={`Các ô của ma trận (${cells.length})`}
                                bordered
                            >
                                <ExamMatrixCellEditor
                                    value={cells}
                                    onChange={setCells}
                                    readOnly={isLocked}
                                />
                            </Card>
                        </div>

                        <aside className="exam-matrix-form-page__sidebar">
                            <Card
                                className="exam-matrix-form-page__summary-card"
                                title="Tổng quan ma trận"
                                bordered
                            >
                                <ExamMatrixTotalSummary value={cells} />
                            </Card>

                            <Card
                                className="exam-matrix-form-page__help-card"
                                title="Lưu ý"
                                bordered
                            >
                                <Text type="secondary">
                                    Mỗi ô là một nhóm câu cùng dạng, cùng mức và cùng chủ đề. Phần I chỉ có trắc
                                    nghiệm, phần II chỉ có đúng/sai, phần III chỉ có trả lời ngắn. Mỗi mức ô dùng
                                    cần có yêu cầu cần đạt.
                                </Text>
                            </Card>
                        </aside>
                    </div>
                </>
            )}
        </main>
    );
}
