import { useEffect, useRef, useState } from "react";
import { Alert, Button, Checkbox, Empty, List, Modal, Popconfirm, Space, Spin, Tabs, Tag, Tooltip, Typography, Upload, message, } from "antd";
import { DeleteOutlined, EyeOutlined, InboxOutlined, PlusOutlined, WarningOutlined, } from "@ant-design/icons";
import type { UploadProps } from "antd";
import type { MaterialDto } from "../types/material.types";
import { deleteMaterial, getMaterial, listMaterials, uploadMaterial, } from "../services/materialApi";
import MaterialPreviewModal from "./MaterialPreviewModal";
import "./MaterialManagerModal.css";
import { describeMaterialWarning, formatBytes, renderFileIcon } from "../../../utils/material.utils";

const { Text } = Typography;
const { Dragger } = Upload;
// Khớp giới hạn mặc định của backend (docs/api mục 9, spring.servlet.multipart.max-file-size)
const MAX_FILE_SIZE_MB = 10;

export interface MaterialManagerModalProps {
    open: boolean;
    selectedIds?: number[];
    onClose: () => void;
    /** Bỏ qua thì modal chỉ quản lý / tải lên, không có phần chọn. */
    onSelect?: (selectedMaterials: MaterialDto[]) => void;
    onRemoved?: (removedId: number) => void;
    maxSelect?: number;
    /** Id đã tính ở chỗ khác cùng lần tạo đề; maxSelect khi đó áp cho hợp checkedIds ∪ reservedIds (docs/api mục 7.2). */
    reservedIds?: number[];
    title?: string;
}

