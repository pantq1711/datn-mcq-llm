import { useEffect, useState } from "react";
import { Button, Empty, Popconfirm, Space, Table, Tag, Tooltip, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { DeleteOutlined, EyeOutlined, FolderOpenOutlined, PlusOutlined, ReloadOutlined, WarningOutlined, } from "@ant-design/icons";
import dayjs from "dayjs";
import type { MaterialDto } from "../types/material.types";
import { deleteMaterial, listMaterials } from "../services/materialApi";
import MaterialManagerModal from "../components/MaterialManagerModal";
import MaterialPreviewModal from "../components/MaterialPreviewModal";
import "./MaterialListPage.css";
import { describeMaterialWarning, formatBytes, renderFileIcon } from "../../../utils/material.utils";
import { useMaterialPreview } from "../hooks/useMaterialPreview";

const { Title, Paragraph, Text } = Typography;

export default function MaterialListPage() {
    const [materials, setMaterials] = useState<MaterialDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [total, setTotal] = useState(0);

    const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);

    const { previewMaterial, previewLoading, openPreview, closePreview } = useMaterialPreview();

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

    const columns: TableColumnsType<MaterialDto> = [
        {
            title: "Tài liệu",
            key: "fileName",
            render: (_, record) => (
                <Space size={10} align="center">
                    {renderFileIcon(record.kind)}
                    <div>
                        <Text strong>
                            {record.fileName}
                        </Text>
                        <div>
                            <Text type="secondary" style={{ fontSize: 14 }}>
                                {formatBytes(record.sizeBytes)} • {record.kind.toUpperCase()}
                                {record.pageCount ? ` • ${record.pageCount} trang` : ""}
                            </Text>
                        </div>
                    </div>
                </Space>
            ),
        },
        {
            title: "Đã đọc được",
            key: "charCount",
            width: 170,
            align: "center",
            render: (_, record) => (
                <Tag color="blue" style={{ fontSize: 14, padding: "2px 8px" }}>
                    {record.charCount.toLocaleString()} ký tự
                </Tag>
            ),
        },
        {
            title: "Lưu ý",
            key: "warnings",
            width: 220,
            render: (_, record) => {
                if (!record.warnings || record.warnings.length === 0) {
                    return <Tag color="success">Không có</Tag>;
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
                // Cùng một dạng ngày giờ với các trang khác: "14/11/2026 18:07", bỏ giây (rà soát giao diện A6)
                const d = dayjs(val);
                return d.isValid() ? d.format("DD/MM/YYYY HH:mm") : val;
            },
        },
        {
            title: "Thao tác",
            key: "action",
            width: 190,
            align: "center",
            render: (_, record) => (
                // Nút có chữ thay cho biểu tượng (rà soát giao diện I6). Chặn lan sự kiện để bấm nút (kể cả trong
                // hộp xác nhận Xóa, vẫn nổi bọt qua portal) không kích hoạt bấm cả hàng
                <Space size={4} onClick={(event) => event.stopPropagation()}>
                    <Button
                        icon={<EyeOutlined />}
                        onClick={() => openPreview(record)}
                    >
                        Xem
                    </Button>
                    <Popconfirm
                        title="Xóa tài liệu này?"
                        description="Nội dung hệ thống đã đọc từ tài liệu này cũng bị xóa."
                        onConfirm={() => handleDelete(record.id)}
                        okText="Xóa"
                        cancelText="Hủy"
                        okButtonProps={{ danger: true }}
                    >
                        <Button type="text" danger icon={<DeleteOutlined />}>
                            Xóa
                        </Button>
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
                        Tải lên SGK hoặc giáo án để câu hỏi bám sát nội dung bài học
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

            {/* Bảng đặt thẳng trên trang, không bọc card */}
            <div className="material-list-card">
                <Table<MaterialDto>
                    columns={columns}
                    dataSource={materials}
                    rowKey="id"
                    loading={loading}
                    // Màn hẹp: bảng cuộn ngang trong khung, cả trang không bị kéo rộng (rà soát giao diện I7)
                    scroll={{ x: 1000 }}
                    // Bấm cả hàng để xem nội dung đã đọc, như trang Đề thi và Ngân hàng câu hỏi
                    onRow={(record) => ({
                        onClick: () => openPreview(record),
                        className: "material-list-row-clickable",
                    })}
                    locale={{
                        emptyText: (
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description="Chưa có tài liệu. Tải lên file SGK hoặc giáo án (Word, PDF) để câu hỏi bám nội dung bài."
                            >
                                <Button type="primary" size="large" onClick={() => setIsUploadModalOpen(true)}>
                                    Tải lên
                                </Button>
                            </Empty>
                        ),
                    }}
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
            </div>

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
                onClose={closePreview}
            />
        </div>
    );
}