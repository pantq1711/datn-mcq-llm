import { Card, Typography } from "antd";
import { FileWordOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import type { ImportBatch } from "../types/bankImport.types";
import "./ImportSummary.css";

const { Text, Title } = Typography;

interface ImportSummaryProps {
    batch: ImportBatch;
}

/** Tóm tắt một lần nhập: tên, lớp, chủ đề, số câu đọc được / chọn được / bị chặn, giờ hết hạn (chỉ để biết, không đếm ngược). */
export default function ImportSummary({ batch }: ImportSummaryProps) {
    return (
        <Card className="bank-import-page__summary-card import-summary" title="Tóm tắt lần nhập" bordered>
            <div className="import-summary__header">
                <span className="import-summary__icon">
                    <FileWordOutlined />
                </span>
                <div className="import-summary__meta">
                    <Title level={5} className="import-summary-title">
                        {batch.displayName}
                    </Title>
                    <Text type="secondary">
                        Lớp {batch.gradeLevel}
                        {batch.topic ? ` · ${batch.topic}` : ""}
                    </Text>
                </div>
            </div>

            <div className="import-summary-counts">
                <div className="import-summary-stat">
                    <Text strong className="import-summary-stat__value">
                        {batch.questionCount}
                    </Text>
                    <Text type="secondary" className="import-summary-stat__label">
                        Đọc được
                    </Text>
                </div>
                <div className="import-summary-stat">
                    <Text strong className="import-summary-stat__value">
                        {batch.importableCount}
                    </Text>
                    <Text type="secondary" className="import-summary-stat__label">
                        Chọn được
                    </Text>
                </div>
                <div className="import-summary-stat">
                    <Text strong type={batch.blockedCount > 0 ? "danger" : undefined} className="import-summary-stat__value">
                        {batch.blockedCount}
                    </Text>
                    <Text type="secondary" className="import-summary-stat__label">
                        Bị chặn
                    </Text>
                </div>
            </div>

            <Text type="secondary" className="import-summary__expiry">
                Hết hạn lúc {dayjs(batch.expiresAt).format("HH:mm DD/MM/YYYY")}
            </Text>
        </Card>
    );
}
