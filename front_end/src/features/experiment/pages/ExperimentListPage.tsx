import { message, Button, Card, Col, Form, Input, Row, Select, Space, Table, Tag, Typography, } from "antd";
import type { ColumnsType } from "antd/es/table";
import { BarChartOutlined, FilterOutlined, ReloadOutlined } from "@ant-design/icons";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCatalogSubjects } from "../services/experimentApi";
import type { SubjectOption } from "../types/experiment.types";
import "./ExperimentListPage.css";
import type { ExperimentRun } from "../types/experiment.types";

const { Title } = Typography;

interface ExperimentFilterValues {
    subjectId?: number;
    modelName?: string;
    strategy?: string;
    sourceKeyword?: string;
}

const modelOptions = [
    { label: "GPT-4o-mini", value: "gpt-4o-mini" },
    { label: "Gemini Flash", value: "gemini-flash" },
];

const strategyOptions = [
    { label: "Zero-shot", value: "ZERO_SHOT" },
    { label: "Few-shot", value: "FEW_SHOT" },
    { label: "Structured Output", value: "STRUCTURED_OUTPUT" },
];

const mathContent =
    "Hàm số bậc hai có dạng y = ax² + bx + c, trong đó a, b, c là các hằng số và a khác 0. Đồ thị của hàm số bậc hai là một đường parabol. Trục đối xứng của parabol có phương trình x = -b/(2a). Tọa độ đỉnh của parabol được xác định dựa trên trục đối xứng và giá trị của hàm số tại hoành độ đỉnh.";

const physicsContent =
    "Định luật II Newton cho biết gia tốc của một vật cùng hướng với lực tổng hợp tác dụng lên vật, có độ lớn tỉ lệ thuận với độ lớn của lực và tỉ lệ nghịch với khối lượng của vật. Công thức của định luật là F = ma, trong đó F là lực tổng hợp, m là khối lượng và a là gia tốc.";

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
        endedAt: "01/10/2026 10:45",
        temperature: 0.7,
        promptText:
            "Tạo câu hỏi trắc nghiệm 4 phương án dựa trên nội dung bài học được cung cấp. Chỉ có một phương án đúng.",
        contentSource: {
            title: "Bài 3 - Hàm số bậc hai",
            lessonId: 101,
            fullContent: mathContent,
            contentHash: "mock-hash-math-001",
        },
        selectedSegments: [
            {
                id: "seg-1-1",
                text: "Hàm số bậc hai có dạng y = ax² + bx + c, trong đó a, b, c là các hằng số và a khác 0.",
                startOffset: 0,
                endOffset: 85,
            },
            {
                id: "seg-1-2",
                text: "Trục đối xứng của parabol có phương trình x = -b/(2a). Tọa độ đỉnh của parabol được xác định dựa trên trục đối xứng và giá trị của hàm số tại hoành độ đỉnh.",
                startOffset: 134,
                endOffset: 290,
            },
        ],
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
        endedAt: "01/10/2026 11:32",
        temperature: 0.7,
        promptText:
            "Tạo câu hỏi trắc nghiệm 4 phương án dựa trên nội dung bài học và các ví dụ mẫu. Bảo đảm chỉ có một phương án đúng, các phương án nhiễu hợp lý và không trùng lặp.",
        contentSource: {
            title: "Bài 3 - Hàm số bậc hai",
            lessonId: 101,
            fullContent: mathContent,
            contentHash: "mock-hash-math-001",
        },
        selectedSegments: [
            {
                id: "seg-2-1",
                text: "Hàm số bậc hai có dạng y = ax² + bx + c, trong đó a, b, c là các hằng số và a khác 0.",
                startOffset: 0,
                endOffset: 85,
            },
            {
                id: "seg-2-2",
                text: "Trục đối xứng của parabol có phương trình x = -b/(2a). Tọa độ đỉnh của parabol được xác định dựa trên trục đối xứng và giá trị của hàm số tại hoành độ đỉnh.",
                startOffset: 134,
                endOffset: 290,
            },
        ],
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
        temperature: 0.5,
        promptText:
            "Tạo câu hỏi trắc nghiệm về định luật II Newton dựa trên nội dung đã chọn và các ví dụ mẫu. Mỗi câu hỏi có bốn phương án và một đáp án đúng.",
        contentSource: {
            title: "Bài 10 - Định luật II Newton",
            lessonId: 202,
            fullContent: physicsContent,
            contentHash: "mock-hash-physics-001",
        },
        selectedSegments: [
            {
                id: "seg-3-1",
                text: "Định luật II Newton cho biết gia tốc của một vật cùng hướng với lực tổng hợp tác dụng lên vật, có độ lớn tỉ lệ thuận với độ lớn của lực và tỉ lệ nghịch với khối lượng của vật.",
                startOffset: 0,
                endOffset: 175,
            },
            {
                id: "seg-3-2",
                text: "Công thức của định luật là F = ma, trong đó F là lực tổng hợp, m là khối lượng và a là gia tốc.",
                startOffset: 176,
                endOffset: 271,
            },
        ],
    },
];

