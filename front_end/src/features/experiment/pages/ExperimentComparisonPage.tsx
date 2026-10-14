import { useMemo, useState } from "react";
import { Alert, Button, Card, Checkbox, Col, Descriptions, Form, Row, Select, Space, Tag, Typography, } from "antd";
import { ArrowLeftOutlined, BarChartOutlined, ReloadOutlined, } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import AcceptanceRateChart from "../components/AcceptanceRateChart";
import EditDistanceChart from "../components/EditDistanceChart";
import QualityMetricsChart from "../components/QualityMetricsChart";
import StrategyComparison from "../components/StrategyComparison";
import type { ExperimentRun } from "../types/experiment.types";

import "./ExperimentComparisonPage.css";

const { Title, Text } = Typography;

interface ComparisonFormValues {
    experimentIds: number[];
}

// Dữ liệu minh họa. Khi nối API, thay mảng này bằng danh sách thực nghiệm lấy từ backend.
// Ba thực nghiệm đầu dùng cùng contentHash; thực nghiệm thứ tư dùng nguồn khác để minh họa cảnh báo.
const experimentRecords: ExperimentRun[] = [
    {
        id: 1,
        subject: "Toán học",
        modelName: "GPT-4o-mini",
        strategy: "Zero-shot",
        promptVersion: "v1.0",
        questionCount: 100,
        status: "COMPLETED",
        startedAt: "2026-09-20T09:00:00.000Z",
        temperature: 0.2,
        contentSource: {
            title: "Toán học – Hàm số bậc hai",
            lessonId: 101,
            fullContent:
                "Hàm số bậc hai có dạng y = ax² + bx + c, với a khác 0. Đồ thị của hàm số bậc hai là một parabol. Trục đối xứng có phương trình x = -b/(2a).",
            contentHash: "sha256-demo-quadratic-lesson-001",
        },
        selectedSegments: [
            {
                id: "segment-1-1",
                text: "Hàm số bậc hai có dạng y = ax² + bx + c, với a khác 0.",
                startOffset: 0,
                endOffset: 57,
            },
        ],
    },
    {
        id: 2,
        subject: "Toán học",
        modelName: "GPT-4o-mini",
        strategy: "Few-shot",
        promptVersion: "v1.0",
        questionCount: 100,
        status: "COMPLETED",
        startedAt: "2026-09-20T10:00:00.000Z",
        temperature: 0.2,
        contentSource: {
            title: "Toán học – Hàm số bậc hai",
            lessonId: 101,
            fullContent:
                "Hàm số bậc hai có dạng y = ax² + bx + c, với a khác 0. Đồ thị của hàm số bậc hai là một parabol. Trục đối xứng có phương trình x = -b/(2a).",
            contentHash: "sha256-demo-quadratic-lesson-001",
        },
        selectedSegments: [
            {
                id: "segment-2-1",
                text: "Hàm số bậc hai có dạng y = ax² + bx + c, với a khác 0.",
                startOffset: 0,
                endOffset: 57,
            },
        ],
    },
    {
        id: 3,
        subject: "Toán học",
        modelName: "GPT-4o-mini",
        strategy: "Structured Output",
        promptVersion: "v1.0",
        questionCount: 100,
        status: "COMPLETED",
        startedAt: "2026-09-20T11:00:00.000Z",
        temperature: 0.2,
        contentSource: {
            title: "Toán học – Hàm số bậc hai",
            lessonId: 101,
            fullContent:
                "Hàm số bậc hai có dạng y = ax² + bx + c, với a khác 0. Đồ thị của hàm số bậc hai là một parabol. Trục đối xứng có phương trình x = -b/(2a).",
            contentHash: "sha256-demo-quadratic-lesson-001",
        },
        selectedSegments: [
            {
                id: "segment-3-1",
                text: "Hàm số bậc hai có dạng y = ax² + bx + c, với a khác 0.",
                startOffset: 0,
                endOffset: 57,
            },
        ],
    },
    {
        id: 4,
        subject: "Toán học",
        modelName: "Gemini Flash",
        strategy: "Few-shot",
        promptVersion: "v1.0",
        questionCount: 100,
        status: "COMPLETED",
        startedAt: "2026-09-21T09:00:00.000Z",
        temperature: 0.2,
        contentSource: {
            title: "Toán học – Phương trình bậc hai",
            lessonId: 102,
            fullContent:
                "Phương trình bậc hai một ẩn có dạng ax² + bx + c = 0, với a khác 0. Biệt thức delta được tính bằng b² - 4ac.",
            contentHash: "sha256-demo-quadratic-equation-002",
        },
        selectedSegments: [
            {
                id: "segment-4-1",
                text: "Phương trình bậc hai một ẩn có dạng ax² + bx + c = 0, với a khác 0.",
                startOffset: 0,
                endOffset: 70,
            },
        ],
    },
];

