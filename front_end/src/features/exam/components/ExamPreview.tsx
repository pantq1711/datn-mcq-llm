import {
    Alert,
    Descriptions,
    Empty,
    Skeleton,
    Space,
    Tag,
    Typography,
} from "antd";
import {
    ClockCircleOutlined,
    FileTextOutlined,
    BookOutlined,
} from "@ant-design/icons";

import "./ExamPreview.css";

const { Title, Paragraph, Text } = Typography;

export type ExamPreviewDifficulty =
    | "ALL"
    | "EASY"
    | "MEDIUM"
    | "HARD";

export interface ExamPreviewData {
    title: string;
    subject: string;
    questionCount: number;
    duration: number;
    difficulty: ExamPreviewDifficulty;
    instructions?: string;
}

interface ExamPreviewProps {
    exam?: ExamPreviewData | null;
    loading?: boolean;
}

const difficultyConfig: Record<
    ExamPreviewDifficulty,
    {
        label: string;
        color: string;
    }
> = {
    ALL: {
        label: "Tất cả mức độ",
        color: "blue",
    },
    EASY: {
        label: "Dễ",
        color: "green",
    },
    MEDIUM: {
        label: "Trung bình",
        color: "orange",
    },
    HARD: {
        label: "Khó",
        color: "red",
    },
};

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

    const difficulty = difficultyConfig[exam.difficulty];

    return (
        <div className="exam-preview">
            <div className="exam-preview-header">
                <Space
                    direction="vertical"
                    size={8}
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
                        <Tag
                            icon={<BookOutlined />}
                            color="blue"
                        >
                            {exam.subject}
                        </Tag>

                        <Tag
                            icon={<FileTextOutlined />}
                        >
                            {exam.questionCount} câu
                        </Tag>

                        <Tag
                            icon={<ClockCircleOutlined />}
                        >
                            {exam.duration} phút
                        </Tag>

                        <Tag color={difficulty.color}>
                            {difficulty.label}
                        </Tag>
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

            <Paragraph
                type="secondary"
                className="exam-preview-note"
            >
                Danh sách và thứ tự câu hỏi sẽ được hiển thị ở khu vực
                bên cạnh.
            </Paragraph>
        </div>
    );
}