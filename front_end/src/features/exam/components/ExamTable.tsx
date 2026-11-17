import { Button, Empty, Grid, Popconfirm, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { DeleteOutlined, EyeOutlined, WarningOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import "./ExamTable.css";
import type { ExamKind, ExamListItem } from "../types/exam.types";
import { examKindConfig } from "../../../utils/exam.utils";

const { Text } = Typography;

interface ExamTableProps {
    data: ExamListItem[];
    loading?: boolean;
    total?: number;
    page?: number;
    pageSize?: number;
    onPageChange?: (page: number, pageSize: number) => void;
    onView?: (exam: ExamListItem) => void;
    /** NV-01: xác nhận xóa đề (sau hộp xác nhận). Không truyền thì không có nút xóa. */
    onDelete?: (exam: ExamListItem) => void | Promise<void>;
    /** Đề đang được xóa (nút xóa hiện trạng thái đang chạy). */
    deletingId?: number | null;
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
    onDelete,
    deletingId = null,
    onCreate,
}: ExamTableProps) {
    // Màn hẹp (dưới 768px): không ghim cột "Thao tác" bên phải, để tên đề còn chỗ hiện
    const screens = Grid.useBreakpoint();

    // Bỏ cột "Mã hệ thống" và cột "Trạng thái" (luôn là "Bản nháp"); tên đề đứng đầu (rà soát giao diện F1, F4)
    const columns: ColumnsType<ExamListItem> = [
        {
            title: "Tên đề thi",
            dataIndex: "title",
            key: "title",
            // Cột co giãn nhưng không hẹp hơn 220px (màn hẹp thì bảng cuộn ngang trong khung)
            minWidth: 220,
            render: (title: string) => (
                <Tooltip title={title}>
                    <Text strong className="exam-table-title" ellipsis>
                        {title}
                    </Text>
                </Tooltip>
            ),
        },
        {
            title: "Loại đề",
            dataIndex: "kind",
            key: "kind",
            width: 130,
            align: "center",
            render: (kind: ExamKind) => {
                // Giá trị lạ (backend thêm loại mới) thì hiện nguyên giá trị
                const config = examKindConfig[kind] ?? { label: kind, color: "default" };
                return <Text>{config.label}</Text>;
            },
        },
        {
            // Một cột thay cho "Số câu & Tiến độ" và "Tình trạng" (hai cột cũ lặp cùng thông tin, F2)
            title: "Tình trạng",
            key: "progress",
            width: 190,
            align: "center",
            render: (_, record) => {
                const done = (record.keptSlots ?? 0) + (record.readySlots ?? 0);
                const total = record.totalSlots ?? 0;
                const generating = record.generatingSlots ?? 0;
                const failed = record.failedSlots ?? 0;

                if (generating > 0) {
                    return (
                        <Space direction="vertical" size={0}>
                            <Text>Đang soạn {generating} câu</Text>
                            <Text type="secondary">Đã có {done}/{total} câu</Text>
                        </Space>
                    );
                }
                if (failed > 0) {
                    return (
                        <Space direction="vertical" size={0}>
                            <Text type="danger">Thiếu {failed} câu</Text>
                            <Text type="secondary">Đã có {done}/{total} câu</Text>
                        </Space>
                    );
                }
                return <Text>Đủ {total} câu</Text>;
            },
        },
        {
            title: "Câu cần xem kỹ",
            dataIndex: "needsReviewSlots",
            key: "needsReviewSlots",
            width: 150,
            align: "center",
            render: (needsReviewSlots: number = 0) => {
                if (needsReviewSlots > 0) {
                    return (
                        <Tag color="volcano" icon={<WarningOutlined />}>
                            {needsReviewSlots} câu
                        </Tag>
                    );
                }
                return <Text type="secondary">Không có</Text>;
            },
        },
        {
            title: "Ngày tạo",
            dataIndex: "createdAt",
            key: "createdAt",
            width: 160,
            align: "center",
            render: (createdAt: string) =>
                createdAt ? dayjs(createdAt).format("DD/MM/YYYY HH:mm") : "—",
        },
        {
            title: "Thao tác",
            key: "actions",
            width: 190,
            align: "center",
            fixed: screens.md ? "right" : undefined,
            render: (_, record) => (
                // Bấm nút không tính là bấm dòng (kể cả nút trong hộp xác nhận xóa)
                <Space size={4} onClick={(event) => event.stopPropagation()}>
                    <Button
                        icon={<EyeOutlined />}
                        onClick={() => onView?.(record)}
                    >
                        Mở đề
                    </Button>
                    {onDelete && (
                        <Popconfirm
                            title="Xóa đề này?"
                            description="Câu đã lưu vào ngân hàng vẫn được giữ. Không hoàn tác được."
                            okText="Xóa đề"
                            cancelText="Hủy"
                            okButtonProps={{ danger: true }}
                            onConfirm={() => onDelete(record)}
                        >
                            <Button
                                type="text"
                                danger
                                icon={<DeleteOutlined />}
                                loading={deletingId === record.examId}
                                aria-label={`Xóa đề ${record.title}`}
                            >
                                Xóa
                            </Button>
                        </Popconfirm>
                    )}
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
                // Bấm cả dòng để mở đề (F5)
                onRow={(record) => ({
                    onClick: () => onView?.(record),
                    className: onView ? "exam-table-row-clickable" : undefined,
                })}
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
                scroll={{ x: 1000 }}
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
