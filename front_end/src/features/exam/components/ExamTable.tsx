import { Button, Empty, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { EyeOutlined, WarningOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import "./ExamTable.css";
import type { ExamKind, ExamListItem, ExamStatus } from "../types/exam.types";
import { examKindConfig, examStatusConfig } from "../../../utils/exam.utils";

const { Text } = Typography;

interface ExamTableProps {
    data: ExamListItem[];
    loading?: boolean;
    total?: number;
    page?: number;
    pageSize?: number;
    onPageChange?: (page: number, pageSize: number) => void;
    onView?: (exam: ExamListItem) => void;
    /** Bấm "Tạo đề đầu tiên" ở trạng thái rỗng. */
    onCreate?: () => void;
}


export default function ExamTable({
    data,
    loading = false,
    total = 0,
    page = 1,
    pageSize = 10,
    onPageChange,
    onView,
    onCreate,
}: ExamTableProps) {
    const columns: ColumnsType<ExamListItem> = [
        {
            title: "Mã hệ thống",
            dataIndex: "examCode",
            key: "examCode",
            width: 130,
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
            render: (kind: ExamKind) => {
                // Giá trị lạ (backend thêm loại mới) thì hiện nguyên giá trị
                const config = examKindConfig[kind] ?? { label: kind, color: "default" };
                return <Text>{config.label}</Text>;
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
                            <Text type="secondary">{generating} đang soạn...</Text>
                        )}
                        {failed > 0 && (
                            <Text type="danger">{failed} chưa soạn được</Text>
                        )}
                    </Space>
                );
            },
        },
        {
            title: "Tình trạng",
            key: "generatingStatus",
            width: 140,
            align: "center",
            render: (_, record) => {
                const generating = record.generatingSlots ?? 0;
                const failed = record.failedSlots ?? 0;

                if (generating > 0) {
                    return <Text>{generating} đang soạn</Text>;
                }
                if (failed > 0) {
                    return <Text type="danger">{failed} chưa soạn được</Text>;
                }
                return <Text>Hoàn tất</Text>;
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
                        <Tag color="volcano" icon={<WarningOutlined />}>
                            Cần xem kỹ ({needsReviewSlots})
                        </Tag>
                    );
                }
                return <Text>Đạt</Text>;
            },
        },
        {
            title: "Trạng thái",
            dataIndex: "status",
            key: "status",
            width: 130,
            align: "center",
            render: (status: ExamStatus) => {
                const config = examStatusConfig[status] ?? { label: status, color: "default" };
                return <Text>{config.label}</Text>;
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
                locale={{
                    emptyText: (
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có đề nào.">
                            {onCreate && (
                                <Button type="primary" size="large" onClick={onCreate}>
                                    Tạo đề đầu tiên
                                </Button>
                            )}
                        </Empty>
                    ),
                }}
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