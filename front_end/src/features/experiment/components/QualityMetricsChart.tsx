import { Progress, Tag, Typography } from "antd";
import "./QualityMetricsChart.css";

const { Text } = Typography;

export type QualityMetricType =
    | "FORMAT"
    | "DUPLICATE"
    | "DIFFICULTY";

export interface QualityMetricItem {
    type: QualityMetricType;
    label: string;
    description: string;
    passRate: number;
    sampleCount: number;
}

interface QualityMetricsChartProps {
    data?: QualityMetricItem[];
}

const DEFAULT_DATA: QualityMetricItem[] = [
    {
        type: "FORMAT",
        label: "Định dạng",
        description: "Tỷ lệ câu hỏi có output đúng định dạng yêu cầu",
        passRate: 98.4,
        sampleCount: 300,
    },
    {
        type: "DUPLICATE",
        label: "Không trùng lặp",
        description: "Tỷ lệ câu hỏi không bị phát hiện tương đồng/trùng lặp",
        passRate: 89.7,
        sampleCount: 300,
    },
    {
        type: "DIFFICULTY",
        label: "Độ khó",
        description: "Tỷ lệ câu hỏi đạt yêu cầu về độ khó",
        passRate: 81.3,
        sampleCount: 300,
    },
];

export default function QualityMetricsChart({
    data = DEFAULT_DATA,
}: QualityMetricsChartProps) {
    const averagePassRate =
        data.length > 0
            ? data.reduce((total, item) => total + item.passRate, 0) /
            data.length
            : 0;

    const bestMetric =
        data.length > 0
            ? data.reduce((best, current) =>
                current.passRate > best.passRate ? current : best,
            )
            : null;

    return (
        <div className="quality-metrics-chart">
            <div className="quality-metrics-chart-summary">
                <div className="quality-metrics-chart-summary-item">
                    <Text type="secondary">Tỷ lệ đạt trung bình</Text>

                    <Text
                        strong
                        className="quality-metrics-chart-summary-value"
                    >
                        {averagePassRate.toFixed(1)}%
                    </Text>
                </div>

                {bestMetric && (
                    <div className="quality-metrics-chart-summary-item">
                        <Text type="secondary">Cao nhất</Text>

                        <div className="quality-metrics-chart-best">
                            <Text
                                strong
                                className="quality-metrics-chart-summary-value"
                            >
                                {bestMetric.passRate.toFixed(1)}%
                            </Text>

                            <Tag>{bestMetric.label}</Tag>
                        </div>
                    </div>
                )}
            </div>

            <div className="quality-metrics-chart-list">
                {data.map((item) => (
                    <div
                        key={item.type}
                        className="quality-metrics-chart-item"
                    >
                        <div className="quality-metrics-chart-item-header">
                            <div className="quality-metrics-chart-item-title">
                                <Text strong>{item.label}</Text>

                                <Text
                                    type="secondary"
                                    className="quality-metrics-chart-description"
                                >
                                    {item.description}
                                </Text>
                            </div>

                            <Text
                                strong
                                className="quality-metrics-chart-value"
                            >
                                {item.passRate.toFixed(1)}%
                            </Text>
                        </div>

                        <Progress
                            percent={item.passRate}
                            showInfo={false}
                            size="default"
                        />

                        <div className="quality-metrics-chart-item-footer">
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