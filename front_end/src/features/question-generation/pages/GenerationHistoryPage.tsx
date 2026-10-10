import { useState } from "react";
import { Alert, Button, Card, Col, Drawer, Form, Input, Row, Select, Space, Statistic, Table, Tag, Typography, } from "antd";
import { EyeOutlined, FilterOutlined, ReloadOutlined, } from "@ant-design/icons";

import GenerationResult from "../components/GenerationResult";

import "./GenerationHistoryPage.css";

const { Title, Text } = Typography;

type GenerationStatus =
    | "COMPLETED"
    | "RUNNING"
    | "FAILED";

type SourceType = "TEXT" | "WORD" | "PDF" | "IMAGE";

interface GenerationHistoryItem {
    id: number;

    // Thông tin nguồn
    sourceName: string;
    sourceType: "TEXT" | "WORD" | "PDF" | "IMAGE";
    selectedSegmentCount: number;

    // Thông tin sinh câu hỏi
    subject: string;
    strategy: string;
    model: string;
    temperature: number;
    questionCount: number;

    // Trạng thái và thời gian
    status: GenerationStatus;
    startedAt: string;

    // Có thể có khi sinh thất bại
    errorMessage?: string;
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
        sourceName: "Bai_3_Ham_so_bac_hai.pdf",
        sourceType: "PDF",
        selectedSegmentCount: 2,
        subject: "Toán học",
        strategy: "Zero-shot",
        model: "GPT-4o-mini",
        temperature: 0.7,
        questionCount: 10,
        status: "COMPLETED",
        startedAt: "01/10/2026 10:30",
    },
    {
        id: 2,
        sourceName: "Noi_dung_Dinh_luat_II_Newton.docx",
        sourceType: "WORD",
        selectedSegmentCount: 3,
        subject: "Vật lý",
        strategy: "Few-shot",
        model: "GPT-4o-mini",
        temperature: 0.5,
        questionCount: 20,
        status: "COMPLETED",
        startedAt: "01/10/2026 11:15",
    },
    {
        id: 3,
        sourceName: "Phan_ung_este_hoa.pdf",
        sourceType: "PDF",
        selectedSegmentCount: 2,
        subject: "Hóa học",
        strategy: "Structured Output",
        model: "Gemini Flash",
        temperature: 0.3,
        questionCount: 15,
        status: "RUNNING",
        startedAt: "02/10/2026 09:20",
    },
    {
        id: 4,
        sourceName: "Bai_di_truyen_Mendel.png",
        sourceType: "IMAGE",
        selectedSegmentCount: 1,
        subject: "Sinh học",
        strategy: "Few-shot",
        model: "Gemini Flash",
        temperature: 0.7,
        questionCount: 10,
        status: "FAILED",
        startedAt: "02/10/2026 09:45",
        errorMessage:
            "Không thể trích xuất đầy đủ nội dung từ hình ảnh. Vui lòng kiểm tra chất lượng ảnh hoặc thử tải lại tài liệu.",
    },
    {
        id: 5,
        sourceName: "Nội dung nhập trực tiếp",
        sourceType: "TEXT",
        selectedSegmentCount: 4,
        subject: "Toán học",
        strategy: "Zero-shot",
        model: "GPT-4o-mini",
        temperature: 0.6,
        questionCount: 12,
        status: "COMPLETED",
        startedAt: "03/10/2026 14:10",
    },
    {
        id: 6,
        sourceName: "Bai_dien_tu_hoc.docx",
        sourceType: "WORD",
        selectedSegmentCount: 2,
        subject: "Vật lý",
        strategy: "Structured Output",
        model: "Gemini Flash",
        temperature: 0.4,
        questionCount: 15,
        status: "COMPLETED",
        startedAt: "04/10/2026 08:45",
    },
];

// Thêm các hàm helper này vào trước phần generationColumns
const getSourceColor = (type: SourceType) => {
    switch (type) {
        case "PDF":
            return "red";
        case "WORD":
            return "blue";
        case "IMAGE":
            return "green";
        case "TEXT":
        default:
            return "default";
    }
};

const getSourceLabel = (type: SourceType) => {
    switch (type) {
        case "PDF":
            return "PDF";
        case "WORD":
            return "Word";
        case "IMAGE":
            return "Ảnh";
        case "TEXT":
        default:
            return "Văn bản";
    }
};

const generationColumns = [
    {
        title: "Mã",
        dataIndex: "id",
        key: "id",
        width: 50,
    },
    {
        title: "Nguồn nội dung",
        dataIndex: "sourceName",
        key: "sourceName",
        ellipsis: true,
        render: (text: string, record: GenerationHistoryItem) => (
            <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                <span>{text}</span>
                <Tag
                    color={getSourceColor(record.sourceType)}
                    style={{ width: "fit-content" }}
                >
                    {getSourceLabel(record.sourceType)}
                </Tag>
            </div>
        ),
    },
    {
        title: "Số đoạn chọn",
        dataIndex: "selectedSegmentCount",
        key: "selectedSegmentCount",
        width: 120,
    },
    {
        title: "Môn học",
        dataIndex: "subject",
        key: "subject",
        width: 140,
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
        width: 140,
    },
    {
        title: "Số câu",
        dataIndex: "questionCount",
        key: "questionCount",
        width: 80,
    },
    {
        title: "Trạng thái",
        dataIndex: "status",
        key: "status",
        width: 110,
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
        width: 160,
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
                                        placeholder="Tìm theo tên tài liệu hoặc nội dung..."
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

                            {selectedGeneration.status === "FAILED" && (
                                <Alert
                                    type="error"
                                    showIcon
                                    message="Sinh câu hỏi thất bại"
                                    description={
                                        selectedGeneration.errorMessage ||
                                        "Đã xảy ra lỗi trong quá trình sinh câu hỏi. Vui lòng thử lại."
                                    }
                                />
                            )}

                            <div>
                                <Text type="secondary">
                                    Cấu hình
                                </Text>

                                <div className="generation-history-page-detail-tags">
                                    <Tag>{selectedGeneration.selectedSegmentCount} đoạn được chọn</Tag>
                                    <Tag>
                                        {selectedGeneration.subject}
                                    </Tag>

                                    <Tag>
                                        {selectedGeneration.questionCount} câu
                                    </Tag>
                                    <Tag>
                                        {selectedGeneration.strategy}
                                    </Tag>
                                    <Tag>
                                        {selectedGeneration.model}
                                    </Tag>
                                    <Tag>Temperature: {selectedGeneration.temperature}</Tag>
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