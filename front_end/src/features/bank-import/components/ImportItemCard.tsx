import { memo } from "react";
import { Alert, Card, Checkbox, Tag, Tooltip, Typography } from "antd";
import MathText from "../../../components/common/MathText/MathText";
import { QUESTION_TYPE_LABELS, describeLevel } from "../../../utils/exam.utils";
import { describeFlag, splitItemFlags } from "../utils/importFlags";
import type { ImportItem } from "../types/bankImport.types";
import "./ImportItemCard.css";

const { Text } = Typography;

/** Câu dự phòng khi lô lưu trước 16/11 không có blockReasons. */
const BLOCKED_FALLBACK = "Câu chưa đúng cấu trúc của mẫu nhập; sửa trong file Word rồi tải lại.";

interface ImportItemCardProps {
    item: ImportItem;
    selected: boolean;
    onToggle: (key: string) => void;
}

function ImportItemCard({ item, selected, onToggle }: ImportItemCardProps) {
    const { figure, other } = splitItemFlags(item);
    const level = describeLevel(item.level);
    const images = item.images;
    const reasons = item.blockReasons?.length ? item.blockReasons : [BLOCKED_FALLBACK];

    return (
        <Card
            size="small"
            className={item.blocking ? "import-item import-item--blocked" : "import-item"}
            data-testid={`import-item-${item.key}`}
        >
            <div className="import-item-head">
                <Checkbox
                    aria-label={`Chọn câu ${item.key}`}
                    checked={selected && !item.blocking}
                    disabled={item.blocking}
                    onChange={() => onToggle(item.key)}
                />
                <Text strong>{item.locator}</Text>
                <Tag>{QUESTION_TYPE_LABELS[item.type] ?? item.type}</Tag>
                <Tag color={level.color}>{level.label}</Tag>
                {other.map((flag) => {
                    const info = describeFlag(flag);
                    return (
                        <Tooltip key={flag} title={info.hint}>
                            <Tag>{info.label}</Tag>
                        </Tooltip>
                    );
                })}
            </div>

            {item.blocking && (
                <Alert
                    type="error"
                    showIcon
                    className="import-item-alert"
                    title="Không nhập được câu này."
                    description={
                        <ul className="import-item-list">
                            {reasons.map((reason) => (
                                <li key={reason}>{reason}</li>
                            ))}
                        </ul>
                    }
                />
            )}

            {figure.length > 0 && (
                <div className="import-item-figure-flags">
                    {figure.map((flag) => {
                        const info = describeFlag(flag);
                        return (
                            <div key={flag} className="import-item-figure-flag">
                                <Tag color={info.tone === "warn" ? "orange" : "blue"}>{info.label}</Tag>
                                <Text type={info.tone === "warn" ? undefined : "secondary"}>{info.hint}</Text>
                            </div>
                        );
                    })}
                </div>
            )}

            <div className="import-item-stem">
                <MathText text={item.stem} images={images} />
            </div>

            {item.type === "mcq" && (item.options ?? []).length > 0 && (
                <div className="import-item-options">
                    {(item.options ?? []).map((option) => (
                        <div key={option.key} className="import-item-option">
                            <Text strong>{option.key}.</Text>
                            <span>
                                <MathText text={option.text} images={images} />
                            </span>
                            {item.correctOption === option.key && <Tag color="green">Đáp án</Tag>}
                        </div>
                    ))}
                </div>
            )}

            {item.type === "tf" && (item.statements ?? []).length > 0 && (
                <div className="import-item-options">
                    {(item.statements ?? []).map((statement) => (
                        <div key={statement.key} className="import-item-option">
                            <Text strong>{statement.key})</Text>
                            <span>
                                <MathText text={statement.text} images={images} />
                            </span>
                            {statement.label === true && <Tag color="green">Đúng</Tag>}
                            {statement.label === false && <Tag color="red">Sai</Tag>}
                            {statement.label === null && <Tag>Chưa rõ</Tag>}
                        </div>
                    ))}
                </div>
            )}

            {item.type === "sa" && (
                <div className="import-item-options">
                    <Text strong>Đáp số: </Text>
                    <MathText text={item.saAnswer} images={images} />
                </div>
            )}

            {item.explanation && (
                <details className="import-item-solution">
                    <summary>Lời giải</summary>
                    <MathText text={item.explanation} images={images} />
                </details>
            )}
        </Card>
    );
}

export default memo(ImportItemCard);
