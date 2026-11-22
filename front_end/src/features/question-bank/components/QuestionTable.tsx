import { Button, Empty, Space, Table, Tag, Tooltip, Typography, Card, Select } from "antd";
import type { TableColumnsType, TablePaginationConfig } from "antd";
import { EyeOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import "./QuestionTable.css";
import type { BankSort, QuestionTableItem, QuestionType } from "../types/questionBank.types";
import MathText from "../../../components/common/MathText/MathText";
import { isBlankMath } from "../../../components/common/MathText/mathSegments";
import { describeLevel, QUESTION_TYPE_LABELS } from "../../../utils/exam.utils";

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
    /** Đang có điều kiện lọc: bảng rỗng thì gợi ý bỏ bớt điều kiện. */
    hasFilters?: boolean;
    /** Nút "Xóa điều kiện lọc" ở trạng thái rỗng. */
    onClearFilters?: () => void;
}

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
    onView,
    hasFilters = false,
    onClearFilters,
}: QuestionTableProps) {
    // Bỏ cột "Mã" và phần "Môn" để bảng vừa khung ở chữ 16px; mã câu, môn vẫn xem trong ngăn chi tiết (rà soát giao diện H5)
    const columns: TableColumnsType<QuestionTableItem> = [
        {
            title: "Câu hỏi",
            dataIndex: "questionText",
            key: "questionText",
            // Cột co giãn: đủ rộng để thấy công thức đầu câu, không bị ép còn vài chữ
            minWidth: 240,
            ellipsis: {
                showTitle: false,
            },
            render: (questionText: string, record) => {
                // Không để ô trống: nội dung không vẽ ra được gì (chữ rỗng, công thức rỗng) thì hiện dòng dự phòng
                if (isBlankMath(questionText)) {
                    return <Text type="secondary">(Không có nội dung hiển thị)</Text>;
                }
                // Một dòng: ảnh thành nhãn [Hình] (không tải ảnh cho cả trang); hover mới vẽ ảnh thật
                return (
                    <Tooltip title={<MathText text={questionText} images={record.images} />} placement="topLeft">
                        <span className="question-table-question">
                            <MathText text={questionText} images={record.images} compact />
                        </span>
                    </Tooltip>
                );
            },
        },
        {
            // Môn xem trong ngăn chi tiết; bảng chỉ giữ lớp để cột "Câu hỏi" đủ rộng
            title: "Lớp",
            key: "gradeLevel",
            width: 80,
            align: "center",
            render: (_, record) => (record.gradeLevel ? <Text>Lớp {record.gradeLevel}</Text> : "—"),
        },
        {
            title: "Dạng câu",
            dataIndex: "questionType",
            key: "questionType",
            width: 130,
            align: "center",
            render: (type: QuestionType | undefined, record) => (
                <div className="question-table-type">
                    <Text>{QUESTION_TYPE_LABELS[type ?? "mcq"] ?? QUESTION_TYPE_LABELS.mcq}</Text>
                    {record.images?.length ? <Tag color="blue">Có hình</Tag> : null}
                </div>
            ),
        },
        {
            title: "Chủ đề / Bài",
            dataIndex: "topic",
            key: "topic",
            width: 150,
            ellipsis: true,
            render: (topic?: string) => topic ? <Text type="secondary">{topic}</Text> : "—",
        },
        {
            title: "Mức độ",
            key: "difficulty",
            width: 105,
            align: "center",
            render: (_, record) => {
                if (record.questionType === "tf") {
                    return "Theo từng ý";
                }
                // Mức thiếu hoặc lạ (dữ liệu cũ): "Chưa phân loại", không hiện mã thô hay ô trống
                return <Text>{describeLevel(record.difficulty).label}</Text>;
            },
        },
        {
            // NV-30: câu dùng chung hay câu riêng của thầy/cô (docs/api "Ngân hàng câu hỏi")
            title: (
                <Tooltip title="Chung: mọi giáo viên thấy và hệ thống có thể dùng lại khi tạo đề. Riêng: chỉ thầy/cô thấy và dùng lại.">
                    <span>Phạm vi</span>
                </Tooltip>
            ),
            dataIndex: "visibility",
            key: "visibility",
            width: 95,
            align: "center",
            render: (visibility?: QuestionTableItem["visibility"]) => visibility === "private"
                ? <Tag color="purple">Riêng</Tag>
                : visibility === "shared" ? <Tag>Chung</Tag> : "—",
        },
        {
            title: (
                <Tooltip title="Số đề (của mọi giáo viên) đang dùng câu này và ngày tạo đề mới nhất có câu này. Đây là căn cứ của sắp xếp “Ít đề dùng nhất” và “Dùng gần đây”.">
                    <span>Số đề dùng</span>
                </Tooltip>
            ),
            dataIndex: "usageCount",
            key: "usageCount",
            width: 130,
            align: "center",
            render: (usageCount: number, record) => (
                <Space direction="vertical" size={0} align="center">
                    <Space size={4}>
                        <Text>{usageCount ?? 0}</Text>
                        {record.origin?.edited && (
                            <Tag color="gold" style={{ marginRight: 0 }}>
                                Đã sửa
                            </Tag>
                        )}
                    </Space>
                    {/* Cho thấy lý do thứ tự khi sắp "Dùng gần đây" (backend xếp theo lastUsedAt) */}
                    <Text type="secondary" style={{ fontSize: 14 }}>
                        {record.lastUsedAt ? `Gần nhất ${dayjs(record.lastUsedAt).format("DD/MM/YYYY")}` : "Chưa dùng"}
                    </Text>
                </Space>
            ),
        },
        {
            title: "Ngày tạo",
            dataIndex: "createdAt",
            key: "createdAt",
            width: 115,
            render: (val: string) => {
                if (!val) return "—";
                const d = dayjs(val);
                return d.isValid() ? d.format("DD/MM/YYYY") : val;
            },
        },
        {
            title: "Thao tác",
            key: "action",
            width: 100,
            align: "center",
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined />}
                    onClick={(event) => {
                        // Không tính thêm lần bấm dòng
                        event.stopPropagation();
                        onView?.(record);
                    }}
                >
                    Xem
                </Button>
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
                        <Text type="secondary">Sắp xếp:</Text>
                        <Select
                            value={sort}
                            onChange={(val) => onSortChange?.(val)}
                            options={sortOptions}
                            style={{ width: 190 }}
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
                    // Bấm cả dòng để mở chi tiết (rà soát giao diện H5)
                    onRow={(record) => ({
                        onClick: () => onView?.(record),
                        className: onView ? "question-table-row-clickable" : undefined,
                    })}
                    locale={{
                        emptyText: hasFilters ? (
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description="Không có câu nào khớp. Thầy/cô thử bỏ bớt điều kiện lọc."
                            >
                                {onClearFilters && (
                                    <Button onClick={onClearFilters}>Xóa điều kiện lọc</Button>
                                )}
                            </Empty>
                        ) : (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Ngân hàng chưa có câu nào." />
                        ),
                    }}
                />
            </Card>
        </div>
    );
}
