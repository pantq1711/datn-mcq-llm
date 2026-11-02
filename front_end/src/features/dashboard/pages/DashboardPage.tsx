import { useEffect, useState } from "react";
import { Alert, Button, Card, Empty, List, Spin, Tag, Typography } from "antd";
import { PlusOutlined, WarningOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";

import { getMyExams } from "../../exam/services/examApi";
import type { ExamListItem } from "../../exam/types/exam.types";
import "./DashboardPage.css";

const { Title, Text } = Typography;

const RECENT_EXAM_COUNT = 5;

/** Tình trạng soạn của một đề, nói bằng chữ của giáo viên. */
function describeExamProgress(exam: ExamListItem): string {
    if ((exam.generatingSlots ?? 0) > 0) return `Đang soạn ${exam.generatingSlots} câu`;
    if ((exam.failedSlots ?? 0) > 0) return `${exam.failedSlots} câu chưa soạn được`;
    return "Đã soạn xong";
}

export default function DashboardPage() {
    const navigate = useNavigate();
    const [exams, setExams] = useState<ExamListItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);

    // Đề gần đây: dữ liệu thật từ GET /api/exams (mới nhất trước)
    useEffect(() => {
        let cancelled = false;
        getMyExams({ page: 0, size: RECENT_EXAM_COUNT })
            .then((res) => {
                if (!cancelled) setExams(res.items);
            })
            .catch(() => {
                if (!cancelled) setFailed(true);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, []);

    return (
        <div className="dashboard-page">
            <div className="dashboard-page-header">
                <Title level={2} className="dashboard-page-title">
                    Tổng quan
                </Title>
            </div>

            <Button
                type="primary"
                size="large"
                icon={<PlusOutlined />}
                className="dashboard-create-button"
                onClick={() => navigate("/exams/generate")}
            >
                Tạo đề mới
            </Button>

            <Card
                title="Đề gần đây"
                className="dashboard-recent"
                extra={
                    <Button type="link" onClick={() => navigate("/exams")}>
                        Xem tất cả đề thi
                    </Button>
                }
            >
                {loading ? (
                    <div className="dashboard-recent-loading">
                        <Spin />
                    </div>
                ) : failed ? (
                    <Alert type="error" showIcon message="Không tải được danh sách đề. Thầy/cô thử tải lại trang." />
                ) : exams.length === 0 ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có đề nào.">
                        <Button type="primary" size="large" onClick={() => navigate("/exams/generate")}>
                            Tạo đề đầu tiên
                        </Button>
                    </Empty>
                ) : (
                    <List
                        dataSource={exams}
                        renderItem={(exam) => (
                            <List.Item
                                className="dashboard-recent-item"
                                onClick={() => navigate(`/exams/${exam.examId}`)}
                            >
                                <div className="dashboard-recent-main">
                                    <Text strong className="dashboard-recent-title">
                                        {exam.title}
                                    </Text>
                                    <div className="dashboard-recent-meta">
                                        {exam.createdAt ? dayjs(exam.createdAt).format("DD/MM/YYYY HH:mm") : "—"}
                                        {" · "}
                                        {describeExamProgress(exam)}
                                    </div>
                                </div>
                                {(exam.needsReviewSlots ?? 0) > 0 && (
                                    <Tag color="volcano" icon={<WarningOutlined />}>
                                        Cần xem kỹ
                                    </Tag>
                                )}
                            </List.Item>
                        )}
                    />
                )}
            </Card>
        </div>
    );
}
