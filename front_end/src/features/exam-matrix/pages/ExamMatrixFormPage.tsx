import { Button, Card, Space, Typography, message, Form, Spin } from 'antd';
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useEffect, useState } from 'react';

import { ExamMatrixBasicInfoForm } from '../components/ExamMatrixBasicInfoForm';
import { ExamMatrixDistributionTable, createEmptyExamMatrixCounts, type ExamMatrixCounts, type ExamMatrixQuestionType, type ExamMatrixCognitiveLevel } from '../components/ExamMatrixDistributionTable';
import { ExamMatrixTotalSummary } from '../components/ExamMatrixTotalSummary';
import { createExamMatrix, updateExamMatrix, getExamMatrix, getExamMatrixTemplate } from '../services/examMatrixService';
import './ExamMatrixFormPage.css';

const { Title, Text } = Typography;

/**
 * Trang tạo/chỉnh sửa ma trận.
 */
export default function ExamMatrixFormPage() {
    const navigate = useNavigate();
    const { id } = useParams<{ id: string }>();
    const [searchParams] = useSearchParams();

    const templateId = searchParams.get('templateId');
    const isEditMode = Boolean(id);
    const isTemplateMode = !isEditMode && Boolean(templateId);

    const [basicForm] = Form.useForm();
    const [saving, setSaving] = useState(false);

    // Khởi tạo state đúng chuẩn ExamMatrixCounts thay vì mảng any[]
    const [matrixCounts, setMatrixCounts] = useState<ExamMatrixCounts>(createEmptyExamMatrixCounts);

    const pageTitle = isEditMode
        ? 'Chỉnh sửa ma trận'
        : isTemplateMode
            ? 'Tạo ma trận từ mẫu'
            : 'Tạo ma trận';

    const handleCancel = () => {
        navigate('/exam-matrices');
    };

    // Hàm build payload và validate theo quy tắc backend
    const buildPayload = async () => {
        // 1. Validate và lấy giá trị từ form thông tin chung
        const basicValues = await basicForm.validateFields();

        // Validate tên ma trận (tối đa 255 ký tự, không để trống)
        if (!basicValues.name || basicValues.name.trim() === '') {
            throw new Error('Vui lòng nhập tên ma trận.');
        }
        if (basicValues.name.length > 255) {
            throw new Error('Tên ma trận không được vượt quá 255 ký tự.');
        }

        // Validate môn học (phải là number)
        const subjectIdNum = Number(basicValues.subjectId);
        if (!basicValues.subjectId || isNaN(subjectIdNum)) {
            throw new Error('Vui lòng chọn môn học hợp lệ.');
        }

        // Validate khối lớp (từ 6 đến 9)
        const grade = Number(basicValues.gradeLevel ?? basicValues.grade);
        if (isNaN(grade) || grade < 6 || grade > 9) {
            throw new Error('Khối lớp phải nằm trong khoảng từ 6 đến 9.');
        }

        // 2. Validate phân bổ câu hỏi từ ma trận object (matrixCounts)
        let totalQuestions = 0;
        const parts: any[] = [];
        const cells: any[] = []; // Thêm mảng cells nếu CreateExamMatrixPayload yêu cầu

        const questionTypes: ExamMatrixQuestionType[] = ['MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER'];
        const cognitiveLevels: ExamMatrixCognitiveLevel[] = ['KNOW', 'UNDERSTAND', 'APPLY'];

        // Map dạng câu hỏi UI sang định dạng partType của backend
        const typeMapping: Record<ExamMatrixQuestionType, string> = {
            MULTIPLE_CHOICE: 'mcq',
            TRUE_FALSE: 'tf',
            SHORT_ANSWER: 'sa',
        };

        // Map mức độ nhận thức UI sang difficulty của backend
        const difficultyMapping: Record<ExamMatrixCognitiveLevel, string> = {
            KNOW: 'easy',
            UNDERSTAND: 'medium',
            APPLY: 'hard',
        };

        for (const qType of questionTypes) {
            for (const cLevel of cognitiveLevels) {
                const count = Number(matrixCounts[qType]?.[cLevel] || 0);

                if (count > 0) {
                    if (count > 50) {
                        throw new Error('Số lượng câu hỏi trong mỗi ô phải từ 1 đến 50.');
                    }
                    totalQuestions += count;

                    const partType = typeMapping[qType];
                    const basePart: any = {
                        partType,
                        questionCount: count,
                        learningOutcomes: `Yêu cầu cần đạt cho ${qType} ${cLevel}`,
                    };

                    if (partType === 'tf') {
                        basePart.statementDifficulties = ['easy', 'easy', 'medium', 'hard'];
                    } else {
                        basePart.difficulty = difficultyMapping[cLevel];
                    }

                    parts.push(basePart);
                    cells.push(basePart); // Đồng bộ dữ liệu vào cells nếu type yêu cầu
                }
            }
        }

        if (totalQuestions === 0) {
            throw new Error('Vui lòng cấu hình ít nhất một ô phân bổ câu hỏi có số lượng lớn hơn 0.');
        }

        return {
            name: basicValues.name.trim(),
            subjectId: subjectIdNum,
            gradeLevel: grade,
            description: basicValues.description || '',
            parts,
            cells, // Bổ sung thuộc tính cells để khớp hoàn toàn với CreateExamMatrixPayload
        };
    };

    // Xử lý sự kiện Lưu
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
            const errMsg = error?.response?.data?.error || error?.response?.data?.message || error?.message || "Tạo ma trận thất bại!";
            message.error(errMsg);
        } finally {
            setSaving(false);
        }
    };

    useEffect(() => {
        if (!isEditMode) return;
        setSaving(true); // Dùng chung state "saving" để hiển thị spinner
        (async () => {
            try {
                const data = await getExamMatrix(id!);
                // Điền dữ liệu vào form chung
                basicForm.setFieldsValue({
                    name: data.name,
                    subjectId: data.subjectId,
                    gradeLevel: data.gradeLevel,
                    description: data.notes?.join('\n') ?? '',
                });
                // Điền matrixCounts (các ô) – chuyển từ cells sang cấu trúc UI
                const initCounts = createEmptyExamMatrixCounts();
                data.cells.forEach(cell => {
                    const { questionType, difficulty, questionCount } = cell;

                    const qType = (
                        questionType === 'mcq' ? 'MULTIPLE_CHOICE' :
                            questionType === 'tf' ? 'TRUE_FALSE' : 'SHORT_ANSWER'
                    ) as ExamMatrixQuestionType;

                    // Dạng 'tf' không dùng difficulty riêng thì mặc định gán 'KNOW' hoặc xử lý tùy ý
                    const cLevel = (
                        questionType === 'tf'
                            ? 'KNOW'
                            : (difficulty?.toUpperCase() as ExamMatrixCognitiveLevel) || 'KNOW'
                    );

                    // Gán trực tiếp vì initCounts[qType] đã được khởi tạo sẵn từ createEmptyExamMatrixCounts()
                    if (initCounts[qType] && cLevel) {
                        initCounts[qType][cLevel] = questionCount;
                    }
                });
                setMatrixCounts(initCounts);
            } catch (err: any) {
                const msg = err?.response?.data?.error || err?.response?.data?.message || err?.message;
                message.error(msg ?? 'Không thể tải ma trận để sửa. Vui lòng thử lại.');
            } finally {
                setSaving(false);
            }
        })();
    }, [isEditMode, id, basicForm]);

    useEffect(() => {
        if (!isTemplateMode) return;
        setSaving(true);                    // reuse same spinner state
        (async () => {
            try {
                const tmpl = await getExamMatrixTemplate(templateId!);
                // ---- Basic form -------------------------------------------------
                basicForm.setFieldsValue({
                    name: tmpl.name ?? '',
                    subjectId: tmpl.subjectId ?? '',
                    gradeLevel: tmpl.gradeLevel ?? '',
                    description: tmpl.description ?? '',
                });

                // ---- matrixCounts ------------------------------------------------
                const initCounts = createEmptyExamMatrixCounts();
                tmpl.cells?.forEach(cell => {
                    const { questionType, difficulty, questionCount } = cell;
                    const qType = (
                        questionType === 'mcq' ? 'MULTIPLE_CHOICE' :
                            questionType === 'tf' ? 'TRUE_FALSE' : 'SHORT_ANSWER'
                    ) as ExamMatrixQuestionType;
                    const cLevel = (
                        questionType === 'tf'
                            ? 'KNOW'
                            : (difficulty?.toUpperCase() as ExamMatrixCognitiveLevel) || 'KNOW'
                    );
                    if (initCounts[qType] && cLevel) {
                        initCounts[qType][cLevel] = questionCount;
                    }
                });
                setMatrixCounts(initCounts);
            } catch (err: any) {
                const msg = err?.response?.data?.error || err?.response?.data?.message || err?.message;
                message.error(msg ?? 'Không thể tải mẫu ma trận. Vui lòng thử lại.');
            } finally {
                setSaving(false);
            }
        })();
    }, [isTemplateMode, templateId, basicForm]);

    return (
        <main className="exam-matrix-form-page">
            {(isEditMode || isTemplateMode) && saving ? (
                <div className="profile-loading" style={{ textAlign: 'center', padding: '80px 0' }}>
                    <Spin size="large" />
                    <p className="profile-loading-text" style={{ marginTop: '16px' }}>
                        {isEditMode ? 'Đang tải ma trận…' : 'Đang tải mẫu ma trận…'}
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

                    <div className="exam-matrix-form-page__content">
                        <div className="exam-matrix-form-page__main-column">
                            <Card
                                className="exam-matrix-form-page__section-card"
                                title="Thông tin chung"
                                bordered
                            >
                                <ExamMatrixBasicInfoForm form={basicForm} />
                            </Card>

                            <Card
                                className="exam-matrix-form-page__section-card"
                                title="Phân bổ câu hỏi"
                                bordered
                            >
                                <ExamMatrixDistributionTable
                                    value={matrixCounts}
                                    onChange={setMatrixCounts}
                                />
                            </Card>
                        </div>

                        <aside className="exam-matrix-form-page__sidebar">
                            <Card
                                className="exam-matrix-form-page__summary-card"
                                title="Tổng quan ma trận"
                                bordered
                            >
                                <ExamMatrixTotalSummary value={matrixCounts} />
                            </Card>

                            <Card
                                className="exam-matrix-form-page__help-card"
                                title="Lưu ý"
                                bordered
                            >
                                <Text type="secondary">
                                    Kiểm tra tổng số câu và số câu ở từng dạng trước khi lưu ma trận.
                                    Khi tạo từ mẫu, bạn có thể điều chỉnh lại thông tin và phân bổ cho
                                    phù hợp với đề kiểm tra cần xây dựng.
                                </Text>
                            </Card>
                        </aside>
                    </div>
                </>
            )}
        </main>
    );
}