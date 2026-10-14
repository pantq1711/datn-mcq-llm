import { Button, Card, Col, Collapse, Descriptions, Row, Space, Tag, Typography, } from "antd";
import { ArrowLeftOutlined, CheckCircleOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import AcceptanceRateChart from "../components/AcceptanceRateChart";
import EditDistanceChart from "../components/EditDistanceChart";
import QualityMetricsChart from "../components/QualityMetricsChart";
import TextSegmentSelector from "../../question-generation/components/TextSegmentSelector";
import type { ExperimentRun } from "../types/experiment.types";

import "./ExperimentDetailPage.css";

const { Title, Text, Paragraph } = Typography;

const sourceContent =
    "Hàm số bậc hai có dạng y = ax² + bx + c, trong đó a, b, c là các hằng số và a khác 0. " +
    "Đồ thị của hàm số bậc hai là một đường parabol. " +
    "Trục đối xứng của parabol có phương trình x = -b/(2a). " +
    "Tọa độ đỉnh của parabol được xác định dựa trên trục đối xứng và giá trị của hàm số tại hoành độ đỉnh.";

const selectedSegmentTexts = [
    "Hàm số bậc hai có dạng y = ax² + bx + c, trong đó a, b, c là các hằng số và a khác 0.",
    "Trục đối xứng của parabol có phương trình x = -b/(2a). Tọa độ đỉnh của parabol được xác định dựa trên trục đối xứng và giá trị của hàm số tại hoành độ đỉnh.",
];

// Tính offset từ chính nội dung nguồn để các vùng highlight không lệch vị trí.
const selectedSegments = selectedSegmentTexts.map((text, index) => {
    const startOffset = sourceContent.indexOf(text);
    return {
        id: `seg-1-${index + 1}`,
        text,
        startOffset,
        endOffset: startOffset + text.length,
    };
});

const experiment: ExperimentRun & { name: string } = {
    id: 1,
    name: "Experiment #1 - So sánh Zero-shot",
    subject: "Toán học",
    modelName: "GPT-4o-mini",
    strategy: "Zero-shot",
    promptVersion: "v1.0",
    questionCount: 100,
    temperature: 0.7,
    status: "COMPLETED",
    startedAt: "01/10/2026 10:30",
    endedAt: "01/10/2026 10:42",
    promptText:
        "Tạo câu hỏi trắc nghiệm 4 phương án dựa trên nội dung bài học và các đoạn được chọn. " +
        "Bảo đảm chỉ có một phương án đúng, các phương án nhiễu hợp lý và không trùng lặp.",
    contentSource: {
        title: "Bài 3 - Hàm số bậc hai",
        lessonId: 101,
        fullContent: sourceContent,
        contentHash: "mock-hash-math-001",
    },
    selectedSegments,
};

export default function ExperimentDetailPage() {
    const navigate = useNavigate();

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
                        <Title level={2} className="experiment-detail-page-title">
                            {experiment.name}
                        </Title>
                        <Space size={8} wrap className="experiment-detail-page-meta">
                            <Tag color="success">
                                <CheckCircleOutlined /> Hoàn thành
                            </Tag>
                            <Text type="secondary">Bắt đầu: {experiment.startedAt}</Text>
                            <Text type="secondary">Kết thúc: {experiment.endedAt}</Text>
                        </Space>
                    </div>
                </div>
            </div>

            <div className="experiment-detail-page-information">
                <Card title="Thông tin thực nghiệm">
                    <Descriptions
                        bordered
                        column={{ xs: 1, sm: 2, lg: 3 }}
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
                        <Descriptions.Item label="Nguồn nội dung">
                            {experiment.contentSource.title}
                        </Descriptions.Item>
                        <Descriptions.Item label="Số đoạn được chọn">
                            {experiment.selectedSegments.length}
                        </Descriptions.Item>
                    </Descriptions>
                </Card>
            </div>

            <div className="experiment-detail-page-source">
                <Card title="Nguồn nội dung">
                    <Descriptions size="small" column={{ xs: 1, sm: 2 }}>
                        <Descriptions.Item label="Tên nguồn">
                            {experiment.contentSource.title}
                        </Descriptions.Item>
                        {experiment.contentSource.lessonId !== undefined && (
                            <Descriptions.Item label="Mã bài học">
                                {experiment.contentSource.lessonId}
                            </Descriptions.Item>
                        )}
                        <Descriptions.Item label="Content hash" span={2}>
                            <Text copyable>{experiment.contentSource.contentHash}</Text>
                        </Descriptions.Item>
                    </Descriptions>

                    <Collapse
                        className="experiment-detail-page-source-collapse"
                        items={[
                            {
                                key: "full-content",
                                label: "Xem nội dung bài học đầy đủ",
                                children: (
                                    <Paragraph className="experiment-detail-page-source-content">
                                        {experiment.contentSource.fullContent}
                                    </Paragraph>
                                ),
                            },
                        ]}
                    />
                </Card>
            </div>

            <div className="experiment-detail-page-selected-scope">
                <Card title={`Phạm vi đoạn đã chọn (${experiment.selectedSegments.length})`}>
                    <Text type="secondary">
                        Các đoạn được tô vàng bên dưới là phạm vi nội dung đã dùng cho lần thực nghiệm.
                    </Text>
                    <div className="experiment-detail-page-segment-selector">
                        <TextSegmentSelector
                            content={experiment.contentSource.fullContent}
                            value={experiment.selectedSegments}
                            onChange={() => undefined}
                            readOnly
                        />
                    </div>
                </Card>
            </div>

            <div className="experiment-detail-page-results">
                <div className="experiment-detail-page-section-header">
                    <Title level={4} className="experiment-detail-page-section-title">
                        Kết quả thực nghiệm
                    </Title>
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
                            <Paragraph copyable className="experiment-detail-page-prompt-text">
                                {experiment.promptText}
                            </Paragraph>
                        </Descriptions.Item>
                    </Descriptions>
                </Card>
            </div>
        </div>
    );
}
