import {
    Button,
    Card,
    Col,
    Input,
    Row,
    Select,
    Statistic,
    Table,
    Tag,
    Typography,
} from "antd";
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseCircleOutlined,
    EditOutlined,
    ReloadOutlined,
    SearchOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import "./ReviewQuestionsPage.css";

const { Title, Text } = Typography;

interface ReviewQuestion {
    id: number;
    question: string;
    subject: string;
    qualityStatus: "PASSED" | "FAILED";
    reviewStatus: "PENDING" | "APPROVED" | "EDITED" | "REJECTED";
    difficulty: "EASY" | "MEDIUM" | "HARD";
    createdAt: string;
}

const reviewQuestions: ReviewQuestion[] = [
    {
        id: 101,
        question:
            "Một vật chuyển động thẳng đều với vận tốc 5 m/s. Trong 4 giây, vật đi được quãng đường bao nhiêu?",
        subject: "Vật lý",
        qualityStatus: "PASSED",
        reviewStatus: "PENDING",
        difficulty: "MEDIUM",
        createdAt: "05/10/2026 14:30",
    },
    {
        id: 102,
        question:
            "Hành tinh nào nằm gần Mặt Trời nhất trong hệ Mặt Trời?",
        subject: "Vật lý",
        qualityStatus: "PASSED",
        reviewStatus: "PENDING",
        difficulty: "EASY",
        createdAt: "05/10/2026 14:18",
    },
    {
        id: 103,
        question:
            "Cơ quan nào giữ vai trò bơm máu đi khắp cơ thể người?",
        subject: "Sinh học",
        qualityStatus: "FAILED",
        reviewStatus: "PENDING",
        difficulty: "EASY",
        createdAt: "05/10/2026 13:52",
    },
];

const getQualityStatusTag = (
    status: ReviewQuestion["qualityStatus"],
) => {
    if (status === "PASSED") {
        return <Tag color="success">Đạt QC</Tag>;
    }

    return <Tag color="error">Không đạt QC</Tag>;
};

const getReviewStatusTag = (
    status: ReviewQuestion["reviewStatus"],
) => {
    switch (status) {
        case "PENDING":
            return <Tag color="processing">Chờ duyệt</Tag>;

        case "APPROVED":
            return <Tag color="success">Đã duyệt</Tag>;

        case "EDITED":
            return <Tag color="warning">Đã chỉnh sửa</Tag>;

        case "REJECTED":
            return <Tag color="error">Từ chối</Tag>;

        default:
            return null;
    }
};

const getDifficultyTag = (
    difficulty: ReviewQuestion["difficulty"],
) => {
    switch (difficulty) {
        case "EASY":
            return <Tag>Dễ</Tag>;

        case "MEDIUM":
            return <Tag color="blue">Trung bình</Tag>;

        case "HARD":
            return <Tag color="purple">Khó</Tag>;

        default:
            return null;
    }
};

export default function ReviewQuestionsPage() {
    const navigate = useNavigate();

    const handleViewQuestion = (id: number) => {
        navigate(`/review/${id}`);
    };

    const columns: ColumnsType<ReviewQuestion> = [
        {
            title: "ID",
            dataIndex: "id",
            key: "id",
            width: 80,
        },
        {
            title: "Câu hỏi",
            dataIndex: "question",
            key: "question",
            ellipsis: true,
        },
        {
            title: "Môn học",
            dataIndex: "subject",
            key: "subject",
            width: 130,
        },
        {
            title: "QC tự động",
            dataIndex: "qualityStatus",
            key: "qualityStatus",
            width: 120,
            render: (status) => getQualityStatusTag(status),
        },
        {
            title: "Độ khó",
            dataIndex: "difficulty",
            key: "difficulty",
            width: 120,
            render: (difficulty) => getDifficultyTag(difficulty),
        },
        {
            title: "Trạng thái",
            dataIndex: "reviewStatus",
            key: "reviewStatus",
            width: 120,
            render: (status) => getReviewStatusTag(status),
        },
        {
            title: "Ngày tạo",
            dataIndex: "createdAt",
            key: "createdAt",
            width: 160,
        },
        {
            title: "Thao tác",
            key: "action",
            width: 100,
            render: (_, record) => (
                <Button
                    type="link"
                    onClick={() => handleViewQuestion(record.id)}
                >
                    Xem
                </Button>
            ),
        },
    ];

    return (
        <div className="review-questions-page">
            <div className="review-questions-header">
                <div>
                    <Title level={2} className="review-questions-title">
                        Duyệt câu hỏi
                    </Title>

                    <Text type="secondary">
                        Xem và kiểm duyệt các câu hỏi trắc nghiệm được hệ thống sinh tự động.
                    </Text>
                </div>
            </div>

            <Row gutter={[16, 16]} className="review-summary">
                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="Chờ duyệt"
                            value={24}
                            prefix={<ClockCircleOutlined />}
                        />
                    </Card>
                </Col>

                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="Đã duyệt"
                            value={156}
                            prefix={<CheckCircleOutlined />}
                        />
                    </Card>
                </Col>

                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="Đã chỉnh sửa"
                            value={31}
                            prefix={<EditOutlined />}
                        />
                    </Card>
                </Col>

                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="Từ chối"
                            value={18}
                            prefix={<CloseCircleOutlined />}
                        />
                    </Card>
                </Col>
            </Row>

            <Card className="review-filter-card">
                <div className="review-filter">
                    <Input
                        className="review-search"
                        placeholder="Tìm kiếm câu hỏi..."
                        prefix={<SearchOutlined />}
                    />

                    <Select
                        className="review-filter-select"
                        placeholder="Môn học"
                        allowClear
                        options={[
                            { label: "Toán", value: "math" },
                            { label: "Vật lý", value: "physics" },
                            { label: "Hóa học", value: "chemistry" },
                            { label: "Sinh học", value: "biology" },
                        ]}
                    />

                    <Select
                        className="review-filter-select"
                        placeholder="Trạng thái QC"
                        allowClear
                        options={[
                            { label: "Đạt QC", value: "PASSED" },
                            { label: "Không đạt QC", value: "FAILED" },
                        ]}
                    />

                    <Select
                        className="review-filter-select"
                        placeholder="Trạng thái duyệt"
                        allowClear
                        options={[
                            { label: "Chờ duyệt", value: "PENDING" },
                            { label: "Đã duyệt", value: "APPROVED" },
                            { label: "Đã chỉnh sửa", value: "EDITED" },
                            { label: "Từ chối", value: "REJECTED" },
                        ]}
                    />

                    <Button icon={<ReloadOutlined />}>
                        Đặt lại
                    </Button>
                </div>
            </Card>

            <Card className="review-question-list-card">
                <div className="review-question-list-header">
                    <div>
                        <Title level={4} className="review-question-list-title">
                            Danh sách câu hỏi
                        </Title>

                        <Text type="secondary">
                            Các câu hỏi được sinh từ hệ thống và đang chờ giáo viên kiểm duyệt.
                        </Text>
                    </div>
                </div>

                <Table
                    rowKey="id"
                    columns={columns}
                    dataSource={reviewQuestions}
                    pagination={{
                        pageSize: 10,
                        showSizeChanger: true,
                        showTotal: (total) => `Tổng ${total} câu hỏi`,
                    }}
                    scroll={{ x: 1100 }}
                />
            </Card>
        </div>
    );
}