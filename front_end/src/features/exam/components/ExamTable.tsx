import {
    Button,
    Space,
    Table,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { EyeOutlined } from "@ant-design/icons";

import "./ExamTable.css";

const { Text } = Typography;

export type ExamStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface ExamTableItem {
    id: number;
    title: string;
    subject: string;
    questionCount: number;
    duration: number;
    status: ExamStatus;
    createdAt: string;
}

interface ExamTableProps {
    data: ExamTableItem[];
    loading?: boolean;
    total?: number;
    page?: number;
    pageSize?: number;
    onPageChange?: (page: number, pageSize: number) => void;
    onView?: (exam: ExamTableItem) => void;
}

const statusConfig: Record<
    ExamStatus,
    {
        label: string;
        color: string;
    }
> = {
    DRAFT: {
        label: "Bản nháp",
        color: "default",
    },
    PUBLISHED: {
        label: "Đã phát hành",
        color: "success",
    },
    ARCHIVED: {
        label: "Lưu trữ",
        color: "warning",
    },
};

export default function ExamTable({
    data,
    loading = false,
    total = 0,
    page = 1,
    pageSize = 10,
    onPageChange,
    onView,
}: ExamTableProps) {
    const columns: ColumnsType<ExamTableItem> = [
        {
            title: "Mã",
            dataIndex: "id",
            key: "id",
            width: 80,
            align: "center",
        },
        {
            title: "Tên đề thi",
            dataIndex: "title",
            key: "title",
            width: 280,
            render: (title: string) => (
                <Tooltip title={title}>
                    <Text className="exam-table-title" ellipsis>
                        {title}
                    </Text>
                </Tooltip>
            ),
        },
        {
            title: "Môn học",
            dataIndex: "subject",
            key: "subject",
            width: 140,
        },
        {
            title: "Số câu",
            dataIndex: "questionCount",
            key: "questionCount",
            width: 100,
            align: "center",
            render: (questionCount: number) => `${questionCount} câu`,
        },
        {
            title: "Thời gian",
            dataIndex: "duration",
            key: "duration",
            width: 110,
            align: "center",
            render: (duration: number) => `${duration} phút`,
        },
        {
            title: "Trạng thái",
            dataIndex: "status",
            key: "status",
            width: 130,
            align: "center",
            render: (status: ExamStatus) => {
                const config = statusConfig[status];

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
            width: 120,
            align: "center",
        },
        {
            title: "Thao tác",
            key: "actions",
            width: 90,
            align: "center",
            fixed: "right",
            render: (_, record) => (
                <Space size={4}>
                    <Tooltip title="Xem đề thi">
                        <Button
                            type="text"
                            icon={<EyeOutlined />}
                            onClick={() => onView?.(record)}
                        />
                    </Tooltip>
                </Space>
            ),
        },
    ];

    const handleTableChange = (pagination: TablePaginationConfig) => {
        const currentPage = pagination.current ?? 1;
        const currentPageSize = pagination.pageSize ?? pageSize;

        onPageChange?.(currentPage, currentPageSize);
    };

    return (
        <div className="exam-table">
            <Table<ExamTableItem>
                rowKey="id"
                columns={columns}
                dataSource={data}
                loading={loading}
                scroll={{ x: 1050 }}
                pagination={{
                    current: page,
                    pageSize,
                    total,
                    showSizeChanger: true,
                    pageSizeOptions: ["10", "20", "50"],
                    showTotal: (totalItems, range) =>
                        `${range[0]}-${range[1]} / ${totalItems} đề thi`,
                }}
                onChange={handleTableChange}
            />
        </div>
    );
}