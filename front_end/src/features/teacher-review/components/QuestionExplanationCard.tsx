import { Typography } from "antd";
import "./QuestionExplanationCard.css";

interface QuestionExplanationCardProps {
    correctAnswer?: string | null;
    explanation?: string;
}

export default function QuestionExplanationCard({
    correctAnswer,
    explanation,
}: QuestionExplanationCardProps) {
    if (!explanation && !correctAnswer) return null;

    return (
        <div className="question-explanation-wrapper">
            <details className="question-explanation-details">
                <summary className="question-explanation-summary">
                    Xem lời giải
                </summary>
                <div className="question-explanation-content">
                    {correctAnswer && (
                        <div className="explanation-item">
                            <Typography.Text strong>Đáp án đúng: </Typography.Text>
                            <Typography.Text type="success" strong>
                                Phương án {correctAnswer}
                            </Typography.Text>
                        </div>
                    )}
                    {explanation && (
                        <div className="explanation-item">
                            <Typography.Text strong>Lời giải chi tiết: </Typography.Text>
                            <Typography.Paragraph className="explanation-text">
                                {explanation}
                            </Typography.Paragraph>
                        </div>
                    )}
                </div>
            </details>
        </div>
    );
}