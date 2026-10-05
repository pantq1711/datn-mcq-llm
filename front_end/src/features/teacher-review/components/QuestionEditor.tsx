import {
    Input,
    Radio,
    Space,
    Typography,
} from "antd";

import type { QuestionDraft } from "../types/review.types";

import "./QuestionEditor.css";

interface QuestionEditorProps {
    value: QuestionDraft;
    onChange: (value: QuestionDraft) => void;
}

const answerOptions = [
    { value: "A", label: "A" },
    { value: "B", label: "B" },
    { value: "C", label: "C" },
    { value: "D", label: "D" },
] as const;

export default function QuestionEditor({
    value,
    onChange,
}: QuestionEditorProps) {
    const updateField = (
        field: keyof QuestionDraft,
        fieldValue: string,
    ) => {
        onChange({
            ...value,
            [field]: fieldValue,
        });
    };

    return (
        <div className="question-editor">
            {/* Câu hỏi */}
            <div className="question-editor-field">
                <Typography.Text strong>
                    Nội dung câu hỏi
                </Typography.Text>

                <Input.TextArea
                    value={value.questionText}
                    onChange={(event) =>
                        updateField(
                            "questionText",
                            event.target.value,
                        )
                    }
                    placeholder="Nhập nội dung câu hỏi"
                    autoSize={{
                        minRows: 4,
                        maxRows: 8,
                    }}
                />
            </div>

            {/* Phương án A */}
            <div className="question-editor-field">
                <Typography.Text strong>
                    Phương án A
                </Typography.Text>

                <Input.TextArea
                    value={value.optionA}
                    onChange={(event) =>
                        updateField(
                            "optionA",
                            event.target.value,
                        )
                    }
                    placeholder="Nhập phương án A"
                    autoSize={{
                        minRows: 3,
                        maxRows: 6,
                    }}
                />
            </div>

            {/* Phương án B */}
            <div className="question-editor-field">
                <Typography.Text strong>
                    Phương án B
                </Typography.Text>

                <Input.TextArea
                    value={value.optionB}
                    onChange={(event) =>
                        updateField(
                            "optionB",
                            event.target.value,
                        )
                    }
                    placeholder="Nhập phương án B"
                    autoSize={{
                        minRows: 3,
                        maxRows: 6,
                    }}
                />
            </div>

            {/* Phương án C */}
            <div className="question-editor-field">
                <Typography.Text strong>
                    Phương án C
                </Typography.Text>

                <Input.TextArea
                    value={value.optionC}
                    onChange={(event) =>
                        updateField(
                            "optionC",
                            event.target.value,
                        )
                    }
                    placeholder="Nhập phương án C"
                    autoSize={{
                        minRows: 3,
                        maxRows: 6,
                    }}
                />
            </div>

            {/* Phương án D */}
            <div className="question-editor-field">
                <Typography.Text strong>
                    Phương án D
                </Typography.Text>

                <Input.TextArea
                    value={value.optionD}
                    onChange={(event) =>
                        updateField(
                            "optionD",
                            event.target.value,
                        )
                    }
                    placeholder="Nhập phương án D"
                    autoSize={{
                        minRows: 3,
                        maxRows: 6,
                    }}
                />
            </div>

            {/* Đáp án đúng */}
            <div className="question-editor-field">
                <Typography.Text strong>
                    Đáp án đúng sau khi chỉnh sửa
                </Typography.Text>

                <Radio.Group
                    value={value.correctAnswer}
                    onChange={(event) =>
                        updateField(
                            "correctAnswer",
                            event.target.value,
                        )
                    }
                >
                    <Space
                        size={24}
                        wrap
                        className="question-editor-answer-options"
                    >
                        {answerOptions.map((option) => (
                            <Radio
                                key={option.value}
                                value={option.value}
                            >
                                {option.label}
                            </Radio>
                        ))}
                    </Space>
                </Radio.Group>
            </div>
        </div>
    );
}