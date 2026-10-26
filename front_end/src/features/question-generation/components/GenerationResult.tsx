import {
    Card,
    Empty,
    List,
    Skeleton,
    Space,
    Tag,
    Typography,
} from "antd";
import {
    CheckCircleOutlined,
    CloseCircleOutlined,
} from "@ant-design/icons";

import "./GenerationResult.css";

const { Text, Paragraph } = Typography;

export type GenerationResultStatus =
    | "SUCCESS"
    | "FAILED";

export interface GenerationResultItem {
    id: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    correctOption: "A" | "B" | "C" | "D";
    status?: GenerationResultStatus;
    errorMessage?: string;
}

interface GenerationResultProps {
    data?: GenerationResultItem[];
    loading?: boolean;
}

const optionLabels = {
    A: "A",
    B: "B",
    C: "C",
    D: "D",
};

export default function GenerationResult({
    data = [],
    loading = false,
}: GenerationResultProps) {
    if (loading) {
        return (
            <div className="generation-result">
                <div className="generation-result-loading">
                    <Skeleton active paragraph={{ rows: 5 }} />
                    <Skeleton active paragraph={{ rows: 5 }} />
                </div>
            </div>
        );
    }

    if (data.length === 0) {
        return (
            <div className="generation-result">
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="Chưa có câu hỏi được sinh"
                />
            </div>
        );
    }

    return (
        <div className="generation-result">
            <div className="generation-result-summary">
                <div className="generation-result-summary-item">
                    <Text type="secondary">
                        Tổng số câu hỏi
                    </Text>

                    <Text strong className="generation-result-summary-value">
                        {data.length}
                    </Text>
                </div>

                <div className="generation-result-summary-item">
                    <Text type="secondary">
                        Sinh thành công
                    </Text>

                    <Text
                        strong
                        className="generation-result-summary-value"
                    >
                        {
                            data.filter(
                                (item) =>
                                    item.status !== "FAILED",
                            ).length
                        }
                    </Text>
                </div>

                <div className="generation-result-summary-item">
                    <Text type="secondary">
                        Sinh thất bại
                    </Text>

                    <Text
                        strong
                        className="generation-result-summary-value"
                    >
                        {
                            data.filter(
                                (item) =>
                                    item.status === "FAILED",
                            ).length
                        }
                    </Text>
                </div>
            </div>

            <List
                className="generation-result-list"
                dataSource={data}
                split={false}
                renderItem={(item, index) => {
                    const isFailed = item.status === "FAILED";

                    return (
                        <List.Item className="generation-result-list-item">
                            <Card
                                className="generation-result-card"
                                title={
                                    <Space size={8}>
                                        <Text strong>
                                            Câu {index + 1}
                                        </Text>

                                        {isFailed ? (
                                            <Tag
                                                color="error"
                                                icon={
                                                    <CloseCircleOutlined />
                                                }
                                            >
                                                Thất bại
                                            </Tag>
                                        ) : (
                                            <Tag
                                                color="success"
                                                icon={
                                                    <CheckCircleOutlined />
                                                }
                                            >
                                                Thành công
                                            </Tag>
                                        )}
                                    </Space>
                                }
                            >
                                {isFailed ? (
                                    <div className="generation-result-error">
                                        <Text type="danger">
                                            {item.errorMessage ??
                                                "Không thể sinh câu hỏi."}
                                        </Text>
                                    </div>
                                ) : (
                                    <>
                                        <div className="generation-result-question">
                                            <Text strong>
                                                {item.questionText}
                                            </Text>
                                        </div>

                                        <div className="generation-result-options">
                                            {(
                                                [
                                                    [
                                                        optionLabels.A,
                                                        item.optionA,
                                                    ],
                                                    [
                                                        optionLabels.B,
                                                        item.optionB,
                                                    ],
                                                    [
                                                        optionLabels.C,
                                                        item.optionC,
                                                    ],
                                                    [
                                                        optionLabels.D,
                                                        item.optionD,
                                                    ],
                                                ] as const
                                            ).map(
                                                ([label, content]) => (
                                                    <div
                                                        key={label}
                                                        className={`generation-result-option ${item.correctOption ===
                                                                label
                                                                ? "generation-result-option-correct"
                                                                : ""
                                                            }`}
                                                    >
                                                        <div className="generation-result-option-label">
                                                            <Text strong>
                                                                {label}
                                                            </Text>
                                                        </div>

                                                        <div className="generation-result-option-content">
                                                            <Paragraph>
                                                                {content}
                                                            </Paragraph>
                                                        </div>

                                                        {item.correctOption ===
                                                            label && (
                                                                <Tag
                                                                    color="success"
                                                                    className="generation-result-option-answer"
                                                                >
                                                                    Đáp án đúng
                                                                </Tag>
                                                            )}
                                                    </div>
                                                ),
                                            )}
                                        </div>
                                    </>
                                )}
                            </Card>
                        </List.Item>
                    );
                }}
            />
        </div>
    );
}