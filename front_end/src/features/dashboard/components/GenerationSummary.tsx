import {
    Card,
    Col,
    Progress,
    Row,
    Statistic,
    Typography,
} from "antd";
import {
    CheckCircleOutlined,
    CloseCircleOutlined,
    FileTextOutlined,
    PercentageOutlined,
} from "@ant-design/icons";
import "./GenerationSummary.css";

const { Text } = Typography;

export default function GenerationSummary() {
    const totalGenerated = 1240;
    const successCount = 1186;
    const failedCount = 54;
    const successRate = 95.6;

    return (
        <Card
            title="Tình hình sinh câu hỏi"
            className="generation-summary"
        >
            <Row gutter={[16, 20]}>
                <Col xs={24} sm={12}>
                    <Statistic
                        title="Tổng số câu hỏi"
                        value={totalGenerated}
                        prefix={<FileTextOutlined />}
                    />
                </Col>

                <Col xs={24} sm={12}>
                    <Statistic
                        title="Sinh thành công"
                        value={successCount}
                        prefix={<CheckCircleOutlined />}
                    />
                </Col>

                <Col xs={24} sm={12}>
                    <Statistic
                        title="Sinh thất bại"
                        value={failedCount}
                        prefix={<CloseCircleOutlined />}
                    />
                </Col>

                <Col xs={24} sm={12}>
                    <Statistic
                        title="Tỷ lệ thành công"
                        value={successRate}
                        suffix="%"
                        prefix={<PercentageOutlined />}
                    />
                </Col>
            </Row>

            <div className="generation-summary-progress">
                <div className="generation-summary-progress-header">
                    <Text strong>Tiến độ sinh thành công</Text>

                    <Text type="secondary">
                        {successCount}/{totalGenerated}
                    </Text>
                </div>

                <Progress
                    percent={successRate}
                    showInfo={false}
                />
            </div>
        </Card>
    );
}
