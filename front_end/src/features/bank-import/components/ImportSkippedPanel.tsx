import { Alert, Typography } from "antd";
import { describeSkipReason, skippedEntries } from "../utils/importSkipReasons";
import type { ImportSkippedItem } from "../types/bankImport.types";

const { Text } = Typography;

interface ImportSkippedPanelProps {
    skipped?: Record<string, number> | null;
    skippedItems?: ImportSkippedItem[] | null;
}

/** Câu bị bỏ khỏi bản xem trước (không nhập được ở bản này), đếm theo lý do kèm cách xử lý. Không có câu nào bị bỏ thì không hiện. */
export default function ImportSkippedPanel({ skipped, skippedItems }: ImportSkippedPanelProps) {
    const entries = skippedEntries(skipped);
    if (entries.length === 0) return null;
    const total = entries.reduce((sum, entry) => sum + entry.count, 0);
    const positions = skippedItems ?? [];

    return (
        <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            title={`Tổng cộng ${total} câu bị bỏ, không có trong danh sách dưới đây`}
            description={
                <div>
                    <ul style={{ margin: "0 0 8px", paddingLeft: 18 }}>
                        {entries.map((entry) => (
                            <li key={entry.reason}>
                                <Text strong>{entry.label}</Text>: {entry.count} câu.
                                {entry.hint ? <Text type="secondary"> {entry.hint}</Text> : null}
                            </li>
                        ))}
                    </ul>
                    {positions.length > 0 && (
                        <details>
                            <summary style={{ cursor: "pointer" }}>Xem vị trí các câu bị bỏ</summary>
                            <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
                                {positions.map((item, index) => (
                                    <li key={`${item.locator}-${index}`}>
                                        {item.locator} ({describeSkipReason(item.reason).label})
                                    </li>
                                ))}
                            </ul>
                        </details>
                    )}
                </div>
            }
        />
    );
}
