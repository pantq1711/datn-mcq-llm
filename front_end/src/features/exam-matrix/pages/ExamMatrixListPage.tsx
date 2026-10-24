import { useState } from 'react';
import { Button, Card, Input, Space, Typography, message } from 'antd';
import { CopyOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

import { ExamMatrixTable } from '../components/ExamMatrixTable';
import { ExamMatrixTemplateModal } from '../components/ExamMatrixTemplateModal';
import { MOCK_EXAM_MATRICES, MOCK_EXAM_MATRIX_TEMPLATES } from '../components/examMatrix.mock.ts';
import type { ExamMatrix } from '../components/ExamMatrixTable';
import './ExamMatrixListPage.css';

const { Title } = Typography;

export default function ExamMatrixListPage() {
    const navigate = useNavigate();
    const [searchText, setSearchText] = useState('');
    const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false);
    const [matrices, setMatrices] = useState<ExamMatrix[]>(MOCK_EXAM_MATRICES);

    const handleCreateMatrix = () => navigate('/exam-matrices/new');
    const handleEditMatrix = (matrixId: string) =>
        navigate(`/exam-matrices/${matrixId}/edit`);

    const handleDeleteMatrix = (matrixId: string) => {
        setMatrices((current) => current.filter((matrix) => matrix.id !== matrixId));
        message.success('Đã xóa ma trận khỏi danh sách mẫu thử.');
    };

    const handleSelectTemplate = (templateId: string) => {
        setIsTemplateModalOpen(false);
        navigate(`/exam-matrices/new?templateId=${encodeURIComponent(templateId)}`);
    };

    const handleReset = () => {
        setSearchText('');
    };

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
                    loading={false}
                    onEditMatrix={handleEditMatrix}
                    onDeleteMatrix={handleDeleteMatrix}
                />
            </Card>

            <ExamMatrixTemplateModal
                open={isTemplateModalOpen}
                onCancel={() => setIsTemplateModalOpen(false)}
                onSelectTemplate={handleSelectTemplate}
                templates={MOCK_EXAM_MATRIX_TEMPLATES}
                loading={false}
            />
        </div>
    );
}