import { Button, Card, Typography } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import ExamTable from "../components/ExamTable";
import type { ExamTableItem } from "../components/ExamTable";
import "./ExamListPage.css";

const { Title } = Typography;

export default function ExamListPage() {
    const examData: ExamTableItem[] = [
        {
            id: 1,
            title: "Đề thi Toán lớp 10 - Học kỳ 1",
            subject: "Toán",
            questionCount: 40,
            duration: 60,
            status: "PUBLISHED",
            createdAt: "2023-10-01",
        },
        {
            id: 2,
            title: "Đề thi Văn lớp 10 - Học kỳ 1",
            subject: "Ngữ Văn",
            questionCount: 35,
            duration: 90,
            status: "DRAFT",
            createdAt: "2023-10-02",
        },
        {
            id: 3,
            title: "Đề thi Lý lớp 10 - Học kỳ 1",
            subject: "Vật Lý",
            questionCount: 30,
            duration: 45,
            status: "ARCHIVED",
            createdAt: "2023-10-03",
        },
    ];
    const navigate = useNavigate();

    const handleViewExam = (exam: ExamTableItem) => {
        console.log("View exam:", exam);
        navigate(`/exams/${exam.id}`);
    };

    const handlePageChange = (page: number, pageSize: number) => {
        console.log("Page:", page);
        console.log("Page size:", pageSize);
    };

    return (
        <div className="exam-list-page">
            <div className="exam-list-page-header">
                <div className="exam-list-page-header-content">
                    <div>
                        <Title level={2} className="exam-list-page-title">
                            Đề thi
                        </Title>
                    </div>

                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => navigate("/exams/generate")}
                    >
                        Tạo đề thi
                    </Button>
                </div>
            </div>

            <div className="exam-list-page-table">
                <Card>
                    <ExamTable
                        data={examData}
                        total={100}
                        page={1}
                        pageSize={10}
                        onView={handleViewExam}
                        onPageChange={handlePageChange}
                    />
                </Card>
            </div>
        </div>
    );
}