import { Button, Space, Table, Tag, Tooltip, Typography, Card, Select } from "antd";
import type { TableColumnsType, TablePaginationConfig } from "antd";
import { EyeOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import "./QuestionTable.css";
import type { BankSort, QuestionTableItem, QuestionType } from "../types/questionBank.types";

const { Text } = Typography;

interface QuestionTableProps {
    data: QuestionTableItem[];
    loading?: boolean;
    total?: number;
    page?: number;
    pageSize?: number;
    sort?: BankSort;
    onSortChange?: (sort: BankSort) => void;
    onPageChange?: (page: number, pageSize: number) => void;
    onView?: (question: QuestionTableItem) => void;
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

// Đúng giá trị docs/api "Ngân hàng câu hỏi"; giá trị lạ backend trả 400
const sortOptions: { label: string; value: BankSort }[] = [
    { label: "Mới nhất", value: "newest" },
    { label: "Cũ nhất", value: "oldest" },
    { label: "Ít đề dùng nhất", value: "least_used" },
    { label: "Dùng gần đây", value: "recently_used" },
];

export default function QuestionTable({
    data,
    loading = false,
    total,
    page = 1,
    pageSize = 10,
    sort = "newest",
    onSortChange,
    onPageChange,
    onView
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
            title: "Môn / Khối",
            key: "subjectAndGrade",
            width: 140,
            render: (_, record) => (
                <Space direction="vertical" size={2}>
                    <Text strong>{record.subject || "—"}</Text>
                    {record.gradeLevel && (
                        <Tag color="geekblue" style={{ fontSize: 11, marginRight: 0 }}>
                            Khối {record.gradeLevel}
                        </Tag>
                    )}
                </Space>
            ),
        },
        {
            title: "Dạng câu",
            dataIndex: "questionType",
            key: "questionType",
            width: 130,
            align: "center",
            render: (type?: QuestionType) => {
                if (type === "tf") return <Tag color="cyan">Đúng / Sai</Tag>;
                if (type === "sa") return <Tag color="purple">Trả lời ngắn</Tag>;
                return <Tag color="blue">Trắc nghiệm</Tag>;
            },
        },
        {
            title: "Chủ đề / Bài",
            dataIndex: "topic",
            key: "topic",
            width: 160,
            ellipsis: true,
            render: (topic?: string) => topic ? <Text type="secondary">{topic}</Text> : "—",
        },
        {
            title: "Độ khó",
            key: "difficulty",
            width: 130,
            align: "center",
            render: (_, record) => {
                if (record.questionType === "tf") {
                    return "Theo từng ý";
                }
                if (record.difficulty && difficultyMap[record.difficulty]) {
                    const config = difficultyMap[record.difficulty];
                    return (
                        <Tag color={config.color}>
                            {config.label}
                        </Tag>
                    );
                }
                return "—";
            },
        },
        {
            title: (
                <Tooltip title="Số đề (của mọi giáo viên) đang dùng câu này và ngày tạo đề mới nhất có câu này. Đây là căn cứ của sắp xếp “Ít đề dùng nhất” và “Dùng gần đây”.">
                    <span>Số đề dùng</span>
                </Tooltip>
            ),
            dataIndex: "usageCount",
            key: "usageCount",
            width: 150,
            align: "center",
            render: (usageCount: number, record) => (
                <Space direction="vertical" size={0} align="center">
                    <Space size={4}>
                        <Text>{usageCount ?? 0}</Text>
                        {record.origin?.edited && (
                            <Tag color="blue" style={{ fontSize: 10, marginRight: 0, padding: "0 4px" }}>
                                Đã sửa
                            </Tag>
                        )}
                    </Space>
                    {/* Cho thấy lý do thứ tự khi sắp "Dùng gần đây" (backend xếp theo lastUsedAt) */}
                    <Text type="secondary" style={{ fontSize: 11 }}>
                        {record.lastUsedAt ? `Gần nhất ${dayjs(record.lastUsedAt).format("DD/MM/YYYY")}` : "Chưa dùng"}
                    </Text>
                </Space>
            ),
        },
        {
            title: "Ngày tạo",
            dataIndex: "createdAt",
            key: "createdAt",
            width: 140,
            render: (val: string) => {
                if (!val) return "—";
                const d = dayjs(val);
                return d.isValid() ? d.format("DD/MM/YYYY") : val;
            },
        },
        {
            title: "Thao tác",
            key: "action",
            width: 80,
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
                </Space>
            ),
        },
    ];

    const pagination: TablePaginationConfig = {
        current: page,
        pageSize,
        total: total ?? data.length,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50],
        showTotal: (totalItems, range) =>
            `${range[0]}-${range[1]} trong tổng số ${totalItems} câu hỏi`,
        onChange: (nextPage, nextPageSize) => {
            onPageChange?.(nextPage, nextPageSize);
        },
    };

    return (
        <div className="question-table">
            <Card
                bordered={false}
                extra={
                    <Space size={8}>
                        <Text type="secondary" style={{ fontSize: 13 }}>Sắp xếp:</Text>
                        <Select
                            value={sort}
                            onChange={(val) => onSortChange?.(val)}
                            options={sortOptions}
                            style={{ width: 160 }}
                            size="small"
                        />
                    </Space>
                }
            >
                <Table<QuestionTableItem>
                    rowKey="id"
                    columns={columns}
                    dataSource={data}
                    loading={loading}
                    pagination={pagination}
                    scroll={{ x: 1100 }}
                />
            </Card>
        </div>
    );
}