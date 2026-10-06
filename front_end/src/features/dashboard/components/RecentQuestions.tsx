import {
    Button,
    Card,
    List,
    Tag,
    Typography,
} from "antd";
import {
    ClockCircleOutlined,
    RightOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import "./RecentQuestions.css";

const { Text } = Typography;

interface RecentQuestion {
    id: number;
    question: string;
    subject: string;
    reviewStatus: "PENDING" | "APPROVED" | "EDITED" | "REJECTED";
    createdAt: string;
}

const recentQuestions: RecentQuestion[] = [
    {
        id: 101,
        question:
            "Một vật chuyển động thẳng đều với vận tốc 5 m/s. Trong 4 giây, vật đi được quãng đường bao nhiêu?",
        subject: "Vật lý",
        reviewStatus: "PENDING",
        createdAt: "05/10/2026 14:30",
    },
    {
        id: 102,
        question:
            "Hành tinh nào nằm gần Mặt Trời nhất trong hệ Mặt Trời?",
        subject: "Vật lý",
        reviewStatus: "PENDING",
        createdAt: "05/10/2026 14:18",
    },
    {
        id: 103,
        question:
            "Cơ quan nào giữ vai trò bơm máu đi khắp cơ thể người?",
        subject: "Sinh học",
        reviewStatus: "APPROVED",
        createdAt: "05/10/2026 13:52",
    },
    {
        id: 104,
        question:
            "Phản ứng giữa axit và bazơ tạo ra sản phẩm chính nào?",
        subject: "Hóa học",
        reviewStatus: "EDITED",
        createdAt: "05/10/2026 13:35",
    },
    {
        id: 105,
        question:
            "Nghiệm của phương trình bậc hai được xác định dựa trên đại lượng nào?",
        subject: "Toán",
        reviewStatus: "REJECTED",
        createdAt: "05/10/2026 13:10",
    },
];

const getReviewStatusTag = (
    status: RecentQuestion["reviewStatus"],
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

export default function RecentQuestions() {
    const navigate = useNavigate();

    const handleViewQuestion = (id: number) => {
        navigate(`/review/${id}`);
    };

    const handleViewAll = () => {
        navigate("/review");
    };

    return (
        <Card
            title="Câu hỏi gần đây"
            className="recent-questions"
            extra={
                <Button
                    type="link"
                    onClick={handleViewAll}
                    className="recent-questions-view-all"
                >
                    Xem tất cả
                </Button>
            }
        >
            <List
                dataSource={recentQuestions}
                renderItem={(item) => (
                    <List.Item
                        className="recent-question-item"
                        actions={[
                            <Button
                                key="view"
                                type="link"
                                icon={<RightOutlined />}
                                onClick={() =>
                                    handleViewQuestion(item.id)
                                }
                            />,
                        ]}
                    >
                        <List.Item.Meta
                            title={
                                <Typography.Link
                                    onClick={() =>
                                        handleViewQuestion(item.id)
                                    }
                                    className="recent-question-title"
                                >
                                    {item.question}
                                </Typography.Link>
                            }
                            description={
                                <div className="recent-question-meta">
                                    <Text type="secondary">
                                        {item.subject}
                                    </Text>

                                    {getReviewStatusTag(
                                        item.reviewStatus,
                                    )}

                                    <Text
                                        type="secondary"
                                        className="recent-question-time"
                                    >
                                        <ClockCircleOutlined />
                                        {item.createdAt}
                                    </Text>
                                </div>
                            }
                        />
                    </List.Item>
                )}
            />
        </Card>
    );
}