import { Alert, Button } from "antd";

import MathText from "../../../components/common/MathText/MathText";
import type { AttemptView, ReviewItem, ReviewPart } from "../types/assignment.types";
import { formatPoints, formatServerTime } from "../utils/timeFormat";

const PART_TITLES: Record<string, string> = {
    I: "Phần I. Trắc nghiệm nhiều phương án",
    II: "Phần II. Trắc nghiệm đúng sai",
    III: "Phần III. Trả lời ngắn",
};

const TF_WORD: Record<string, string> = { T: "Đúng", F: "Sai" };

interface AttemptOutcomeProps {
    view: AttemptView;
    /** Giáo viên có bật hiện điểm / đáp án (từ trang chào); chỉ để chọn câu chữ khi cả hai tắt. */
    showScore: boolean;
    showSolutions: boolean;
    refreshing?: boolean;
    onRefresh: () => void;
    /** "Xong": xóa token và câu trả lời khỏi máy này (AN-06). */
    onDone: () => void;
}

/**
 * Màn sau khi nộp. Bài còn mở hoặc còn trong ân hạn: chỉ "Đã nộp, kết quả có sau …" (không điểm, không đáp án). Chỉ khi máy
 * chủ báo `resultsReleased` mới hiện điểm theo phần (nếu có `result`) và đáp án + lời giải (nếu có `review`).
 */
export default function AttemptOutcome({ view, showScore, showSolutions, refreshing, onRefresh, onDone }: AttemptOutcomeProps) {
    const identity = `Lớp ${view.classLabel}, số ${view.seatNo}`;

    let body;
    if (!view.submitted) {
        body = (
            <Alert
                type="warning"
                showIcon
                message="Em chưa nộp bài này."
                description="Bài đã đóng nên em không nộp được nữa. Em báo thầy cô."
            />
        );
    } else if (!view.resultsReleased) {
        body = (
            <section className="student-card" aria-live="polite">
                <h1 className="student-heading">
                    {showScore || showSolutions
                        ? `Đã nộp, kết quả có sau ${formatServerTime(view.resultsAt)}`
                        : "Đã nộp bài"}
                </h1>
                <p className="student-muted">
                    {showScore || showSolutions
                        ? "Em không cần làm gì thêm. Đến giờ đó em mở lại link này để xem."
                        : "Thầy cô sẽ thông báo kết quả cho em."}
                </p>
                {(showScore || showSolutions) && (
                    <Button size="large" block loading={refreshing} onClick={onRefresh}>
                        Xem kết quả
                    </Button>
                )}
            </section>
        );
    } else {
        body = (
            <>
                <section className="student-card">
                    <h1 className="student-heading">Đã nộp bài</h1>
                    {view.result ? (
                        <ScoreSummary view={view} />
                    ) : (
                        <p className="student-muted">
                            {view.review ? "Điểm không được công khai. Em xem đáp án và lời giải bên dưới." : "Điểm không được công khai. Em hỏi thầy cô nhé."}
                        </p>
                    )}
                </section>
                {view.review && <ReviewList review={view.review} />}
            </>
        );
    }

    return (
        <div className="student-outcome">
            <p className="student-identity">{identity}</p>
            {body}
            <div className="student-actions student-done">
                <Button size="large" block onClick={onDone}>
                    Xong
                </Button>
                <p className="student-muted student-small">
                    Bấm Xong để xóa bài làm khỏi máy này, nhất là khi em dùng máy chung.
                </p>
            </div>
        </div>
    );
}

function ScoreSummary({ view }: { view: AttemptView }) {
    const result = view.result;
    if (!result) return null;
    return (
        <div className="student-score">
            <p className="student-score-main">
                <strong>{formatPoints(result.score10)}</strong>
                <span> / 10 điểm</span>
            </p>
            <p className="student-muted">
                Điểm thô: {formatPoints(result.score)} / {formatPoints(result.maxScore)}
            </p>
            <ul className="student-score-parts">
                {result.parts.map((p) => (
                    <li key={p.part}>
                        <span>{PART_TITLES[p.part] ?? `Phần ${p.part}`}</span>
                        <span>
                            {p.correct}/{p.total} {p.part === "II" ? "ý" : "câu"} đúng · {formatPoints(p.score)} điểm
                        </span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

function ReviewList({ review }: { review: ReviewPart[] }) {
    return (
        <div className="student-review">
            {review.map((p) => (
                <section key={p.part} className="student-part" aria-label={PART_TITLES[p.part] ?? `Phần ${p.part}`}>
                    <h2 className="student-part-title">{PART_TITLES[p.part] ?? `Phần ${p.part}`}</h2>
                    {p.items.map((item) => (
                        <ReviewCard key={item.slotId} item={item} />
                    ))}
                </section>
            ))}
        </div>
    );
}

function ReviewCard({ item }: { item: ReviewItem }) {
    return (
        <article className="student-question">
            <h3 className="student-question-title">
                Câu {item.number} <span className="student-review-score">· {formatPoints(item.score)} điểm</span>
            </h3>
            <div className="student-question-text student-math">
                <MathText text={item.questionText} />
            </div>

            {item.questionType === "mcq" && (
                <ul className="student-review-options">
                    {Object.entries(item.options ?? {}).map(([key, text]) => {
                        const isCorrect = key === item.correctOption;
                        const isYours = key === item.yourChoice;
                        return (
                            <li
                                key={key}
                                className={`student-review-option${isCorrect ? " is-correct" : ""}${isYours && !isCorrect ? " is-wrong" : ""}`}
                            >
                                <strong>{key}.</strong> <MathText text={text} />
                                {isCorrect && <span className="student-tag"> Đáp án đúng</span>}
                                {isYours && <span className="student-tag"> Em chọn</span>}
                            </li>
                        );
                    })}
                    {!item.yourChoice && <li className="student-muted">Em để trống câu này.</li>}
                </ul>
            )}

            {item.questionType === "tf" && (
                <ul className="student-review-options">
                    {(item.statements ?? []).map((s) => (
                        <li key={s.key} className={`student-review-option${s.correct ? " is-correct" : " is-wrong"}`}>
                            <strong>{s.key})</strong> <MathText text={s.text} />
                            <div className="student-small">
                                Đáp án: <strong>{TF_WORD[s.label] ?? s.label}</strong> · Em chọn:{" "}
                                <strong>{s.yourAnswer ? TF_WORD[s.yourAnswer] ?? s.yourAnswer : "để trống"}</strong>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            {item.questionType === "sa" && (
                <p>
                    Đáp số đúng: <strong>{item.saAnswer}</strong> · Em trả lời: <strong>{item.yourText || "để trống"}</strong>
                </p>
            )}

            {item.explanation && (
                <div className="student-explanation student-math">
                    <strong>Lời giải</strong>
                    <div>
                        <MathText text={item.explanation} />
                    </div>
                </div>
            )}
        </article>
    );
}
