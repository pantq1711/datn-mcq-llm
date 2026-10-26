import { Button, Card, Typography, message } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";

import ExamTable from "../components/ExamTable";
import type { ExamListItem } from "../types/exam.types";
import { getMyExams } from "../services/examApi";

import "./ExamListPage.css";

const { Title } = Typography;

export default function ExamListPage() {
    const navigate = useNavigate();

    // 1. Khởi tạo các state theo yêu cầu
    const [exams, setExams] = useState<ExamListItem[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [page, setPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<number>(10);
    const [total, setTotal] = useState<number>(0);
    const [selectedKind, setSelectedKind] = useState<string>(""); // Tùy chọn lọc theo loại đề

    // 2. Hàm gọi API lấy danh sách đề thi
    const fetchExams = async (currentPage: number, currentPageSize: number) => {
        setLoading(true);
        try {
            // Lưu ý: Ant Design page bắt đầu từ 1, backend Spring Data bắt đầu từ 0
            const res = await getMyExams({
                page: currentPage - 1,
                size: currentPageSize,
            });
            setExams(res.items);
            setTotal(res.totalItems);
        } catch (error) {
            message.error("Không thể tải danh sách đề thi. Vui lòng thử lại!");
        } finally {
            setLoading(false);
        }
    };

    // 3. Sử dụng useEffect để gọi API khi page, pageSize hoặc selectedKind thay đổi
    useEffect(() => {
        fetchExams(page, pageSize);
    }, [page, pageSize]);

    const handleViewExam = (exam: ExamListItem) => {
        navigate(`/exams/${exam.examId}`);
    };

    const handlePageChange = (newPage: number, newPageSize: number) => {
        setPage(newPage);
        setPageSize(newPageSize);
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
                        data={exams}
                        loading={loading}
                        total={total}
                        page={page}
                        pageSize={pageSize}
                        onView={handleViewExam}
                        onPageChange={handlePageChange}
                    />
                </Card>
            </div>
        </div>
    );
}