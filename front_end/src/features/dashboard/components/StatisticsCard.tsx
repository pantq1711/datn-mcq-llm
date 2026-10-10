import { Card, Statistic, Typography } from "antd";
import type { ReactNode } from "react";
import "./StatisticsCard.css";

const { Text } = Typography;

interface StatisticsCardProps {
    title: string;
    value: number;
    prefix?: ReactNode;
    suffix?: string;
    description?: string;
}

export default function StatisticsCard({
    title,
    value,
    prefix,
    suffix,
    description,
}: StatisticsCardProps) {
    return (
        <Card className="statistics-card">
            <Statistic
                title={title}
                value={value}
                prefix={prefix}
                suffix={suffix}
            />

            {description && (
                <Text
                    type="secondary"
                    className="statistics-card-description"
                >
                    {description}
                </Text>
            )}
        </Card>
    );
}