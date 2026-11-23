import { Card, Typography } from "antd";
import dayjs from "dayjs";
import type { ImportBatch } from "../types/bankImport.types";
import "./ImportSummary.css";

const { Text, Title } = Typography;

interface ImportSummaryProps {
    batch: ImportBatch;
}

/** Tóm tắt một lần nhập: tên, khối, chủ đề, số câu đọc được / chọn được / bị chặn, giờ hết hạn (chỉ để biết, không đếm ngược). */
export default function ImportSummary({ batch }: ImportSummaryProps) {
    return (
        <Card size="small" className="import-summary">
            <Title level={5} className="import-summary-title">
                {batch.displayName}
            </Title>
            <Text type="secondary">
                Khối {batch.gradeLevel}
                {batch.topic ? ` · ${batch.topic}` : ""}
            </Text>
            <div className="import-summary-counts">
                <div>
                    <Text strong>{batch.questionCount}</Text>
                    <Text type="secondary"> câu đọc được trong file</Text>
                </div>
                <div>
                    <Text strong>{batch.importableCount}</Text>
                    <Text type="secondary"> câu chọn được</Text>
                </div>
                <div>
                    <Text strong type={batch.blockedCount > 0 ? "danger" : undefined}>
                        {batch.blockedCount}
                    </Text>
                    <Text type="secondary"> câu bị chặn (sửa trong file Word rồi tải lại)</Text>
                </div>
            </div>
            <Text type="secondary">Hết hạn lúc {dayjs(batch.expiresAt).format("HH:mm DD/MM/YYYY")}</Text>
        </Card>
    );
}
