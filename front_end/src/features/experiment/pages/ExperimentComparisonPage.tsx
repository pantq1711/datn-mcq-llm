import {
    Button,
    Card,
    Col,
    Descriptions,
    Form,
    Row,
    Select,
    Space,
    Tag,
    Typography,
} from "antd";
import {
    ArrowLeftOutlined,
    BarChartOutlined,
    ReloadOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import AcceptanceRateChart from "../components/AcceptanceRateChart";
import EditDistanceChart from "../components/EditDistanceChart";
import QualityMetricsChart from "../components/QualityMetricsChart";
import StrategyComparison from "../components/StrategyComparison";

import "./ExperimentComparisonPage.css";

const { Title, Text } = Typography;

interface ComparisonFormValues {
    experimentIds: number[];
}

const experimentOptions = [
    {
        value: 1,
        label: "EXP-001 - GPT-4o-mini - Zero-shot",
    },
    {
        value: 2,
        label: "EXP-002 - GPT-4o-mini - Few-shot",
    },
    {
        value: 3,
        label: "EXP-003 - GPT-4o-mini - Structured Output",
    },
    {
        value: 4,
        label: "EXP-004 - Gemini Flash - Few-shot",
    },
];

export default function ExperimentComparisonPage() {
    const navigate = useNavigate();
    const [form] = Form.useForm<ComparisonFormValues>();

    const handleCompare = (values: ComparisonFormValues) => {
        console.log("Selected experiments:", values.experimentIds);
    };

    const handleReset = () => {
        form.resetFields();
    };

    return (
        <div className="experiment-comparison-page">
            {/* Page Header */}
            <div className="experiment-comparison-page-header">
                <div className="experiment-comparison-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="experiment-detail-back-button"
                        onClick={() => navigate("/teacher/experiments")}
                    >
                        Quay lại
                    </Button>
                </div>

                <div className="experiment-comparison-page-header-content">
                    <div className="experiment-comparison-page-title-section">
                        <Title
                            level={2}
                            className="experiment-comparison-page-title"
                        >
                            So sánh thực nghiệm
                        </Title>

                        <Text type="secondary">
                            So sánh kết quả sinh câu hỏi giữa các thực nghiệm
                            theo model và chiến lược prompting.
                        </Text>
                    </div>
                </div>
            </div>

            {/* Experiment Selection */}
            <div className="experiment-comparison-page-selection">
                <Card
                    title={
                        <Space>
                            <BarChartOutlined />
                            <span>Chọn thực nghiệm</span>
                        </Space>
                    }
                >
                    <Form
                        form={form}
                        layout="vertical"
                        onFinish={handleCompare}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} lg={18}>
                                <Form.Item
                                    label="Thực nghiệm"
                                    name="experimentIds"
                                    rules={[
                                        {
                                            required: true,
                                            message:
                                                "Vui lòng chọn ít nhất một thực nghiệm.",
                                        },
                                    ]}
                                >
                                    <Select
                                        mode="multiple"
                                        allowClear
                                        showSearch
                                        optionFilterProp="label"
                                        placeholder="Chọn các thực nghiệm cần so sánh"
                                        options={experimentOptions}
                                    />
                                </Form.Item>
                            </Col>

                            <Col
                                xs={24}
                                lg={6}
                                className="experiment-comparison-page-selection-actions"
                            >
                                <Form.Item label=" ">
                                    <Space>
                                        <Button
                                            type="primary"
                                            htmlType="submit"
                                            icon={<BarChartOutlined />}
                                        >
                                            So sánh
                                        </Button>

                                        <Button
                                            icon={<ReloadOutlined />}
                                            onClick={handleReset}
                                        >
                                            Đặt lại
                                        </Button>
                                    </Space>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </Card>
            </div>

            {/* Comparison Overview */}
            <div className="experiment-comparison-page-overview">
                <Card title="Thông tin thực nghiệm được chọn">
                    <Descriptions
                        bordered
                        column={{
                            xs: 1,
                            sm: 2,
                            lg: 3,
                        }}
                    >
                        <Descriptions.Item label="Số thực nghiệm">
                            3
                        </Descriptions.Item>

                        <Descriptions.Item label="Model">
                            <Space size={6} wrap>
                                <Tag>GPT-4o-mini</Tag>
                                <Tag>Gemini Flash</Tag>
                            </Space>
                        </Descriptions.Item>

                        <Descriptions.Item label="Môn học">
                            Toán học
                        </Descriptions.Item>

                        <Descriptions.Item label="Chiến lược">
                            <Space size={6} wrap>
                                <Tag>Zero-shot</Tag>
                                <Tag>Few-shot</Tag>
                                <Tag>Structured Output</Tag>
                            </Space>
                        </Descriptions.Item>

                        <Descriptions.Item label="Số câu">
                            100 câu / thực nghiệm
                        </Descriptions.Item>

                        <Descriptions.Item label="Trạng thái">
                            <Tag color="success">Đã hoàn thành</Tag>
                        </Descriptions.Item>
                    </Descriptions>
                </Card>
            </div>

            {/* Strategy Comparison */}
            <div className="experiment-comparison-page-strategy">
                <Card title="So sánh tổng hợp">
                    <StrategyComparison />
                </Card>
            </div>

            {/* Metric Comparison */}
            <div className="experiment-comparison-page-metrics">
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
        </div>
    );
}