const experimentOptions = experimentRecords.map((experiment) => ({
    value: experiment.id,
    label: `EXP-${String(experiment.id).padStart(3, "0")} - ${experiment.modelName} - ${experiment.strategy}`,
}));

const experimentById = new Map(
    experimentRecords.map((experiment) => [experiment.id, experiment]),
);

export default function ExperimentComparisonPage() {
    const navigate = useNavigate();
    const [form] = Form.useForm<ComparisonFormValues>();
    const [sameSourceOnly, setSameSourceOnly] = useState(false);
    const selectedIds = Form.useWatch("experimentIds", form) ?? [];

    const selectedExperiments = useMemo(
        () => selectedIds
            .map((id: number) => experimentById.get(id))
            .filter((experiment): experiment is ExperimentRun => Boolean(experiment)),
        [selectedIds],
    );

    const distinctSources = useMemo(() => {
        const sourcesByHash = new Map<string, string>();
        selectedExperiments.forEach((experiment) => {
            const { contentHash, title } = experiment.contentSource;
            if (!sourcesByHash.has(contentHash)) {
                sourcesByHash.set(contentHash, title);
            }
        });
        return Array.from(sourcesByHash, ([contentHash, title]) => ({
            contentHash,
            title,
        }));
    }, [selectedExperiments]);

    const hasDifferentSources = distinctSources.length > 1;
    const anchorExperiment = selectedExperiments[0];

    const visibleExperimentOptions = useMemo(() => {
        if (!sameSourceOnly || !anchorExperiment) {
            return experimentOptions;
        }

        return experimentOptions.filter((option) => {
            const experiment = experimentById.get(option.value);
            return experiment?.contentSource.contentHash ===
                anchorExperiment.contentSource.contentHash;
        });
    }, [sameSourceOnly, anchorExperiment]);

    const handleCompare = (values: ComparisonFormValues) => {
        // The selected IDs are already reflected in the overview and source warning.
        // The chart components currently receive their own data and can be wired to the API separately.
        console.log("Selected experiments:", values.experimentIds);
    };

    const handleReset = () => {
        form.resetFields();
        setSameSourceOnly(false);
    };

    const handleSameSourceFilterChange = (checked: boolean) => {
        if (checked) {
            const currentIds: number[] = form.getFieldValue("experimentIds") ?? [];
            const firstSelected = currentIds.length > 0
                ? experimentById.get(currentIds[0])
                : undefined;

            if (firstSelected) {
                const firstHash = firstSelected.contentSource.contentHash;
                form.setFieldsValue({
                    experimentIds: currentIds.filter((id) =>
                        experimentById.get(id)?.contentSource.contentHash === firstHash,
                    ),
                });
            }
        }

        setSameSourceOnly(checked);
    };

    return (
        <div className="experiment-comparison-page">
            {/* Page Header */}
            <div className="experiment-comparison-page-header">
                <div className="experiment-comparison-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="experiment-comparison-back-button"
                        onClick={() => navigate("/experiments")}
                    >
                        Quay lại
                    </Button>
                </div>

                <div className="experiment-comparison-page-header-content">
                    <div className="experiment-comparison-page-title-section">
                        <Title level={2} className="experiment-comparison-page-title">
                            So sánh thực nghiệm
                        </Title>
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
                                            type: "array",
                                            required: true,
                                            min: 2,
                                            message: "Vui lòng chọn ít nhất hai thực nghiệm.",
                                        },
                                    ]}
                                >
                                    <Select
                                        mode="multiple"
                                        allowClear
                                        showSearch
                                        optionFilterProp="label"
                                        placeholder="Chọn các thực nghiệm cần so sánh"
                                        options={visibleExperimentOptions}
                                    />
                                </Form.Item>

                                <div className="experiment-comparison-page-source-filter">
                                    <Checkbox
                                        checked={sameSourceOnly}
                                        onChange={(event) =>
                                            handleSameSourceFilterChange(event.target.checked)
                                        }
                                        disabled={selectedIds.length === 0}
                                    >
                                        Chỉ hiển thị thực nghiệm cùng nguồn nội dung với thực nghiệm đầu tiên đang chọn
                                    </Checkbox>
                                    <Text type="secondary" className="experiment-comparison-page-source-filter-help">
                                        Bộ lọc dựa trên <code>contentSource.contentHash</code>. Hãy chọn một thực nghiệm trước để làm mốc.
                                    </Text>
                                </div>
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

            {hasDifferentSources && (
                <Alert
                    className="experiment-comparison-page-source-warning"
                    type="warning"
                    showIcon
                    message="Các thực nghiệm sử dụng nguồn nội dung khác nhau — kết quả so sánh có thể không công bằng."
                    description="Để so sánh công bằng hơn, hãy bật bộ lọc cùng nguồn nội dung hoặc chọn lại các thực nghiệm có cùng contentHash."
                />
            )}

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
                            {selectedExperiments.length}
                        </Descriptions.Item>

                        <Descriptions.Item label="Model">
                            {selectedExperiments.length > 0 ? (
                                <Space size={6} wrap>
                                    {Array.from(new Set(selectedExperiments.map((item) => item.modelName))).map((model) => (
                                        <Tag key={model}>{model}</Tag>
                                    ))}
                                </Space>
                            ) : (
                                <Text type="secondary">Chưa chọn</Text>
                            )}
                        </Descriptions.Item>

                        <Descriptions.Item label="Môn học">
                            {selectedExperiments.length > 0
                                ? Array.from(new Set(selectedExperiments.map((item) => item.subject))).join(", ")
                                : <Text type="secondary">Chưa chọn</Text>}
                        </Descriptions.Item>

                        <Descriptions.Item label="Chiến lược">
                            {selectedExperiments.length > 0 ? (
                                <Space size={6} wrap>
                                    {Array.from(new Set(selectedExperiments.map((item) => item.strategy))).map((strategy) => (
                                        <Tag key={strategy}>{strategy}</Tag>
                                    ))}
                                </Space>
                            ) : (
                                <Text type="secondary">Chưa chọn</Text>
                            )}
                        </Descriptions.Item>

                        <Descriptions.Item label="Số câu">
                            {selectedExperiments.length > 0
                                ? Array.from(new Set(selectedExperiments.map((item) => item.questionCount))).length === 1
                                    ? `${selectedExperiments[0].questionCount} câu / thực nghiệm`
                                    : Array.from(new Set(selectedExperiments.map((item) => item.questionCount)))
                                        .map((count) => `${count} câu`).join(", ")
                                : <Text type="secondary">Chưa chọn</Text>}
                        </Descriptions.Item>

                        <Descriptions.Item label="Trạng thái">
                            {selectedExperiments.length > 0 && selectedExperiments.every((item) => item.status === "COMPLETED") ? (
                                <Tag color="success">Đã hoàn thành</Tag>
                            ) : selectedExperiments.some((item) => item.status === "RUNNING") ? (
                                <Tag color="processing">Đang chạy</Tag>
                            ) : selectedExperiments.some((item) => item.status === "FAILED") ? (
                                <Tag color="error">Có thực nghiệm thất bại</Tag>
                            ) : (
                                <Text type="secondary">Chưa có dữ liệu</Text>
                            )}
                        </Descriptions.Item>

                        <Descriptions.Item label="Nguồn nội dung">
                            {selectedExperiments.length === 0 ? (
                                <Text type="secondary">Chưa chọn thực nghiệm</Text>
                            ) : !hasDifferentSources ? (
                                <Space direction="vertical" size={2}>
                                    <Space size={6} wrap>
                                        <Tag color="success">Cùng nguồn</Tag>
                                        <Text>{distinctSources[0]?.title}</Text>
                                    </Space>
                                    <Text type="secondary" className="experiment-comparison-page-content-hash">
                                        contentHash: {distinctSources[0]?.contentHash}
                                    </Text>
                                </Space>
                            ) : (
                                <Space direction="vertical" size={8} className="experiment-comparison-page-source-list">
                                    <Tag color="warning">Khác nguồn nội dung ({distinctSources.length} nguồn)</Tag>
                                    {distinctSources.map((source, index) => (
                                        <div key={source.contentHash} className="experiment-comparison-page-source-item">
                                            <Text>{`Nguồn ${index + 1}: ${source.title}`}</Text>
                                            <Text type="secondary" className="experiment-comparison-page-content-hash">
                                                contentHash: {source.contentHash}
                                            </Text>
                                        </div>
                                    ))}
                                </Space>
                            )}
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
