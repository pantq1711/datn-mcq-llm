import {
    Button,
    Card,
    Col,
    Descriptions,
    Row,
    Space,
    Tag,
    Typography,
} from "antd";
import {
    ArrowLeftOutlined,
    CheckCircleOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import AcceptanceRateChart from "../components/AcceptanceRateChart";
import EditDistanceChart from "../components/EditDistanceChart";
import QualityMetricsChart from "../components/QualityMetricsChart";

import "./ExperimentDetailPage.css";

const { Title, Text, Paragraph } = Typography;

export default function ExperimentDetailPage() {
    const navigate = useNavigate();

    const experiment = {
        id: 1,
        name: "Experiment #1 - So sánh Zero-shot",
        subject: "Toán học",
        modelName: "GPT-4o-mini",
        strategy: "Zero-shot",
        promptVersion: "v1.0",
        questionCount: 100,
        temperature: 0.7,
        status: "COMPLETED" as const,
        startedAt: "01/10/2026 10:30",
        endedAt: "01/10/2026 10:42",
        promptText:
            "Tạo một câu hỏi trắc nghiệm mới dựa trên câu hỏi nguồn, giữ nguyên chủ đề và phạm vi kiến thức, thay đổi nội dung và đảm bảo chỉ có một đáp án đúng.",
    };

    const handleBack = () => {
        navigate("/experiments");
    };

    return (
        <div className="experiment-detail-page">
            <div className="experiment-detail-page-header">
                <div className="experiment-detail-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="experiment-detail-back-button"
                        onClick={handleBack}
                    >
                        Quay lại
                    </Button>
                </div>

                <div className="experiment-detail-page-header-content">
                    <div className="experiment-detail-page-title-section">
                        <Title
                            level={2}
                            className="experiment-detail-page-title"
                        >
                            {experiment.name}
                        </Title>

                        <Space
                            size={8}
                            wrap
                            className="experiment-detail-page-meta"
                        >
                            <Tag color="success">
                                <CheckCircleOutlined />
                                {" "}Hoàn thành
                            </Tag>

                            <Text type="secondary">
                                Bắt đầu: {experiment.startedAt}
                            </Text>

                            <Text type="secondary">
                                Kết thúc: {experiment.endedAt}
                            </Text>
                        </Space>
                    </div>
                </div>
            </div>

            <div className="experiment-detail-page-information">
                <Card title="Thông tin thực nghiệm">
                    <Descriptions
                        bordered
                        column={{
                            xs: 1,
                            sm: 2,
                            lg: 3,
                        }}
                    >
                        <Descriptions.Item label="Mã thực nghiệm">
                            EXP-{experiment.id}
                        </Descriptions.Item>

                        <Descriptions.Item label="Môn học">
                            {experiment.subject}
                        </Descriptions.Item>

                        <Descriptions.Item label="Model">
                            {experiment.modelName}
                        </Descriptions.Item>

                        <Descriptions.Item label="Chiến lược">
                            {experiment.strategy}
                        </Descriptions.Item>

                        <Descriptions.Item label="Prompt version">
                            {experiment.promptVersion}
                        </Descriptions.Item>

                        <Descriptions.Item label="Số câu hỏi">
                            {experiment.questionCount}
                        </Descriptions.Item>

                        <Descriptions.Item label="Temperature">
                            {experiment.temperature}
                        </Descriptions.Item>

                        <Descriptions.Item label="Trạng thái">
                            <Tag color="success">Hoàn thành</Tag>
                        </Descriptions.Item>

                        <Descriptions.Item label="Thời gian">
                            {experiment.startedAt} → {experiment.endedAt}
                        </Descriptions.Item>
                    </Descriptions>
                </Card>
            </div>

            <div className="experiment-detail-page-results">
                <div className="experiment-detail-page-section-header">
                    <Title
                        level={4}
                        className="experiment-detail-page-section-title"
                    >
                        Kết quả thực nghiệm
                    </Title>

                    <Text type="secondary">
                        Các chỉ số đánh giá chất lượng câu hỏi được sinh trong
                        lần thực nghiệm này.
                    </Text>
                </div>

                <Row gutter={[16, 16]}>
                    <Col xs={24} xl={8}>
                        <Card title="Tỷ lệ chấp nhận">
                            <AcceptanceRateChart />
                        </Card>
                    </Col>

                    <Col xs={24} xl={8}>
                        <Card title="Chỉ số chất lượng">
                            <QualityMetricsChart />
                        </Card>
                    </Col>

                    <Col xs={24} xl={8}>
                        <Card title="Khoảng cách chỉnh sửa">
                            <EditDistanceChart />
                        </Card>
                    </Col>
                </Row>
            </div>

            <div className="experiment-detail-page-prompt">
                <Card title="Cấu hình Prompt">
                    <Descriptions column={1} bordered>
                        <Descriptions.Item label="Prompt version">
                            {experiment.promptVersion}
                        </Descriptions.Item>

                        <Descriptions.Item label="Prompt">
                            <Paragraph
                                copyable
                                className="experiment-detail-page-prompt-text"
                            >
                                {experiment.promptText}
                            </Paragraph>
                        </Descriptions.Item>
                    </Descriptions>
                </Card>
            </div>
        </div>
    );
}