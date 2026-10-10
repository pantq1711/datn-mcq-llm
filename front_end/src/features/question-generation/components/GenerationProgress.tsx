import {
    Progress,
    Space,
    Statistic,
    Steps,
    Tag,
    Typography,
} from "antd";
import {
    CheckCircleOutlined,
    CloseCircleOutlined,
    LoadingOutlined,
    ClockCircleOutlined,
} from "@ant-design/icons";

import "./GenerationProgress.css";

const { Text } = Typography;

export type GenerationStatus =
    | "IDLE"
    | "RUNNING"
    | "COMPLETED"
    | "FAILED";

interface GenerationProgressProps {
    status?: GenerationStatus;
    completedCount?: number;
    totalCount?: number;
    currentQuestion?: number;
    errorMessage?: string;
}

const statusConfig: Record<
    GenerationStatus,
    {
        label: string;
        color: string;
        icon: React.ReactNode;
    }
> = {
    IDLE: {
        label: "Chưa bắt đầu",
        color: "default",
        icon: <ClockCircleOutlined />,
    },
    RUNNING: {
        label: "Đang sinh",
        color: "processing",
        icon: <LoadingOutlined />,
    },
    COMPLETED: {
        label: "Hoàn thành",
        color: "success",
        icon: <CheckCircleOutlined />,
    },
    FAILED: {
        label: "Thất bại",
        color: "error",
        icon: <CloseCircleOutlined />,
    },
};

export default function GenerationProgress({
    status = "IDLE",
    completedCount = 0,
    totalCount = 0,
    currentQuestion,
    errorMessage,
}: GenerationProgressProps) {
    const progress =
        totalCount > 0
            ? Math.min((completedCount / totalCount) * 100, 100)
            : 0;

    const config = statusConfig[status];

    const currentStep =
        status === "IDLE"
            ? 0
            : status === "RUNNING"
                ? 1
                : 2;

    const stepStatus =
        status === "FAILED"
            ? "error"
            : status === "COMPLETED"
                ? "finish"
                : "process";

    return (
        <div className="generation-progress">
            <div className="generation-progress-header">
                <div className="generation-progress-title">
                    <Text strong>Tiến trình sinh câu hỏi</Text>

                    <Tag
                        color={config.color}
                        icon={config.icon}
                    >
                        {config.label}
                    </Tag>
                </div>

                {status === "RUNNING" && currentQuestion !== undefined && (
                    <Text type="secondary">
                        Đang xử lý câu hỏi {currentQuestion}
                        {totalCount > 0 ? ` / ${totalCount}` : ""}
                    </Text>
                )}
            </div>

            <div className="generation-progress-steps">
                <Steps
                    current={currentStep}
                    status={stepStatus}
                    items={[
                        {
                            title: "Chuẩn bị",
                        },
                        {
                            title: "Đang sinh",
                        },
                        {
                            title: "Hoàn tất",
                        },
                    ]}
                />
            </div>

            <div className="generation-progress-content">
                <Progress
                    percent={Math.round(progress)}
                    status={
                        status === "FAILED"
                            ? "exception"
                            : status === "COMPLETED"
                                ? "success"
                                : "active"
                    }
                    strokeWidth={10}
                    format={(percent) => `${percent}%`}
                />
            </div>

            <div className="generation-progress-statistics">
                <Space
                    size="large"
                    wrap
                    className="generation-progress-statistics-space"
                >
                    <Statistic
                        title="Đã sinh"
                        value={completedCount}
                        suffix={
                            totalCount > 0
                                ? ` / ${totalCount}`
                                : undefined
                        }
                    />

                    <Statistic
                        title="Còn lại"
                        value={Math.max(
                            totalCount - completedCount,
                            0,
                        )}
                    />
                </Space>
            </div>

            {status === "FAILED" && errorMessage && (
                <div className="generation-progress-error">
                    <Text type="danger">
                        {errorMessage}
                    </Text>
                </div>
            )}
        </div>
    );
}