import { useMemo } from 'react';
import { Button, Empty, Popconfirm, Space, Table, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import {
    DeleteOutlined,
    EditOutlined,
} from '@ant-design/icons';

import './ExamMatrixTable.css';

const { Text } = Typography;

/** Dữ liệu tối thiểu để hiển thị một ma trận trong bảng. */
export interface ExamMatrix {
    id: string;
    name: string;
    subjectName: string;
    /** Ví dụ: 8 hoặc "Lớp 8". */
    grade?: string | number;
    /** Tổng số câu được tính từ các dòng phân bổ của ma trận. */
    questionCount: number;
    /** Ngày cập nhật dạng ISO hoặc chuỗi ngày có thể parse bởi Date. */
    updatedAt?: string | null;
}

export interface ExamMatrixTableProps {
    /** Danh sách được lấy từ API hoặc component cha. */
    matrices?: ExamMatrix[];
    /** Từ khóa tìm kiếm do ExamMatrixListPage quản lý. */
    searchText: string;
    /** Hiển thị trạng thái tải dữ liệu. */
    loading?: boolean;
    /** Điều hướng đến trang chỉnh sửa ma trận. */
    onEditMatrix: (matrixId: string) => void;
    /** Xóa ma trận sau khi người dùng xác nhận. Nếu bỏ qua, nút xóa không hiển thị. */
    onDeleteMatrix?: (matrixId: string) => void;
}

/** Bỏ dấu tiếng Việt để tìm kiếm linh hoạt hơn (ví dụ: "ma tran" tìm được "Ma trận"). */
function normalizeSearchValue(value: string): string {
    return value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/đ/g, 'd')
        .replace(/Đ/g, 'D')
        .toLocaleLowerCase('vi-VN')
        .trim();
}

function formatDate(value?: string | null): string {
    if (!value) return '—';

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return new Intl.DateTimeFormat('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
    }).format(date);
}

/**
 * Bảng danh sách ma trận.
 * Component chỉ nhận dữ liệu và phát sự kiện; không gọi API hoặc tự xóa dữ liệu ở phía client.
 */
export function ExamMatrixTable({
    matrices = [],
    searchText,
    loading = false,
    onEditMatrix,
    onDeleteMatrix,
}: ExamMatrixTableProps) {
    const filteredMatrices = useMemo(() => {
        const keyword = normalizeSearchValue(searchText);
        if (!keyword) return matrices;

        return matrices.filter((matrix) => {
            const searchableText = normalizeSearchValue(
                [matrix.name, matrix.subjectName, matrix.grade == null ? '' : String(matrix.grade)]
                    .join(' '),
            );
            return searchableText.includes(keyword);
        });
    }, [matrices, searchText]);

    const columns: TableColumnsType<ExamMatrix> = [
        {
            title: 'Tên ma trận',
            dataIndex: 'name',
            key: 'name',
            render: (name: string) => (
                <div className="exam-matrix-table__name-cell">
                    <Text className="exam-matrix-table__name">
                        {name}
                    </Text>
                </div>
            ),
        },
        {
            title: 'Môn học',
            dataIndex: 'subjectName',
            key: 'subjectName',
            width: 170,
            render: (subjectName: string, record) => (
                <div className="exam-matrix-table__subject-cell">
                    <Text>{subjectName}</Text>
                    {record.grade != null && record.grade !== '' && (
                        <Text type="secondary" className="exam-matrix-table__meta">
                            Lớp {String(record.grade).replace(/^Lớp\s*/i, '')}
                        </Text>
                    )}
                </div>
            ),
        },
        {
            title: 'Tổng số câu',
            dataIndex: 'questionCount',
            key: 'questionCount',
            align: 'center',
            width: 130,
            sorter: (a, b) => a.questionCount - b.questionCount,
            render: (questionCount: number) => (
                <Text className="exam-matrix-table__question-count">
                    {questionCount.toLocaleString('vi-VN')}
                </Text>
            ),
        },
        {
            title: 'Cập nhật lần cuối',
            dataIndex: 'updatedAt',
            key: 'updatedAt',
            width: 170,
            sorter: (a, b) => {
                const first = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
                const second = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
                return first - second;
            },
            render: (updatedAt?: string | null) => (
                <Text type="secondary">{formatDate(updatedAt)}</Text>
            ),
        },
        {
            title: 'Thao tác',
            key: 'actions',
            align: 'center',
            width: onDeleteMatrix ? 120 : 80,
            render: (_value, record) => (
                <Space size={4} className="exam-matrix-table__actions">
                    <Tooltip title="Chỉnh sửa ma trận">
                        <Button
                            type="text"
                            icon={<EditOutlined />}
                            aria-label={`Chỉnh sửa ma trận ${record.name}`}
                            onClick={() => onEditMatrix(record.id)}
                        />
                    </Tooltip>
                    {onDeleteMatrix && (
                        <Popconfirm
                            title="Xóa ma trận này?"
                            description="Thao tác này không thể hoàn tác."
                            okText="Xóa"
                            cancelText="Hủy"
                            okButtonProps={{ danger: true }}
                            onConfirm={() => onDeleteMatrix(record.id)}
                        >
                            <Tooltip title="Xóa ma trận">
                                <Button
                                    type="text"
                                    danger
                                    icon={<DeleteOutlined />}
                                    aria-label={`Xóa ma trận ${record.name}`}
                                />
                            </Tooltip>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ];

    const emptyDescription = searchText.trim()
        ? 'Không tìm thấy ma trận phù hợp.'
        : 'Chưa có ma trận nào. Hãy tạo ma trận mới hoặc bắt đầu từ mẫu có sẵn.';

    return (
        <div className="exam-matrix-table">
            <div className="exam-matrix-table__summary">
                <Text type="secondary">
                    {searchText.trim()
                        ? `Tìm thấy ${filteredMatrices.length} ma trận`
                        : `${filteredMatrices.length} ma trận`}
                </Text>
            </div>

            <Table<ExamMatrix>
                rowKey="id"
                columns={columns}
                dataSource={filteredMatrices}
                loading={loading}
                size="middle"
                tableLayout="fixed"
                scroll={{ x: 760 }}
                locale={{
                    emptyText: (
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={emptyDescription}
                        />
                    ),
                }}
                pagination={{
                    defaultPageSize: 10,
                    showSizeChanger: true,
                    pageSizeOptions: [10, 20, 50],
                    showTotal: (total, range) =>
                        `${range[0]}–${range[1]} trong ${total} ma trận`,
                }}
            />
        </div>
    );
}

export default ExamMatrixTable;
