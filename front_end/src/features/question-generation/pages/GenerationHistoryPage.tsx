import { useState } from "react";
import {
    Button,
    Card,
    Col,
    Drawer,
    Form,
    Input,
    Row,
    Select,
    Space,
    Statistic,
    Table,
    Tag,
    Typography,
} from "antd";
import {
    EyeOutlined,
    FilterOutlined,
    ReloadOutlined,
} from "@ant-design/icons";

import GenerationResult from "../components/GenerationResult";

import "./GenerationHistoryPage.css";

const { Title, Text } = Typography;

type GenerationStatus =
    | "COMPLETED"
    | "RUNNING"
    | "FAILED";

interface GenerationHistoryItem {
    id: number;
    template: string;
    subject: string;
    strategy: string;
    model: string;
    questionCount: number;
    status: GenerationStatus;
    startedAt: string;
}

interface GenerationFilterValues {
    keyword?: string;
    subject?: string;
    strategy?: string;
    model?: string;
    status?: GenerationStatus;
}

const subjectOptions = [
    {
        label: "Toán học",
        value: "MATH",
    },
    {
        label: "Vật lý",
        value: "PHYSICS",
    },
    {
        label: "Hóa học",
        value: "CHEMISTRY",
    },
    {
        label: "Sinh học",
        value: "BIOLOGY",
    },
];

const strategyOptions = [
    {
        label: "Zero-shot",
        value: "ZERO_SHOT",
    },
    {
        label: "Few-shot",
        value: "FEW_SHOT",
    },
    {
        label: "Structured Output",
        value: "STRUCTURED_OUTPUT",
    },
];

const modelOptions = [
    {
        label: "GPT-4o-mini",
        value: "gpt-4o-mini",
    },
    {
        label: "Gemini Flash",
        value: "gemini-flash",
    },
];

const statusOptions = [
    {
        label: "Hoàn thành",
        value: "COMPLETED",
    },
    {
        label: "Đang xử lý",
        value: "RUNNING",
    },
    {
        label: "Thất bại",
        value: "FAILED",
    },
];

const mockGenerationHistory: GenerationHistoryItem[] = [
    {
        id: 1,
        template: "Hàm số bậc hai",
        subject: "Toán học",
        strategy: "Zero-shot",
        model: "GPT-4o-mini",
        questionCount: 10,
        status: "COMPLETED",
        startedAt: "01/10/2026 10:30",
    },
    {
        id: 2,
        template: "Định luật II Newton",
        subject: "Vật lý",
        strategy: "Few-shot",
        model: "GPT-4o-mini",
        questionCount: 20,
        status: "COMPLETED",
        startedAt: "01/10/2026 11:15",
    },
    {
        id: 3,
        template: "Phản ứng este hóa",
        subject: "Hóa học",
        strategy: "Structured Output",
        model: "Gemini Flash",
        questionCount: 15,
        status: "RUNNING",
        startedAt: "02/10/2026 09:20",
    },
    {
        id: 4,
        template: "Di truyền Mendel",
        subject: "Sinh học",
        strategy: "Few-shot",
        model: "Gemini Flash",
        questionCount: 10,
        status: "FAILED",
        startedAt: "02/10/2026 09:45",
    },
];

const generationColumns = [
    {
        title: "Mã",
        dataIndex: "id",
        key: "id",
        width: 80,
    },
    {
        title: "Câu hỏi mẫu",
        dataIndex: "template",
        key: "template",
        ellipsis: true,
    },
    {
        title: "Môn học",
        dataIndex: "subject",
        key: "subject",
        width: 120,
    },
    {
        title: "Chiến lược",
        dataIndex: "strategy",
        key: "strategy",
        width: 150,
    },
    {
        title: "Model",
        dataIndex: "model",
        key: "model",
        width: 130,
    },
    {
        title: "Số câu",
        dataIndex: "questionCount",
        key: "questionCount",
        width: 90,
    },
    {
        title: "Trạng thái",
        dataIndex: "status",
        key: "status",
        width: 130,
        render: (status: GenerationStatus) => {
            if (status === "COMPLETED") {
                return <Tag color="success">Hoàn thành</Tag>;
            }

            if (status === "RUNNING") {
                return <Tag color="processing">Đang xử lý</Tag>;
            }

            return <Tag color="error">Thất bại</Tag>;
        },
    },
    {
        title: "Thời gian bắt đầu",
        dataIndex: "startedAt",
        key: "startedAt",
        width: 170,
    },
    {
        title: "Thao tác",
        key: "action",
        width: 90,
        fixed: "right" as const,
        render: () => (
            <Button
                type="text"
                icon={<EyeOutlined />}
            >
                Xem
            </Button>
        ),
    },
];

