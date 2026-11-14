import { Typography } from "antd";
import { BulbOutlined } from "@ant-design/icons";
import type { ExamQuestionItem } from "../types/exam.types";
import { describeLevel, resolveQuestionType } from "../../../utils/exam.utils";
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
                                const statementDifficulty = describeLevel(statement.difficulty);
                                return (
                                    <div key={statement.key} className="exam-question-option">
                                        <div className="exam-question-option-label"><Text strong>{statement.key})</Text></div>
                                        <Text className="exam-question-option-text"><MathText text={statement.text} /></Text>
                                        <Text type="secondary">{statementDifficulty.label}</Text>
                                        <Text
                                            strong
                                            type={statement.label === "T" ? "success" : "danger"}
                                            className="exam-question-option-answer"
                                        >
                                            {statement.label === "T" ? "Đúng" : "Sai"}
                                        </Text>
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
                const distractorErrors = question.distractorErrors;

                return (
                    <div className="exam-question-item-options">
                        {keys.map(label => {
                            const isCorrect = label === question.correctOption;
                            // PA1: lỗi sai dẫn tới phương án sai này; ✓ khi Solver khớp, ? khi chưa rõ hoặc chưa kiểm.
                            const error = isCorrect ? undefined : distractorErrors?.[label];
                            const mark = !error ? "" : error.check === "match" ? " ✓"
                                : error.check === "mismatch" ? "" : " ?";
                            return (
                                <div
                                    key={label}
                                    className={`exam-question-option ${isCorrect ? 'exam-question-option-correct' : ''}`}
                                >
                                    <div className="exam-question-option-label"><Text strong>{label}.</Text></div>
                                    <Text className="exam-question-option-text">
                                        <MathText text={optionsMap[label]} />
                                        {error && (
                                            <span className="exam-question-option-error">
                                                Lỗi sai: <MathText text={error.text} />{mark}
                                            </span>
                                        )}
                                    </Text>
                                    {isCorrect && (
                                        <Text strong type="success" className="exam-question-option-answer">Đáp án đúng</Text>
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