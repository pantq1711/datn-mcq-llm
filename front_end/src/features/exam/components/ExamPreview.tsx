import { Empty, Skeleton, Typography } from "antd";

import "./ExamPreview.css";
import type { ExamPreviewData } from "../types/exam.types";

interface ExamPreviewProps {
    exam?: ExamPreviewData | null;
    loading?: boolean;
}

/** Bảng "Ma trận cấu trúc đề thi": số câu theo từng Phần, cộng lại đúng tổng. */
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
            <div className="exam-preview-matrix-section">
                <Typography.Title level={5} className="exam-preview-section-title">
                    Ma trận cấu trúc đề thi
                </Typography.Title>
                <table className="exam-preview-matrix-table">
                    <thead>
                        <tr>
                            <th>Phần</th>
                            <th style={{ textAlign: "right" }}>Số câu</th>
                        </tr>
                    </thead>
                    <tbody>
                        {exam.parts.map((p) => (
                            <tr key={p.part}>
                                <td>{p.label}</td>
                                <td style={{ textAlign: "right" }}>{p.count}</td>
                            </tr>
                        ))}
                        <tr style={{ fontWeight: 600, background: "#fafafa" }}>
                            <td>Tổng số câu</td>
                            <td style={{ textAlign: "right" }}>{exam.questionCount}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    );
}
