import { Button, Card, Input, Select, Table, Tag, Typography } from "antd";
import { ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import "./ReviewQuestionsPage.css";
import type { ReviewQuestion } from "../types/review.types";

const { Title } = Typography;

const reviewQuestions: ReviewQuestion[] = [
    {
        id: 101,
        question:
            "Một vật chuyển động thẳng đều với vận tốc 5 m/s. Trong 4 giây, vật đi được quãng đường bao nhiêu?",
        subject: "Vật lý",
        qualityStatus: "qc_passed",
        reviewStatus: "pending",
        difficulty: "medium",
        createdAt: "05/10/2026 14:30",
    },
    {
        id: 102,
        question:
            "Hành tinh nào nằm gần Mặt Trời nhất trong hệ Mặt Trời?",
        subject: "Vật lý",
        qualityStatus: "qc_passed",
        reviewStatus: "pending",
        difficulty: "easy",
        createdAt: "05/10/2026 14:18",
    },
    {
        id: 103,
        question:
            "Cơ quan nào giữ vai trò bơm máu đi khắp cơ thể người?",
        subject: "Sinh học",
        qualityStatus: "format_failed",
        reviewStatus: "pending",
        difficulty: "easy",
        createdAt: "05/10/2026 13:52",
    },
];

const getQualityStatusTag = (
    status: ReviewQuestion["qualityStatus"],
) => {
    if (status === "qc_passed") {
        return <Tag color="success">Đạt QC</Tag>;
    }

    return <Tag color="error">Không đạt QC</Tag>;
};

const getReviewStatusTag = (
    status: ReviewQuestion["reviewStatus"],
) => {
    switch (status) {
        case "pending":
            return <Tag color="processing">Chờ duyệt</Tag>;

        case "approved_as_is":
            return <Tag color="success">Đã duyệt</Tag>;

        case "approved_with_edit":
            return <Tag color="warning">Đã chỉnh sửa</Tag>;

        case "rejected":
            return <Tag color="error">Từ chối</Tag>;

        default:
            return null;
    }
};

const getDifficultyTag = (
    difficulty: ReviewQuestion["difficulty"],
) => {
    switch (difficulty) {
        case "easy":
            return <Tag>Dễ</Tag>;

        case "medium":
            return <Tag color="blue">Trung bình</Tag>;

        case "hard":
            return <Tag color="purple">Khó</Tag>;

        default:
            return null;
    }
};

export default function ReviewQuestionsPage() {
    const navigate = useNavigate();

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
    ];

    return (
        <div className="review-questions-page">
            <div className="review-questions-header">
                <div>
                    <Title level={2} className="review-questions-title">
                        Duyệt câu hỏi
                    </Title>
                </div>
            </div>

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
                            { label: "Đạt QC", value: "qc_passed" },
                            { label: "Không đạt QC", value: "format_failed" },
                        ]}
                    />

                    <Select
                        className="review-filter-select"
                        placeholder="Trạng thái duyệt"
                        allowClear
                        options={[
                            { label: "Chờ duyệt", value: "pending" },
                            { label: "Đã duyệt", value: "approved_as_is" },
                            { label: "Đã chỉnh sửa", value: "approved_with_edit" },
                            { label: "Từ chối", value: "rejected" },
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
                    </div>
                </div>

                <Table<ReviewQuestion>
                    className="review-question-table"
                    rowKey="id"
                    columns={columns}
                    dataSource={reviewQuestions}
                    pagination={{
                        pageSize: 10,
                        showSizeChanger: true,
                        showTotal: (total) => `Tổng ${total} câu hỏi`,
                    }}
                    scroll={{ x: 1000 }}
                    rowClassName={(record) =>
                        record.qualityStatus === "qc_passed"
                            ? "review-row-clickable"
                            : "review-row-disabled"
                    }
                    onRow={(record) => ({
                        onClick: () => {
                            // Chỉ cho phép chuyển trang khi đạt QC
                            if (record.qualityStatus === "qc_passed") {
                                navigate(`/review/${record.id}`);
                            }
                        },
                    })}
                />
            </Card>
        </div>
    );
}