const experimentColumns: ColumnsType<ExperimentRun> = [
    {
        title: "Mã",
        dataIndex: "id",
        key: "id",
        width: 70,
    },
    {
        title: "Môn học",
        dataIndex: "subject",
        key: "subject",
        width: 120,
    },
    {
        title: "Nguồn nội dung",
        dataIndex: ["contentSource", "title"],
        key: "contentSourceTitle",
        width: 240,
        render: (title: string) => (
            <Typography.Text ellipsis={{ tooltip: title }} style={{ maxWidth: 220 }}>
                {title}
            </Typography.Text>
        ),
    },
    {
        title: "Số đoạn",
        key: "selectedSegmentCount",
        width: 90,
        align: "center",
        render: (_value, record) => record.selectedSegments.length,
    },
    {
        title: "Model",
        dataIndex: "modelName",
        key: "modelName",
        width: 140,
    },
    {
        title: "Chiến lược",
        dataIndex: "strategy",
        key: "strategy",
        width: 140,
    },
    {
        title: "Prompt",
        dataIndex: "promptVersion",
        key: "promptVersion",
        width: 90,
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
        width: 120,
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
        width: 170,
    },
];

export default function ExperimentListPage() {
    const navigate = useNavigate();
    const [form] = Form.useForm<ExperimentFilterValues>();
    const [filteredExperiments, setFilteredExperiments] =
        useState<ExperimentRun[]>(mockExperiments);

    const [subjectOptions, setSubjectOptions] = useState<SubjectOption[]>([]);
    const [loadingSubjects, setLoadingSubjects] = useState<boolean>(false);
    useEffect(() => {
        const fetchSubjects = async () => {
            setLoadingSubjects(true);
            try {
                const subjects = await getCatalogSubjects();
                const options = subjects.map((s) => ({
                    label: s.name,
                    value: s.id,
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

    const handleFilter = (values: ExperimentFilterValues) => {
        let results = [...mockExperiments];

        if (values.subjectId !== undefined) {
            const selectedSubject = subjectOptions.find(
                (option) => option.value === values.subjectId,
            )?.label;

            if (selectedSubject) {
                results = results.filter((item) => item.subject === selectedSubject);
            }
        }

        if (values.modelName) {
            const selectedModel = modelOptions.find(
                (option) => option.value === values.modelName,
            )?.label;

            if (selectedModel) {
                results = results.filter((item) => item.modelName === selectedModel);
            }
        }

        if (values.strategy) {
            const selectedStrategy = strategyOptions.find(
                (option) => option.value === values.strategy,
            )?.label;

            if (selectedStrategy) {
                results = results.filter((item) => item.strategy === selectedStrategy);
            }
        }

        const sourceKeyword = values.sourceKeyword?.trim().toLowerCase();
        if (sourceKeyword) {
            results = results.filter(({ contentSource }) =>
                `${contentSource.title} ${contentSource.contentHash}`
                    .toLowerCase()
                    .includes(sourceKeyword),
            );
        }

        setFilteredExperiments(results);
    };

    const handleReset = () => {
        form.resetFields();
        setFilteredExperiments(mockExperiments);
    };

    return (
        <div className="experiment-list-page">
            <div className="experiment-list-page-header">
                <Title level={2} className="experiment-list-page-title">
                    Thực nghiệm
                </Title>
            </div>

            <Card className="experiment-list-page-filters">
                <Form form={form} layout="vertical" onFinish={handleFilter}>
                    <Row gutter={[16, 0]}>
                        <Col xs={24} sm={12} lg={5}>
                            <Form.Item label="Môn học" name="subjectId">
                                <Select
                                    allowClear
                                    placeholder="Chọn môn học"
                                    loading={loadingSubjects}
                                    options={subjectOptions}
                                />
                            </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} lg={5}>
                            <Form.Item label="Model" name="modelName">
                                <Select
                                    allowClear
                                    placeholder="Chọn model"
                                    options={modelOptions}
                                />
                            </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} lg={5}>
                            <Form.Item label="Chiến lược" name="strategy">
                                <Select
                                    allowClear
                                    placeholder="Chọn chiến lược"
                                    options={strategyOptions}
                                />
                            </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} lg={5}>
                            <Form.Item label="Nguồn nội dung" name="sourceKeyword">
                                <Input allowClear placeholder="Tên nguồn hoặc content hash" />
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
                                    <Button icon={<ReloadOutlined />} onClick={handleReset}>
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
                    <Table<ExperimentRun>
                        className="experiment-list-table"
                        rowKey="id"
                        columns={experimentColumns}
                        dataSource={filteredExperiments}
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