export default function MaterialManagerModal({
    open,
    selectedIds = [],
    onClose,
    onSelect,
    onRemoved,
    maxSelect = 5,
    reservedIds,
    title = "Quản lý tài liệu bài học",
}: MaterialManagerModalProps) {
    const [materials, setMaterials] = useState<MaterialDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [activeTab, setActiveTab] = useState<string>("list");

    const [checkedIds, setCheckedIds] = useState<number[]>([]);
    const [previewMaterial, setPreviewMaterial] = useState<MaterialDto | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);

    // Lưu lại thông tin tài liệu vừa upload/tái sử dụng để hiển thị cảnh báo trong tab upload
    const [lastUploadedMaterial, setLastUploadedMaterial] = useState<MaterialDto | null>(null);

    // Dùng useRef để giữ giá trị selectedIds ban đầu khi modal mở mà không gây re-trigger effect
    const selectedIdsRef = useRef(selectedIds);
    selectedIdsRef.current = selectedIds;

    // Không có onSelect (trang /materials): modal chỉ để quản lý và tải lên, ẩn mọi phần chọn
    const selectable = onSelect !== undefined;

    useEffect(() => {
        if (open) {
            // Chỉ quản lý / tải lên (trang /materials đã có bảng danh sách): mở thẳng tab tải lên
            setActiveTab(selectable ? "list" : "upload");
            setCheckedIds(selectedIdsRef.current);
            setLastUploadedMaterial(null); // Reset cảnh báo cũ khi mở lại modal
            fetchMaterials();
        }
    }, [open]);

    const fetchMaterials = async () => {
        setLoading(true);
        try {
            const data = await listMaterials(0, 50);
            setMaterials(data.items || []);
        } catch (err: any) {
            message.error(err?.response?.data?.error || "Không thể tải danh sách tài liệu");
        } finally {
            setLoading(false);
        }
    };

    // Số tài liệu khác nhau tính vào giới hạn: id đang chọn cộng id đã dùng ở chỗ khác (trùng tính một)
    const countDistinct = (ids: number[]) => new Set([...ids, ...(reservedIds ?? [])]).size;
    const canAdd = (ids: number[], id: number) => countDistinct([...ids, id]) <= maxSelect;

    const handleToggleCheck = (id: number) => {
        if (!selectable) return;
        if (checkedIds.includes(id)) {
            setCheckedIds((prev) => prev.filter((item) => item !== id));
        } else {
            if (!canAdd(checkedIds, id)) {
                message.warning(
                    reservedIds
                        ? `Cả lần tạo đề chỉ được tối đa ${maxSelect} tài liệu khác nhau (gồm tài liệu chung và tài liệu từng ô).`
                        : `Tối đa chỉ được chọn ${maxSelect} tài liệu.`
                );
                return;
            }
            setCheckedIds((prev) => [...prev, id]);
        }
    };

    const handleConfirmSelection = () => {
        const selected = materials.filter((m) => checkedIds.includes(m.id));
        onSelect?.(selected);
        onClose();
    };

    const handleDelete = async (id: number) => {
        try {
            await deleteMaterial(id);
            message.success("Đã xóa tài liệu thành công");
            setMaterials((prev) => prev.filter((m) => m.id !== id));
            setCheckedIds((prev) => prev.filter((item) => item !== id));

            // Gọi callback thông báo cho component cha gỡ id này ra khỏi danh sách đang chọn
            onRemoved?.(id);
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
        } catch (err: any) {
            message.error("Không tải được chi tiết tài liệu");
        } finally {
            setPreviewLoading(false);
        }
    };

    const uploadProps: UploadProps = {
        name: "file",
        multiple: false,
        accept: ".docx,.pdf,.txt",
        beforeUpload: (file) => {
            if (file.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
                message.error(`File "${file.name}" có dung lượng ${formatBytes(file.size)}, vượt quá giới hạn ${MAX_FILE_SIZE_MB} MB.`);
                return Upload.LIST_IGNORE;
            }
            return true;
        },
        customRequest: async ({ file, onSuccess, onError }) => {
            try {
                const { material: uploaded, created } = await uploadMaterial(file as File);

                if (created) {
                    message.success(`Tải lên thành công: ${uploaded.fileName}`);
                } else {
                    message.success(`Tài liệu này đã có (${uploaded.fileName}), dùng lại bản đã lưu.`);
                }

                // Lưu lại object tài liệu để hiển thị alert warning ngay trong tab upload
                setLastUploadedMaterial(uploaded);

                onSuccess?.(uploaded);
                // Refresh list
                await fetchMaterials();

                // Chỉ tự động chọn tài liệu vừa tải/tái sử dụng khi modal có hỗ trợ chọn (selectable)
                if (selectable) {
                    setCheckedIds((prev) =>
                        !prev.includes(uploaded.id) && canAdd(prev, uploaded.id) ? [...prev, uploaded.id] : prev
                    );
                }
            } catch (err: any) {
                const msg =
                    err?.response?.data?.error ||
                    err?.response?.data?.message ||
                    `Tải lên thất bại. Vui lòng kiểm tra định dạng hoặc dung lượng (tối đa ${MAX_FILE_SIZE_MB} MB).`;
                message.error(msg);
                onError?.(err);
            }
        },
        showUploadList: false,
    };

    return (
        <Modal
            open={open}
            title={title}
            onCancel={onClose}
            width={780}
            destroyOnClose
            className="material-manager-modal"
            footer={
                selectable ? (
                    <div className="material-modal-footer">
                        <div>
                            {reservedIds ? (
                                <>
                                    <Text strong>Tổng cả lần tạo đề: </Text>
                                    <Tag color={countDistinct(checkedIds) > 0 ? "blue" : "default"}>
                                        {countDistinct(checkedIds)} / {maxSelect} tài liệu
                                    </Tag>
                                </>
                            ) : (
                                <>
                                    <Text strong>Đã chọn: </Text>
                                    <Tag color={checkedIds.length > 0 ? "blue" : "default"}>
                                        {checkedIds.length} / {maxSelect} tài liệu
                                    </Tag>
                                </>
                            )}
                        </div>
                        <Space>
                            <Button onClick={onClose}>Đóng</Button>
                            <Button
                                type="primary"
                                onClick={handleConfirmSelection}
                            >
                                Xác nhận chọn ({checkedIds.length})
                            </Button>
                        </Space>
                    </div>
                ) : (
                    <div className="material-modal-footer" style={{ justifyContent: "flex-end" }}>
                        <Button onClick={onClose}>Đóng</Button>
                    </div>
                )
            }
        >
            <Tabs
                activeKey={activeTab}
                onChange={setActiveTab}
                items={[
                    {
                        key: "list",
                        label: `Danh sách tài liệu (${materials.length})`,
                        children: (
                            <div className="material-list-container">
                                {loading ? (
                                    <div style={{ textAlign: "center", padding: "40px 0" }}>
                                        <Spin tip="Đang tải danh sách tài liệu..." />
                                    </div>
                                ) : materials.length === 0 ? (
                                    <Empty
                                        description="Bạn chưa tải lên tài liệu bài học nào."
                                        style={{ padding: "30px 0" }}
                                    >
                                        <Button
                                            type="primary"
                                            icon={<PlusOutlined />}
                                            onClick={() => setActiveTab("upload")}
                                        >
                                            Tải lên tài liệu ngay
                                        </Button>
                                    </Empty>
                                ) : (
                                    <List
                                        dataSource={materials}
                                        renderItem={(item) => {
                                            const isChecked = checkedIds.includes(item.id);
                                            return (
                                                <List.Item
                                                    className={`material-list-item ${selectable && isChecked ? "is-selected" : ""}`}
                                                    onClick={() => selectable && handleToggleCheck(item.id)}
                                                    style={{ cursor: selectable ? "pointer" : "default" }}
                                                >
                                                    <div className="material-item-left">
                                                        {selectable && (
                                                            <Checkbox
                                                                checked={isChecked}
                                                                onChange={(e) => {
                                                                    e.stopPropagation();
                                                                    handleToggleCheck(item.id);
                                                                }}
                                                            />
                                                        )}
                                                        <div className="material-icon-box">
                                                            {renderFileIcon(item.kind, 24)}
                                                        </div>
                                                        <div className="material-item-info">
                                                            <div className="material-item-title-row">
                                                                <Text strong className="material-item-filename">
                                                                    {item.fileName}
                                                                </Text>
                                                                {selectable && reservedIds?.includes(item.id) && (
                                                                    <Tooltip title="Đã tính vào giới hạn; chọn thêm ở đây không tốn suất">
                                                                        <Tag color="default">Đã dùng ở chỗ khác</Tag>
                                                                    </Tooltip>
                                                                )}
                                                                {item.warnings && item.warnings.length > 0 && (
                                                                    item.warnings.map((w, wIndex) => {
                                                                        const { label, severity } = describeMaterialWarning(w.code);
                                                                        return (
                                                                            <Tooltip key={wIndex} title={w.message}>
                                                                                <Tag color={severity} icon={<WarningOutlined />}>
                                                                                    {label}
                                                                                </Tag>
                                                                            </Tooltip>
                                                                        );
                                                                    })
                                                                )}
                                                            </div>
                                                            <div className="material-item-meta">
                                                                <Text type="secondary">
                                                                    {formatBytes(item.sizeBytes)} • {item.charCount.toLocaleString()} ký tự
                                                                    {item.pageCount ? ` • ${item.pageCount} trang` : ""}
                                                                </Text>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    <Space size={4} onClick={(e) => e.stopPropagation()}>
                                                        <Tooltip title="Xem trước văn bản trích xuất">
                                                            <Button
                                                                type="text"
                                                                icon={<EyeOutlined />}
                                                                onClick={() => handlePreview(item)}
                                                            />
                                                        </Tooltip>
                                                        <Popconfirm
                                                            title="Xóa tài liệu này?"
                                                            description="Hệ thống sẽ xóa văn bản trích xuất đã lưu."
                                                            onConfirm={() => handleDelete(item.id)}
                                                            okText="Xóa"
                                                            cancelText="Hủy"
                                                            okButtonProps={{ danger: true }}
                                                        >
                                                            <Tooltip title="Xóa tài liệu">
                                                                <Button
                                                                    type="text"
                                                                    danger
                                                                    icon={<DeleteOutlined />}
                                                                />
                                                            </Tooltip>
                                                        </Popconfirm>
                                                    </Space>
                                                </List.Item>
                                            );
                                        }}
                                    />
                                )}
                            </div>
                        ),
                    },
                    {
                        key: "upload",
                        label: "Tải lên tài liệu mới",
                        children: (
                            <div className="material-upload-container">
                                <Alert
                                    type="info"
                                    showIcon
                                    message="Quy định tải tài liệu bài học (W2-3)"
                                    description={`Chấp nhận file .docx, .pdf hoặc .txt UTF-8, tối đa ${MAX_FILE_SIZE_MB} MB. Hệ thống sẽ tự động trích xuất nội dung văn bản để cung cấp ngữ cảnh chuẩn xác cho mô hình AI sinh câu hỏi.`}
                                    style={{ marginBottom: 16 }}
                                />

                                {/* Hiển thị cảnh báo từ kết quả tải lên gần nhất (nếu có) */}
                                {lastUploadedMaterial?.warnings && lastUploadedMaterial.warnings.length > 0 && (
                                    <div style={{ marginBottom: 16 }}>
                                        {lastUploadedMaterial.warnings.map((w, index) => {
                                            const { label, severity } = describeMaterialWarning(w.code);
                                            return (
                                                <Alert
                                                    key={index}
                                                    type={severity}
                                                    showIcon
                                                    message={label}
                                                    description={w.message}
                                                    style={{ marginBottom: 8 }}
                                                />
                                            );
                                        })}
                                    </div>
                                )}

                                <Dragger {...uploadProps} className="material-dragger">
                                    <p className="ant-upload-drag-icon">
                                        <InboxOutlined style={{ color: "#1677ff", fontSize: 48 }} />
                                    </p>
                                    <p className="ant-upload-text">
                                        Nhấp hoặc kéo thả file bài học vào khu vực này để tải lên
                                    </p>
                                    <p className="ant-upload-hint">
                                        Hỗ trợ các định dạng Microsoft Word (.docx), PDF (.pdf) hoặc Plain Text (.txt)
                                    </p>
                                </Dragger>
                            </div>
                        ),
                    },
                ]}
            />

            {/* Modal Xem trước văn bản trích xuất (Sử dụng component tách rời) */}
            <MaterialPreviewModal
                material={previewMaterial}
                loading={previewLoading}
                onClose={() => setPreviewMaterial(null)}
            />
        </Modal>
    );
}