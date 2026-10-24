import { Button, Card, Space, Tag, Typography } from "antd";
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import ExamPreview from "../components/ExamPreview";
import ExamQuestionList from "../components/ExamQuestionList";
import type { ExamPreviewData, ExamQuestionItem } from "../types/exam.types";

import "./ExamDetailPage.css";

const { Title, Text } = Typography;

export default function ExamDetailPage() {
    const navigate = useNavigate();

    const exam: ExamPreviewData = {
        title: "Đề thi giữa kỳ Hóa học",
        subject: "Hóa học",
        questionCount: 3,
        duration: 30,
        easyCount: 1,
        mediumCount: 1,
        hardCount: 1,
    };

    // Bổ sung mock data câu hỏi để ExamQuestionList hiển thị
    const questions: ExamQuestionItem[] = [
        {
            id: 1,
            questionText: "Nguyên tố hóa học nào có ký hiệu hóa học là 'H'?",
            optionA: "Heli",
            optionB: "Hiđrô",
            optionC: "Hafni",
            optionD: "Holmi",
            correctOption: "B",
            explanation: "Hiđrô là nguyên tố hóa học có ký hiệu là H, là nguyên tố nhẹ nhất trong bảng tuần hoàn.",
            difficulty: "easy",
        },
        {
            id: 2,
            questionText: "Dung dịch axit nào sau đây làm quỳ tím chuyển sang màu đỏ?",
            optionA: "NaCl",
            optionB: "NaOH",
            optionC: "HCl",
            optionD: "Ba(OH)₂",
            correctOption: "C",
            explanation: "HCl là axit mạnh, làm quỳ tím hóa đỏ. NaCl là muối trung tính, NaOH và Ba(OH)₂ là bazơ làm quỳ tím hóa xanh.",
            difficulty: "medium",
        },
        {
            id: 3,
            questionText: "Oxit nào sau đây là oxit lưỡng tính?",
            optionA: "SO₂",
            optionB: "Al₂O₃",
            optionC: "CaO",
            optionD: "CO₂",
            correctOption: "B",
            explanation: "Al₂O₃ (Nhôm oxit) là oxit lưỡng tính, vừa tác dụng với axit vừa tác dụng với bazơ.",
            difficulty: "hard",
        },
    ];

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