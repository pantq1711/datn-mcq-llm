import { Badge, Button, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { EyeOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import "./ExamTable.css";
import type { ExamListItem, ExamStatus } from "../types/exam.types";

const { Text } = Typography;

interface ExamTableProps {
    data: ExamListItem[];
    loading?: boolean;
    total?: number;
    page?: number;
    pageSize?: number;
    onPageChange?: (page: number, pageSize: number) => void;
    onView?: (exam: ExamListItem) => void;
}

const statusConfig: Record<string, { label: string; color: string }> = {
    draft: { label: "Bản nháp", color: "default" },
    confirmed: { label: "Đã xác nhận", color: "success" },
    DRAFT: { label: "Bản nháp", color: "default" },
    PUBLISHED: { label: "Đã phát hành", color: "success" },
    ARCHIVED: { label: "Lưu trữ", color: "warning" },
};

const kindConfig: Record<string, { label: string; color: string }> = {
    matrix: { label: "Ma trận", color: "blue" },
    lesson: { label: "Theo bài", color: "purple" },
    manual: { label: "Thủ công", color: "default" },
    MATRIX: { label: "Ma trận", color: "blue" },
    BY_TOPIC: { label: "Theo bài", color: "purple" },
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
    const columns: ColumnsType<ExamListItem> = [
        {
            title: "Mã đề",
            dataIndex: "examCode",
            key: "examCode",
            width: 110,
            align: "center",
            render: (_, record) => <Text strong>{record.examCode || "—"}</Text>,
        },
        {
            title: "Tên đề thi",
            dataIndex: "title",
            key: "title",
            width: 250,
            render: (title: string) => (
                <Tooltip title={title}>
                    <Text className="exam-table-title" ellipsis>
                        {title}
                    </Text>
                </Tooltip>
            ),
        },
        {
            title: "Loại đề",
            dataIndex: "kind",
            key: "kind",
            width: 110,
            align: "center",
            render: (kind: string) => {
                const config = kindConfig[kind] || { label: kind, color: "default" };
                return <Tag color={config.color}>{config.label}</Tag>;
            },
        },
        {
            title: "Số câu & Tiến độ",
            key: "progress",
            width: 160,
            align: "center",
            render: (_, record) => {
                const done = (record.keptSlots ?? 0) + (record.readySlots ?? 0);
                const total = record.totalSlots ?? 0;
                const generating = record.generatingSlots ?? 0;
                const failed = record.failedSlots ?? 0;

                return (
                    <Space direction="vertical" size={2} style={{ display: "flex" }}>
                        <Text>{`${done} / ${total} câu`}</Text>
                        {generating > 0 && (
                            <Tag color="processing" style={{ fontSize: 11, margin: 0 }}>
                                {generating} đang sinh...
                            </Tag>
                        )}
                        {failed > 0 && (
                            <Tag color="error" style={{ fontSize: 11, margin: 0 }}>
                                {failed} thất bại
                            </Tag>
                        )}
                    </Space>
                );
            },
        },
        {
            title: "Trạng thái sinh",
            key: "generatingStatus",
            width: 140,
            align: "center",
            render: (_, record) => {
                const generating = record.generatingSlots ?? 0;
                const failed = record.failedSlots ?? 0;

                if (generating > 0) {
                    return <Tag color="processing">{generating} đang tạo</Tag>;
                }
                if (failed > 0) {
                    return <Tag color="error">{failed} lỗi</Tag>;
                }
                return <Tag color="success">Hoàn tất</Tag>;
            },
        },
        {
            title: "Cần xem lại",
            dataIndex: "needsReviewSlots",
            key: "needsReviewSlots",
            width: 120,
            align: "center",
            render: (needsReviewSlots: number = 0) => {
                if (needsReviewSlots > 0) {
                    return (
                        <Badge
                            count={needsReviewSlots}
                            style={{ backgroundColor: "#faad14" }}
                        >
                            <Tag color="warning">Cần xem lại</Tag>
                        </Badge>
                    );
                }
                return <Tag color="success">Đạt</Tag>;
            },
        },
        {
            title: "Trạng thái",
            dataIndex: "status",
            key: "status",
            width: 130,
            align: "center",
            render: (status: ExamStatus) => {
                const config = statusConfig[status] || { label: status, color: "default" };
                return <Tag color={config.color}>{config.label}</Tag>;
            },
        },
        {
            title: "Ngày tạo",
            dataIndex: "createdAt",
            key: "createdAt",
            width: 150,
            align: "center",
            render: (createdAt: string) =>
                createdAt ? dayjs(createdAt).format("DD/MM/YYYY HH:mm") : "—",
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
            <Table<ExamListItem>
                rowKey="examId"
                columns={columns}
                dataSource={data}
                loading={loading}
                scroll={{ x: 1150 }}
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