import { useEffect, useState } from "react";
import { Button, Card, Popconfirm, Space, Table, Tag, Tooltip, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { DeleteOutlined, EyeOutlined, FolderOpenOutlined, PlusOutlined, ReloadOutlined, WarningOutlined, } from "@ant-design/icons";

import type { MaterialDto } from "../types/material.types";
import { deleteMaterial, getMaterial, listMaterials } from "../services/materialApi";
import MaterialManagerModal from "../components/MaterialManagerModal";
import MaterialPreviewModal from "../components/MaterialPreviewModal";
import "./MaterialListPage.css";
import { describeMaterialWarning, formatBytes, renderFileIcon } from "../../../utils/material.utils";

const { Title, Paragraph, Text } = Typography;

export default function MaterialListPage() {
    const [materials, setMaterials] = useState<MaterialDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [total, setTotal] = useState(0);

    const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
    const [previewMaterial, setPreviewMaterial] = useState<MaterialDto | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);

    const fetchMaterials = async (currentPage = page, currentPageSize = pageSize) => {
        setLoading(true);
        try {
            const data = await listMaterials(currentPage - 1, currentPageSize);

            // Trang hiện tại đã rỗng (vừa xóa mục cuối, kể cả xóa trong modal) mà vẫn còn tài liệu: lùi về trang cuối có dữ liệu
            if ((data.items?.length ?? 0) === 0 && currentPage > 1 && (data.totalItems ?? 0) > 0) {
                setPage(Math.max(1, data.totalPages ?? 1));
                return;
            }

            setMaterials(data.items || []);
            setTotal(data.totalItems || 0);
        } catch (err: any) {
            message.error(err?.response?.data?.error || "Không thể tải danh sách tài liệu");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchMaterials(page, pageSize);
    }, [page, pageSize]);

    const handleDelete = async (id: number) => {
        try {
            await deleteMaterial(id);
            message.success("Đã xóa tài liệu thành công");
            fetchMaterials(page, pageSize);
        } catch (err: any) {
            const msg =
                err?.response?.data?.error ||
                (err?.response?.status === 409
                    ? "Tài liệu này đã được gắn vào đề thi nên không thể xóa."
                    : "Lỗi khi xóa tài liệu");
            message.error(msg);
        }
    };

    const handlePreview = async (item: MaterialDto) => {
        setPreviewLoading(true);
        setPreviewMaterial(item);
        try {
            const detail = await getMaterial(item.id);
            setPreviewMaterial(detail);
        } catch (err) {
            message.error("Không tải được chi tiết tài liệu");
        } finally {
            setPreviewLoading(false);
        }
    };

    const columns: TableColumnsType<MaterialDto> = [
        {
            title: "Tài liệu",
            key: "fileName",
            render: (_, record) => (
                <Space size={10} align="center">
                    {renderFileIcon(record.kind)}
                    <div>
                        <Text strong style={{ fontSize: 14 }}>
                            {record.fileName}
                        </Text>
                        <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                                {formatBytes(record.sizeBytes)} • {record.kind.toUpperCase()}
                                {record.pageCount ? ` • ${record.pageCount} trang` : ""}
                            </Text>
                        </div>
                    </div>
                </Space>
            ),
        },
        {
            title: "Văn bản trích xuất",
            key: "charCount",
            width: 170,
            align: "center",
            render: (_, record) => (
                <Tag color="blue" style={{ fontSize: 13, padding: "2px 8px" }}>
                    {record.charCount.toLocaleString()} ký tự
                </Tag>
            ),
        },
        {
            title: "Cảnh báo trích xuất",
            key: "warnings",
            width: 220,
            render: (_, record) => {
                if (!record.warnings || record.warnings.length === 0) {
                    return <Tag color="success">Không có cảnh báo</Tag>;
                }
                return (
                    <Space size={4} wrap>
                        {record.warnings.map((w, index) => {
                            const { label, severity } = describeMaterialWarning(w.code);
                            return (
                                <Tooltip key={index} title={w.message}>
                                    <Tag color={severity} icon={<WarningOutlined />}>
                                        {label}
                                    </Tag>
                                </Tooltip>
                            );
                        })}
                    </Space>
                );
            },
        },
        {
            title: "Ngày tải lên",
            dataIndex: "createdAt",
            key: "createdAt",
            width: 160,
            render: (val: string) => {
                if (!val) return "—";
                const d = new Date(val);
                return isNaN(d.getTime()) ? val : d.toLocaleString("vi-VN");
            },
        },
        {
            title: "Thao tác",
            key: "action",
            width: 120,
            align: "center",
            render: (_, record) => (
                <Space size={4}>
                    <Tooltip title="Xem trước văn bản trích xuất">
                        <Button
                            type="text"
                            icon={<EyeOutlined />}
                            onClick={() => handlePreview(record)}
                        />
                    </Tooltip>
                    <Popconfirm
                        title="Xóa tài liệu này?"
                        description="Hệ thống sẽ xóa văn bản trích xuất đã lưu."
                        onConfirm={() => handleDelete(record.id)}
                        okText="Xóa"
                        cancelText="Hủy"
                        okButtonProps={{ danger: true }}
                    >
                        <Tooltip title="Xóa tài liệu">
                            <Button type="text" danger icon={<DeleteOutlined />} />
                        </Tooltip>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div className="material-list-page">
            <div className="material-list-page-header">
                <div className="material-list-page-title-box">
                    <Title level={2} className="material-list-page-title">
                        <FolderOpenOutlined style={{ marginRight: 10, color: "#1677ff" }} />
                        Tài liệu bài học
                    </Title>
                    <Paragraph type="secondary" className="material-list-page-subtitle">
                        Tải lên các file Word (.docx), PDF hoặc TXT bài giảng, sách giáo khoa để cung cấp ngữ cảnh tài liệu
                        cho mô hình AI sinh câu hỏi bám sát chương trình giảng dạy.
                    </Paragraph>
                </div>
                <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => fetchMaterials(page, pageSize)}>
                        Làm mới
                    </Button>
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => setIsUploadModalOpen(true)}
                    >
                        Tải lên tài liệu
                    </Button>
                </Space>
            </div>

            <Card bordered={false} className="material-list-card">
                <Table<MaterialDto>
                    columns={columns}
                    dataSource={materials}
                    rowKey="id"
                    loading={loading}
                    pagination={{
                        current: page,
                        pageSize,
                        total,
                        showSizeChanger: true,
                        pageSizeOptions: [10, 20, 50],
                        showTotal: (totalItems, range) =>
                            `${range[0]}-${range[1]} trong tổng số ${totalItems} tài liệu`,
                        onChange: (p, ps) => {
                            setPage(p);
                            setPageSize(ps);
                        },
                    }}
                />
            </Card>

            {/* Modal Quản lý / Tải lên tài liệu */}
            <MaterialManagerModal
                open={isUploadModalOpen}
                onClose={() => {
                    setIsUploadModalOpen(false);
                    fetchMaterials(page, pageSize);
                }}
            />

            {/* Modal Xem trước văn bản trích xuất (Tách component) */}
            <MaterialPreviewModal
                material={previewMaterial}
                loading={previewLoading}
                onClose={() => setPreviewMaterial(null)}
            />
        </div>
    );
}