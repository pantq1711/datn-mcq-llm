import { Tag, Typography } from "antd";
import { BulbOutlined } from "@ant-design/icons";
import type { ExamQuestionItem } from "../types/exam.types";
import { difficultyConfig, resolveQuestionType } from "../../../utils/exam.utils";
import MathText from "../../../components/common/MathText/MathText";

import "./ExamQuestionList.css";

const { Text, Paragraph } = Typography;

interface QuestionContentProps {
    question: ExamQuestionItem;
    showExplanation?: boolean; // Tùy chọn bật/tắt hiển thị lời giải
}

export default function QuestionContent({ question, showExplanation = true }: QuestionContentProps) {
    const type = resolveQuestionType(question);

    return (
        <div className="exam-question-content-wrapper">
            <Paragraph className="exam-question-item-text">
                <MathText text={question.questionText} />
            </Paragraph>

            {/* Render đáp án theo từng dạng câu */}
            {(() => {
                if (type === "sa") {
                    return (
                        <div className="exam-question-short-answer">
                            <Text strong>Đáp số:</Text> <MathText text={question.saAnswer ?? question.optionA} />
                        </div>
                    );
                }

                if (type === "tf") {
                    return (
                        <div className="exam-question-item-options">
                            {(question.statements ?? []).map((statement) => {
                                const statementDifficulty = statement.difficulty
                                    ? difficultyConfig[statement.difficulty]
                                    : null;
                                return (
                                    <div key={statement.key} className="exam-question-option">
                                        <div className="exam-question-option-label"><Text strong>{statement.key})</Text></div>
                                        <Text className="exam-question-option-text"><MathText text={statement.text} /></Text>
                                        {statementDifficulty && (
                                            <Tag color={statementDifficulty.color}>{statementDifficulty.label}</Tag>
                                        )}
                                        <Tag
                                            color={statement.label === "T" ? "success" : "error"}
                                            className="exam-question-option-answer"
                                        >
                                            {statement.label === "T" ? "Đúng" : "Sai"}
                                        </Tag>
                                    </div>
                                );
                            })}
                        </div>
                    );
                }

                // Mặc định: MCQ (Trắc nghiệm 4 lựa chọn)
                const optionsMap: Record<'A' | 'B' | 'C' | 'D', string | undefined> = {
                    A: question.optionA,
                    B: question.optionB,
                    C: question.optionC,
                    D: question.optionD,
                };
                const keys: ('A' | 'B' | 'C' | 'D')[] = ['A', 'B', 'C', 'D'];

                return (
                    <div className="exam-question-item-options">
                        {keys.map(label => {
                            const isCorrect = label === question.correctOption;
                            return (
                                <div
                                    key={label}
                                    className={`exam-question-option ${isCorrect ? 'exam-question-option-correct' : ''}`}
                                >
                                    <div className="exam-question-option-label"><Text strong>{label}.</Text></div>
                                    <Text className="exam-question-option-text"><MathText text={optionsMap[label]} /></Text>
                                    {isCorrect && (
                                        <Tag color="success" className="exam-question-option-answer">Đáp án đúng</Tag>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                );
            })()}

            {/* Hiển thị lời giải chi tiết nếu có */}
            {showExplanation && question.explanation && (
                <div className="exam-question-explanation">
                    <div className="exam-question-explanation-header">
                        <BulbOutlined className="exam-question-explanation-icon" />
                        <Text strong>Lời giải chi tiết:</Text>
                    </div>
                    <Paragraph className="exam-question-explanation-text">
                        <MathText text={question.explanation} />
                    </Paragraph>
                </div>
            )}
        </div>
    );
}