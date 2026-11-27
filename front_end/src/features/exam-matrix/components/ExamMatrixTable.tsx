import { useMemo } from 'react';
import { Button, Empty, Popconfirm, Space, Table, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { CopyOutlined, DeleteOutlined, EditOutlined, FileAddOutlined } from '@ant-design/icons';

import './ExamMatrixTable.css';
import type { ExamMatrixSummary } from "../types/examMatrix.types";
import { filterMatrixRows, toMatrixRows, type MatrixRow } from "../utils/matrixSearch";

const { Text } = Typography;

export interface ExamMatrixTableProps {
    /** Danh sách từ GET /api/exam-matrices (MatrixSummaryDto). */
    matrices?: ExamMatrixSummary[];
    /** Tên môn theo id, trang tải từ /api/catalog/subjects. */
    subjectNames?: Record<number, string>;
    /** Từ khóa tìm kiếm do ExamMatrixListPage quản lý. */
    searchText: string;
    /** Hiển thị trạng thái tải dữ liệu. */
    loading?: boolean;
    /** Điều hướng đến trang chỉnh sửa ma trận. */
    onEditMatrix: (matrixId: string) => void;
    /** Xóa ma trận sau khi người dùng xác nhận. Nếu bỏ qua, nút xóa không hiển thị. */
    onDeleteMatrix?: (matrixId: string) => void;
    /** Sao chép ma trận. */
    onDuplicateMatrix?: (matrixId: string) => void;
    /** Tạo đề thi từ ma trận. Nếu bỏ qua, nút tạo đề không hiển thị. */
    onGenerateExam?: (matrixId: string) => void;
    /** Bấm "Tạo từ mẫu" ở trạng thái rỗng (chưa có ma trận nào). */
    onCreateFromTemplate?: () => void;
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
    subjectNames = {},
    searchText,
    loading = false,
    onEditMatrix,
    onDeleteMatrix,
    onDuplicateMatrix,
    onGenerateExam,
    onCreateFromTemplate,
}: ExamMatrixTableProps) {
    const rows = useMemo(() => toMatrixRows(matrices, subjectNames), [matrices, subjectNames]);
    // Cùng phép lọc với số đếm trên tiêu đề card ở ExamMatrixListPage
    const filteredMatrices = useMemo(() => filterMatrixRows(rows, searchText), [rows, searchText]);

    const columns: TableColumnsType<MatrixRow> = [
        {
            title: 'Tên ma trận',
            dataIndex: 'name',
            key: 'name',
            render: (name: string, record) => (
                <div className="exam-matrix-table__name-cell">
                    <Text className="exam-matrix-table__name">
                        {name}
                    </Text>
                    {record.examCount > 0 && (
                        <div>
                            <Text type="secondary">Đã dùng cho {record.examCount} đề</Text>
                        </div>
                    )}
                </div>
            ),
        },
        {
            title: 'Môn học',
            dataIndex: 'subjectName',
            key: 'subjectName',
            width: 130,
            render: (subjectName: string, record) => (
                <div className="exam-matrix-table__subject-cell">
                    <Text>{subjectName}</Text>
                    {record.gradeLevel != null && (
                        <Text type="secondary" className="exam-matrix-table__meta">
                            Lớp {record.gradeLevel}
                        </Text>
                    )}
                </div>
            ),
        },
        {
            // Danh sách không có tổng câu (MatrixSummaryDto chỉ có cellCount)
            title: 'Số ô',
            dataIndex: 'cellCount',
            key: 'cellCount',
            align: 'center',
            width: 90,
            sorter: (a, b) => a.cellCount - b.cellCount,
            render: (cellCount: number) => (
                <Text className="exam-matrix-table__question-count">
                    {cellCount.toLocaleString('vi-VN')}
                </Text>
            ),
        },
        {
            title: 'Ngày tạo',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 140,
            sorter: (a, b) => {
                const first = a.createdAt ? new Date(a.createdAt).getTime() : 0;
                const second = b.createdAt ? new Date(b.createdAt).getTime() : 0;
                return first - second;
            },
            render: (createdAt?: string | null) => (
                <Text type="secondary">{formatDate(createdAt)}</Text>
            ),
        },
        {
            title: 'Thao tác',
            key: 'actions',
            align: 'center',
            // Nút có chữ thay cho biểu tượng (rà soát giao diện D1)
            width: 450,
            render: (_value, record) => {
                // Mỗi ô có ít nhất 1 câu: còn ô là còn câu để tạo đề
                const hasNoCells = record.cellCount === 0;
                // Backend trả 409 MATRIX_IN_USE khi xóa ma trận đã có đề (docs/api mục 7.1a)
                const inUse = record.examCount > 0;

                return (
                    <Space size={4} className="exam-matrix-table__actions">
                        {onGenerateExam && (
                            <Tooltip title={hasNoCells ? "Ma trận chưa có ô nào" : undefined}>
                                <Button
                                    type="primary"
                                    icon={<FileAddOutlined />}
                                    disabled={hasNoCells}
                                    aria-label={`Tạo đề từ ma trận ${record.name}`}
                                    onClick={() => !hasNoCells && onGenerateExam(record.id)}
                                >
                                    Tạo đề
                                </Button>
                            </Tooltip>
                        )}
                        {onDuplicateMatrix && (
                            <Button
                                icon={<CopyOutlined />}
                                aria-label={`Sao chép ma trận ${record.name}`}
                                onClick={() => onDuplicateMatrix(record.id)}
                            >
                                Sao chép
                            </Button>
                        )}
                        <Button
                            icon={<EditOutlined />}
                            aria-label={`Sửa ma trận ${record.name}`}
                            onClick={() => onEditMatrix(record.id)}
                        >
                            Sửa
                        </Button>
                        {onDeleteMatrix && (
                            <Popconfirm
                                title="Xóa ma trận này?"
                                description="Thao tác này không thể hoàn tác."
                                okText="Xóa"
                                cancelText="Hủy"
                                okButtonProps={{ danger: true }}
                                disabled={inUse}
                                onConfirm={() => onDeleteMatrix(record.id)}
                            >
                                <Tooltip
                                    title={
                                        inUse
                                            ? "Ma trận đã có đề, không xóa được. Có thể sao chép."
                                            : undefined
                                    }
                                >
                                    <Button
                                        type="text"
                                        danger
                                        disabled={inUse}
                                        icon={<DeleteOutlined />}
                                        aria-label={`Xóa ma trận ${record.name}`}
                                    >
                                        Xóa
                                    </Button>
                                </Tooltip>
                            </Popconfirm>
                        )}
                    </Space>
                );
            },
        },
    ];

    const isSearching = searchText.trim() !== '';
    const emptyDescription = isSearching ? 'Không tìm thấy ma trận phù hợp.' : 'Chưa có ma trận.';

    return (
        <div className="exam-matrix-table">
            {/* Số ma trận hiện trên tiêu đề card (ExamMatrixListPage) */}
            <Table<MatrixRow>
                rowKey="id"
                columns={columns}
                dataSource={filteredMatrices}
                loading={loading}
                size="middle"
                tableLayout="fixed"
                scroll={{ x: 1000 }}
                locale={{
                    emptyText: (
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={emptyDescription}
                        >
                            {!isSearching && onCreateFromTemplate && (
                                <Button type="primary" size="large" onClick={onCreateFromTemplate}>
                                    Tạo từ mẫu
                                </Button>
                            )}
                        </Empty>
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