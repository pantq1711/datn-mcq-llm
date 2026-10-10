import { Progress, Tag, Typography } from "antd";
import "./AcceptanceRateChart.css";

const { Text } = Typography;

export interface AcceptanceRateItem {
    strategy: string;
    label: string;
    acceptanceRate: number;
    sampleCount: number;
}

interface AcceptanceRateChartProps {
    data?: AcceptanceRateItem[];
}

const DEFAULT_DATA: AcceptanceRateItem[] = [
    {
        strategy: "ZERO_SHOT",
        label: "Zero-shot",
        acceptanceRate: 72.4,
        sampleCount: 100,
    },
    {
        strategy: "FEW_SHOT",
        label: "Few-shot",
        acceptanceRate: 84.7,
        sampleCount: 100,
    },
    {
        strategy: "STRUCTURED_OUTPUT",
        label: "Structured Output",
        acceptanceRate: 91.2,
        sampleCount: 100,
    },
];

export default function AcceptanceRateChart({
    data = DEFAULT_DATA,
}: AcceptanceRateChartProps) {
    const averageRate =
        data.length > 0
            ? data.reduce(
                (total, item) => total + item.acceptanceRate,
                0,
            ) / data.length
            : 0;

    const bestStrategy =
        data.length > 0
            ? data.reduce((best, current) =>
                current.acceptanceRate > best.acceptanceRate
                    ? current
                    : best,
            )
            : null;

    return (
        <div className="acceptance-rate-chart">
            <div className="acceptance-rate-chart-summary">
                <div className="acceptance-rate-chart-summary-item">
                    <Text type="secondary">Trung bình</Text>
                    <Text strong className="acceptance-rate-chart-summary-value">
                        {averageRate.toFixed(1)}%
                    </Text>
                </div>

                {bestStrategy && (
                    <div className="acceptance-rate-chart-summary-item">
                        <Text type="secondary">Cao nhất</Text>
                        <div className="acceptance-rate-chart-best">
                            <Text strong className="acceptance-rate-chart-summary-value">
                                {bestStrategy.acceptanceRate.toFixed(1)}%
                            </Text>
                            <Tag>{bestStrategy.label}</Tag>
                        </div>
                    </div>
                )}
            </div>

            <div className="acceptance-rate-chart-list">
                {data.map((item) => (
                    <div
                        key={item.strategy}
                        className="acceptance-rate-chart-item"
                    >
                        <div className="acceptance-rate-chart-item-header">
                            <Text className="acceptance-rate-chart-label">
                                {item.label}
                            </Text>

                            <Text strong className="acceptance-rate-chart-value">
                                {item.acceptanceRate.toFixed(1)}%
                            </Text>
                        </div>

                        <Progress
                            percent={item.acceptanceRate}
                            showInfo={false}
                            size="default"
                        />

                        <div className="acceptance-rate-chart-item-footer">
                            <Text type="secondary">
                                {item.sampleCount} câu hỏi
                            </Text>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}