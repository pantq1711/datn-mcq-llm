import { Radio, Tag, Typography } from "antd";
import "./StrategySelector.css";

const { Text } = Typography;

export type GenerationStrategy =
    | "ZERO_SHOT"
    | "FEW_SHOT"
    | "STRUCTURED_OUTPUT";

export interface StrategyOption {
    value: GenerationStrategy;
    label: string;
    description: string;
    tag?: string;
}

interface StrategySelectorProps {
    value: GenerationStrategy | null;
    options?: StrategyOption[];
    disabled?: boolean;
    onChange: (value: GenerationStrategy) => void;
}

const defaultStrategies: StrategyOption[] = [
    {
        value: "ZERO_SHOT",
        label: "Zero-shot",
        description:
            "Sinh câu hỏi trực tiếp từ yêu cầu và câu hỏi mẫu mà không cung cấp ví dụ.",
        tag: "C1",
    },
    {
        value: "FEW_SHOT",
        label: "Few-shot",
        description:
            "Cung cấp một số cặp ví dụ đầu vào - đầu ra để định hướng cách mô hình sinh câu hỏi.",
        tag: "C2",
    },
    {
        value: "STRUCTURED_OUTPUT",
        label: "Structured Output",
        description:
            "Yêu cầu mô hình trả về dữ liệu theo cấu trúc JSON Schema được xác định trước.",
        tag: "C3",
    },
];

export default function StrategySelector({
    value,
    options = defaultStrategies,
    disabled = false,
    onChange,
}: StrategySelectorProps) {
    return (
        <div className="strategy-selector">
            <div className="strategy-selector-header">
                <Text strong>Chiến lược sinh</Text>
            </div>

            <Radio.Group
                value={value ?? undefined}
                disabled={disabled}
                onChange={(event) => {
                    onChange(event.target.value as GenerationStrategy);
                }}
                className="strategy-selector-group"
            >
                <div className="strategy-selector-options">
                    {options.map((strategy) => (
                        <Radio
                            key={strategy.value}
                            value={strategy.value}
                            className="strategy-selector-option"
                        >
                            <div className="strategy-selector-option-content">
                                <div className="strategy-selector-option-header">
                                    <Text strong>
                                        {strategy.label}
                                    </Text>

                                    {strategy.tag && (
                                        <Tag>{strategy.tag}</Tag>
                                    )}
                                </div>

                                <Text
                                    type="secondary"
                                    className="strategy-selector-option-description"
                                >
                                    {strategy.description}
                                </Text>
                            </div>
                        </Radio>
                    ))}
                </div>
            </Radio.Group>
        </div>
    );
}