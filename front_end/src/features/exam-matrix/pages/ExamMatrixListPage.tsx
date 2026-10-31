import { useState, useEffect } from 'react';
import { Button, Card, Input, Space, Spin, Typography, message } from 'antd';
import { CopyOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

import { ExamMatrixTable } from '../components/ExamMatrixTable';
import { ExamMatrixTemplateModal } from '../components/ExamMatrixTemplateModal';
import type { ExamMatrix } from '../types/examMatrix.types.ts';
import { listExamMatrices, deleteExamMatrix, duplicateExamMatrix, createExamMatrixFromSample } from "../services/examMatrixService";
import './ExamMatrixListPage.css';

const { Title } = Typography;

export default function ExamMatrixListPage() {
    const navigate = useNavigate();
    const [searchText, setSearchText] = useState('');
    const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false);
    const [matrices, setMatrices] = useState<ExamMatrix[]>([]);

    const [loading, setLoading] = useState(false);

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

    const handleDuplicateMatrix = async (matrixId: string) => {
        try {
            const newMatrix = await duplicateExamMatrix(matrixId);
            message.success(`Đã tạo bản sao: ${newMatrix.name}`);
            await fetchMatrices();
        } catch (err: any) {
            message.error(err?.response?.data?.error || 'Không thể nhân bản ma trận');
        }
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
    }, []);         // chạy một lần khi component mount

    return (
        <div className="exam-matrix-list-page">
            <div className="exam-matrix-list-page-header">
                <div>
                    <Title level={2} className="exam-matrix-list-page-title">
                        Quản lý ma trận
                    </Title>
                </div>
                <Space className="exam-matrix-list-page-actions" wrap>
                    <Button icon={<CopyOutlined />} onClick={() => setIsTemplateModalOpen(true)}>
                        Tạo từ mẫu
                    </Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateMatrix}>
                        Tạo ma trận
                    </Button>
                </Space>
            </div>

            <Card className="exam-matrix-filter-card">
                <div className="exam-matrix-filter">
                    <Input
                        className="exam-matrix-search"
                        placeholder="Tìm theo tên ma trận, môn học hoặc khối lớp..."
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
            </Card>

            {loading ? (
                <div className="exam-matrix-list-loading" style={{ textAlign: "center", padding: "80px 0" }}>
                    <Spin size="large" />
                    <p style={{ marginTop: "16px" }}>Đang tải danh sách ma trận…</p>
                </div>
            ) : (
                <Card className="exam-matrix-list-card">
                    <div className="exam-matrix-list-header">
                        <div>
                            <Title level={4} className="exam-matrix-list-title">
                                Danh sách ma trận
                            </Title>
                        </div>
                    </div>

                    <ExamMatrixTable
                        matrices={matrices}
                        searchText={searchText}
                        loading={loading}
                        onEditMatrix={handleEditMatrix}
                        onDeleteMatrix={handleDeleteMatrix}
                        onDuplicateMatrix={handleDuplicateMatrix}
                        onGenerateExam={handleGenerateExam}
                    />
                </Card>
            )}

            <ExamMatrixTemplateModal
                open={isTemplateModalOpen}
                onCancel={() => setIsTemplateModalOpen(false)}
                onCreateFromSample={handleCreateFromSample}
            />
        </div>
    );
}