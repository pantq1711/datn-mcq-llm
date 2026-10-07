import { Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import "./StrategyComparison.css";

const { Text } = Typography;

export interface StrategyComparisonItem {
    strategy: string;
    label: string;
    acceptanceRate: number;
    formatPassRate: number;
    duplicatePassRate: number;
    difficultyPassRate: number;
    averageEditDistance: number;
    sampleCount: number;
}

interface StrategyComparisonProps {
    data?: StrategyComparisonItem[];
    loading?: boolean;
}

const DEFAULT_DATA: StrategyComparisonItem[] = [
    {
        strategy: "ZERO_SHOT",
        label: "Zero-shot",
        acceptanceRate: 72.4,
        formatPassRate: 96.8,
        duplicatePassRate: 86.2,
        difficultyPassRate: 78.4,
        averageEditDistance: 18.6,
        sampleCount: 100,
    },
    {
        strategy: "FEW_SHOT",
        label: "Few-shot",
        acceptanceRate: 84.7,
        formatPassRate: 98.1,
        duplicatePassRate: 90.4,
        difficultyPassRate: 83.7,
        averageEditDistance: 12.4,
        sampleCount: 100,
    },
    {
        strategy: "STRUCTURED_OUTPUT",
        label: "Structured Output",
        acceptanceRate: 91.2,
        formatPassRate: 99.6,
        duplicatePassRate: 92.1,
        difficultyPassRate: 87.9,
        averageEditDistance: 8.7,
        sampleCount: 100,
    },
];

function getBestValue(
    data: StrategyComparisonItem[],
    key: keyof StrategyComparisonItem,
    direction: "MAX" | "MIN",
): number | null {
    if (data.length === 0) {
        return null;
    }

    const values = data
        .map((item) => item[key])
        .filter((value): value is number => typeof value === "number");

    if (values.length === 0) {
        return null;
    }

    return direction === "MAX"
        ? Math.max(...values)
        : Math.min(...values);
}

function renderMetric(
    value: number,
    bestValue: number | null,
    suffix = "%",
) {
    const isBest =
        bestValue !== null &&
        Math.abs(value - bestValue) < Number.EPSILON;

    return (
        <div className="strategy-comparison-metric">
            <Text strong>{value.toFixed(1)}{suffix}</Text>

            {isBest && (
                <Tag color="success">
                    Tốt nhất
                </Tag>
            )}
        </div>
    );
}

export default function StrategyComparison({
    data = DEFAULT_DATA,
    loading = false,
}: StrategyComparisonProps) {
    const bestAcceptanceRate = getBestValue(
        data,
        "acceptanceRate",
        "MAX",
    );

    const bestFormatPassRate = getBestValue(
        data,
        "formatPassRate",
        "MAX",
    );

    const bestDuplicatePassRate = getBestValue(
        data,
        "duplicatePassRate",
        "MAX",
    );

    const bestDifficultyPassRate = getBestValue(
        data,
        "difficultyPassRate",
        "MAX",
    );

    const bestEditDistance = getBestValue(
        data,
        "averageEditDistance",
        "MIN",
    );

    const columns: ColumnsType<StrategyComparisonItem> = [
        {
            title: "Chiến lược",
            dataIndex: "label",
            key: "strategy",
            fixed: "left",
            width: 180,
            render: (
                label: string,
                record: StrategyComparisonItem,
            ) => (
                <div className="strategy-comparison-strategy">
                    <Text strong>{label}</Text>

                    <Text
                        type="secondary"
                        className="strategy-comparison-sample"
                    >
                        {record.sampleCount} câu hỏi
                    </Text>
                </div>
            ),
        },
        {
            title: "Tỷ lệ chấp nhận",
            dataIndex: "acceptanceRate",
            key: "acceptanceRate",
            width: 150,
            render: (value: number) =>
                renderMetric(value, bestAcceptanceRate),
        },
        {
            title: "Định dạng",
            dataIndex: "formatPassRate",
            key: "formatPassRate",
            width: 140,
            render: (value: number) =>
                renderMetric(value, bestFormatPassRate),
        },
        {
            title: "Không trùng lặp",
            dataIndex: "duplicatePassRate",
            key: "duplicatePassRate",
            width: 160,
            render: (value: number) =>
                renderMetric(value, bestDuplicatePassRate),
        },
        {
            title: "Độ khó",
            dataIndex: "difficultyPassRate",
            key: "difficultyPassRate",
            width: 140,
            render: (value: number) =>
                renderMetric(value, bestDifficultyPassRate),
        },
        {
            title: "Edit distance",
            dataIndex: "averageEditDistance",
            key: "averageEditDistance",
            width: 150,
            render: (value: number) =>
                renderMetric(value, bestEditDistance, ""),
        },
    ];

    return (
        <div className="strategy-comparison">
            <div className="strategy-comparison-description">
                <Text type="secondary">
                    So sánh kết quả của các chiến lược sinh câu hỏi trên
                    các chỉ số chất lượng và mức độ chỉnh sửa của giáo viên.
                </Text>
            </div>

            <Table
                rowKey="strategy"
                columns={columns}
                dataSource={data}
                loading={loading}
                pagination={false}
                scroll={{ x: "max-content" }}
                className="strategy-comparison-table"
            />
        </div>
    );
}