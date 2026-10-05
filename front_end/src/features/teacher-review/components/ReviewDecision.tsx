import {
    Radio,
    Space,
} from "antd";

import type {
    CorrectAnswer,
    ReviewDecisions as ReviewDecisionType,
} from "../types/review.types";

import "./ReviewDecision.css";

interface ReviewDecisionProps {
    value: ReviewDecisionType | null;
    correctAnswer: CorrectAnswer | null;
    onChange: (value: ReviewDecisionType) => void;
}

const decisionOptions: {
    value: ReviewDecisionType;
    label: string;
}[] = [
        {
            value: "EDIT",
            label: "Cần chỉnh sửa nhẹ",
        },
        {
            value: "APPROVE",
            label: "Chấp thuận",
        },
        {
            value: "REJECT",
            label: "Từ chối",
        },
    ];

export default function ReviewDecision({
    value,
    correctAnswer,
    onChange,
}: ReviewDecisionProps) {
    const forceReject =
        correctAnswer === "NONE" ||
        correctAnswer === "MULTIPLE";

    const selectedValue = forceReject
        ? "REJECT"
        : value ?? undefined;

    const handleChange = (
        nextValue: ReviewDecisionType,
    ) => {
        if (forceReject) {
            return;
        }

        onChange(nextValue);
    };

    return (
        <div className="review-decision">

            <Radio.Group
                value={selectedValue}
                onChange={(event) =>
                    handleChange(
                        event.target.value as ReviewDecisionType,
                    )
                }
                disabled={forceReject}
            >
                <Space
                    direction="vertical"
                    size={12}
                    className="review-decision-options"
                >
                    {decisionOptions.map((option) => (
                        <Radio
                            key={option.value}
                            value={option.value}
                            className="review-decision-option"
                        >
                            {option.label}
                        </Radio>
                    ))}
                </Space>
            </Radio.Group>
        </div>
    );
}