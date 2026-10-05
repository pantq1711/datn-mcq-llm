import { Radio, Space } from "antd";
import type { CorrectAnswer } from "../types/review.types";

import "./CorrectAnswerSelector.css";

interface CorrectAnswerSelectorProps {
    value: CorrectAnswer | null;
    onChange: (value: CorrectAnswer) => void;
}

const answerOptions: {
    value: CorrectAnswer;
    label: string;
}[] = [
        {
            value: "A",
            label: "Phương án A",
        },
        {
            value: "B",
            label: "Phương án B",
        },
        {
            value: "C",
            label: "Phương án C",
        },
        {
            value: "D",
            label: "Phương án D",
        },
        {
            value: "NONE",
            label: "Không có phương án đúng",
        },
        {
            value: "MULTIPLE",
            label: "Có nhiều phương án đúng",
        },
    ];

export default function CorrectAnswerSelector({
    value,
    onChange,
}: CorrectAnswerSelectorProps) {
    return (
        <div className="correct-answer-selector">

            <Radio.Group
                value={value ?? undefined}
                onChange={(event) => {
                    onChange(event.target.value as CorrectAnswer);
                }}
            >
                <Space
                    direction="vertical"
                    size={12}
                    className="correct-answer-options"
                >
                    {answerOptions.map((option) => (
                        <Radio
                            key={option.value}
                            value={option.value}
                            className="correct-answer-option"
                        >
                            {option.label}
                        </Radio>
                    ))}
                </Space>
            </Radio.Group>
        </div>
    );
}