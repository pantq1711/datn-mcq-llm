import { Alert, Col, Empty, Row, Skeleton, Space, Tag, Typography } from "antd";
import { ClockCircleOutlined, FileTextOutlined, BookOutlined } from "@ant-design/icons";

import "./ExamPreview.css";
import type { ExamPreviewData } from "../types/exam.types";

const { Title } = Typography;

interface ExamPreviewProps {
    exam?: ExamPreviewData | null;
    loading?: boolean;
}

const DEFAULT_INSTRUCTIONS =
    "Thí sinh đọc kỹ câu hỏi và chọn một đáp án đúng cho mỗi câu. " +
    "Kiểm tra lại bài trước khi nộp.";

export default function ExamPreview({
    exam = null,
    loading = false,
}: ExamPreviewProps) {
    if (loading) {
        return (
            <div className="exam-preview">
                <Skeleton
                    active
                    paragraph={{
                        rows: 6,
                    }}
                />
            </div>
        );
    }

    if (!exam) {
        return (
            <div className="exam-preview exam-preview-empty">
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="Chưa có đề thi để xem trước"
                />
            </div>
        );
    }

    return (
        <div className="exam-preview">
            <Row gutter={[16, 16]} align="top">
                <Col xs={24} lg={15}>
                    <div className="exam-preview-left-content">
                        <div className="exam-preview-header">
                            <Space
                                direction="vertical"
                                size={12}
                                className="exam-preview-header-content"
                            >
                                <Title
                                    level={3}
                                    className="exam-preview-title"
                                >
                                    {exam.title}
                                </Title>

                                <Space
                                    wrap
                                    size={[8, 8]}
                                    className="exam-preview-tags"
                                >
                                    {exam.subject && (
                                        <Tag
                                            icon={<BookOutlined />}
                                            color="blue"
                                        >
                                            {exam.subject}
                                        </Tag>
                                    )}

                                    <Tag
                                        icon={<FileTextOutlined />}
                                    >
                                        {exam.questionCount} câu
                                    </Tag>

                                    {exam.duration !== undefined && (
                                        <Tag
                                            icon={<ClockCircleOutlined />}
                                        >
                                            {exam.duration} phút
                                        </Tag>
                                    )}
                                </Space>
                            </Space>
                        </div>

                        <div className="exam-preview-instructions">
                            <Alert
                                type="info"
                                showIcon
                                message="Hướng dẫn làm bài"
                                description={
                                    exam.instructions ||
                                    DEFAULT_INSTRUCTIONS
                                }
                            />
                        </div>
                    </div>
                </Col>

                <Col xs={24} lg={9}>
                    <div className="exam-preview-matrix-section">
                        <Typography.Title level={5} className="exam-preview-section-title">
                            Ma trận cấu trúc đề thi
                        </Typography.Title>
                        <table className="exam-preview-matrix-table">
                            <thead>
                                <tr>
                                    <th>Mức độ nhận thức</th>
                                    <th style={{ textAlign: "right" }}>Số câu</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>Biết</td>
                                    <td style={{ textAlign: "right" }}>{exam.easyCount ?? 0}</td>
                                </tr>
                                <tr>
                                    <td>Hiểu</td>
                                    <td style={{ textAlign: "right" }}>{exam.mediumCount ?? 0}</td>
                                </tr>
                                <tr>
                                    <td>Vận dụng</td>
                                    <td style={{ textAlign: "right" }}>{exam.hardCount ?? 0}</td>
                                </tr>
                                <tr>
                                    <td>Đúng/Sai (mức theo từng ý)</td>
                                    <td style={{ textAlign: "right" }}>{exam.tfCount ?? 0}</td>
                                </tr>
                                {(exam.missingCount ?? 0) > 0 && (
                                    <tr style={{ color: "#8c8c8c" }}>
                                        <td>Chưa có câu</td>
                                        <td style={{ textAlign: "right" }}>{exam.missingCount}</td>
                                    </tr>
                                )}
                                <tr style={{ fontWeight: 600, background: "#fafafa" }}>
                                    <td>Tổng số câu</td>
                                    <td style={{ textAlign: "right" }}>{exam.questionCount}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </Col>
            </Row>
        </div>
    );
}