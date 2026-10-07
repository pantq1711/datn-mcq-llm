import {
    Button,
    Card,
    Col,
    Form,
    Row,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from "antd";
import { BarChartOutlined, FilterOutlined, ReloadOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import "./ExperimentListPage.css";

const { Title, Text } = Typography;

interface ExperimentFilterValues {
    subjectId?: number;
    modelName?: string;
    strategy?: string;
}

interface ExperimentRun {
    id: number;
    subject: string;
    modelName: string;
    strategy: string;
    promptVersion: string;
    questionCount: number;
    status: "COMPLETED" | "RUNNING" | "FAILED";
    startedAt: string;
}

const subjectOptions = [
    { label: "Toán học", value: 1 },
    { label: "Vật lý", value: 2 },
    { label: "Hóa học", value: 3 },
    { label: "Sinh học", value: 4 },
];

const modelOptions = [
    { label: "GPT-4o-mini", value: "gpt-4o-mini" },
    { label: "Gemini Flash", value: "gemini-flash" },
];

const strategyOptions = [
    { label: "Zero-shot", value: "ZERO_SHOT" },
    { label: "Few-shot", value: "FEW_SHOT" },
    { label: "Structured Output", value: "STRUCTURED_OUTPUT" },
];

const mockExperiments: ExperimentRun[] = [
    {
        id: 1,
        subject: "Toán học",
        modelName: "GPT-4o-mini",
        strategy: "Zero-shot",
        promptVersion: "v1.0",
        questionCount: 100,
        status: "COMPLETED",
        startedAt: "01/10/2026 10:30",
    },
    {
        id: 2,
        subject: "Toán học",
        modelName: "GPT-4o-mini",
        strategy: "Few-shot",
        promptVersion: "v1.2",
        questionCount: 100,
        status: "COMPLETED",
        startedAt: "01/10/2026 11:15",
    },
    {
        id: 3,
        subject: "Vật lý",
        modelName: "Gemini Flash",
        strategy: "Few-shot",
        promptVersion: "v1.1",
        questionCount: 100,
        status: "RUNNING",
        startedAt: "02/10/2026 09:20",
    },
];

const experimentColumns = [
    {
        title: "Mã",
        dataIndex: "id",
        key: "id",
    },
    {
        title: "Môn học",
        dataIndex: "subject",
        key: "subject",
    },
    {
        title: "Model",
        dataIndex: "modelName",
        key: "modelName",
    },
    {
        title: "Chiến lược",
        dataIndex: "strategy",
        key: "strategy",
    },
    {
        title: "Prompt",
        dataIndex: "promptVersion",
        key: "promptVersion",
    },
    {
        title: "Số câu",
        dataIndex: "questionCount",
        key: "questionCount",
    },
    {
        title: "Trạng thái",
        dataIndex: "status",
        key: "status",
        render: (status: ExperimentRun["status"]) => {
            if (status === "COMPLETED") {
                return <Tag color="success">Hoàn thành</Tag>;
            }

            if (status === "RUNNING") {
                return <Tag color="processing">Đang chạy</Tag>;
            }

            return <Tag color="error">Thất bại</Tag>;
        },
    },
    {
        title: "Thời gian bắt đầu",
        dataIndex: "startedAt",
        key: "startedAt",
    },
];

export default function ExperimentListPage() {
    const navigate = useNavigate();

    const [form] = Form.useForm<ExperimentFilterValues>();

    const handleFilter = (values: ExperimentFilterValues) => {
        console.log("Experiment filters:", values);
    };

    const handleReset = () => {
        form.resetFields();
    };

    return (
        <div className="experiment-list-page">
            <div className="experiment-list-page-header">
                <Title level={2} className="experiment-list-page-title">
                    Thực nghiệm
                </Title>

                <Text type="secondary">
                    Theo dõi và so sánh kết quả các thực nghiệm sinh câu hỏi theo model
                    và chiến lược prompting.
                </Text>
            </div>

            <Card className="experiment-list-page-filters">
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleFilter}
                >
                    <Row gutter={[16, 0]}>
                        <Col xs={24} sm={12} lg={7}>
                            <Form.Item label="Môn học" name="subjectId">
                                <Select
                                    allowClear
                                    placeholder="Chọn môn học"
                                    options={subjectOptions}
                                />
                            </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} lg={7}>
                            <Form.Item label="Model" name="modelName">
                                <Select
                                    allowClear
                                    placeholder="Chọn model"
                                    options={modelOptions}
                                />
                            </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} lg={6}>
                            <Form.Item label="Chiến lược" name="strategy">
                                <Select
                                    allowClear
                                    placeholder="Chọn chiến lược"
                                    options={strategyOptions}
                                />
                            </Form.Item>
                        </Col>

                        <Col
                            xs={24}
                            sm={12}
                            lg={4}
                            className="experiment-list-page-filter-actions"
                        >
                            <Form.Item label=" ">
                                <Space>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        icon={<FilterOutlined />}
                                    >
                                        Lọc
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

            <div className="experiment-list-page-section">
                <div className="experiment-list-page-section-header">
                    <div>
                        <Title level={4} className="experiment-list-page-section-title">
                            Danh sách lần thực nghiệm
                        </Title>
                        <Text type="secondary">
                            Chọn các thực nghiệm để xem chi tiết hoặc so sánh kết quả.
                        </Text>
                    </div>

                    <Button
                        type="primary"
                        icon={<BarChartOutlined />}
                        onClick={() => navigate("/experiments/comparison")}
                    >
                        So sánh thực nghiệm
                    </Button>
                </div>

                <Card>
                    <Table
                        className="experiment-list-table"
                        rowKey="id"
                        columns={experimentColumns}
                        dataSource={mockExperiments}
                        pagination={{
                            pageSize: 10,
                            showSizeChanger: true,
                        }}
                        scroll={{ x: "max-content" }}
                        onRow={(record) => ({
                            onClick: () => {
                                navigate(`/experiments/${record.id}`);
                            },
                        })}
                    />
                </Card>
            </div>
        </div>
    );
}