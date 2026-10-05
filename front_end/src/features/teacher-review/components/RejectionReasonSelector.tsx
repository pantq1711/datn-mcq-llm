import {
    Checkbox,
    Input,
    Typography,
} from "antd";

import "./RejectionReasonSelector.css";

interface RejectionReason {
    code: string;
    label: string;
}

interface RejectionReasonGroup {
    title: string;
    reasons: RejectionReason[];
}

interface RejectionReasonSelectorProps {
    selectedReasons: string[];
    otherReason: string;
    onReasonsChange: (reasons: string[]) => void;
    onOtherReasonChange: (value: string) => void;
}

const reasonGroups: RejectionReasonGroup[] = [
    {
        title: "1. Lỗi về nội dung câu hỏi",
        reasons: [
            {
                code: "INACCURATE_INFORMATION",
                label: "Câu hỏi chứa thông tin không chính xác",
            },
            {
                code: "WRONG_SUBJECT_KNOWLEDGE",
                label:
                    "Câu hỏi không phù hợp với kiến thức của môn học",
            },
            {
                code: "INSUFFICIENT_INFORMATION",
                label:
                    "Câu hỏi thiếu thông tin để trả lời",
            },
            {
                code: "UNCLEAR_QUESTION",
                label:
                    "Câu hỏi diễn đạt không rõ ràng",
            },
        ],
    },
    {
        title: "2. Lỗi về chất lượng phương án",
        reasons: [
            {
                code: "NO_CORRECT_ANSWER",
                label: "Không có phương án đúng",
            },
            {
                code: "MULTIPLE_CORRECT_ANSWERS",
                label: "Có nhiều phương án đúng",
            },
            {
                code: "OUT_OF_SCOPE_OPTION",
                label:
                    "Phương án không cùng phạm vi/nội dung với câu hỏi",
            },
            {
                code: "UNCLEAR_OPTION_DIFFERENCE",
                label:
                    "Sự khác biệt giữa các phương án không rõ ràng",
            },
        ],
    },
    {
        title: "3. Lỗi trình bày và định dạng",
        reasons: [
            {
                code: "QUESTION_PRESENTATION",
                label:
                    "Hình thức trình bày câu hỏi gây khó khăn cho việc đọc hiểu",
            },
            {
                code: "ANSWER_PRESENTATION",
                label:
                    "Hình thức trình bày đáp án gây khó khăn cho việc đọc hiểu",
            },
            {
                code: "MISSING_QUESTION_CONTENT",
                label:
                    "Thiếu nội dung câu hỏi",
            },
            {
                code: "GRAMMAR_ERROR",
                label: "Lỗi ngữ pháp",
            },
            {
                code: "FORMULA_SYMBOL_ERROR",
                label:
                    "Công thức/kí hiệu bị lỗi",
            },
        ],
    },
];

export default function RejectionReasonSelector({
    selectedReasons,
    otherReason,
    onReasonsChange,
    onOtherReasonChange,
}: RejectionReasonSelectorProps) {
    const handleReasonChange = (
        code: string,
        checked: boolean,
    ) => {
        if (checked) {
            if (!selectedReasons.includes(code)) {
                onReasonsChange([
                    ...selectedReasons,
                    code,
                ]);
            }

            return;
        }

        onReasonsChange(
            selectedReasons.filter(
                (reason) => reason !== code,
            ),
        );
    };

    return (
        <div className="rejection-reason-selector">
            <div className="rejection-reason-groups">
                {reasonGroups.map((group) => (
                    <details
                        key={group.title}
                        className="rejection-reason-group"
                    >
                        <summary className="rejection-reason-summary">
                            {group.title}
                        </summary>

                        <div className="rejection-reason-list">
                            {group.reasons.map((reason) => (
                                <Checkbox
                                    key={reason.code}
                                    checked={selectedReasons.includes(
                                        reason.code,
                                    )}
                                    onChange={(event) =>
                                        handleReasonChange(
                                            reason.code,
                                            event.target.checked,
                                        )
                                    }
                                    className="rejection-reason-checkbox"
                                >
                                    {reason.label}
                                </Checkbox>
                            ))}
                        </div>
                    </details>
                ))}
            </div>

            <div className="rejection-other-reason">
                <Typography.Text strong>
                    Lý do khác
                </Typography.Text>

                <Input.TextArea
                    value={otherReason}
                    onChange={(event) =>
                        onOtherReasonChange(
                            event.target.value,
                        )
                    }
                    placeholder="Nhập lý do khác nếu không có trong danh sách..."
                    autoSize={{
                        minRows: 4,
                        maxRows: 8,
                    }}
                />
            </div>
        </div>
    );
}