export default function GenerationHistoryPage() {
    const [form] = Form.useForm<GenerationFilterValues>();

    const [selectedGeneration, setSelectedGeneration] =
        useState<GenerationHistoryItem | null>(null);

    const handleFilter = (values: GenerationFilterValues) => {
        console.log("Generation history filters:", values);
    };

    const handleReset = () => {
        form.resetFields();
    };

    return (
        <div className="generation-history-page">
            {/* Page Header */}
            <div className="generation-history-page-header">
                <Title
                    level={2}
                    className="generation-history-page-title"
                >
                    Lịch sử sinh câu hỏi
                </Title>
            </div>

            {/* Statistics */}
            <Row
                gutter={[16, 16]}
                className="generation-history-page-statistics"
            >
                <Col xs={24} sm={12} xl={6}>
                    <Card>
                        <Statistic
                            title="Tổng số lần sinh"
                            value={124}
                        />
                    </Card>
                </Col>

                <Col xs={24} sm={12} xl={6}>
                    <Card>
                        <Statistic
                            title="Đã hoàn thành"
                            value={115}
                        />
                    </Card>
                </Col>

                <Col xs={24} sm={12} xl={6}>
                    <Card>
                        <Statistic
                            title="Đang xử lý"
                            value={3}
                        />
                    </Card>
                </Col>

                <Col xs={24} sm={12} xl={6}>
                    <Card>
                        <Statistic
                            title="Thất bại"
                            value={6}
                        />
                    </Card>
                </Col>
            </Row>

            {/* Filters */}
            <div className="generation-history-page-filters">
                <Card title="Bộ lọc">
                    <Form
                        form={form}
                        layout="vertical"
                        onFinish={handleFilter}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} lg={7}>
                                <Form.Item
                                    label="Từ khóa"
                                    name="keyword"
                                >
                                    <Input
                                        allowClear
                                        placeholder="Tìm theo câu hỏi mẫu..."
                                    />
                                </Form.Item>
                            </Col>

                            <Col xs={24} sm={12} lg={3}>
                                <Form.Item
                                    label="Môn học"
                                    name="subject"
                                >
                                    <Select
                                        allowClear
                                        placeholder="Chọn môn"
                                        options={subjectOptions}
                                    />
                                </Form.Item>
                            </Col>

                            <Col xs={24} sm={12} lg={4}>
                                <Form.Item
                                    label="Chiến lược"
                                    name="strategy"
                                >
                                    <Select
                                        allowClear
                                        placeholder="Chọn chiến lược"
                                        options={strategyOptions}
                                    />
                                </Form.Item>
                            </Col>

                            <Col xs={24} sm={12} lg={3}>
                                <Form.Item
                                    label="Model"
                                    name="model"
                                >
                                    <Select
                                        allowClear
                                        placeholder="Chọn model"
                                        options={modelOptions}
                                    />
                                </Form.Item>
                            </Col>

                            <Col xs={24} sm={12} lg={3}>
                                <Form.Item
                                    label="Trạng thái"
                                    name="status"
                                >
                                    <Select
                                        allowClear
                                        placeholder="Trạng thái"
                                        options={statusOptions}
                                    />
                                </Form.Item>
                            </Col>

                            <Col
                                xs={24}
                                lg={4}
                                className="generation-history-page-filter-actions"
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
            </div>

            {/* History Table */}
            <div className="generation-history-page-table">
                <Card title="Danh sách lần sinh">
                    <Table
                        rowKey="id"
                        columns={generationColumns}
                        dataSource={mockGenerationHistory}
                        pagination={{
                            pageSize: 10,
                            showSizeChanger: true,
                            showTotal: (total) =>
                                `Tổng cộng ${total} lần sinh`,
                        }}
                        scroll={{ x: "max-content" }}
                        onRow={(record) => ({
                            onClick: () => {
                                setSelectedGeneration(record);
                            },
                            style: {
                                cursor: "pointer",
                            },
                        })}
                    />
                </Card>
            </div>

            {/* Generation Detail */}
            <Drawer
                title="Chi tiết lần sinh"
                width={720}
                open={selectedGeneration !== null}
                onClose={() => setSelectedGeneration(null)}
            >
                {selectedGeneration && (
                    <div className="generation-history-page-detail">
                        <Space
                            direction="vertical"
                            size={16}
                            className="generation-history-page-detail-content"
                        >
                            <div>
                                <Text type="secondary">
                                    Mã lần sinh
                                </Text>

                                <div>
                                    <Text strong>
                                        GEN-{selectedGeneration.id
                                            .toString()
                                            .padStart(3, "0")}
                                    </Text>
                                </div>
                            </div>

                            <div>
                                <Text type="secondary">
                                    Cấu hình
                                </Text>

                                <div className="generation-history-page-detail-tags">
                                    <Tag>
                                        {selectedGeneration.subject}
                                    </Tag>
                                    <Tag>
                                        {selectedGeneration.strategy}
                                    </Tag>
                                    <Tag>
                                        {selectedGeneration.model}
                                    </Tag>
                                    <Tag>
                                        {selectedGeneration.questionCount} câu
                                    </Tag>
                                </div>
                            </div>

                            <GenerationResult
                                data={[]}
                            />
                        </Space>
                    </div>
                )}
            </Drawer>
        </div>
    );
}