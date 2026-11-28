import { useState, useEffect, useMemo } from 'react';
import { Button, Card, Input, Space, Spin, Typography, message } from 'antd';
import { CopyOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

import { ExamMatrixTable } from '../components/ExamMatrixTable';
import { ExamMatrixTemplateModal } from '../components/ExamMatrixTemplateModal';
import { DuplicateMatrixModal, type DuplicateMatrixSource } from '../components/DuplicateMatrixModal';
import type { ExamMatrixDetail, ExamMatrixSummary } from '../types/examMatrix.types.ts';
import { listExamMatrices, deleteExamMatrix, createExamMatrixFromSample } from "../services/examMatrixService";
import { getCatalogSubjects } from '../../exam/services/examApi';
import { filterMatrixRows, toMatrixRows } from '../utils/matrixSearch';
import './ExamMatrixListPage.css';

const { Title, Text } = Typography;

export default function ExamMatrixListPage() {
    const navigate = useNavigate();
    const [searchText, setSearchText] = useState('');
    const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false);
    const [matrices, setMatrices] = useState<ExamMatrixSummary[]>([]);
    // Tên môn theo id, tải một lần từ /api/catalog/subjects
    const [subjectNames, setSubjectNames] = useState<Record<number, string>>({});

    const [loading, setLoading] = useState(false);

    // Số ma trận trên tiêu đề card: cùng phép lọc với bảng (tên, môn, lớp)
    const matchedCount = useMemo(
        () => filterMatrixRows(toMatrixRows(matrices, subjectNames), searchText).length,
        [matrices, subjectNames, searchText]
    );

    const handleCreateMatrix = () => navigate('/exam-matrices/new');
    const handleEditMatrix = (matrixId: string) =>
        navigate(`/exam-matrices/${matrixId}/edit`);

    // Tải danh sách ma trận (dùng lại khi cần làm mới)
    const fetchMatrices = async () => {
        setLoading(true);
        try {
            const data = await listExamMatrices();
            setMatrices(data);
        } catch (err: any) {
            const msg =
                err?.response?.data?.error ||
                err?.message ||
                "Không thể tải danh sách ma trận.";
            message.error(msg);
        } finally {
            setLoading(false);
        }
    };

    const handleDeleteMatrix = async (matrixId: string) => {
        setLoading(true);
        try {
            await deleteExamMatrix(matrixId);
            // Cập nhật state sau khi xóa thành công
            setMatrices((curr) => curr.filter((m) => m.id !== matrixId));
            message.success("Đã xóa ma trận thành công.");
        } catch (err: any) {
            const msg =
                err?.response?.data?.error ||
                err?.message ||
                "Xóa ma trận thất bại.";
            message.error(msg);
        } finally {
            setLoading(false);
        }
    };

    // Đợt 3 (D2): sao chép hỏi tên trước (điền sẵn "<tên gốc> – DD/MM")
    const [duplicateSource, setDuplicateSource] = useState<DuplicateMatrixSource | null>(null);

    const handleDuplicateMatrix = (matrixId: string) => {
        const source = matrices.find((m) => String(m.id) === String(matrixId));
        if (source) setDuplicateSource({ id: source.id, name: source.name });
    };

    const handleDuplicated = async (newMatrix: ExamMatrixDetail) => {
        setDuplicateSource(null);
        message.success(`Đã sao chép ma trận: ${newMatrix.name}`);
        await fetchMatrices();
    };

    const handleCreateFromSample = async (key: string) => {
        try {
            const newMatrix = await createExamMatrixFromSample(key);
            message.success(`Đã tạo ma trận: ${newMatrix.name}`);
            setIsTemplateModalOpen(false)
            handleEditMatrix(String(newMatrix.id))
        } catch (err: any) {
            message.error(err?.response?.data?.error || 'Không thể tạo ma trận từ mẫu');
        }
    };

    const handleGenerateExam = (matrixId: string) =>
        navigate(`/exams/generate?tab=by-matrix&matrixId=${encodeURIComponent(matrixId)}`);

    const handleReset = () => {
        setSearchText('');
    };

    useEffect(() => {
        fetchMatrices();
        getCatalogSubjects()
            .then((subjects) =>
                setSubjectNames(Object.fromEntries(subjects.map((s) => [Number(s.id), s.name])))
            )
            // Không có tên môn thì bảng hiện "Môn #id", không chặn danh sách
            .catch((err) => console.warn("Không tải được danh sách môn học", err));
    }, []);         // chạy một lần khi component mount

    return (
        <div className="exam-matrix-list-page">
            <div className="exam-matrix-list-page-header">
                <div>
                    <Title level={2} className="exam-matrix-list-page-title">
                        <TableOutlined style={{ marginRight: 10, color: '#1677ff' }} />
                        Quản lý ma trận
                    </Title>
                </div>
                {/* Ô tìm kiếm nằm trên header, ngay bên trái các nút tạo ma trận (bỏ card tìm kiếm riêng) */}
                <div className="exam-matrix-list-page-actions">
                    <div className="exam-matrix-filter">
                        <Input
                            className="exam-matrix-search"
                            placeholder="Tìm theo tên ma trận, môn học hoặc lớp..."
                            prefix={<SearchOutlined />}
                            allowClear
                            value={searchText}
                            onChange={(event) => setSearchText(event.target.value)}
                            aria-label="Tìm kiếm ma trận"
                        />

                        <Button icon={<ReloadOutlined />} onClick={handleReset}>
                            Đặt lại
                        </Button>
                    </div>

                    <Space className="exam-matrix-create-buttons" wrap>
                        <Button icon={<CopyOutlined />} onClick={() => setIsTemplateModalOpen(true)}>
                            Tạo từ mẫu
                        </Button>
                        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateMatrix}>
                            Tạo ma trận
                        </Button>
                    </Space>
                </div>
            </div>

            {loading ? (
                <div className="exam-matrix-list-loading" style={{ textAlign: "center", padding: "80px 0" }}>
                    <Spin size="large" />
                    <p style={{ marginTop: "16px" }}>Đang tải danh sách ma trận…</p>
                </div>
            ) : (
                <Card className="exam-matrix-list-card">
                    <div className="exam-matrix-list-header">
                        <Title level={4} className="exam-matrix-list-title">
                            Danh sách ma trận{" "}
                            {/* Số ma trận (đang tìm thì là số tìm thấy), cùng phép lọc với bảng */}
                            <Text type="secondary" className="exam-matrix-list-count">
                                {searchText.trim()
                                    ? `(tìm thấy ${matchedCount} ma trận)`
                                    : `(${matchedCount} ma trận)`}
                            </Text>
                        </Title>
                    </div>

                    <ExamMatrixTable
                        matrices={matrices}
                        subjectNames={subjectNames}
                        searchText={searchText}
                        loading={loading}
                        onEditMatrix={handleEditMatrix}
                        onDeleteMatrix={handleDeleteMatrix}
                        onDuplicateMatrix={handleDuplicateMatrix}
                        onGenerateExam={handleGenerateExam}
                        onCreateFromTemplate={() => setIsTemplateModalOpen(true)}
                    />
                </Card>
            )}

            <ExamMatrixTemplateModal
                open={isTemplateModalOpen}
                onCancel={() => setIsTemplateModalOpen(false)}
                onCreateFromSample={handleCreateFromSample}
            />

            <DuplicateMatrixModal
                source={duplicateSource}
                onCancel={() => setDuplicateSource(null)}
                onDuplicated={handleDuplicated}
            />
        </div>
    );
}