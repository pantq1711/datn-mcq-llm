import { Alert, Button, Card, Modal, Space, Spin, Tag, Typography } from "antd";
import type { MaterialDto } from "../types/material.types";
import "./MaterialPreviewModal.css";
import { describeMaterialWarning, formatBytes } from "../../../utils/material.utils";
import MathText from "../../../components/common/MathText/MathText";

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
                    <Spin description="Đang tải nội dung đã đọc..." />
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
                            message="Lưu ý khi đọc tài liệu"
                            description={
                                <>
                                    {material.warnings.map((w, i) => {
                                        const { label } = describeMaterialWarning(w.code);
                                        return (
                                            <div key={i}>
                                                <strong>{label}:</strong> {w.message}
                                            </div>
                                        );
                                    })}
                                    <div style={{ marginTop: 4 }}>
                                        Thầy/cô xem nội dung đã đọc trước khi dùng, hoặc tải bản PDF.
                                    </div>
                                </>
                            }
                            className="material-preview-alert"
                        />
                    )}

                    <Card size="small" title="Nội dung đã đọc (500 ký tự đầu)">
                        <Paragraph className="material-preview-text">
                            {!material.textPreview
                                ? "Không có đoạn xem trước."
                                // Chỉ docx có quy ước công thức $...$ (pandoc, MathType → LaTeX). PDF/txt giữ chữ thô: dấu $
                                // thật (giá tiền…) không bị vẽ nhầm thành công thức; công thức trong PDF vốn méo khi trích.
                                // Đoạn cắt ở 500 ký tự giữa công thức: MathText giữ phần thiếu dấu đóng như chữ thường.
                                : material.kind === "docx"
                                    ? <MathText text={material.textPreview} />
                                    : material.textPreview}
                        </Paragraph>
                    </Card>
                </div>
            ) : null}
        </Modal>
    );
}