import { Button, Space, Table, Tag, Tooltip, Typography, } from "antd";
import type { TableColumnsType, TablePaginationConfig } from "antd";
import { EyeOutlined, EyeInvisibleOutlined } from "@ant-design/icons";
import "./QuestionTable.css";
import type { QuestionDifficulty, QuestionStatus, QuestionTableItem } from "../types/questionBank.types";

const { Text } = Typography;

interface QuestionTableProps {
    data: QuestionTableItem[];
    loading?: boolean;
    total?: number;
    page?: number;
    pageSize?: number;
    onPageChange?: (page: number, pageSize: number) => void;
    onView?: (question: QuestionTableItem) => void;
    onHide?: (question: QuestionTableItem) => void; // Thêm callback ẩn câu hỏi
}

const difficultyMap = {
    easy: {
        label: "Biết",
        color: "green",
    },
    medium: {
        label: "Hiểu",
        color: "orange",
    },
    hard: {
        label: "Vận dụng",
        color: "red",
    },
} as const;

const statusMap = {
    approved: {
        label: "Đã duyệt",
        color: "green",
    },
    edited: {
        label: "Đã chỉnh sửa",
        color: "blue",
    },
} as const;

export default function QuestionTable({
    data,
    loading = false,
    total,
    page = 1,
    pageSize = 10,
    onPageChange,
    onView,
    onHide,
}: QuestionTableProps) {
    const columns: TableColumnsType<QuestionTableItem> = [
        {
            title: "Mã",
            dataIndex: "id",
            key: "id",
            width: 80,
            align: "center",
            render: (id: number) => (
                <Text strong>#{id}</Text>
            ),
        },
        {
            title: "Câu hỏi",
            dataIndex: "questionText",
            key: "questionText",
            ellipsis: {
                showTitle: false,
            },
            render: (questionText: string) => (
                <Tooltip title={questionText} placement="topLeft">
                    <span className="question-table-question">
                        {questionText}
                    </span>
                </Tooltip>
            ),
        },
        {
            title: "Môn học",
            dataIndex: "subject",
            key: "subject",
            width: 160,
        },
        {
            title: "Độ khó",
            dataIndex: "difficulty",
            key: "difficulty",
            width: 130,
            align: "center",
            render: (difficulty: QuestionDifficulty) => {
                const config = difficultyMap[difficulty];

                return (
                    <Tag color={config.color}>
                        {config.label}
                    </Tag>
                );
            },
        },
        {
            title: "Trạng thái",
            dataIndex: "status",
            key: "status",
            width: 140,
            align: "center",
            render: (status: QuestionStatus) => {
                const config = statusMap[status];

                return (
                    <Tag color={config.color}>
                        {config.label}
                    </Tag>
                );
            },
        },
        {
            title: "Ngày tạo",
            dataIndex: "createdAt",
            key: "createdAt",
            width: 140,
        },
        {
            title: "Thao tác",
            key: "action",
            width: 120, // Tăng nhẹ width để vừa 2 nút
            align: "center",
            render: (_, record) => (
                <Space size={0}>
                    <Tooltip title="Xem chi tiết">
                        <Button
                            type="text"
                            icon={<EyeOutlined />}
                            onClick={() => onView?.(record)}
                        />
                    </Tooltip>
                    <Tooltip title="Ẩn câu hỏi">
                        <Button
                            type="text"
                            danger // Có thể thêm màu đỏ nhẹ hoặc giữ mặc định tùy ý
                            icon={<EyeInvisibleOutlined />}
                            onClick={() => onHide?.(record)}
                        />
                    </Tooltip>
                </Space>
            ),
        },
    ];

    const pagination: TablePaginationConfig = {
        current: page,
        pageSize,
        total: total ?? data.length,
        showSizeChanger: true,
        showTotal: (totalItems, range) =>
            `${range[0]}-${range[1]} trong tổng số ${totalItems} câu hỏi`,
        onChange: (nextPage, nextPageSize) => {
            onPageChange?.(nextPage, nextPageSize);
        },
    };

    return (
        <div className="question-table">
            <Table<QuestionTableItem>
                rowKey="id"
                columns={columns}
                dataSource={data}
                loading={loading}
                pagination={pagination}
                scroll={{ x: 900 }}
            />
        </div>
    );
}