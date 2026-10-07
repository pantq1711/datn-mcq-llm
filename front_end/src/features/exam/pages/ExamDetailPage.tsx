import { Button, Card, Space, Tag, Typography } from "antd";
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import ExamPreview, { type ExamPreviewData } from "../components/ExamPreview";
import ExamQuestionList, { type ExamQuestionItem } from "../components/ExamQuestionList";

import "./ExamDetailPage.css";

const { Title, Text } = Typography;

export default function ExamDetailPage() {
    const navigate = useNavigate();

    const exam: ExamPreviewData = {
        title: "Đề thi giữa kỳ Hóa học",
        subject: "Hóa học",
        questionCount: 20,
        duration: 30,
        difficulty: "MEDIUM",
    };

    const questions: ExamQuestionItem[] = [];

    return (
        <div className="exam-detail-page">
            <div className="exam-detail-page-header">
                <div className="exam-detail-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="exam-detail-page-back-button"
                        onClick={() => navigate("/exams")}
                    >
                        Quay lại danh sách
                    </Button>
                </div>

                <div className="exam-detail-page-header-content">
                    <div className="exam-detail-page-title-section">
                        <Title
                            level={2}
                            className="exam-detail-page-title"
                        >
                            {exam.title}
                        </Title>

                        <Space
                            size={8}
                            wrap
                            className="exam-detail-page-meta"
                        >
                            <Tag color="success">
                                Đã phát hành
                            </Tag>

                            <Text type="secondary">
                                {exam.subject}
                            </Text>
                        </Space>
                    </div>

                    <Space
                        size={8}
                        className="exam-detail-page-actions"
                    >
                        <Button
                            icon={<EditOutlined />}
                            onClick={() =>
                                console.log(
                                    "Edit exam",
                                )
                            }
                        >
                            Chỉnh sửa
                        </Button>

                        <Button
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() =>
                                console.log(
                                    "Delete exam",
                                )
                            }
                        >
                            Xóa
                        </Button>
                    </Space>
                </div>
            </div>

            <div className="exam-detail-page-overview">
                <Card title="Thông tin đề thi">
                    <ExamPreview exam={exam} />
                </Card>
            </div>

            <div className="exam-detail-page-question-list">
                <Card title="Danh sách câu hỏi">
                    <ExamQuestionList
                        questions={questions}
                    />
                </Card>
            </div>
        </div>
    );
}