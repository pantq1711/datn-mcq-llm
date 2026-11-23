import { useEffect, useRef, useState } from "react";
import { Alert, Button, Modal } from "antd";

import MathText from "../../../components/common/MathText/MathText";
import { submitAttempt } from "../services/publicAttemptApi";
import type { AttemptView, DraftAnswers, PublicQuestion, SubmitAnswerPayload, SubmitResult } from "../types/assignment.types";
import { buildSubmitPayload, countAnswered, allQuestions, isAnswered } from "../utils/answers";
import { saveDraft } from "../utils/attemptStorage";
import {
    classifySubmitError,
    MAX_SUBMIT_ATTEMPTS,
    submitWithRetry,
    type SubmitFailureKind,
    type SubmitOutcome,
} from "../utils/submitRetry";
import { formatCountdown } from "../utils/timeFormat";
import { McqInput, SaInput, TfInput } from "./QuestionInputs";
// Tên Phần dùng chung với trang đề của giáo viên và file Word (G25)
import { PART_TITLES } from "../../../utils/exam.utils";

/** Ân hạn nộp sau hạn của lượt (docs/api mục 11.2: 2 phút). */
export const GRACE_MS = 2 * 60 * 1000;
const WARNING_SECONDS = 5 * 60;


/** Lỗi nộp mà thử lại cũng vô ích: máy chủ đã từ chối hẳn. */
const NON_RETRYABLE: SubmitFailureKind[] = ["closed", "expired", "token_invalid"];

type SendFn =(token: string, answers: SubmitAnswerPayload[]) => Promise<SubmitResult>;

type SubmitStatus =
    | { kind: "idle" }
    | { kind: "sending" }
    | { kind: "waiting"; attempt: number; retryAt: number; reason: SubmitFailureKind }
    | { kind: "failed"; reason: SubmitFailureKind; message: string };

interface ExamSessionProps {
    code: string;
    title: string;
    token: string;
    view: AttemptView;
    initialDraft: DraftAnswers;
    /** Máy này không lưu được token / câu trả lời (localStorage bị chặn). */
    storageBlocked?: boolean;
    /** Gửi bài; mặc định gọi API thật. Tách ra để test. */
    send?: SendFn;
    /** Chờ giữa các lần thử lại; mặc định chờ thật. */
    sleep?: (ms: number) => Promise<void>;
    onSubmitted: (outcome: SubmitOutcome) => void;
}

function failureMessage(error: unknown, reason: SubmitFailureKind): string {
    switch (reason) {
        case "closed":
            return "Thầy cô đã đóng bài trước khi em nộp, nên hệ thống không nhận bài nữa. Em báo thầy cô.";
        case "expired":
            return "Đã quá thời gian ân hạn nên hệ thống không nhận bài nữa. Em báo thầy cô.";
        case "token_invalid":
            return "Lượt làm này không còn hợp lệ (có thể thầy cô đã xóa). Em báo thầy cô. Bài làm vẫn còn trên máy này.";
        case "invalid":
            return (error as { response?: { data?: { error?: string } } })?.response?.data?.error
                ?? "Bài làm có chỗ chưa hợp lệ. Em kiểm tra lại các đáp số rồi nộp lại.";
        default:
            return "Chưa nộp được sau nhiều lần thử. Câu trả lời của em vẫn được giữ trên máy. Khi có mạng, em bấm Nộp lại.";
    }
}

function waitingReason(reason: SubmitFailureKind): string {
    if (reason === "rate_limited") return "máy chủ đang bận";
    if (reason === "server") return "máy chủ chưa phản hồi";
    return "mạng đang yếu hoặc bị mất";
}

