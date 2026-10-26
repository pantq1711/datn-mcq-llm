import { Button, Card, Space, Typography, message } from 'antd';
import {
    ArrowLeftOutlined,
    SaveOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { ExamMatrixBasicInfoForm } from '../components/ExamMatrixBasicInfoForm';
import { ExamMatrixDistributionTable } from '../components/ExamMatrixDistributionTable';
import { ExamMatrixTotalSummary } from '../components/ExamMatrixTotalSummary';
import './ExamMatrixFormPage.css';

const { Title, Text } = Typography;

/**
 * Trang tạo/chỉnh sửa ma trận.
 * Trang này chỉ ghép bố cục và điều phối điều hướng; nội dung của các form con
 * và việc lưu dữ liệu sẽ được triển khai riêng.
 */
export default function ExamMatrixFormPage() {
    const navigate = useNavigate();
    const { id } = useParams<{ id: string }>();
    const [searchParams] = useSearchParams();

    const templateId = searchParams.get('templateId');
    const isEditMode = Boolean(id);
    const isTemplateMode = !isEditMode && Boolean(templateId);

    const pageTitle = isEditMode
        ? 'Chỉnh sửa ma trận'
        : isTemplateMode
            ? 'Tạo ma trận từ mẫu'
            : 'Tạo ma trận';

    const handleCancel = () => {
        navigate('/exam-matrices');
    };

    const handleSave = () => {
        // Chưa kết nối dữ liệu form hoặc API lưu ở bước dựng bố cục.
        message.info('Chức năng lưu sẽ được hoàn thiện sau khi tích hợp các component form và API.');
    };

    return (
        <main className="exam-matrix-form-page">
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
                        <ExamMatrixBasicInfoForm />
                    </Card>

                    <Card
                        className="exam-matrix-form-page__section-card"
                        title="Phân bổ câu hỏi"
                        bordered
                    >
                        <ExamMatrixDistributionTable />
                    </Card>
                </div>

                <aside className="exam-matrix-form-page__sidebar">
                    <Card
                        className="exam-matrix-form-page__summary-card"
                        title="Tổng quan ma trận"
                        bordered
                    >
                        <ExamMatrixTotalSummary />
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
        </main>
    );
}
