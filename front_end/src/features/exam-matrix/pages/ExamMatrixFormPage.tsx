import { Alert, Button, Card, Modal, Space, Typography, message, Form, Spin } from 'antd';
import { ArrowLeftOutlined, CopyOutlined, SaveOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { useCallback, useEffect, useRef, useState } from 'react';

import { ExamMatrixBasicInfoForm, type ExamMatrixBasicInfoValues, type ExamMatrixSubjectOption } from '../components/ExamMatrixBasicInfoForm';
import { ExamMatrixCellEditor } from '../components/ExamMatrixCellEditor';
import { ExamMatrixTotalSummary } from '../components/ExamMatrixTotalSummary';
import { createExamMatrix, updateExamMatrix, getExamMatrix, duplicateExamMatrix } from '../services/examMatrixService';
import { getCatalogSubjects } from '../../exam/services/examApi';
import type { CreateExamMatrixPayload, ExamMatrixCellInput } from '../types/examMatrix.types';
import { createEmptyCell, findCellError, matrixCellAnchorId, normalizeCell, summarizeCells, toCellInput } from '../../../utils/matrixCell.utils';
import { useUnsavedChangesBlocker } from '../../../hooks/useUnsavedChangesBlocker';
import './ExamMatrixFormPage.css';

const { Title, Text, Paragraph } = Typography;

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

    const [cells, setCellsState] = useState<ExamMatrixCellInput[]>(() => [createEmptyCell()]);
    // Ô lỗi lần bấm Lưu gần nhất (rà soát D9): thẻ ô đó viền đỏ kèm lý do; sửa bất kỳ ô nào thì bỏ đánh dấu
    const [cellError, setCellError] = useState<{ index: number; reason: string } | null>(null);
    // Danh sách ô mới nhất cho phần hỏi lại khi rời trang (D8); cập nhật cùng lúc đổi state, không gán trong lúc render
    const cellsRef = useRef(cells);
    const setCells = (next: ExamMatrixCellInput[]) => {
        cellsRef.current = next;
        setCellsState(next);
        setCellError(null);
    };
    // Ma trận đã có đề: giữ nguyên ô gốc để gửi lại đúng cấu trúc cũ (backend trả 200, không đổi gì)
    const [originalCells, setOriginalCells] = useState<ExamMatrixCellInput[]>([]);
    const [examCount, setExamCount] = useState(0);
    const isLocked = isEditMode && examCount > 0;
    // Ma trận lưu trước khi bắt buộc khối lớp có thể chưa có khối: cho bổ sung (kể cả khi đã có đề)
    const [missingGrade, setMissingGrade] = useState(false);

    const [subjects, setSubjects] = useState<ExamMatrixSubjectOption[]>([]);
    const [loadingSubjects, setLoadingSubjects] = useState(false);

    const pageTitle = isEditMode ? 'Chỉnh sửa ma trận' : 'Tạo ma trận';
    // Tổng số câu theo Phần cho thanh Lưu dưới đáy (cùng cách tính với cột Tổng quan)
    const totals = summarizeCells(cells);

    // ===== Thay đổi chưa lưu (rà soát D8) =====
    // Ô: so với bản chụp lúc mở trang (ô trống khi tạo mới, ô vừa nạp khi sửa); thông tin chung: ô giáo viên đã tự sửa
    // (isFieldsTouched bỏ qua giá trị trang tự điền như môn Toán, ma trận nạp để sửa). Đọc qua ref lúc cần hỏi.
    const cellsSnapshotRef = useRef(JSON.stringify(cells));
    // Lưu xong thì không hỏi nữa (đang chuyển về danh sách)
    const savedRef = useRef(false);
    const hasUnsavedChanges = useCallback(
        () => !savedRef.current
            && (basicForm.isFieldsTouched() || JSON.stringify(cellsRef.current) !== cellsSnapshotRef.current),
        [basicForm]
    );

    // Đóng tab hoặc tải lại trang khi còn thay đổi chưa lưu: trình duyệt hỏi lại
    useEffect(() => {
        const warn = (e: BeforeUnloadEvent) => {
            if (!hasUnsavedChanges()) return;
            e.preventDefault();
            e.returnValue = '';
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [hasUnsavedChanges]);

    // Mọi lần rời trang trong ứng dụng (nút Hủy / Quay lại, menu bên trái, nút Back) đi qua một chỗ: còn thay đổi chưa lưu
    // thì chặn lại và hiện hộp "Ma trận chưa lưu"; chọn rời thì đi tiếp tới đúng nơi định tới
    const leaveBlocker = useUnsavedChangesBlocker(hasUnsavedChanges);

    const handleCancel = () => {
        navigate('/exam-matrices');
    };

    const leaveWithoutSaving = () => {
        savedRef.current = true;
        leaveBlocker.leave();
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
            const found = findCellError(payloadCells);
            if (found) {
                // Đánh dấu thẻ ô lỗi và cuộn thẻ ra giữa màn hình (không bị thanh đầu trang, thanh Lưu dưới đáy che)
                if (found.index !== null) {
                    const index = found.index;
                    setCellError({ index, reason: found.reason });
                    window.setTimeout(() => {
                        document.getElementById(matrixCellAnchorId(index))
                            ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    }, 0);
                }
                throw new Error(found.message);
            }
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
            savedRef.current = true;
            // "Lưu rồi rời trang": đi tiếp tới nơi đang định tới (menu, Back…); bấm Lưu thường thì về danh sách
            if (leaveBlocker.blocked) {
                leaveBlocker.leave();
            } else {
                navigate('/exam-matrices');
            }
        } catch (error: any) {
            // "Lưu rồi rời trang" mà còn lỗi: đóng hộp để thấy ô lỗi (đã đánh dấu và cuộn tới), ở lại trang
            if (leaveBlocker.blocked) leaveBlocker.stay();
            if (error?.errorFields) {
                // Trang tự gọi validateFields (không qua onFinish) nên scrollToFirstError của Form không chạy: tự cuộn ô
                // lỗi đầu tiên ra giữa màn hình và đặt con trỏ vào đó (rà soát D9)
                const firstField = error.errorFields[0]?.name;
                if (firstField) {
                    basicForm.scrollToField(firstField, { behavior: 'smooth', block: 'center', focus: true });
                }
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
            message.success(`Đã sao chép thành "${copy.name}". Thầy/cô sửa cấu trúc ở bản này.`);
            navigate(`/exam-matrices/${copy.id}/edit`);
        } catch (err: any) {
            message.error(err?.response?.data?.error || 'Không sao chép được ma trận');
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
                    // null thì để undefined cho ô hiện "Chọn khối lớp"
                    gradeLevel: data.gradeLevel ?? undefined,
                    source: data.source ?? '',
                    notesText: data.notes?.join('\n') ?? '',
                });
                // Giữ đủ từng ô theo thứ tự (không gộp theo dạng × mức như trước)
                const loadedCells = (data.cells ?? []).map(toCellInput);
                setCellsState(loadedCells);
                // Bản chụp để biết có sửa ô hay chưa (rà soát D8)
                cellsRef.current = loadedCells;
                cellsSnapshotRef.current = JSON.stringify(loadedCells);
                setOriginalCells(loadedCells);
                setExamCount(data.examCount ?? 0);
                setMissingGrade(data.gradeLevel == null);
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
                    {/* Bố cục đầu trang như trang Tạo đề thi: nút quay lại một hàng riêng, dưới là tiêu đề + mô tả, nút lưu bên phải */}
                    <div className="exam-matrix-form-page__header-top">
                        <Button
                            type="text"
                            icon={<ArrowLeftOutlined />}
                            className="exam-matrix-form-page__back-button"
                            onClick={handleCancel}
                        >
                            Quay lại danh sách
                        </Button>
                    </div>

                    <header className="exam-matrix-form-page__header">
                        <div className="exam-matrix-form-page__heading">
                            <Title level={2} className="exam-matrix-form-page__title">
                                {pageTitle}
                            </Title>
                            <Paragraph type="secondary" className="exam-matrix-form-page__subtitle">
                                Khai báo môn, lớp và các ô (dạng câu, mức độ, chủ đề, số câu) để dùng khi tạo đề theo ma trận.
                            </Paragraph>
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

                    {missingGrade && (
                        <Alert
                            type="info"
                            showIcon
                            message="Ma trận này chưa có lớp. Thầy/cô chọn lớp trước khi lưu."
                        />
                    )}

                    {isLocked && (
                        <Alert
                            type="warning"
                            showIcon
                            message={
                                missingGrade
                                    ? `Ma trận đã dùng cho ${examCount} đề: chỉ sửa được tên, nguồn, ghi chú và bổ sung lớp.`
                                    : `Ma trận đã dùng cho ${examCount} đề: chỉ sửa được tên, nguồn, ghi chú.`
                            }
                            description="Muốn đổi môn, lớp hoặc các ô, thầy/cô sao chép ma trận rồi sửa bản sao chép."
                            action={
                                <Button icon={<CopyOutlined />} loading={duplicating} onClick={handleDuplicate}>
                                    Sao chép để sửa
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
                                    allowGradeFill={missingGrade}
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
                                    errorIndex={cellError?.index ?? null}
                                    errorReason={cellError?.reason}
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

                    {/* Thanh dính đáy màn hình (rà soát D5): tổng số câu theo Phần và nút Lưu luôn thấy khi sửa ô ở giữa form */}
                    <div className="exam-matrix-form-page__save-bar">
                        <Text strong className="exam-matrix-form-page__save-bar-total">
                            Tổng {totals.totalQuestions} câu ({totals.questionsByPart.I} · {totals.questionsByPart.II} ·{' '}
                            {totals.questionsByPart.III})
                        </Text>
                        <Space wrap className="exam-matrix-form-page__save-bar-actions">
                            <Button onClick={handleCancel}>Hủy</Button>
                            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
                                Lưu ma trận
                            </Button>
                        </Space>
                    </div>

                    {/* Rời trang khi còn thay đổi chưa lưu (rà soát D8): ba lựa chọn, nút an toàn "Ở lại" được chọn sẵn */}
                    <Modal
                        open={leaveBlocker.blocked}
                        title="Ma trận chưa lưu"
                        onCancel={leaveBlocker.stay}
                        footer={[
                            <Button key="stay" autoFocus onClick={leaveBlocker.stay}>
                                Ở lại
                            </Button>,
                            <Button key="discard" danger onClick={leaveWithoutSaving}>
                                Rời trang, bỏ thay đổi
                            </Button>,
                            <Button key="save" type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
                                Lưu rồi rời trang
                            </Button>,
                        ]}
                    >
                        Thầy/cô có thay đổi chưa lưu. Rời trang thì các thay đổi này sẽ mất.
                    </Modal>
                </>
            )}
        </main>
    );
}
