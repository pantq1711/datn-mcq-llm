import { useMemo } from 'react';
import { Button, Empty, Popconfirm, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { CopyOutlined, DeleteOutlined, EditOutlined, FileAddOutlined } from '@ant-design/icons';

import './ExamMatrixTable.css';
import type { ExamMatrixSummary } from "../types/examMatrix.types";

const { Text } = Typography;

type MatrixRow = ExamMatrixSummary & { subjectName: string };

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
    /** Nhân bản ma trận. */
    onDuplicateMatrix?: (matrixId: string) => void;
    /** Tạo đề thi từ ma trận. Nếu bỏ qua, nút tạo đề không hiển thị. */
    onGenerateExam?: (matrixId: string) => void;
}

/** Bỏ dấu tiếng Việt để tìm kiếm linh hoạt hơn (ví dụ: "ma tran" tìm được "Ma trận"). */
function normalizeSearchValue(value: string): string {
    return value
        .normalize('NFD')
        .replace(/[̀-ͯ]/g, '')
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
    subjectNames = {},
    searchText,
    loading = false,
    onEditMatrix,
    onDeleteMatrix,
    onDuplicateMatrix,
    onGenerateExam,
}: ExamMatrixTableProps) {
    const rows = useMemo<MatrixRow[]>(
        () =>
            matrices.map((m) => ({
                ...m,
                subjectName: subjectNames[m.subjectId] ?? `Môn #${m.subjectId}`,
            })),
        [matrices, subjectNames]
    );

    const filteredMatrices = useMemo(() => {
        const keyword = normalizeSearchValue(searchText);
        if (!keyword) return rows;

        return rows.filter(matrix => {
            const searchableText = normalizeSearchValue(
                [
                    matrix.name,
                    matrix.subjectName,
                    matrix.gradeLevel == null ? '' : `khoi ${matrix.gradeLevel}`,
                ].join(' ')
            );
            return searchableText.includes(keyword);
        });
    }, [rows, searchText]);

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
                            <Tag color="gold" style={{ marginTop: 4 }}>
                                Đã dùng cho {record.examCount} đề
                            </Tag>
                        </div>
                    )}
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
                    {record.gradeLevel != null && (
                        <Text type="secondary" className="exam-matrix-table__meta">
                            Khối {record.gradeLevel}
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
            width: 110,
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
            width: 150,
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
            width: 180, // Tăng chiều rộng cột thao tác lên 180px cho 4 nút
            render: (_value, record) => {
                // Mỗi ô có ít nhất 1 câu: còn ô là còn câu để tạo đề
                const hasNoCells = record.cellCount === 0;
                // Backend trả 409 MATRIX_IN_USE khi xóa ma trận đã có đề (docs/api mục 7.1a)
                const inUse = record.examCount > 0;

                return (
                    <Space size={4} className="exam-matrix-table__actions">
                        {onGenerateExam && (
                            <Tooltip title={hasNoCells ? "Ma trận chưa có ô nào" : "Tạo đề từ ma trận này"}>
                                <Button
                                    type="text"
                                    icon={<FileAddOutlined />}
                                    disabled={hasNoCells}
                                    aria-label={`Tạo đề từ ma trận này ${record.name}`}
                                    onClick={() => !hasNoCells && onGenerateExam(record.id)}
                                />
                            </Tooltip>
                        )}
                        {onDuplicateMatrix && (
                            <Tooltip title="Nhân bản ma trận">
                                <Button
                                    type="text"
                                    icon={<CopyOutlined />}
                                    aria-label={`Nhân bản ma trận ${record.name}`}
                                    onClick={() => onDuplicateMatrix(record.id)}
                                />
                            </Tooltip>
                        )}
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
                                disabled={inUse}
                                onConfirm={() => onDeleteMatrix(record.id)}
                            >
                                <Tooltip
                                    title={
                                        inUse
                                            ? "Ma trận đã có đề, không xóa được. Có thể nhân bản."
                                            : "Xóa ma trận"
                                    }
                                >
                                    <Button
                                        type="text"
                                        danger
                                        disabled={inUse}
                                        icon={<DeleteOutlined />}
                                        aria-label={`Xóa ma trận ${record.name}`}
                                    />
                                </Tooltip>
                            </Popconfirm>
                        )}
                    </Space>
                );
            },
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

            <Table<MatrixRow>
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