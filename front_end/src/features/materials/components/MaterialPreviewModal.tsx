import { Alert, Button, Card, Modal, Space, Spin, Tag, Typography } from "antd";
import type { MaterialDto } from "../types/material.types";
import "./MaterialPreviewModal.css";
import { describeMaterialWarning } from "../../../utils/material.utils";

const { Paragraph } = Typography;

interface MaterialPreviewModalProps {
    material: MaterialDto | null;
    loading?: boolean;
    onClose: () => void;
}

export default function MaterialPreviewModal({
    material,
    loading = false,
    onClose,
}: MaterialPreviewModalProps) {
    const formatBytes = (bytes?: number) => {
        if (!bytes) return "0 B";
        const k = 1024;
        const sizes = ["B", "KB", "MB"];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
    };

    // Kiểm tra nếu có ít nhất một cảnh báo có severity là "error" (ví dụ: FORMULA_LOSS_HIGH)
    const hasErrorWarning = material?.warnings?.some(
        (w) => describeMaterialWarning(w.code).severity === "error"
    );

    return (
        <Modal
            open={material !== null}
            title={material ? `Xem trước: ${material.fileName}` : ""}
            onCancel={onClose}
            footer={[
                <Button key="close" onClick={onClose}>
                    Đóng
                </Button>,
            ]}
            width={650}
            className="material-preview-modal"
        >
            {loading ? (
                <div className="material-preview-loading">
                    <Spin tip="Đang nạp văn bản trích xuất..." />
                </div>
            ) : material ? (
                <div>
                    <div className="material-preview-tags">
                        <Space size={8} wrap>
                            <Tag color="blue">{material.kind.toUpperCase()}</Tag>
                            <Tag>{formatBytes(material.sizeBytes)}</Tag>
                            <Tag>{material.charCount.toLocaleString()} ký tự</Tag>
                        </Space>
                    </div>

                    {material.warnings && material.warnings.length > 0 && (
                        <Alert
                            type={hasErrorWarning ? "error" : "warning"}
                            showIcon
                            message="Cảnh báo trích xuất"
                            description={material.warnings.map((w, i) => {
                                const { label } = describeMaterialWarning(w.code);
                                return (
                                    <div key={i}>
                                        <strong>{label}:</strong> {w.message}
                                    </div>
                                );
                            })}
                            className="material-preview-alert"
                        />
                    )}

                    <Card size="small" title="Đoạn trích xuất văn bản (500 ký tự đầu)">
                        <Paragraph className="material-preview-text">
                            {material.textPreview || "Không có đoạn xem trước."}
                        </Paragraph>
                    </Card>
                </div>
            ) : null}
        </Modal>
    );
}