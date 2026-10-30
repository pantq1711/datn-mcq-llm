import { useEffect, useMemo, useState } from 'react';
import { getSubjectById } from '../services/subjectService';
import { Button, Empty, Popconfirm, Space, Table, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { CopyOutlined, DeleteOutlined, EditOutlined, FileAddOutlined } from '@ant-design/icons';

import './ExamMatrixTable.css';
import type { ExamMatrix } from "../types/examMatrix.types";

const { Text } = Typography;

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
    /** Nhân bản ma trận. */
    onDuplicateMatrix?: (matrixId: string) => void;
    /** Tạo đề thi từ ma trận. Nếu bỏ qua, nút tạo đề không hiển thị. */
    onGenerateExam?: (matrixId: string) => void;
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
    onDuplicateMatrix,
    onGenerateExam,
}: ExamMatrixTableProps) {
    const [subjectMap, setSubjectMap] = useState<Record<string | number, string>>({});

    useEffect(() => {
        const missingIds = Array.from(
            new Set(matrices.map(m => m.subjectId))
        ).filter(id => !(id in subjectMap));

        if (missingIds.length === 0) return;

        (async () => {
            try {
                const results = await Promise.all(
                    missingIds.map(id => getSubjectById(id))
                );
                // Cập nhật cache
                setSubjectMap(prev => {
                    const next = { ...prev };
                    results.forEach(sub => {
                        next[sub.id] = sub.name;
                    });
                    return next;
                });
            } catch (err: any) {
                // Không làm gián đoạn UI; chỉ log lỗi và bỏ qua
                console.error('Failed to load subject names:', err);
            }
        })();
    }, [matrices, subjectMap]);

    // Bổ sung tạm thời trường subjectName cho mỗi matrix
    const matricesWithName = useMemo(() => {
        return matrices.map(m => ({
            ...m,
            // Nếu cache đã có tên thì dùng, còn không thì fallback là raw id
            subjectName: subjectMap[m.subjectId] ?? String(m.subjectId),
        }));
    }, [matrices, subjectMap]);

    const filteredMatrices = useMemo(() => {
        const keyword = normalizeSearchValue(searchText);
        if (!keyword) return matricesWithName;

        return matricesWithName.filter(matrix => {
            const searchableText = normalizeSearchValue(
                [
                    matrix.name,
                    matrix.subjectName,
                    matrix.gradeLevel == null ? '' : String(matrix.gradeLevel),
                ].join(' ')
            );
            return searchableText.includes(keyword);
        });
    }, [matricesWithName, searchText]);

    const columns: TableColumnsType<any> = [
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
                    {record.gradeLevel != null && (
                        <Text type="secondary" className="exam-matrix-table__meta">
                            Lớp {String(record.gradeLevel).replace(/^Lớp\s*/i, '')}
                        </Text>
                    )}
                </div>
            ),
        },
        {
            title: 'Tổng số câu',
            key: 'questionCount',
            align: 'center',
            width: 130,
            sorter: (a: any, b: any) => {
                const countA = a.questionCount ?? a.cells?.reduce((sum: number, cell: any) => sum + (cell.questionCount || 0), 0) ?? 0;
                const countB = b.questionCount ?? b.cells?.reduce((sum: number, cell: any) => sum + (cell.questionCount || 0), 0) ?? 0;
                return countA - countB;
            },
            render: (_, record: any) => {
                const total = record.questionCount ?? record.cells?.reduce((sum: number, cell: any) => sum + (cell.questionCount || 0), 0) ?? 0;
                return (
                    <Text className="exam-matrix-table__question-count">
                        {total.toLocaleString('vi-VN')}
                    </Text>
                );
            },
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
            width: 180, // Tăng chiều rộng cột thao tác lên 180px cho 4 nút
            render: (_value, record) => {
                const totalQuestions = record.questionCount ?? record.cells?.reduce((sum: number, cell: any) => sum + (cell.questionCount || 0), 0) ?? 0;
                const isZeroQuestions = totalQuestions === 0;

                return (
                    <Space size={4} className="exam-matrix-table__actions">
                        {onGenerateExam && (
                            <Tooltip title={isZeroQuestions ? "Ma trận chưa có câu nào" : "Tạo đề từ ma trận này"}>
                                <Button
                                    type="text"
                                    icon={<FileAddOutlined />}
                                    disabled={isZeroQuestions}
                                    aria-label={`Tạo đề từ ma trận này ${record.name}`}
                                    onClick={() => !isZeroQuestions && onGenerateExam(record.id)}
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

            <Table<any>
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