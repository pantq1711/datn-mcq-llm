import { useState, useEffect } from "react";
import { message, Alert, Button, Card, Col, Drawer, Form, Input, Row, Select, Space, Statistic, Table, Tag, Typography, } from "antd";
import { getCatalogSubjects, getGenerationJobs } from "../services/generationApi";
import type { GenerationJob, SubjectOption } from "../types/generation.types";
import { EyeOutlined, FilterOutlined, ReloadOutlined, } from "@ant-design/icons";

import GenerationResult from "../components/GenerationResult";
import { processNextJob } from "../services/generationApi";

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
        render: (status: string) => {
            let color = "default";
            if (status === "COMPLETED") color = "green";
            if (status === "RUNNING") color = "blue";
            if (status === "FAILED") color = "red";
            return <Tag color={color}>{status}</Tag>;
        },
        filters: [
            { text: "Completed", value: "COMPLETED" },
            { text: "Running", value: "RUNNING" },
            { text: "Failed", value: "FAILED" },
        ],
        onFilter: (value, record) => record.status === value,
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

    const [subjectOptions, setSubjectOptions] = useState<SubjectOption[]>([]);
    const [loadingSubjects, setLoadingSubjects] = useState<boolean>(false);

    const [dataSource, setDataSource] = useState<GenerationJob[]>([]);

    useEffect(() => {
        const fetchSubjects = async () => {
            setLoadingSubjects(true);
            try {
                const subjects = await getCatalogSubjects();
                // Map sang options cho Select
                const options = subjects.map((s) => ({
                    label: s.name,
                    value: s.code || s.id, // dùng code hoặc id tương thích với filter
                }));
                setSubjectOptions(options);
            } catch (error) {
                console.error("Lỗi khi tải danh sách môn học:", error);
                message.error("Không thể tải danh sách môn học");
            } finally {
                setLoadingSubjects(false);
            }
        };
        fetchSubjects();
    }, []);


    const handleFilter = (values: GenerationFilterValues) => {
        console.log("Generation history filters:", values);
    };

    const handleReset = () => {
        form.resetFields();
    };

    const refreshHistory = async (updatedJob?: GenerationJob) => {
        try {
            const jobs = await getGenerationJobs(); // API GET /api/generation/jobs
            let newData = jobs;
            // Nếu có job vừa được xử lý → merge vào danh sách hiện tại
            if (updatedJob) {
                newData = jobs.map((job) =>
                    job.id === updatedJob.id ? { ...job, ...updatedJob } : job
                );
            }
            setDataSource(newData);
        } catch (err) {
            console.error("Lỗi tải lịch sử job:", err);
            message.error("Không thể tải lịch sử job");
        }
    };

    const handleProcessNext = async () => {
        try {
            const result = await processNextJob();   // {jobId, status, message}
            message.success(
                result.message ?? `Job ${result.jobId} đã được xử lý (${result.status})`
            );
            // Gọi refreshHistory và truyền thông tin job mới để merge nhanh
            await refreshHistory(
                //     {
                //     id: result.jobId,
                //     status: result.status,
                //     // nếu API trả về thêm thông tin, có thể bổ sung ở đây
                // }
            );
        } catch (err) {
            console.error("Xử lý job lỗi:", err);
            message.error("Xử lý job thất bại");
            // Nếu muốn chắc chắn UI đồng bộ, tải lại toàn bộ lịch sử
            await refreshHistory();
        }
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
                                        loading={loadingSubjects}
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