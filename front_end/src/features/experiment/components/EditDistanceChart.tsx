import { Tag, Tooltip, Typography } from "antd";
import "./EditDistanceChart.css";

const { Text } = Typography;

export interface EditDistanceItem {
    strategy: string;
    label: string;
    averageEditDistance: number;
    sampleCount: number;
}

interface EditDistanceChartProps {
    data?: EditDistanceItem[];
}

const DEFAULT_DATA: EditDistanceItem[] = [
    {
        strategy: "ZERO_SHOT",
        label: "Zero-shot",
        averageEditDistance: 18.6,
        sampleCount: 100,
    },
    {
        strategy: "FEW_SHOT",
        label: "Few-shot",
        averageEditDistance: 12.4,
        sampleCount: 100,
    },
    {
        strategy: "STRUCTURED_OUTPUT",
        label: "Structured Output",
        averageEditDistance: 8.7,
        sampleCount: 100,
    },
];

export default function EditDistanceChart({
    data = DEFAULT_DATA,
}: EditDistanceChartProps) {
    const maxDistance =
        data.length > 0
            ? Math.max(...data.map((item) => item.averageEditDistance))
            : 0;

    const averageDistance =
        data.length > 0
            ? data.reduce(
                (total, item) => total + item.averageEditDistance,
                0,
            ) / data.length
            : 0;

    const bestStrategy =
        data.length > 0
            ? data.reduce((best, current) =>
                current.averageEditDistance < best.averageEditDistance
                    ? current
                    : best,
            )
            : null;

    return (
        <div className="edit-distance-chart">
            <div className="edit-distance-chart-summary">
                <div className="edit-distance-chart-summary-item">
                    <Text type="secondary">Trung bình</Text>

                    <Text
                        strong
                        className="edit-distance-chart-summary-value"
                    >
                        {averageDistance.toFixed(1)}
                    </Text>
                </div>

                {bestStrategy && (
                    <div className="edit-distance-chart-summary-item">
                        <Text type="secondary">Thấp nhất</Text>

                        <div className="edit-distance-chart-best">
                            <Text
                                strong
                                className="edit-distance-chart-summary-value"
                            >
                                {bestStrategy.averageEditDistance.toFixed(1)}
                            </Text>

                            <Tag>{bestStrategy.label}</Tag>
                        </div>
                    </div>
                )}
            </div>

            <div className="edit-distance-chart-list">
                {data.map((item) => {
                    const percentage =
                        maxDistance > 0
                            ? (item.averageEditDistance / maxDistance) * 100
                            : 0;

                    return (
                        <div
                            key={item.strategy}
                            className="edit-distance-chart-item"
                        >
                            <div className="edit-distance-chart-item-header">
                                <Text className="edit-distance-chart-label">
                                    {item.label}
                                </Text>

                                <Tooltip title="Khoảng cách chỉnh sửa trung bình">
                                    <Text
                                        strong
                                        className="edit-distance-chart-value"
                                    >
                                        {item.averageEditDistance.toFixed(1)}
                                    </Text>
                                </Tooltip>
                            </div>

                            <div className="edit-distance-chart-track">
                                <div
                                    className="edit-distance-chart-bar"
                                    style={{
                                        width: `${percentage}%`,
                                    }}
                                />
                            </div>

                            <div className="edit-distance-chart-item-footer">
                                <Text type="secondary">
                                    {item.sampleCount} câu hỏi
                                </Text>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}