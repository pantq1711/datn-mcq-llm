import { useState } from "react";
import { Alert, Button, Card, Popconfirm, Typography } from "antd";
import dayjs from "dayjs";
import type { ImportBatch } from "../types/bankImport.types";

const { Text } = Typography;

/** Mỗi giáo viên chỉ có tối đa 3 lần nhập chờ xác nhận (docs/api mục 10.1). */
export const MAX_PENDING_IMPORTS = 3;

interface PendingImportsPanelProps {
    batches: ImportBatch[];
    onOpen: (id: number) => void;
    /** Hủy một lần nhập; lỗi do nơi gọi xử lý. */
    onCancel: (id: number) => Promise<void>;
}

/** Các lần nhập đang chờ xác nhận (GET /api/bank-imports?status=preview): mở lại để nhập, hoặc hủy. Không có lô nào thì không hiện. */
export default function PendingImportsPanel({ batches, onOpen, onCancel }: PendingImportsPanelProps) {
    const [cancelling, setCancelling] = useState<number | null>(null);
    if (batches.length === 0) return null;

    const handleCancel = async (id: number) => {
        setCancelling(id);
        try {
            await onCancel(id);
        } finally {
            setCancelling(null);
        }
    };

    return (
        <Card size="small" title="Lần nhập đang chờ xác nhận" style={{ marginTop: 16 }}>
            {batches.length >= MAX_PENDING_IMPORTS && (
                <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 12 }}
                    title={`Thầy/cô đã đủ ${MAX_PENDING_IMPORTS} lần nhập chờ xác nhận; hãy mở lại để nhập hoặc hủy bớt trước khi tải file mới.`}
                />
            )}
            {batches.map((batch) => (
                <div
                    key={batch.id}
                    data-testid={`pending-import-${batch.id}`}
                    style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 8, padding: "6px 0" }}
                >
                    <div style={{ flex: "1 1 240px", minWidth: 0 }}>
                        <div>
                            <Text strong>{batch.displayName}</Text>
                        </div>
                        <Text type="secondary">
                            {batch.questionCount} câu · hết hạn lúc {dayjs(batch.expiresAt).format("HH:mm DD/MM/YYYY")}
                        </Text>
                    </div>
                    <Button type="primary" ghost onClick={() => onOpen(batch.id)}>
                        Mở lại
                    </Button>
                    <Popconfirm
                        title="Hủy lần nhập này?"
                        description="Bản xem trước và hình của lần nhập này sẽ bị xóa."
                        okText="Hủy lần nhập này"
                        cancelText="Giữ lại"
                        onConfirm={() => handleCancel(batch.id)}
                    >
                        <Button danger loading={cancelling === batch.id}>
                            Hủy lần nhập
                        </Button>
                    </Popconfirm>
                </div>
            ))}
        </Card>
    );
}