export default function ExamSession({
    code,
    title,
    token,
    view,
    initialDraft,
    storageBlocked,
    send = submitAttempt,
    sleep,
    onSubmitted,
}: ExamSessionProps) {
    const parts = view.parts ?? [];
    const [draft, setDraft] = useState<DraftAnswers>(initialDraft);
    const [status, setStatus] = useState<SubmitStatus>({ kind: "idle" });
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [paletteOpen, setPaletteOpen] = useState(false);

    // Hạn của lượt tính một lần từ số giây còn lại máy chủ báo; đồng hồ máy chỉ để đếm, máy chủ mới là nơi quyết định
    const [deadlineMs] = useState(() => Date.now() + view.remainingSeconds * 1000);
    const [nowMs, setNowMs] = useState(() => Date.now());
    useEffect(() => {
        const timer = window.setInterval(() => setNowMs(Date.now()), 1000);
        return () => window.clearInterval(timer);
    }, []);

    // Ghi câu trả lời xuống máy sau mỗi lần đổi
    useEffect(() => {
        saveDraft(code, draft);
    }, [code, draft]);

    const submittingRef = useRef(false);
    const unmountedRef = useRef(false);
    useEffect(() => {
        unmountedRef.current = false;
        return () => {
            unmountedRef.current = true;
        };
    }, []);

    const remainingSeconds = Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000));
    const graceLeftMs = deadlineMs + GRACE_MS - nowMs;
    const timeIsUp = remainingSeconds <= 0;

    const questions = allQuestions(parts);
    const answered = countAnswered(parts, draft);
    const unanswered = questions.length - answered;

    const doSubmit = async () => {
        if (submittingRef.current) return;
        submittingRef.current = true;
        setStatus({ kind: "sending" });
        try {
            const payload = buildSubmitPayload(parts, draft);
            const outcome = await submitWithRetry(() => send(token, payload), {
                sleep,
                shouldStop: () => unmountedRef.current,
                onRetry: ({ attempt, waitMs, kind }) =>
                    setStatus({ kind: "waiting", attempt: attempt + 1, retryAt: Date.now() + waitMs, reason: kind }),
            });
            onSubmitted(outcome);
        } catch (error) {
            const reason = classifySubmitError(error);
            if (!unmountedRef.current) setStatus({ kind: "failed", reason, message: failureMessage(error, reason) });
        } finally {
            submittingRef.current = false;
        }
    };

    // Hết giờ thì tự nộp đúng một lần; nếu lần đó không nộp được, em bấm Nộp lại
    const doSubmitRef = useRef(doSubmit);
    useEffect(() => {
        doSubmitRef.current = doSubmit;
    });
    const autoSubmittedRef = useRef(false);
    useEffect(() => {
        if (timeIsUp && !autoSubmittedRef.current) {
            autoSubmittedRef.current = true;
            void doSubmitRef.current();
        }
    }, [timeIsUp]);

    const busy = status.kind === "sending" || status.kind === "waiting";

    const scrollTo = (slotId: number) => {
        setPaletteOpen(false);
        document.getElementById(`q-${slotId}`)?.scrollIntoView?.({ behavior: "smooth", block: "start" });
    };

    const setMcq = (q: PublicQuestion, choice: string | undefined) =>
        setDraft((d) => {
            const mcq = { ...d.mcq };
            if (choice) mcq[String(q.slotId)] = choice;
            else delete mcq[String(q.slotId)];
            return { ...d, mcq };
        });

    const setTf = (q: PublicQuestion, key: string, answer: "T" | "F" | undefined) =>
        setDraft((d) => {
            const row = { ...(d.tf[String(q.slotId)] ?? {}) };
            if (answer) row[key] = answer;
            else delete row[key];
            return { ...d, tf: { ...d.tf, [String(q.slotId)]: row } };
        });

    const setSa = (q: PublicQuestion, text: string) =>
        setDraft((d) => ({ ...d, sa: { ...d.sa, [String(q.slotId)]: text } }));

    return (
        <div className="student-exam">
            <header className={`student-bar${remainingSeconds <= WARNING_SECONDS ? " is-warning" : ""}`}>
                <div className="student-bar-clock" role="timer" aria-label="Thời gian còn lại">
                    <span className="student-bar-label">Còn lại</span> <strong>{formatCountdown(remainingSeconds)}</strong>
                </div>
                <button
                    type="button"
                    className="student-bar-progress"
                    aria-expanded={paletteOpen}
                    onClick={() => setPaletteOpen((open) => !open)}
                >
                    Đã làm {answered}/{questions.length}
                </button>
                <Button type="primary" size="large" disabled={busy} onClick={() => setConfirmOpen(true)}>
                    Nộp bài
                </Button>
            </header>

            {paletteOpen && (
                <nav className="student-palette" aria-label="Danh sách câu">
                    {parts.map((p) => (
                        <div key={p.part} className="student-palette-part">
                            <span className="student-palette-title">Phần {p.part}</span>
                            {p.items.map((q) => (
                                <button
                                    key={q.slotId}
                                    type="button"
                                    className={`student-palette-item${isAnswered(q, draft) ? " is-done" : ""}`}
                                    aria-label={`Phần ${p.part} – Câu ${q.number}${isAnswered(q, draft) ? ", đã làm" : ""}`}
                                    onClick={() => scrollTo(q.slotId)}
                                >
                                    {q.number}
                                </button>
                            ))}
                        </div>
                    ))}
                </nav>
            )}

            <div className="student-notices">
                <h1 className="student-exam-title">{title}</h1>
                {view.duplicateSeat && (
                    <Alert
                        type="warning"
                        showIcon
                        message={`Đã có bạn khác vào với lớp ${view.classLabel}, số ${view.seatNo}. Nếu em nhập nhầm số, em báo thầy cô.`}
                    />
                )}
                {storageBlocked && (
                    <Alert
                        type="warning"
                        showIcon
                        message="Trình duyệt này không lưu được bài làm tạm. Nếu tải lại trang, em sẽ phải làm lại từ đầu. Em đừng đóng hoặc tải lại trang."
                    />
                )}
                {timeIsUp && !(status.kind === "failed" && NON_RETRYABLE.includes(status.reason)) && (
                    <Alert
                        type="warning"
                        showIcon
                        message={
                            graceLeftMs > 0
                                ? `Đã hết giờ. Hệ thống còn nhận bài trong ${formatCountdown(graceLeftMs / 1000)} nữa (ân hạn 2 phút). Em nộp ngay.`
                                : "Đã quá thời gian ân hạn 2 phút. Nếu bài chưa nộp được, em báo thầy cô."
                        }
                    />
                )}
                {status.kind === "sending" && <Alert type="info" showIcon message="Đang nộp bài…" />}
                {status.kind === "waiting" && (
                    <Alert
                        type="warning"
                        showIcon
                        message={`Chưa gửi được vì ${waitingReason(status.reason)}. Tự thử lại sau ${Math.max(
                            1,
                            Math.ceil((status.retryAt - nowMs) / 1000)
                        )} giây (lần ${status.attempt}/${MAX_SUBMIT_ATTEMPTS}).`}
                        description={`Bài làm của em vẫn được giữ trên máy.${
                            timeIsUp && graceLeftMs > 0 ? " Đang trong 2 phút ân hạn, hệ thống vẫn nhận bài." : ""
                        }`}
                    />
                )}
                {status.kind === "failed" && (
                    <Alert
                        type="error"
                        showIcon
                        message={status.message}
                        action={
                            !NON_RETRYABLE.includes(status.reason) ? (
                                <Button size="small" type="primary" onClick={() => void doSubmit()}>
                                    Nộp lại
                                </Button>
                            ) : undefined
                        }
                    />
                )}
            </div>

            {parts.map((p) => (
                <section key={p.part} className="student-part" aria-label={PART_TITLES[p.part] ?? `Phần ${p.part}`}>
                    <h2 className="student-part-title">{PART_TITLES[p.part] ?? `Phần ${p.part}`}</h2>
                    {p.items.map((q) => (
                        <article key={q.slotId} id={`q-${q.slotId}`} className="student-question">
                            <h3 id={`q-${q.slotId}-label`} className="student-question-title">
                                Câu {q.number}
                            </h3>
                            <div className="student-question-text student-math">
                                <MathText text={q.questionText} images={q.images} />
                            </div>
                            {q.questionType === "mcq" && (
                                <McqInput question={q} value={draft.mcq[String(q.slotId)]} onChange={(c) => setMcq(q, c)} />
                            )}
                            {q.questionType === "tf" && (
                                <TfInput
                                    question={q}
                                    value={draft.tf[String(q.slotId)]}
                                    onChange={(key, answer) => setTf(q, key, answer)}
                                />
                            )}
                            {q.questionType === "sa" && (
                                <SaInput question={q} value={draft.sa[String(q.slotId)]} onChange={(text) => setSa(q, text)} />
                            )}
                        </article>
                    ))}
                </section>
            ))}

            <div className="student-submit-area">
                <Button type="primary" size="large" block disabled={busy} onClick={() => setConfirmOpen(true)}>
                    Nộp bài
                </Button>
            </div>

            <Modal
                open={confirmOpen}
                title="Nộp bài?"
                okText="Nộp bài"
                cancelText="Làm tiếp"
                centered
                onOk={() => {
                    setConfirmOpen(false);
                    void doSubmit();
                }}
                onCancel={() => setConfirmOpen(false)}
            >
                <p>
                    {unanswered > 0
                        ? `Em còn ${unanswered} câu chưa làm. Nộp rồi thì không sửa được nữa.`
                        : "Em đã làm hết các câu. Nộp rồi thì không sửa được nữa."}
                </p>
            </Modal>
        </div>
    );
}
